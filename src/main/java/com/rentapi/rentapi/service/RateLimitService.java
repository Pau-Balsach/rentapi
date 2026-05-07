package com.rentapi.rentapi.service;

import com.rentapi.rentapi.exception.RateLimitExceededException;
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

    private static final int LIMITE_FREE = 100;
    private static final int LIMITE_PRO  = 1000;

    private final ApiUsageLogRepository usageLogRepository;

    /**
     * Comprueba si el usuario tiene peticiones disponibles hoy y, si las tiene,
     * incrementa el contador. Lanza RateLimitExceededException si ha superado el límite.
     *
     * @param usuario  usuario autenticado
     * @param endpoint ruta llamada (ej: "/api/v1/stats/ciudad/{slug}")
     */
    @Transactional
    public void checkAndIncrement(Usuario usuario, String endpoint) {
        LocalDate hoy = LocalDate.now();
        int limite = "pro".equalsIgnoreCase(usuario.getPlan()) ? LIMITE_PRO : LIMITE_FREE;

        // Total de peticiones del usuario hoy (todos los endpoints juntos)
        Integer usadaHoyRaw = usageLogRepository.sumPeticionesByUsuarioAndFecha(usuario.getId(), hoy);
        int usadaHoy = usadaHoyRaw != null ? usadaHoyRaw : 0;

        if (usadaHoy >= limite) {
            throw new RateLimitExceededException(
                    "Has alcanzado el límite de " + limite + " peticiones diarias del plan " +
                            usuario.getPlan().toUpperCase() + ". Resetea a medianoche (UTC)."
            );
        }

        // Upsert: si ya existe la fila para hoy + endpoint, incrementa; si no, la crea
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
     * Devuelve cuántas peticiones le quedan al usuario hoy (útil para headers de respuesta).
     */
    public int peticionesRestantes(Usuario usuario) {
        int limite = "pro".equalsIgnoreCase(usuario.getPlan()) ? LIMITE_PRO : LIMITE_FREE;
        Integer usadasRaw = usageLogRepository.sumPeticionesByUsuarioAndFecha(
                usuario.getId(), LocalDate.now());
        int usadas = usadasRaw != null ? usadasRaw : 0;
        return Math.max(0, limite - usadas);
    }
}