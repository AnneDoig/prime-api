package com.doig.primeapi.service;

import com.doig.primeapi.model.PrimeResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class PrimeService {

    private static final String ALGO_TRIAL = "trial";
    private static final String ALGO_SIEVE = "sieve";
    private static final String ALGO_SEGMENTED_SIEVE = "segmented-sieve";
    private static final String ALGO_AUTO = "auto";

    private static final int AUTO_SIEVE_THRESHOLD = 10_000;
    private static final int AUTO_SEGMENTED_SIEVE_THRESHOLD = 200_000;

    private static final int MAX_FULL_RESULT_UP_TO = 200_000;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;

    private final Map<String, TreeMap<Integer, List<Integer>>> cacheByAlgorithm = new HashMap<>();

    public synchronized PrimeResult getPrimesUpTo(int n) {
        return getPrimesUpTo(n, ALGO_AUTO, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public synchronized PrimeResult getPrimesUpTo(int n, String algorithm) {
        return getPrimesUpTo(n, algorithm, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public synchronized PrimeResult getPrimesUpTo(int n, String algorithm, int page, int size) {
        validateUpperBound(n);
        validatePagination(page, size);

        String requestedAlgorithm = normalizeRequestedAlgorithm(algorithm);
        String resolvedAlgorithm = resolveAlgorithm(requestedAlgorithm, n);
        String algorithmDisplay = requestedAlgorithm.equals(ALGO_AUTO)
                ? ALGO_AUTO + "/" + resolvedAlgorithm
                : requestedAlgorithm;

        TreeMap<Integer, List<Integer>> cache =
                cacheByAlgorithm.computeIfAbsent(resolvedAlgorithm, ignored -> new TreeMap<>());

        List<Integer> fullResult;
        String source;

        // Exact hit
        if (cache.containsKey(n)) {
            fullResult = cache.get(n);
            source = "CACHED_EXACT";
        } else {
            // Reuse larger cached list by filtering down
            Integer ceiling = cache.ceilingKey(n);
            if (ceiling != null) {
                fullResult = cache.get(ceiling).stream()
                        .filter(p -> p <= n)
                        .toList();
                cache.put(n, fullResult);
                source = "CACHED_FILTERED (from " + ceiling + ")";
            } else {
                // Reuse smaller cached list and extend
                Integer floor = cache.floorKey(n);
                if (floor != null) {
                    fullResult = new ArrayList<>(cache.get(floor));
                    for (int i = floor + 1; i <= n; i++) {
                        if (isPrime(i)) {
                            fullResult.add(i);
                        }
                    }
                    cache.put(n, fullResult);
                    source = "CACHED_EXTENDED (from " + floor + ")";
                } else {
                    fullResult = computePrimes(n, resolvedAlgorithm);
                    cache.put(n, fullResult);
                    source = "COMPUTED";
                }
            }
        }

        int totalPrimes = fullResult.size();

        int totalPages = Math.max((int) Math.ceil((double) totalPrimes / size), 1);
        int maxPageNumber = totalPages;

        if (page > maxPageNumber) {
            throw new IllegalArgumentException(
                    "Page out of range for this request. Valid page number is 1 to " + maxPageNumber
            );
        }

        long rawStart = (long) (page - 1) * size;
        int start = rawStart >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawStart;
        int end = Math.min(start + size, totalPrimes);

        List<Integer> pagedPrimes = (start < totalPrimes)
                ? fullResult.subList(start, end)
                : List.of();

        boolean hasNext = page < totalPages;
        int pagePrimeCount = pagedPrimes.size();

        return new PrimeResult(
                n,
                source,
                requestedAlgorithm,
                resolvedAlgorithm,
                algorithmDisplay,
                totalPrimes,     // total primes across full result
                pagePrimeCount,  // count in current page only
                page,
                size,
                totalPages,
                maxPageNumber,
                hasNext,
                pagedPrimes
        );
    }

    private void validateUpperBound(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("upTo must be a positive integer");
        }
        if (n > MAX_FULL_RESULT_UP_TO) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "upTo number is too large for a full prime list response. Enter a value of 200_000 or less"
            );
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("Page must be >= 1");
        }
        if (size <= 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Size must be between 1 and " + MAX_SIZE);
        }
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

    private boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i <= n / i; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    private List<Integer> computePrimesByTrialDivision(int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = 2; i <= n; i++) {
            if (isPrime(i)) result.add(i);
        }
        return result;
    }

    private List<Integer> computePrimesBySieve(int n) {
        if (n < 2) return List.of();

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
            if (!composite[i]) primes.add(i);
        }
        return primes;
    }

    private List<Integer> computePrimesBySegmentedSieve(int n) {
        if (n < 2) return List.of();

        int limit = (int) Math.sqrt(n);
        List<Integer> basePrimes = computePrimesBySieve(limit);
        List<Integer> primes = new ArrayList<>(basePrimes);

        int low = Math.max(limit + 1, 2);
        int segmentSize = Math.max(32_768, limit + 1);

        while (low <= n) {
            int high = Math.min(low + segmentSize - 1, n);
            boolean[] composite = new boolean[high - low + 1];

            for (int prime : basePrimes) {
                long start = Math.max((long) prime * prime, ((long) (low + prime - 1) / prime) * prime);
                for (long value = start; value <= high; value += prime) {
                    composite[(int) (value - low)] = true;
                }
            }

            for (int i = 0; i < composite.length; i++) {
                if (!composite[i]) primes.add(low + i);
            }

            low = high + 1;
        }

        return primes;
    }
}