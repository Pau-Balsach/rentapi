package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.dto.AuthDTO;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro de usuarios y gestión de API Keys")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registra un nuevo usuario y genera su API Key")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDTO.RegisterResponse register(@RequestBody AuthDTO.RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    @Operation(
        summary = "Devuelve info del usuario autenticado y uso actual del día",
        security = @SecurityRequirement(name = "apiKey")
    )
    public ResponseEntity<AuthDTO.MeResponse> me(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key requerida");
        }
        return ResponseEntity.ok(authService.getMe(usuario));
    }
}
