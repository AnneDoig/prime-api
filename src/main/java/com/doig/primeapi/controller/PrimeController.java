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
import org.springframework.web.server.ResponseStatusException;


//RestController is required so methods return serialized response bodies (JSON/XML)
// instead of resolving a view template.
//RequestMapping sets a shared base path. All endpoints in this class are under  /api.
@RestController
@RequestMapping("/api")
public class PrimeController {
    private final PrimeService primeService;
    public PrimeController(PrimeService primeService) {
        this.primeService = primeService;
    }
    @GetMapping(value = "/primes", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Get all primes up to N",
            description = "Returns primes with cache metadata (COMPUTED, CACHED_EXACT, CACHED_FILTERED, CACHED_EXTENDED)")
    @ApiResponse(responseCode = "200", description = "Success",
            content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PrimeResult.class)),
                    @Content(mediaType = "application/xml",
                            schema = @Schema(implementation = PrimeResult.class))
    })
    @ApiResponse(
            responseCode = "400",
            description = "Invalid input e.g.non-integer, negative, zero, large requests of 1_000_000 or more",
            content = {
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)
                    ),
                    @Content(
                            mediaType = "application/xml",
                            schema = @Schema(implementation = ApiError.class)
                    )
            }
    )
    public ResponseEntity<PrimeResult> getPrimes(
            @RequestParam
            @Schema(example = "100", description = "Must be positive integer")
            int upTo,
            @RequestParam(defaultValue = "auto")
            @Schema(allowableValues = {"auto", "trial", "sieve", "segmented-sieve"})
            String algorithm) {
        try {
            return ResponseEntity.ok(primeService.getPrimesUpTo(upTo, algorithm));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
