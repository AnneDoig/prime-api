package com.doig.primeapi.service;

import com.doig.primeapi.model.PrimeResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PrimeService {

    private static final String ALGO_TRIAL = "trial";
    private static final String ALGO_SIEVE = "sieve";
    private static final String ALGO_SEGMENTED_SIEVE = "segmented-sieve";
    private static final String ALGO_AUTO = "auto";
    private static final int AUTO_SIEVE_THRESHOLD = 10_000;
    private static final int AUTO_SEGMENTED_SIEVE_THRESHOLD = 200_000;

    // Prevent Swagger/UI and JSON/XML serialization from becoming unmanageable.
    private static final int MAX_FULL_RESULT_UP_TO = 200_000;

    private final Map<String, TreeMap<Integer, List<Integer>>> cacheByAlgorithm = new HashMap<>();

    // defaults algorithm to auto.
    // May appear as "no usages" in IDE, but used as a convenience API for calls/tests that only provide upTo.
    public synchronized PrimeResult getPrimesUpTo(int n) {
        return getPrimesUpTo(n, ALGO_AUTO);
    }

    public synchronized PrimeResult getPrimesUpTo(int n, String algorithm) {
        validateUpperBound(n);

        String requestedAlgorithm = normalizeRequestedAlgorithm(algorithm);
        String resolvedAlgorithm = resolveAlgorithm(requestedAlgorithm, n);
        String algorithmDisplay = requestedAlgorithm.equals(ALGO_AUTO)
                ? ALGO_AUTO + "/" + resolvedAlgorithm
                : requestedAlgorithm;

        TreeMap<Integer, List<Integer>> cache =
                cacheByAlgorithm.computeIfAbsent(resolvedAlgorithm, ignored -> new TreeMap<>());

        // Exact cache hit.
        if (cache.containsKey(n)) {
            System.out.println("CACHED_EXACT -> returning cached primes for " + n);
            List<Integer> cached = cache.get(n);
            return new PrimeResult(
                    n,
                    "CACHED_EXACT",
                    requestedAlgorithm,
                    resolvedAlgorithm,
                    algorithmDisplay,
                    cached.size(),
                    cached
            );
        }

        // Reuse a larger cached result by filtering values <= n.
        Integer ceiling = cache.ceilingKey(n);
        if (ceiling != null) {
            List<Integer> filtered = cache.get(ceiling).stream()
                    .filter(p -> p <= n)
                    .toList();

            cache.put(n, filtered);
            System.out.println("CACHED_FILTERED -> filtered primes from cached(" + ceiling + ") for " + n);
            return new PrimeResult(
                    n,
                    "CACHED_FILTERED (from " + ceiling + ")",
                    requestedAlgorithm,
                    resolvedAlgorithm,
                    algorithmDisplay,
                    filtered.size(),
                    filtered
            );
        }

        // Reuse a smaller cached result and compute only the missing tail.
        Integer floor = cache.floorKey(n);
        List<Integer> result;
        String source;

        if (floor != null) {
            result = new ArrayList<>(cache.get(floor));
            for (int i = floor + 1; i <= n; i++) {
                if (isPrime(i)) {
                    result.add(i);
                }
            }
            source = "CACHED_EXTENDED (from " + floor + ")";
            System.out.println("CACHED_EXTENDED -> extended primes from cached(" + floor + ") up to " + n);
        } else {
            result = computePrimes(n, resolvedAlgorithm);
            source = "COMPUTED";
            System.out.println("COMPUTED -> calculated all primes up to " + n + " using " + resolvedAlgorithm);
        }

        cache.put(n, result);
        return new PrimeResult(
                n,
                source,
                requestedAlgorithm,
                resolvedAlgorithm,
                algorithmDisplay,
                result.size(),
                result
        );
    }

    private void validateUpperBound(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("upTo must be a positive integer");
        }
        if (n > MAX_FULL_RESULT_UP_TO) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "upTo number is too large for a full prime list response. " +
                            "Enter a value of 200_000 or less"
            );
        }
    }

    private boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i <= n / i; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    private String resolveAlgorithm(String requestedAlgorithm, int n) {
        String normalized = normalizeRequestedAlgorithm(requestedAlgorithm);
        return switch (normalized) {
            case ALGO_AUTO -> n >= AUTO_SEGMENTED_SIEVE_THRESHOLD
                    ? ALGO_SEGMENTED_SIEVE
                    : n >= AUTO_SIEVE_THRESHOLD
                      ? ALGO_SIEVE
                      : ALGO_TRIAL;
            case ALGO_SIEVE, ALGO_SEGMENTED_SIEVE, ALGO_TRIAL -> normalized;
            default -> throw new IllegalArgumentException(
                    "Unsupported algorithm: " + requestedAlgorithm + ". Supported values: auto, trial, sieve, segmented-sieve"
            );
        };
    }

    private String normalizeRequestedAlgorithm(String requestedAlgorithm) {
        return requestedAlgorithm == null ? ALGO_AUTO : requestedAlgorithm.trim().toLowerCase(Locale.ROOT);
    }

    private List<Integer> computePrimes(int n, String resolvedAlgorithm) {
        return switch (resolvedAlgorithm) {
            case ALGO_SIEVE -> computePrimesBySieve(n);
            case ALGO_SEGMENTED_SIEVE -> computePrimesBySegmentedSieve(n);
            default -> computePrimesByTrialDivision(n);
        };
    }

    private List<Integer> computePrimesByTrialDivision(int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = 2; i <= n; i++) {
            if (isPrime(i)) {
                result.add(i);
            }
        }
        return result;
    }

    private List<Integer> computePrimesBySieve(int n) {
        if (n < 2) {
            return List.of();
        }

        boolean[] composite = new boolean[n + 1];
        for (int p = 2; p * p <= n; p++) {
            if (!composite[p]) {
                for (int multiple = p * p; multiple <= n; multiple += p) {
                    composite[multiple] = true;
                }
            }
        }

        List<Integer> primes = new ArrayList<>();
        for (int i = 2; i <= n; i++) {
            if (!composite[i]) {
                primes.add(i);
            }
        }
        return primes;
    }

    private List<Integer> computePrimesBySegmentedSieve(int n) {
        if (n < 2) {
            return List.of();
        }

        int limit = (int) Math.sqrt(n);
        List<Integer> basePrimes = computePrimesBySieve(limit);
        List<Integer> primes = new ArrayList<>(basePrimes);

        int low = Math.max(limit + 1, 2);
        int segmentSize = Math.max(32_768, limit + 1);

        while (low <= n) {
            int high = Math.min(low + segmentSize - 1, n);
            boolean[] composite = new boolean[high - low + 1];

            for (int prime : basePrimes) {
                long start = Math.max((long) prime * prime, ((long) ((low + prime - 1) / prime)) * prime);
                for (long value = start; value <= high; value += prime) {
                    composite[(int) (value - low)] = true;
                }
            }

            for (int i = 0; i < composite.length; i++) {
                if (!composite[i]) {
                    primes.add(low + i);
                }
            }

            low = high + 1;
        }

        return primes;
    }
}