package com.aditya.dataautomation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dataAutomationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Data Automation Agent API")
                        .description("AI Business Automation Platform - REST API Documentation")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("Aditya")
                        )
                );
    }
}
