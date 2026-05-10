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