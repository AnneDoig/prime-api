package com.doig.primeapi.controller;

import com.doig.primeapi.model.ApiError;
import com.doig.primeapi.model.PrimeResult;
import com.doig.primeapi.service.PrimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// REST controller for prime-number endpoints.
@RestController
@RequestMapping("/api")
public class PrimeController {

    // Business logic lives in the service layer; the controller handles HTTP concerns only.
    private final PrimeService primeService;

    public PrimeController(PrimeService primeService) {
        this.primeService = primeService;
    }

    // Returns a paginated prime list and supports both JSON and XML responses.
    @GetMapping(value = "/primes", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE
    })
    @Operation(
            summary = "Get primes up to N with pagination",
            description = "Returns prime numbers up to a given upper bound. Results are paginated to avoid large payloads."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Success",
            content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = PrimeResult.class)),
                    @Content(mediaType = "application/xml", schema = @Schema(implementation = PrimeResult.class))
            }
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid input (e.g. negative/zero upTo, invalid algorithm, invalid page/size, or upTo above configured cap)",
            content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)),
                    @Content(mediaType = "application/xml", schema = @Schema(implementation = ApiError.class))
            }
    )
    public ResponseEntity<PrimeResult> getPrimes(
            // Upper bound for prime calculation.
            @RequestParam
            @Schema(example = "100", description = "Must be a positive integer")
            int upTo,

            // Requested algorithm, or auto-selection based on input size.
            @RequestParam(defaultValue = "auto")
            @Schema(allowableValues = {"auto", "trial", "sieve", "segmented-sieve"})
            String algorithm,

            // 1-based page number for the paginated response.
            @RequestParam(defaultValue = "1")
            @Schema(example = "1", description = "Page number (1-based). See response field maxPageNumber for the highest valid value.")
            int page,

            // Number of primes to return in the current page.
            @RequestParam(defaultValue = "100")
            @Schema(example = "100", description = "Page size (1..1000)")
            int size
    ) {
        // No local try/catch: GlobalExceptionHandler keeps ApiError response shape consistent.
        return ResponseEntity.ok(primeService.getPrimesUpTo(upTo, algorithm, page, size));
    }
}