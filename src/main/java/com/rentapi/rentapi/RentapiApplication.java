package com.rentapi.rentapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class RentapiApplication {
	public static void main(String[] args) {
		SpringApplication.run(RentapiApplication.class, args);
	}
}
