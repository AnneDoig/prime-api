package com.doig.primeapi.service;

import com.doig.primeapi.model.PrimeResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

@Service
public class PrimeService {

    /**
     * Service layer for prime number computation with intelligent caching.
     *
     * Supports three algorithms: trial division (small n), sieve (medium n), segmented-sieve (large n).
     * Auto mode selects the best algorithm based on input size.
     *
     * Caching strategy:
     * - Per-algorithm bounded Caffeine caches (weight-based eviction)
     * - Intelligent hit detection: exact match, filter-down (ceil), extend-up (floor)
     * - Cross-algorithm reuse: auto mode searches all caches for best reuse opportunity
     *
     * All public methods are synchronized to ensure thread-safe cache updates.
     */

    private static final String ALGO_TRIAL = "trial";
    private static final String ALGO_SIEVE = "sieve";
    private static final String ALGO_SEGMENTED_SIEVE = "segmented-sieve";
    private static final String ALGO_AUTO = "auto";

    // Auto mode algorithm thresholds: trial for n<10K, sieve for 10K≤n<200K, segmented-sieve for n≥200K.
    private static final int AUTO_SIEVE_THRESHOLD = 10_000;
    private static final int AUTO_SEGMENTED_SIEVE_THRESHOLD = 200_000;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;

    // Configurable max upTo for full-list responses; guards memory/serialization cost.
    @Value("${prime.max-full-result-up-to:200000}")
    private int maxFullResultUpTo = 200_000;

    // Approximate total weight per algorithm cache (uses list size as entry weight).
    @Value("${prime.cache.max-weight:1000000}")
    private long cacheMaxWeight = 1_000_000L;

    // How long an entry can stay idle in cache before eviction.
    @Value("${prime.cache.ttl:30m}")
    private Duration cacheTtl = Duration.ofMinutes(30);

    // One Caffeine cache per algorithm: enables independent weight/TTL management and cross-algorithm reuse optimization.
    private final Map<String, Cache<Integer, List<Integer>>> cacheByAlgorithm = new HashMap<>();

