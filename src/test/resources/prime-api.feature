Feature: Prime API integration tests

  Background:
    * url baseUrl

  Scenario: valid request returns 200
    Given path '/api/primes'
    And param upTo = 30
    And param algorithm = 'auto'
    When method GET
    Then status 200
    And match response.upTo == 30
    And match response.primes == '#[]'
    And assert response.primeCount > 0

  Scenario: negative upTo returns 400
    Given path '/api/primes'
    And param upTo = -1
    And param algorithm = 'auto'
    When method GET
    Then status 400
    And match response.status == 400
    And match response.message contains 'positive'

  Scenario: invalid algorithm returns 400
    Given path '/api/primes'
    And param upTo = 30
    And param algorithm = 'invalid'
    When method GET
    Then status 400
    And match response.status == 400
    And match response.message contains 'Unsupported algorithm'

  Scenario: auto reuses cache warmed by another algorithm
    Given path '/api/primes'
    And param upTo = 200000
    And param algorithm = 'segmented-sieve'
    When method GET
    Then status 200

    Given path '/api/primes'
    And param upTo = 100
    And param algorithm = 'auto'
    When method GET
    Then status 200
    And match response.resolvedAlgorithm == 'segmented-sieve'
    And match response.algorithmDisplay == 'auto/segmented-sieve'
    And match response.source contains 'CACHED_FILTERED'
    And match response.source contains 'cross-algorithm'

  Scenario: repeated request returns cached exact source
    Given path '/api/primes'
    And param upTo = 101
    And param algorithm = 'sieve'
    When method GET
    Then status 200

    Given path '/api/primes'
    And param upTo = 101
    And param algorithm = 'sieve'
    When method GET
    Then status 200
    And match response.source == 'CACHED_EXACT'

  Scenario: page out of range returns 400 with clear max page guidance
    Given path '/api/primes'
    And param upTo = 30
    And param algorithm = 'auto'
    And param size = 4
    And param page = 4
    When method GET
    Then status 400
    And match response.status == 400
    And match response.message contains 'Valid page number is 1 to 3'

  Scenario: invalid size above max returns 400
    Given path '/api/primes'
    And param upTo = 30
    And param algorithm = 'auto'
    And param size = 1001
    When method GET
    Then status 400
    And match response.status == 400
    And match response.message contains 'Size must be between 1 and 1000'

  Scenario: non-numeric upTo returns 400 type mismatch
    Given path '/api/primes'
    And param upTo = 'abc'
    And param algorithm = 'auto'
    When method GET
    Then status 400
    And match response.status == 400
    And match response.message contains "Invalid value for parameter 'upTo'"

