package com.rentapi.rentapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Sistema", description = "Estado del sistema")
public class HealthController {

    @Value("${rentapi.version:1.0.0}")
    private String version;

    @GetMapping
    @Operation(summary = "Estado del sistema. Sin autenticación.")
    public ResponseEntity<Map<String, Object>> health() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "version", version,
                "uptime_seconds", uptimeSeconds
        ));
    }
}
