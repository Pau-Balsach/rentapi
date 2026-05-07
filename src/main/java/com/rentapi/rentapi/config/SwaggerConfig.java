package com.rentapi.rentapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RentAPI")
                        .description("API pública de estadísticas de alquiler en España. " +
                                "Registra tu cuenta en POST /auth/register para obtener tu API Key.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Pau Balsach")
                                .url("https://github.com/paubalsach/rentapi"))
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key obtenida al registrarse en POST /auth/register")));
    }
}
