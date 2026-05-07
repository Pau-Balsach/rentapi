package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.AuthDTO;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.repository.ApiUsageLogRepository;
import com.rentapi.rentapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    // Límits de peticions per pla
    public static final int FREE_LIMIT = 100;
    public static final int PRO_LIMIT = 1000;

    private final UsuarioRepository usuarioRepo;
    private final ApiUsageLogRepository usageLogRepo;

    @Transactional
    public AuthDTO.RegisterResponse register(AuthDTO.RegisterRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }
        if (usuarioRepo.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }

        String apiKey = UUID.randomUUID().toString().replace("-", "");

        Usuario usuario = Usuario.builder()
                .email(request.email().trim().toLowerCase())
                .nombre(request.nombre())
                .apiKey(apiKey)
                .plan("free")
                .activo(true)
                .build();

        usuarioRepo.save(usuario);

        return new AuthDTO.RegisterResponse(apiKey, "free", FREE_LIMIT);
    }

    @Transactional(readOnly = true)
    public AuthDTO.MeResponse getMe(Usuario usuario) {
        Integer requestsTodayRaw = usageLogRepo.sumPeticionesByUsuarioAndFecha(
                usuario.getId(), LocalDate.now());
        int requestsToday = requestsTodayRaw != null ? requestsTodayRaw : 0;
        int limit = "pro".equals(usuario.getPlan()) ? PRO_LIMIT : FREE_LIMIT;

        return new AuthDTO.MeResponse(
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getPlan(),
                requestsToday,
                limit,
                usuario.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Usuario findByApiKey(String apiKey) {
        return usuarioRepo.findByApiKey(apiKey)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElse(null);
    }
}
