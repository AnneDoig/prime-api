package com.doig.primeapi.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// This class holds example error details returned to API users when something goes wrong.

// This model may show "no usages" in IDE, but it is used at runtime when exceptions are returned as API responses.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Standard API error response")
public class ApiError {

    @Schema(example = "2026-05-09T20:12:34.567+01:00")
    private String timestamp;

    @Schema(example = "400")
    private int status;

    @Schema(example = "Bad Request")
    private String error;

    @Schema(example = "upTo must be a positive integer")
    private String message;

    @Schema(example = "/api/primes")
    private String path;
}