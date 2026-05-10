'# prime-api

A Spring Boot REST API that computes prime numbers up to a requested upper bound, with algorithm selection, intelligent caching, and content negotiation for JSON and XML responses.

## Features

- **Multi-algorithm support:**
  - `auto` — selects the best algorithm based on input size
  - `trial` — trial division (best for small ranges)
  - `sieve` — Sieve of Eratosthenes (best for medium ranges)
  - `segmented-sieve` — memory-efficient for very large ranges
- **Smart caching:** stores results and reuses them across requests (exact hits, filtered results, extended ranges)
- **Content negotiation:** returns JSON (default) or XML based on request headers
- **Rich response metadata:**
  - `source` — whether result was computed or retrieved from cache
  - `requestedAlgorithm` and `resolvedAlgorithm` — shows algorithm selection logic
  - `algorithmDisplay` — shows how auto resolved at runtime
  - `primeCount` — number of primes found (useful for large lists)

## API Endpoint

```http
GET /api/primes?upTo=100&algorithm=auto
```
## Query Parameters

- upTo (required): positive integer upper bound
- algorithm (optional): auto, trial, sieve, segmented-sieve
- default = auto

## Validation and Error Handling

- Invalid 'upTo' (e.g. 0, negative) returns '400 Bad Request'
- Unsupported 'algorithm' returns 400 Bad Request
- Very large values above the configured full-list cap return 400 Bad Request
- Error responses are handled centrally using GlobalExceptionHandler and returned in a standard ApiError format.

## Cache Behaviour

For each algorithm, results are cached and reused in three ways:
- CACHED_EXACT — same upTo requested again
- CACHED_FILTERED — smaller request served from larger cached result
- CACHED_EXTENDED — larger request extends a smaller cached result

If no cache is usable, response source is COMPUTED.

## Why There Is an Upper Limit

- Very large prime lists can be expensive to render in browser-based tools (especially Swagger UI), even when backend computation succeeds.
- A full-list upper limit is used to keep responses practical and stable for this project scope.

## With more time, I would add:

- a summary-only endpoint for very large inputs (count + metadata, no full list)
- bounded cache with TTL (e.g. Caffeine)
- optional distributed cache (e.g. Redis)

## Testing Summary

Current test coverage includes:

- algorithm correctness and auto-resolution thresholds
- cache-source behaviour (CACHED_EXACT, CACHED_FILTERED, CACHED_EXTENDED)
- invalid input handling
- max-limit validation (ResponseStatusException path)

Note: Rest Assured controller-level testing was paused due to runtime compatibility issues in this setup (Spring Boot 4 + Java 21). Service-level tests and manual endpoint verification were used to confirm behaviour.

## Live Demo

Try it out:

- https://prime-api-6g66.onrender.com/api/primes?upTo=30&algorithm=auto
- https://prime-api-6g66.onrender.com/swagger-ui.html'