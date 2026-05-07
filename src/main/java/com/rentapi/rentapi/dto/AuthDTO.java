package com.rentapi.rentapi.dto;

import java.time.LocalDateTime;

public class AuthDTO {

    public record RegisterRequest(String email, String nombre) {}

    public record RegisterResponse(String apiKey, String plan, int requestsPerDay) {}

    public record MeResponse(String email, String nombre, String plan,
                             int requestsToday, int requestsLimit,
                             LocalDateTime createdAt) {}
}
