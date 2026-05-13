# prime-api

A Spring Boot REST API that computes prime numbers up to a given number, with algorithm selection, intelligent caching, and content negotiation for JSON and XML responses.

## Tech Stack

- **Java 21** / Spring Boot 4.0.6
- **Maven** (Maven Wrapper)
- **Lombok** — reduces boilerplate
- **Jackson** — JSON (default) and XML via `jackson-dataformat-xml`
- **SpringDoc OpenAPI 3** — Swagger UI at `/swagger-ui.html`
- **Caffeine 3.2.0** — bounded in-memory cache with TTL and weight-based eviction
- **JUnit 5** — unit tests
- **Karate 1.4.1** — integration tests
- **Docker** — containerised for Render deployment

## Features

- **Multi-algorithm support:**
  - `auto` — selects the best algorithm based on input size
  - `trial` — trial division (best for small ranges)
  - `sieve` — Sieve of Eratosthenes (best for medium ranges)
  - `segmented-sieve` — memory-efficient for very large ranges
- **Smart caching:** stores results and reuses them across requests (exact hits, filtered results, extended ranges). In `auto` mode, cached results from any algorithm can be reused to avoid recomputation.
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

- `upTo` (required): positive integer, the highest number to check for primes
- `algorithm` (optional): `auto`, `trial`, `sieve`, `segmented-sieve`
  - default = `auto`
- `page` (optional): page number, starts at 1 (default = 1)
- `size` (optional): number of results per page, 1–1000 (default = 100)

## Pagination Notes

- `page` starts at 1 (page 1 is the first page).
- `totalPages` is the number of pages available.
- `maxPageNumber` is the highest valid page for the current request.
- `primeCount` is total primes found before splitting into pages.
- `pagePrimeCount` is the number of primes returned in the current page.

Example request:

```http
GET /api/primes?upTo=100&algorithm=auto&page=1&size=10
```

Example response (truncated):

```json
{
  "upTo": 100,
  "source": "COMPUTED",
  "requestedAlgorithm": "auto",
  "resolvedAlgorithm": "trial",
  "algorithmDisplay": "auto/trial",
  "primeCount": 25,
  "pagePrimeCount": 10,
  "page": 1,
  "size": 10,
  "totalPages": 3,
  "maxPageNumber": 3,
  "hasNext": true,
  "primes": [2, 3, 5, 7, 11, 13, 17, 19, 23, 29]
}
```

Out-of-range page example:

```http
GET /api/primes?upTo=100&algorithm=auto&page=9&size=10
```

```json
{
  "timestamp": "2026-05-11T14:30:00+01:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Page out of range for this request. Valid page number is 1 to 3",
  "path": "/api/primes"
}
```

## Validation and Error Handling

- Invalid `upTo` (e.g. 0, negative) returns `400 Bad Request`
- Unsupported `algorithm` returns `400 Bad Request`
- Values above the configured limit (default 3,000,000) exceed the full-list cap and return `400 Bad Request`
- Error responses are handled centrally using `GlobalExceptionHandler` and returned in a standard `ApiError` format.

## Cache Behaviour

For each algorithm, results are cached and reused in three ways:
- `CACHED_EXACT` — same number requested again, result served directly from cache
- `CACHED_FILTERED` — smaller request served by filtering down a larger cached result
- `CACHED_EXTENDED` — larger request built by extending a smaller cached result

In `auto` mode, results cached by any algorithm can be reused across algorithm boundaries (cross-algorithm reuse), avoiding unnecessary recomputation.

If no cache is usable, response source is `COMPUTED`.

## Why There Is an Upper Limit

- Very large prime lists can be expensive to serialize (a request to upTo=3M produces ~1-2 MB of JSON).
- The upper limit is configurable via `application.properties` (`prime.max-full-result-up-to`) so it can be safely raised or lowered without code changes.
- Cache weight limits (`prime.cache.max-weight`) and TTL (`prime.cache.ttl`) are also externally configurable to tune memory usage.

## Future Improvements

- **Persistent cache (PostgreSQL)** — storing summary results across restarts; full prime list storage ruled out as segmented sieve computation is fast enough that DB I/O overhead would likely cost more than it saves.

- **Redis distributed cache** — for horizontally scaled deployments needing shared cache state across multiple instances.

- **Request audit logging** — persist request history (upTo, algorithm, source, timestamp) for analytics and observability.

## Testing

This project includes:

- **Unit tests (JUnit 5)** for core prime logic, algorithm selection, cache behaviour, and validation paths.
- **Integration tests (Karate)** for API endpoint behaviour and error responses.

Run all tests with:

```bash
./mvnw test
```

Karate HTML report is generated at `target/karate-reports/karate-summary.html`

**Test coverage includes:**

- Algorithm correctness and auto-resolution thresholds
- Cache-source behaviour (`CACHED_EXACT`, `CACHED_FILTERED`, `CACHED_EXTENDED`)
- Cross-algorithm cache reuse in `auto` mode
- Invalid input handling (`upTo`, `algorithm`, `page`, `size`)
- Max-limit validation (ResponseStatusException path)


## Deploy to Render

This project includes a `Dockerfile` and is ready to deploy on [Render](https://render.com).

1. Push your branch to GitHub
2. In Render, create a new **Web Service** and connect your GitHub repository
3. Set the following:
   - **Environment**: `Docker`
   - **Branch**: your target branch (e.g. `main` or `experiment-caffeine`)
   - **Port**: Render sets the `PORT` environment variable automatically — the app reads `${PORT:8080}` and uses it
4. Deploy

Once live, the API will be available at your Render URL:

```
https://<your-app>.onrender.com/api/primes?upTo=100&algorithm=auto
https://<your-app>.onrender.com/swagger-ui.html
```

> **Note:** Render free-tier services spin down after inactivity. The first request after a cold start may take 30–60 seconds.

## Live Demo

> Live demo will be updated here once redeployed to Render with the latest version.

## Run Locally

1. Clone the repository
2. Build the project:

```bash
./mvnw clean package
```

3. Run the JAR:

```bash
java -jar target/prime-api-0.0.1-SNAPSHOT.jar
```

4. Open:
  - API: `http://localhost:8080/api/primes?upTo=100&algorithm=auto`
  - Swagger UI: `http://localhost:8080/swagger-ui.html`

### Alternative — dev mode

Run directly without building a JAR first:

```bash
./mvnw spring-boot:run
```

### Verify response format

```bash
curl -i "http://localhost:8080/api/primes?upTo=30&algorithm=auto"
curl -i -H "Accept: application/json" "http://localhost:8080/api/primes?upTo=30&algorithm=auto"
curl -i -H "Accept: application/xml" "http://localhost:8080/api/primes?upTo=30&algorithm=auto"
```

## Caffeine Cache Implementation

The unbounded in-memory cache was replaced with a bounded Caffeine cache while preserving all intelligent cache reuse behaviour (`CACHED_EXACT`, `CACHED_FILTERED`, `CACHED_EXTENDED`). 
Cache weight, TTL, and the upper limit cap are all externally configurable via `application.properties`.

### Outcome
- Pagination remained stable for large requests.
- A request up to `2,999,993` resolved correctly to `segmented-sieve`.
- First request returned `COMPUTED`.
- Repeating the same request returned `CACHED_EXACT`.
- Response metadata remained correct:
  - `primeCount = 216815`
  - `pagePrimeCount = 100`
  - `totalPages = 2169`
  - `maxPageNumber = 2169`

### Conclusion
A bounded, configurable cache supports larger prime ranges safely while keeping response payloads manageable through pagination. 
The configurable upper limit means the cap can be raised or lowered without any code changes.
