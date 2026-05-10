package com.doig.primeapi;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrimeApiKarateTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        // Make random Spring port available to karate-config.js
        System.setProperty("test.server.port", String.valueOf(port));
    }

    @Karate.Test
    Karate runApiTests() {
        return Karate.run("classpath:prime-api.feature");
    }
}