package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.AuthDTO;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.repository.ApiUsageLogRepository;
import com.rentapi.rentapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepo;

    @Mock
    private ApiUsageLogRepository usageLogRepo;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioFree;
    private Usuario usuarioPro;

    @BeforeEach
    void setUp() {
        usuarioFree = Usuario.builder()
                .id(1L)
                .email("free@test.com")
                .nombre("Free User")
                .apiKey("testapikey123")
                .plan("free")
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        usuarioPro = Usuario.builder()
                .id(2L)
                .email("pro@test.com")
                .nombre("Pro User")
                .apiKey("proapikey456")
                .plan("pro")
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── register ────────────────────────────────────────────────

    @Test
    void register_emailValido_devuelveApiKey() {
        when(usuarioRepo.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(usuarioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest("nuevo@test.com", "Nuevo");
        AuthDTO.RegisterResponse response = authService.register(request);

        assertThat(response.apiKey()).isNotBlank();
        assertThat(response.plan()).isEqualTo("free");
        assertThat(response.requestsPerDay()).isEqualTo(AuthService.FREE_LIMIT);
        verify(usuarioRepo).save(any(Usuario.class));
    }

    @Test
    void register_emailDuplicado_lanzaConflict() {
        when(usuarioRepo.existsByEmail("existente@test.com")).thenReturn(true);

        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest("existente@test.com", "Test");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void register_emailVacio_lanzaBadRequest() {
        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest("", "Test");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void register_emailNormalizado_guardaEnMinusculas() {
        when(usuarioRepo.existsByEmail(anyString())).thenReturn(false);
        when(usuarioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest("NUEVO@TEST.COM", "Test");
        authService.register(request);

        verify(usuarioRepo).save(argThat(u -> u.getEmail().equals("nuevo@test.com")));
    }

    @Test
    void register_apiKeyGenerada_tieneFormatoUUID() {
        when(usuarioRepo.existsByEmail(any())).thenReturn(false);
        when(usuarioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest("test@test.com", "Test");
        AuthDTO.RegisterResponse response = authService.register(request);

        // UUID sin guiones = 32 caracteres hexadecimales
        assertThat(response.apiKey()).hasSize(32);
        assertThat(response.apiKey()).matches("[a-f0-9]{32}");
    }

    // ─── getMe ───────────────────────────────────────────────────

    @Test
    void getMe_planFree_devuelveLimite100() {
        when(usageLogRepo.sumPeticionesByUsuarioAndFecha(eq(1L), any(LocalDate.class)))
                .thenReturn(10);

        AuthDTO.MeResponse me = authService.getMe(usuarioFree);

        assertThat(me.plan()).isEqualTo("free");
        assertThat(me.requestsLimit()).isEqualTo(100);
        assertThat(me.requestsToday()).isEqualTo(10);
        assertThat(me.email()).isEqualTo("free@test.com");
    }

    @Test
    void getMe_planPro_devuelveLimite1000() {
        when(usageLogRepo.sumPeticionesByUsuarioAndFecha(eq(2L), any(LocalDate.class)))
                .thenReturn(50);

        AuthDTO.MeResponse me = authService.getMe(usuarioPro);

        assertThat(me.plan()).isEqualTo("pro");
        assertThat(me.requestsLimit()).isEqualTo(1000);
    }

    @Test
    void getMe_sinPeticionesHoy_devuelveCero() {
        when(usageLogRepo.sumPeticionesByUsuarioAndFecha(any(), any())).thenReturn(null);

        AuthDTO.MeResponse me = authService.getMe(usuarioFree);

        assertThat(me.requestsToday()).isEqualTo(0);
    }

    // ─── findByApiKey ─────────────────────────────────────────────

    @Test
    void findByApiKey_keyValida_devuelveUsuario() {
        when(usuarioRepo.findByApiKey("testapikey123")).thenReturn(Optional.of(usuarioFree));

        Usuario resultado = authService.findByApiKey("testapikey123");

        assertThat(resultado).isNotNull();
        assertThat(resultado.getEmail()).isEqualTo("free@test.com");
    }

    @Test
    void findByApiKey_keyInvalida_devuelveNull() {
        when(usuarioRepo.findByApiKey("keyinvalida")).thenReturn(Optional.empty());

        Usuario resultado = authService.findByApiKey("keyinvalida");

        assertThat(resultado).isNull();
    }

    @Test
    void findByApiKey_usuarioInactivo_devuelveNull() {
        usuarioFree.setActivo(false);
        when(usuarioRepo.findByApiKey("testapikey123")).thenReturn(Optional.of(usuarioFree));

        Usuario resultado = authService.findByApiKey("testapikey123");

        assertThat(resultado).isNull();
    }
}