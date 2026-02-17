package com.vnfm.lcmcore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * REVISION – Spring Boot testing
 * ------------------------------
 * @SpringBootTest: starts the full Spring application context (same as in production). All
 *   @Component, @Service, @Repository, etc. are loaded; auto-configuration runs. Use for
 *   integration tests that need the real context. (Compare: @WebMvcTest loads only web layer,
 *   @DataJpaTest only JPA and repositories.)
 *
 * @ActiveProfiles("test"): activates the "test" profile so Spring loads application-test.yml
 *   (and application.yml). We use H2 and exclude Kafka so tests don't need external services.
 *
 * contextLoads(): if this passes, the context started successfully — all beans wired, no missing
 *   dependencies or invalid config. A minimal sanity check for the application.
 */
@SpringBootTest
@ActiveProfiles("test")
class LcmCoreApplicationTests {

    @Test
    void contextLoads() {
    }
}
