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
public class ExecutionRequest {
    private String type;         // "START", "INPUT", "CANCEL"
    private String executionId;  // present in INPUT and CANCEL
    private String language;     // present in START
    private String version;      // present in START
    private String filename;     // present in START
    private String code;         // present in START
    private String data;         // present in INPUT (the stdin text)
}