    // All public methods are synchronized to ensure thread-safe cache updates across multiple algorithms.
    public synchronized PrimeResult getPrimesUpTo(int n) {
        return getPrimesUpTo(n, ALGO_AUTO, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public synchronized PrimeResult getPrimesUpTo(int n, String algorithm) {
        return getPrimesUpTo(n, algorithm, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public synchronized PrimeResult getPrimesUpTo(int n, String algorithm, int page, int size) {
        // 1. Validate and normalize input
        validateUpperBound(n);
        validatePagination(page, size);
        // 2. Resolve algorithm and get/create cache
        String requestedAlgorithm = normalizeRequestedAlgorithm(algorithm);
        String resolvedAlgorithm = resolveAlgorithm(requestedAlgorithm, n);
        Cache<Integer, List<Integer>> cache =
                cacheByAlgorithm.computeIfAbsent(resolvedAlgorithm, ignored -> createPrimeCache());

        List<Integer> fullResult;
        String source;
        // 3. Attempt cross-algorithm or per-algorithm cache hit
        // In auto mode, try reusing cache entries from any algorithm before computing fresh data.
        CrossAlgorithmHit crossAlgorithmHit = requestedAlgorithm.equals(ALGO_AUTO)
                ? findCrossAlgorithmCacheHit(n)
                : null;

        if (crossAlgorithmHit != null) {
            resolvedAlgorithm = crossAlgorithmHit.algorithm();
            fullResult = crossAlgorithmHit.primes();
            source = crossAlgorithmHit.source();
        } else {
            // Exact hit
            List<Integer> exactHit = cache.getIfPresent(n);
            if (exactHit != null) {
                fullResult = exactHit;
                source = "CACHED_EXACT";
            } else {
                // Reuse larger cached list by filtering down
                NavigableSet<Integer> cachedSizes = new TreeSet<>(cache.asMap().keySet());
                Integer ceiling = cachedSizes.ceiling(n);
                if (ceiling != null) {
                    List<Integer> ceilingResult = cache.getIfPresent(ceiling);
                    if (ceilingResult != null) {
                        fullResult = ceilingResult.stream()
                                .filter(p -> p <= n)
                                .toList();
                        cache.put(n, fullResult);
                        source = "CACHED_FILTERED (from " + ceiling + ")";
                    } else {
                        fullResult = computeAndCache(n, resolvedAlgorithm, cache);
                        source = "COMPUTED";
                    }
                } else {
                    // Reuse smaller cached list and extend
                    Integer floor = cachedSizes.floor(n);
                    if (floor != null) {
                        List<Integer> floorResult = cache.getIfPresent(floor);
                        if (floorResult != null) {
                            fullResult = new ArrayList<>(floorResult);
                            for (int i = floor + 1; i <= n; i++) {
                                if (isPrime(i)) {
                                    fullResult.add(i);
                                }
                            }
                            cache.put(n, fullResult);
                            source = "CACHED_EXTENDED (from " + floor + ")";
                        } else {
                            fullResult = computeAndCache(n, resolvedAlgorithm, cache);
                            source = "COMPUTED";
                        }
                    } else {
                        fullResult = computeAndCache(n, resolvedAlgorithm, cache);
                        source = "COMPUTED";
                    }
                }
            }
        }

        String algorithmDisplay = requestedAlgorithm.equals(ALGO_AUTO)
                ? ALGO_AUTO + "/" + resolvedAlgorithm
                : requestedAlgorithm;

        // 4. Construct pagination metadata and return
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

    // Utility for debugging and operational checks (e.g. fresh cache validation after deploy).
    public synchronized CacheClearResult clearAllCaches() {
        int clearedAlgorithms = 0;
        int clearedEntries = 0;

        for (Cache<Integer, List<Integer>> cache : cacheByAlgorithm.values()) {
            clearedAlgorithms++;
            clearedEntries += cache.asMap().size();
            cache.invalidateAll();
        }

        return new CacheClearResult(clearedAlgorithms, clearedEntries);
    }

    private CrossAlgorithmHit findCrossAlgorithmCacheHit(int n) {
        Integer bestCeiling = null;
        String bestCeilingAlgorithm = null;
        Cache<Integer, List<Integer>> bestCeilingCache = null;
        List<Integer> bestCeilingResult = null;

        for (Map.Entry<String, Cache<Integer, List<Integer>>> entry : cacheByAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            Cache<Integer, List<Integer>> cache = entry.getValue();

            List<Integer> exactHit = cache.getIfPresent(n);
            if (exactHit != null) {
                return new CrossAlgorithmHit(
                        algorithm,
                        exactHit,
                        "CACHED_EXACT (cross-algorithm: " + algorithm + ")"
                );
            }

            NavigableSet<Integer> cachedSizes = new TreeSet<>(cache.asMap().keySet());
            Integer ceiling = cachedSizes.ceiling(n);
            if (ceiling == null || (bestCeiling != null && ceiling >= bestCeiling)) {
                continue;
            }

            List<Integer> ceilingResult = cache.getIfPresent(ceiling);
            if (ceilingResult != null) {
                bestCeiling = ceiling;
                bestCeilingAlgorithm = algorithm;
                bestCeilingCache = cache;
                bestCeilingResult = ceilingResult;
            }
        }

        if (bestCeiling == null || bestCeilingAlgorithm == null || bestCeilingCache == null || bestCeilingResult == null) {
            return null;
        }

        List<Integer> filtered = bestCeilingResult.stream()
                .filter(p -> p <= n)
                .toList();
        bestCeilingCache.put(n, filtered);

        return new CrossAlgorithmHit(
                bestCeilingAlgorithm,
                filtered,
                "CACHED_FILTERED (from " + bestCeiling + ", cross-algorithm: " + bestCeilingAlgorithm + ")"
        );
    }

    private record CrossAlgorithmHit(String algorithm, List<Integer> primes, String source) {}

    public record CacheClearResult(int clearedAlgorithms, int clearedEntries) {}

    private void validateUpperBound(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("upTo must be a positive integer");
        }
        if (n > maxFullResultUpTo) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "upTo number is too large for a full prime list response. Enter a value of " + maxFullResultUpTo + " or less"
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

    private Cache<Integer, List<Integer>> createPrimeCache() {
        return Caffeine.newBuilder()
                .maximumWeight(cacheMaxWeight)
                .weigher((Integer key, List<Integer> value) -> Math.max(1, value.size()))
                .expireAfterAccess(cacheTtl)
                .build();
    }

    private List<Integer> computeAndCache(int n, String resolvedAlgorithm, Cache<Integer, List<Integer>> cache) {
        List<Integer> result = computePrimes(n, resolvedAlgorithm);
        cache.put(n, result);
        return result;
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