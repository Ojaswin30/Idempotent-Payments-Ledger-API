package com.example.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Production-Grade Idempotent Payments Ledger API")
                        .description("REST API demonstrating distributed idempotency, double-entry financial ledgering, transactional outbox pattern, and Kafka event streaming.")
                        .version("1.0.0")
                        .contact(new Contact().name("Payments Platform Engineering Team").email("payments-eng@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Current Environment Server")
                ));
    }
}
