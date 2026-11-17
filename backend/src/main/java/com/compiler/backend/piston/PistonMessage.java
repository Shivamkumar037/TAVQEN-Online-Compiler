package com.compiler.backend.piston;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a raw message in the Piston WebSocket protocol.
 * Piston v2 message types:
 *   Incoming (from Piston):
 *     - "runtime" : language/version confirmation
 *     - "stage"   : {"stage": "compile"|"run"} — stage started
 *     - "data"    : {"stream": "stdout"|"stderr", "data": "..."} — output
 *     - "exit"    : {"stage": "compile"|"run", "code": N, "signal": S|null} — stage ended
 *   Outgoing (to Piston):
 *     - "init"    : {"language": "...", "version": "...", "files": [...], "run_timeout": N, "compile_timeout": N}
 *     - "data"    : {"stream": "stdin", "data": "..."}
 *     - "signal"  : {"signal": "SIGKILL"}
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PistonMessage {
    private String type;      // "runtime", "stage", "data", "exit", "init", "signal"
    private String language;  // init
    private String version;   // init, runtime
    private String stage;     // stage, exit
    private String stream;    // data ("stdout", "stderr", "stdin")
    private String data;      // data
    private Integer code;     // exit
    private String signal;    // exit, signal
    private Long runTimeout;  // init
    private Long compileTimeout; // init
    private PistonFileEntry[] files;   // init
    private Long runCpuTime;
    private Long compileCpuTime;
}
