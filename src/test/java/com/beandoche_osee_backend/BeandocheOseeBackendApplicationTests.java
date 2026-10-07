package com.beandoche_osee_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class BeandocheOseeBackendApplicationTests {

    @Test
    @DisplayName("t1 application context loads without infrastructure")
    void t1_contextLoadsWithoutInfrastructure() {
    }

}
