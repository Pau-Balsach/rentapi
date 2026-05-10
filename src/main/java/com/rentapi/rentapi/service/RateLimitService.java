package com.rentapi.rentapi.service;

import com.rentapi.rentapi.model.ApiUsageLog;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ApiUsageLogRepository usageLogRepository;

    /**
     * Registra la petición del usuario en el log de uso (solo métricas, sin límite).
     *
     * @param usuario  usuario autenticado
     * @param endpoint ruta llamada (ej: "/api/v1/stats/ciudad/{slug}")
     */
    @Transactional
    public void registrar(Usuario usuario, String endpoint) {
        LocalDate hoy = LocalDate.now();

        int updated = usageLogRepository.incrementPeticiones(usuario.getId(), endpoint, hoy);
        if (updated == 0) {
            usageLogRepository.save(ApiUsageLog.builder()
                    .usuario(usuario)
                    .endpoint(endpoint)
                    .fecha(hoy)
                    .peticiones(1)
                    .build());
        }
    }

    /**
     * Devuelve el total de peticiones del usuario hoy (útil para métricas internas).
     */
    public int peticionesHoy(Usuario usuario) {
        Integer total = usageLogRepository.sumPeticionesByUsuarioAndFecha(
                usuario.getId(), LocalDate.now());
        return total != null ? total : 0;
    }
}