package com.doig.primeapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JacksonXmlRootElement(localName = "PrimeResult")
@Schema(description = "Prime number computation result with cache and pagination metadata")
public class PrimeResult {

    // Original request upper bound.
    @Schema(example = "200", description = "Requested upper bound")
    private int upTo;

    // Cache/source metadata showing whether the result was computed or reused.
    @Schema(example = "CACHED_EXTENDED (from 100000)", description = "Where the response data came from")
    private String source;

    // Algorithm metadata showing what was requested and what was actually used.
    @Schema(example = "auto", description = "Algorithm requested by the caller")
    private String requestedAlgorithm;

    @Schema(example = "sieve", description = "Algorithm actually used to compute the primes")
    private String resolvedAlgorithm;

    @Schema(example = "auto/sieve", description = "Requested/resolved algorithm display")
    private String algorithmDisplay;

    // Pagination summary for the full result set and the current page.
    @Schema(example = "46", description = "Total number of primes found before pagination")
    private int primeCount;

    @Schema(example = "10", description = "Number of primes returned in this page")
    private int pagePrimeCount;

    @Schema(example = "1", description = "Current page number (1-based)")
    private int page;

    @Schema(example = "100", description = "Requested page size")
    private int size;

    @Schema(example = "1", description = "Total number of pages available")
    private int totalPages;

    @Schema(example = "3", description = "Highest valid page number (1-based) for this result")
    private int maxPageNumber;

    @Schema(example = "false", description = "Whether there is another page after this one")
    private boolean hasNext;

    // Only the primes for the requested page are returned here.
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "primes")
    @Schema(description = "Prime numbers for the current page")
    private List<Integer> primes;
}