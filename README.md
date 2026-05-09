# prime-api

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
  - `primeCount` — number of primes found (useful for large lists)

## API Endpoint

```http
GET /api/primes?upTo=100&algorithm=auto
```

## Live Demo

Try it out:

- https://prime-api-6g66.onrender.com/api/primes?upTo=30&algorithm=auto
- https://prime-api-6g66.onrender.com/swagger-ui.html
