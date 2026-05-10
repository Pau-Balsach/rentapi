package com.rentapi.rentapi.scraper;

import com.rentapi.rentapi.repository.CiudadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orquestador del proceso nocturno de RentAPI.
 *
 * Flujo completo (cada noche a las 03:00):
 *   1. Scraping de todas las ciudades activas con IdealistaApiClient
 *   2. Recálculo de estadísticas del mes actual con StatsCalculator
 *
 * También expone métodos públicos para ejecución manual:
 *   - ejecutarScraperManual()    → lanza solo el scraping
 *   - ejecutarStatsManual()      → lanza solo el recálculo de stats
 *   - ejecutarTodoManual()       → lanza scraping + stats
 *   - ejecutarStatsParaMes()     → backfill de un mes concreto
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScraperScheduler {

    private final IdealistaApiClient idealistaApiClient;
    private final StatsCalculator    statsCalculator;
    private final CiudadRepository   ciudadRepo;

    /**
     * Ciudades que el scraper procesará en cada ejecución.
     */
    private static final List<String> CIUDADES_ACTIVAS = List.of(
            "barcelona",
            "madrid",
            "valencia",
            "sevilla",
            "zaragoza",
            "malaga",
            "bilbao",
            "alicante"
    );

    // ─── Cron nocturno ───────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 3 * * *")
    public void ejecutarProcesoPrincipal() {
        LocalDateTime inicio = LocalDateTime.now();
        log.info("====================================================");
        log.info("Iniciando proceso nocturno RentAPI: {}", inicio);
        log.info("====================================================");

        ejecutarScrapingCiudades();

        log.info("----------------------------------------------------");
        log.info("Iniciando recálculo de estadísticas...");
        try {
            statsCalculator.calcularMesActual();
            log.info("Estadísticas recalculadas correctamente.");
        } catch (Exception e) {
            log.error("Error en el recálculo de estadísticas: {}", e.getMessage(), e);
        }

        long segundos = Duration.between(inicio, LocalDateTime.now()).getSeconds();
        log.info("====================================================");
        log.info("Proceso nocturno completado en {} segundos ({} min).",
                segundos, segundos / 60);
        log.info("====================================================");
    }

    // ─── Métodos para ejecución manual ────────────────────────────────────────

    public void ejecutarScraperManual() {
        log.info("Ejecución manual del scraper iniciada.");
        ejecutarScrapingCiudades();
        log.info("Ejecución manual del scraper completada.");
    }

    public void ejecutarStatsManual() {
        log.info("Ejecución manual del cálculo de stats iniciada.");
        statsCalculator.calcularMesActual();
        log.info("Ejecución manual del cálculo de stats completada.");
    }

    public void ejecutarTodoManual() {
        log.info("Ejecución manual completa iniciada.");
        ejecutarScrapingCiudades();
        statsCalculator.calcularMesActual();
        log.info("Ejecución manual completa finalizada.");
    }

    public void ejecutarStatsParaMes(LocalDate mes) {
        log.info("Backfill de stats para el mes: {}", mes);
        statsCalculator.calcularMes(mes);
        log.info("Backfill completado para el mes: {}", mes);
    }

    // ─── Lógica interna ──────────────────────────────────────────────────────

    private void ejecutarScrapingCiudades() {
        log.info("Ciudades a scrapear: {}", CIUDADES_ACTIVAS);
        int exito = 0, fallo = 0;

        for (String slug : CIUDADES_ACTIVAS) {
            log.info("----------------------------------------------------");
            log.info("Scrapeando ciudad: {}", slug);
            try {
                idealistaApiClient.scrapeCiudad(slug);
                log.info("Ciudad {} completada.", slug);
                exito++;
            } catch (Exception e) {
                log.error("Error scrapeando ciudad {}: {}", slug, e.getMessage(), e);
                fallo++;
            }
        }

        log.info("Scraping completado → {} ciudades OK, {} con error.", exito, fallo);
    }
}