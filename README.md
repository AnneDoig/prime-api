<a id="top"></a>

# prime-api

A Spring Boot REST API that computes prime numbers up to a given number, with algorithm selection, intelligent caching, and content negotiation for JSON and XML responses.

## Live Demo 

Try the running app first:

- API: `https://prime-api-service.onrender.com/api/primes?upTo=30&algorithm=auto`
- Swagger UI: `https://prime-api-service.onrender.com/swagger-ui.html`
- Repository: `https://github.com/AnneDoig/prime-api`
- Branch for review: `prime-api-final`

> Note: Render free-tier services may spin down after inactivity. The first request may take 30-60 seconds.
> Note: Browser requests may show XML due to browser `Accept` headers. Use Swagger UI or `Accept: application/json` for JSON.

[Back to top](#top)

## Contents

- [Assessment Requirements Checklist](#assessment-requirements-checklist)
- [Tech Stack](#tech-stack)
- [API Endpoint](#api-endpoint)
- [Query Parameters](#query-parameters)
- [Algorithms](#algorithms)
- [Response Format (JSON and XML)](#response-format-json-and-xml)
- [Validation and Error Handling](#validation-and-error-handling)
- [Caching and Performance](#caching-and-performance)
- [Testing](#testing)
- [API Documentation (Swagger)](#api-documentation-swagger)
- [Deploy Your Own (Optional)](#deploy-your-own-optional)
- [Run Locally](#run-locally)
- [Future Improvements](#future-improvements)

[Back to top](#top)

## Assessment Requirements Checklist

| Assessment requirement | Status | Where to see evidence |
|---|---|---|
| Project written in Java 17/20/21 | Done | Java 21 is used (within the requirement) |
| Uses Maven to build/test/run | Done | Maven wrapper commands in [Run Locally](#run-locally) |
| Unit + integration tests | Done | [Testing](#testing) (`JUnit 5` + `Karate`) |
| Built on Spring Boot | Done | Spring Boot 4.0.6 in `pom.xml`; app structure |
| API responds with valid JSON | Done | [Response Format (JSON and XML)](#response-format-json-and-xml) |
| API is documented | Done | This README + [API Documentation (Swagger)](#api-documentation-swagger) |
| Optional: deploy to accessible platform | Done | [Live Demo (Start Here)](#live-demo-start-here), [Deploy Your Own (Optional)](#deploy-your-own-optional) |
| Optional: support XML via requested media type | Done | [Response Format (JSON and XML)](#response-format-json-and-xml) |
| Optional: improve performance (caching/concurrency) | Done (caching) | [Caching and Performance](#caching-and-performance) |
| Optional: multiple algorithms switchable via params | Done | [Algorithms](#algorithms), [Query Parameters](#query-parameters) |

[Back to top](#top)

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

Security/dependency note:
- Karate `1.4.1` transitively brought vulnerable Armeria `1.25.2` (CVE-2023-44487).
- Explicit override to Armeria `1.38.0` (test scope) is included in `pom.xml`.

[Back to top](#top)

## API Endpoint

```http
GET /api/primes?upTo=100&algorithm=auto
```

[Back to top](#top)

## Query Parameters

- `upTo` (required): positive integer, the highest number to check for primes
- `algorithm` (optional): `auto`, `trial`, `sieve`, `segmented-sieve`
  - default = `auto`
- `page` (optional): page number, starts at 1 (default = 1)
- `size` (optional): number of results per page, 1-1000 (default = 100)

### Pagination Notes

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

[Back to top](#top)

## Algorithms

Supported algorithms:

- `auto` — selects the best algorithm based on input size
- `trial` — trial division (best for small ranges)
- `sieve` — Sieve of Eratosthenes (best for medium ranges)
- `segmented-sieve` — memory-efficient for very large ranges

Auto thresholds currently used:
- `n < 10,000` -> `trial`
- `10,000 <= n < 200,000` -> `sieve`
- `n >= 200,000` -> `segmented-sieve`

[Back to top](#top)

## Response Format (JSON and XML)

The API supports both JSON and XML through content negotiation.

- JSON can be requested with: `Accept: application/json`
- XML can be requested with: `Accept: application/xml`

From `application.properties`:
- `spring.mvc.contentnegotiation.default-content-type=application/json`

### Verify response format

```bash
# Local API call (default browser call may show XML due to Accept header)
curl -i "http://localhost:8080/api/primes?upTo=30&algorithm=auto"

# Explicit JSON
curl -i -H "Accept: application/json" "http://localhost:8080/api/primes?upTo=30&algorithm=auto"

# Explicit XML
curl -i -H "Accept: application/xml" "http://localhost:8080/api/primes?upTo=30&algorithm=auto"
```

[Back to top](#top)

## Validation and Error Handling

- Invalid `upTo` (e.g. 0, negative) returns `400 Bad Request`
- Unsupported `algorithm` returns `400 Bad Request`
- Values above the configured limit (default 3,000,000) return `400 Bad Request`
- Error responses are handled centrally by `GlobalExceptionHandler` in a standard `ApiError` shape.

[Back to top](#top)

## Caching and Performance

For each algorithm, results are cached and reused in three ways:

- `CACHED_EXACT` — same number requested again, served directly from cache
- `CACHED_FILTERED` — smaller request served by filtering down a larger cached result
- `CACHED_EXTENDED` — larger request built by extending a smaller cached result

In `auto` mode, results cached by any algorithm can be reused across algorithm boundaries (cross-algorithm reuse), avoiding unnecessary recomputation.

If no cache is usable, response source is `COMPUTED`.

> Design note: `auto` intentionally prefers the best algorithm for the requested range, then reuses cached data when safe (exact hit or filtering from a larger cached result). It does not currently extend a smaller cache across algorithms.

### Cache configuration (externalized)

From `application.properties`:

- `prime.cache.max-weight=2000000`
- `prime.cache.ttl=15m`
- `prime.max-full-result-up-to=3000000`

### Clear Cache (dev/debug)

To reset in-memory caches without restarting the service:

```http
POST /api/cache/clear
```

Example response:

```json
{
  "message": "Cache cleared",
  "clearedAlgorithms": 3,
  "clearedEntries": 12
}
```

### Why there is an upper limit

- Large prime lists are expensive to serialize (at `upTo=3M`, JSON is roughly 1-2 MB).
- The upper limit is configurable via `prime.max-full-result-up-to`.
- Cache weight and TTL are configurable to balance speed and memory.

[Back to top](#top)

## Testing

This project includes:

- **Unit tests (JUnit 5)** for prime logic, algorithm selection, cache behaviour, and validation
- **Integration tests (Karate)** for endpoint behaviour and error responses

Run all tests:

```bash
./mvnw test
```

Karate HTML report:
- `target/karate-reports/karate-summary.html`

Test coverage includes:

- algorithm correctness and auto thresholds
- cache-source behaviour (`CACHED_EXACT`, `CACHED_FILTERED`, `CACHED_EXTENDED`)
- cross-algorithm cache reuse in `auto` mode
- invalid input handling (`upTo`, `algorithm`, `page`, `size`)
- max-limit validation (`ResponseStatusException` path)

[Back to top](#top)

## API Documentation (Swagger)

- Local: `http://localhost:8080/swagger-ui.html`
- Live: `https://prime-api-service.onrender.com/swagger-ui.html`

[Back to top](#top)

## Deploy Your Own (Optional)

This project includes a `Dockerfile` and is ready to deploy on [Render](https://render.com).

1. Push your branch to GitHub
2. In Render, create a new **Web Service** and connect your repository
3. Set:
   - **Environment**: `Docker`
   - **Branch**: your target branch (`prime-api-final`)
   - **Port**: Render sets `PORT`; app reads `${PORT:8080}`
4. Deploy

Once live, your endpoints are typically:

```text
https://<your-app>.onrender.com/api/primes?upTo=100&algorithm=auto
https://<your-app>.onrender.com/swagger-ui.html
```

[Back to top](#top)

## Run Locally

1. Clone the repository
2. Build:

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

### Alternative - dev mode

```bash
./mvnw spring-boot:run
```

[Back to top](#top)

## Future Improvements

- **Persistent cache (PostgreSQL)** for summary results across restarts
- **Redis distributed cache** for shared cache in scaled deployments
- **Request audit logging** for analytics/observability
- **Cross-algorithm upward cache reuse** in `auto` mode

[Back to top](#top)
