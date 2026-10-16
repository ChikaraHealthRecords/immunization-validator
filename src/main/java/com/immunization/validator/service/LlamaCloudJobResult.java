package com.immunization.validator.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of polling a LlamaCloud extraction job's status.
 * resultJson is populated only when state == SUCCESS.
 * errorMessage is populated only when state == ERROR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlamaCloudJobResult {
    private LlamaCloudJobState state;
    private String resultJson;
    private String errorMessage;
}
