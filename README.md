# prime-api

A Spring Boot REST API that computes prime numbers up to a requested upper bound, with algorithm selection, intelligent caching, and content negotiation for JSON and XML responses.

## Tech Stack

- **Java 21** / Spring Boot 4.0.6
- **Maven** (Maven Wrapper)
- **Lombok** — reduces boilerplate
- **Jackson** — JSON (default) and XML via `jackson-dataformat-xml`
- **SpringDoc OpenAPI 3** — Swagger UI at `/swagger-ui.html`
- **JUnit 5** — unit tests
- **Karate 1.4.1** — integration tests
- **Docker** — containerised for Render deployment

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

- `upTo` (required): positive integer upper bound
- `algorithm` (optional): `auto`, `trial`, `sieve`, `segmented-sieve`
- default = `auto`

## Pagination Notes

- `page` is 1-based (`page=1` is the first page).
- `totalPages` is the number of pages available.
- `maxPageNumber` is the highest valid `page` value for the current request.
- `primeCount` is total primes found before pagination.
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
- Values above 200,000 exceed the full-list cap and return `400 Bad Request`
- Error responses are handled centrally using `GlobalExceptionHandler` and returned in a standard `ApiError` format.

## Cache Behaviour

For each algorithm, results are cached and reused in three ways:
- CACHED_EXACT — same upTo requested again
- CACHED_FILTERED — smaller request served from larger cached result
- CACHED_EXTENDED — larger request extends a smaller cached result

If no cache is usable, response source is COMPUTED.

## Why There Is an Upper Limit

- Very large prime lists can be expensive to render in browser-based tools (especially Swagger UI), even when backend computation succeeds.
- A full-list upper limit is used to keep responses practical and stable for this project scope.

## Future Improvements

- **Summary endpoint** — `/api/primes/summary?upTo=N` returning count + metadata only, making very large inputs practical without serialization overhead.

- **Bounded in-memory cache (Caffeine)** — adds automatic eviction, TTL expiry, and size limits, allowing the 200,000 upper limit to be raised safely.

- **Persistent cache (PostgreSQL)** — storing summary results across restarts; full prime list storage ruled out as segmented sieve computation is fast enough that DB I/O overhead would likely cost more than it saves.

- **Redis distributed cache** — for horizontally scaled deployments needing shared cache state across multiple instances.

- **Request audit logging** — persist request history (upTo, algorithm, source, timestamp) for analytics and observability.

## Testing

This project includes:

- **Unit tests (JUnit 5)** for core prime logic, algorithm selection, cache behaviour, and validation paths.
- **Integration tests (Karate)** for API endpoint behavior and error responses.

Run all tests with:

```bash
./mvnw test
```

Karate HTML report is generated at `target/karate-reports/karate-summary.html`

**Test coverage includes:**

- Algorithm correctness and auto-resolution thresholds
- Cache-source behaviour (CACHED_EXACT, CACHED_FILTERED, CACHED_EXTENDED)
- Invalid input handling
- Max-limit validation (ResponseStatusException path)


## Live Demo

Try it out:

- https://prime-api-6g66.onrender.com/api/primes?upTo=30&algorithm=auto
- https://prime-api-6g66.onrender.com/swagger-ui.html

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