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
@Schema(description = "Prime number computation result with cache metadata")
public class PrimeResult {

    @Schema(example = "200", description = "Requested upper bound")
    private int upTo;

    @Schema(example = "COMPUTED", description = "Where the response data came from")
    private String source;

    @Schema(example = "auto", description = "Algorithm requested by the caller")
    private String requestedAlgorithm;

    @Schema(example = "sieve", description = "Algorithm actually used to compute the primes")
    private String resolvedAlgorithm;

    @Schema(example = "Sieve of Eratosthenes", description = "Name of the used algorithm")
    private String algorithmDisplay;

    @Schema(example = "46", description = "How many primes were found")
    private int primeCount;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "primes")
    @Schema(description = "Prime numbers up to the requested upper bound")
    private List<Integer> primes;
}
