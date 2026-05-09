package com.doig.primeapi;

import com.doig.primeapi.model.PrimeResult;
import com.doig.primeapi.service.PrimeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimeServiceTest {

    private final PrimeService primeService = new PrimeService();

    // Expected Basic functionality: computes primes up to 20 correctly (2, 3, 5, 7, 11, 13, 17, 19)
    @Test
    void getPrimesUpTo20_returnsExpectedPrimes() {
        PrimeResult result = primeService.getPrimesUpTo(20, "auto");

        assertEquals(20, result.getUpTo());
        assertEquals(8, result.getPrimeCount());
        assertEquals(2, result.getPrimes().get(0));
        assertEquals(19, result.getPrimes().get(result.getPrimes().size() - 1));
        assertTrue(result.getPrimes().contains(13));
    }

    // Error handling: rejects unsupported algorithm names
    @Test
    void invalidAlgorithm_throwsIllegalArgumentException() {
        try {
            primeService.getPrimesUpTo(20, "bad-algo");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unsupported algorithm"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    // Cache behaviour: same request returns CACHED_EXACT on second call
    @Test
    void getPrimesUpTo20Twice_secondCallUsesCachedExact() {
        primeService.getPrimesUpTo(20, "trial");
        PrimeResult secondCall = primeService.getPrimesUpTo(20, "trial");

        assertEquals("CACHED_EXACT", secondCall.getSource());
        assertEquals(8, secondCall.getPrimeCount());
    }

    // Cache behaviour: smaller request filters larger cached result
    @Test
    void getPrimesUpTo20ThenUpTo15_upTo15UsesCachedFiltered() {
        primeService.getPrimesUpTo(20, "sieve");
        PrimeResult result = primeService.getPrimesUpTo(15, "sieve");

        assertTrue(result.getSource().contains("CACHED_FILTERED"));
        assertEquals(6, result.getPrimeCount());
        assertTrue(result.getPrimes().stream().allMatch(p -> p <= 15));
    }

    // Cache behaviour: larger request extends smaller cached result
    @Test
    void getPrimesUpTo10ThenUpTo20_upTo20UsesCachedExtended() {
        primeService.getPrimesUpTo(10, "trial");
        PrimeResult result = primeService.getPrimesUpTo(20, "trial");

        assertTrue(result.getSource().contains("CACHED_EXTENDED"));
        assertEquals(8, result.getPrimeCount());
    }

    // Validation: rejects invalid upTo=0
    @Test
    void upToZero_throwsIllegalArgumentException() {
        try {
            primeService.getPrimesUpTo(0, "auto");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("positive"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException for upTo=0");
    }

    //
    @Test
    void autoAlgorithmSelectsCorrectly() {
        // Below 10_000 should use trial
        PrimeResult small = primeService.getPrimesUpTo(100, "auto");
        assertEquals("trial", small.getResolvedAlgorithm());

        // 10_000 to 200_000 should use sieve
        PrimeResult medium = primeService.getPrimesUpTo(50_000, "auto");
        assertEquals("sieve", medium.getResolvedAlgorithm());

        // Above 200_000 should use segmented-sieve
        PrimeResult large = primeService.getPrimesUpTo(500_000, "auto");
        assertEquals("segmented-sieve", large.getResolvedAlgorithm());
    }
    // Validation: rejects values above MAX_FULL_RESULT_UP_TO (e.g. 2_000_000)
    @Test
    void upToTooLarge_throwsResponseStatusException() {
        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> primeService.getPrimesUpTo(2_000_000, "auto")
                );
        assertTrue(ex.getReason().contains("too large"));
    }
}