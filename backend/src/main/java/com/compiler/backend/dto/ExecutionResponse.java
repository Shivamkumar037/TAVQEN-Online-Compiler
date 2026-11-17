package com.compiler.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionResponse {
    private String type;         // "STATUS", "OUTPUT", "ERROR", "EXIT", "TIMEOUT", "INPUT"
    private String executionId;  // always present
    private String status;       // for STATUS messages
    private String message;      // for STATUS, ERROR, TIMEOUT
    private String stream;       // for OUTPUT: "stdout" or "stderr"
    private String data;         // for OUTPUT and INPUT echo
    private Integer code;        // for EXIT
    private String signal;       // for EXIT
    private String stage;        // for EXIT: "compile" or "run"
}
