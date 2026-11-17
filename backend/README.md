# Interactive Online Compiler backend

The browser connects only to `ws://localhost:8080/api/v1/execute`. For each browser socket, the backend creates an isolated outbound Piston socket and translates Piston's interactive protocol into the browser protocol.

## Run

1. Run Piston privately (do not publish port 2004 on a public interface). Configure its hard execution limits to match this application, for example `PISTON_COMPILE_TIMEOUT=10000`, `PISTON_RUN_TIMEOUT=600000`, `PISTON_COMPILE_CPU_TIME=10000`, and `PISTON_RUN_CPU_TIME=10000`. Piston enforces CPU limits at its container/job boundary; the browser and Spring timers cannot do that.
2. From `backend`, run `mvn spring-boot:run` (or use the Maven wrapper). On this Windows workspace use `-Dmaven.repo.local=C:\temp\compiler-m2` if Maven cannot handle the space in the workspace path.
3. Open `http://localhost:8080`. The bundled frontend is unchanged visually and uses the backend WebSocket endpoint.

## Browser protocol

`START` requires `language`, `version`, `filename`, and `code`. `INPUT` and `CANCEL` optionally include the execution id; when supplied it must belong to the current socket. Responses are `STATUS`, `OUTPUT`, `ERROR`, `EXIT`, and `TIMEOUT`. `OUTPUT.stream` is `stdout` or `stderr`.

The Piston side uses its documented `/api/v2/connect` messages: `init`, `data` with `stdin`, and `signal`; it consumes `runtime`, `stage`, `data`, and `exit`. There is no Piston event that reliably means “the program is waiting for stdin.” The backend enables input at the `run` stage and its five-minute timer measures absent meaningful input/output activity, including silent computation, rather than claiming to detect a stdin wait.

## Verification

`mvn test` runs the application context plus WebSocket integration tests against an in-process Piston-protocol mock. Coverage includes normal execution, compile and runtime errors, single and sequential stdin, cancellation, inactivity timeout, frontend disconnect, concurrent sessions, and cleanup. It does not prove a local Docker Piston installation; after starting Piston, manually run the Java scanner example in the UI, send two lines, try cancellation, and repeat from two browser windows.
