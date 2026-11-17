package com.compiler.backend;

import com.compiler.backend.dto.ExecutionRequest;
import com.compiler.backend.dto.ExecutionResponse;
import com.compiler.backend.execution.ExecutionSessionManager;
import com.compiler.backend.mock.FrontendWebSocketTestClient;
import com.compiler.backend.mock.MockPistonWebSocketHandler;
import com.compiler.backend.mock.TestWebSocketConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18080",
        "compiler.piston.websocket-url=ws://localhost:18080/mock/piston/api/v2/connect",
        "compiler.timeout.input-inactivity=1500",
        "compiler.timeout.maximum-execution=5000"})
@Import(TestWebSocketConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class InteractiveExecutionWebSocketIntegrationTest {

    private final int port = 18080;

    @Autowired
    private MockPistonWebSocketHandler mockPiston;

    @Autowired
    private ExecutionSessionManager sessionManager;

    private final List<FrontendWebSocketTestClient> openClients = new ArrayList<>();

    private String getBackendWsUrl() {
        return "ws://localhost:" + port + "/api/v1/execute";
    }

    private String getPistonMockWsUrl() {
        return "ws://localhost:" + port + "/mock/piston/api/v2/connect";
    }


    @AfterEach
    void tearDown() {
        for (FrontendWebSocketTestClient client : openClients) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        openClients.clear();
    }

    private FrontendWebSocketTestClient createAndConnectClient() throws Exception {
        FrontendWebSocketTestClient client = FrontendWebSocketTestClient.connect(getBackendWsUrl());
        openClients.add(client);
        return client;
    }

    @Test
    @DisplayName("Test 4 & 5: Normal Java code execution flow (Piston WS connection & standard output)")
    void testNormalJavaExecution() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.NORMAL_JAVA);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { public static void main(String[] args) { System.out.println(\"Hello World\"); } }")
                .build();

        client.sendRequest(startRequest);

        // 1. Initial STATUS
        ExecutionResponse initStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        assertNotNull(initStatus);
        assertEquals("INITIALIZED", initStatus.getStatus());
        String executionId = initStatus.getExecutionId();
        assertNotNull(executionId);
        assertFalse(executionId.isBlank());

        // 2. Output
        ExecutionResponse output = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(output);
        assertEquals("stdout", output.getStream());
        assertEquals("Hello World\n", output.getData());

        // 3. Exit
        ExecutionResponse exit = client.waitForType("EXIT", Duration.ofSeconds(5));
        assertNotNull(exit);
        assertEquals("run", exit.getStage());
        assertEquals(0, exit.getCode());

        // 4. Final STATUS
        ExecutionResponse completedStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        assertNotNull(completedStatus);
        assertEquals("COMPLETED", completedStatus.getStatus());

        // Verify session cleanup
        Thread.sleep(200);
        assertNull(sessionManager.getSession(executionId));
    }

    @Test
    @DisplayName("Test 6: Compilation error handling")
    void testCompilationError() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.COMPILATION_ERROR);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { syntax error }")
                .build();

        client.sendRequest(startRequest);

        // 1. Output with compiler error on stderr
        ExecutionResponse errorOutput = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(errorOutput);
        assertEquals("stderr", errorOutput.getStream());
        assertTrue(errorOutput.getData().contains("error: cannot find symbol"));

        // 2. Final STATUS indicates FAILED with non-zero exit code
        ExecutionResponse failedStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        assertNotNull(failedStatus);
        assertEquals("FAILED", failedStatus.getStatus());

        // Verify session cleanup
        Thread.sleep(200);
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Test 7: Runtime exception handling")
    void testRuntimeException() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.RUNTIME_EXCEPTION);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { public static void main(String[] args) { int x = 1 / 0; } }")
                .build();

        client.sendRequest(startRequest);

        // 1. Output with exception stack trace on stderr
        ExecutionResponse errorOutput = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(errorOutput);
        assertEquals("stderr", errorOutput.getStream());
        assertTrue(errorOutput.getData().contains("ArithmeticException"));

        // 2. Exit event with code 1
        ExecutionResponse exit = client.waitForType("EXIT", Duration.ofSeconds(5));
        assertNotNull(exit);
        assertEquals("run", exit.getStage());
        assertEquals(1, exit.getCode());

        // 3. Final STATUS indicates FAILED
        ExecutionResponse failedStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        assertNotNull(failedStatus);
        assertEquals("FAILED", failedStatus.getStatus());

        // Verify session cleanup
        Thread.sleep(200);
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Test 8: Interactive runtime input (single Scanner prompt)")
    void testInteractiveScannerSingleInput() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.SCANNER_INPUT_SINGLE);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { ... }")
                .build();

        client.sendRequest(startRequest);

        // Wait for prompt output: "Enter your name:\n"
        ExecutionResponse promptOutput = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(promptOutput);
        assertEquals("Enter your name:\n", promptOutput.getData());
        String executionId = promptOutput.getExecutionId();

        // Send runtime input from user
        ExecutionRequest inputRequest = ExecutionRequest.builder()
                .type("INPUT")
                .executionId(executionId)
                .data("John Doe")
                .build();
        client.sendRequest(inputRequest);

        // Receive program output continuing after input
        ExecutionResponse afterInputOutput = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(afterInputOutput);
        assertEquals("Welcome John Doe\n", afterInputOutput.getData());

        // Program completes
        ExecutionResponse exit = client.waitForType("EXIT", Duration.ofSeconds(5));
        assertNotNull(exit);
        assertEquals(0, exit.getCode());

        // Session cleaned up
        Thread.sleep(200);
        assertNull(sessionManager.getSession(executionId));
    }

    @Test
    @DisplayName("Test 9: Multiple runtime inputs in one execution (Scanner name and age)")
    void testInteractiveScannerMultipleInputs() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.SCANNER_INPUT_MULTIPLE);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("import java.util.Scanner; public class Main { ... }")
                .build();

        client.sendRequest(startRequest);

        // 1. Initial output: "Hello World\nEnter your name:\n"
        ExecutionResponse prompt1 = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(prompt1);
        assertTrue(prompt1.getData().contains("Enter your name:"));
        String executionId = prompt1.getExecutionId();

        // 2. Send input 1: "Alice"
        ExecutionRequest input1 = ExecutionRequest.builder()
                .type("INPUT")
                .executionId(executionId)
                .data("Alice")
                .build();
        client.sendRequest(input1);

        // 3. Output responding to input 1 & requesting input 2: "Welcome Alice\nEnter your age:\n"
        ExecutionResponse prompt2 = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(prompt2);
        assertTrue(prompt2.getData().contains("Welcome Alice"));
        assertTrue(prompt2.getData().contains("Enter your age:"));

        // 4. Send input 2: "22"
        ExecutionRequest input2 = ExecutionRequest.builder()
                .type("INPUT")
                .executionId(executionId)
                .data("22")
                .build();
        client.sendRequest(input2);

        // 5. Final output: "Your age is 22\n"
        ExecutionResponse finalOutput = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(finalOutput);
        assertEquals("Your age is 22\n", finalOutput.getData());

        // 6. Normal exit
        ExecutionResponse exit = client.waitForType("EXIT", Duration.ofSeconds(5));
        assertNotNull(exit);
        assertEquals(0, exit.getCode());

        // Verify session cleanup
        Thread.sleep(200);
        assertNull(sessionManager.getSession(executionId));
    }

    @Test
    @DisplayName("Test 10: Two or more simultaneous executions from different users")
    void testSimultaneousExecutions() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.NORMAL_JAVA);

        FrontendWebSocketTestClient client1 = createAndConnectClient();
        FrontendWebSocketTestClient client2 = createAndConnectClient();

        ExecutionRequest req1 = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main1.java")
                .code("public class Main1 { public static void main(String[] args) {} }")
                .build();

        ExecutionRequest req2 = ExecutionRequest.builder()
                .type("START")
                .language("python")
                .version("3.10.0")
                .filename("main.py")
                .code("print('Hello from Python')")
                .build();

        // Start both concurrently
        client1.sendRequest(req1);
        client2.sendRequest(req2);

        ExecutionResponse init1 = client1.waitForType("STATUS", Duration.ofSeconds(5));
        ExecutionResponse init2 = client2.waitForType("STATUS", Duration.ofSeconds(5));

        assertNotNull(init1);
        assertNotNull(init2);
        assertNotEquals(init1.getExecutionId(), init2.getExecutionId(), "Execution IDs must be distinct");

        // Both complete independently
        ExecutionResponse exit1 = client1.waitForType("EXIT", Duration.ofSeconds(5));
        ExecutionResponse exit2 = client2.waitForType("EXIT", Duration.ofSeconds(5));

        assertNotNull(exit1);
        assertNotNull(exit2);
        assertEquals(init1.getExecutionId(), exit1.getExecutionId());
        assertEquals(init2.getExecutionId(), exit2.getExecutionId());

        // Both cleaned up
        Thread.sleep(300);
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Test 11: Inactivity timeout and resource cleanup")
    void testInactivityTimeoutAndCleanup() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.INACTIVITY_HANG);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { public static void main(String[] args) throws Exception { Thread.sleep(999999); } }")
                .build();

        client.sendRequest(startRequest);

        // Receive initial output: "Waiting forever for input...\n"
        ExecutionResponse output = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(output);
        String executionId = output.getExecutionId();

        // Session is registered in manager
        assertNotNull(sessionManager.getSession(executionId));

        // Wait for inactivity timeout to trigger (configured to 1500ms in test)
        ExecutionResponse timeout = client.waitForType("TIMEOUT", Duration.ofSeconds(6));
        assertNotNull(timeout);
        assertEquals("TIMEOUT", timeout.getType());
        assertEquals(executionId, timeout.getExecutionId());

        // Also receives ERROR message as per specification
        ExecutionResponse error = client.waitForType("ERROR", Duration.ofSeconds(5));
        assertNotNull(error);
        assertTrue(error.getMessage().contains("inactivity timeout"));

        // Verify session was cleanly terminated and removed from memory
        Thread.sleep(300);
        assertNull(sessionManager.getSession(executionId));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Test 12: User cancellation (CANCEL message)")
    void testUserCancellation() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.INACTIVITY_HANG);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { ... }")
                .build();

        client.sendRequest(startRequest);

        ExecutionResponse output = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(output);
        String executionId = output.getExecutionId();

        // User sends CANCEL
        ExecutionRequest cancelRequest = ExecutionRequest.builder()
                .type("CANCEL")
                .executionId(executionId)
                .build();
        client.sendRequest(cancelRequest);

        // Client receives CANCELLED status
        ExecutionResponse cancelledStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        if (!"CANCELLED".equals(cancelledStatus.getStatus())) {
            cancelledStatus = client.waitForType("STATUS", Duration.ofSeconds(5));
        }
        assertNotNull(cancelledStatus);
        assertEquals("CANCELLED", cancelledStatus.getStatus());

        // Verify session is cleaned up
        Thread.sleep(200);
        assertNull(sessionManager.getSession(executionId));
    }

    @Test
    @DisplayName("Test 13: Frontend client disconnection triggers cleanup")
    void testFrontendDisconnectCleanup() throws Exception {
        mockPiston.setBehavior(MockPistonWebSocketHandler.Behavior.INACTIVITY_HANG);
        FrontendWebSocketTestClient client = createAndConnectClient();

        ExecutionRequest startRequest = ExecutionRequest.builder()
                .type("START")
                .language("java")
                .version("15.0.2")
                .filename("Main.java")
                .code("public class Main { ... }")
                .build();

        client.sendRequest(startRequest);

        ExecutionResponse output = client.waitForType("OUTPUT", Duration.ofSeconds(5));
        assertNotNull(output);
        String executionId = output.getExecutionId();
        assertNotNull(sessionManager.getSession(executionId));

        // Disconnect frontend WebSocket
        client.close();

        // Allow disconnect handler to run
        Thread.sleep(500);

        // Verify session was removed and not left in memory
        assertNull(sessionManager.getSession(executionId));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }
}
