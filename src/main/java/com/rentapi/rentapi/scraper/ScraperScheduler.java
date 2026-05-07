package com.rentapi.rentapi.scraper;

import com.rentapi.rentapi.repository.CiudadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orquestador del proceso nocturno de RentAPI.
 *
 * Flujo completo (cada noche a las 03:00):
 *   1. Scraping de todas las ciudades activas con IdealistaScraper
 *   2. Recálculo de estadísticas del mes actual con StatsCalculator
 *
 * También expone métodos públicos para ejecución manual durante desarrollo:
 *   - ejecutarScraperManual()    → lanza solo el scraping
 *   - ejecutarStatsManual()      → lanza solo el recálculo de stats
 *   - ejecutarTodoManual()       → lanza scraping + stats (equivalente al cron)
 *   - ejecutarStatsParaMes()     → backfill de un mes concreto
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScraperScheduler {

    private final IdealistaScraper idealistaScraper;
    private final StatsCalculator  statsCalculator;
    private final CiudadRepository ciudadRepo;

    /**
     * Ciudades que el scraper procesará en cada ejecución.
     * Ampliable según avance el proyecto.
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

    /**
     * Tarea principal programada: cada noche a las 03:00.
     * cron = "segundo minuto hora día-mes mes día-semana"
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void ejecutarProcesoPrincipal() {
        LocalDateTime inicio = LocalDateTime.now();
        log.info("====================================================");
        log.info("Iniciando proceso nocturno RentAPI: {}", inicio);
        log.info("====================================================");

        // 1. Scraping
        ejecutarScrapingCiudades();

        // 2. Recalcular stats del mes actual
        log.info("----------------------------------------------------");
        log.info("Iniciando recálculo de estadísticas...");
        try {
            statsCalculator.calcularMesActual();
            log.info("Estadísticas recalculadas correctamente.");
        } catch (Exception e) {
            log.error("Error en el recálculo de estadísticas: {}", e.getMessage(), e);
        }

        LocalDateTime fin = LocalDateTime.now();
        log.info("====================================================");
        log.info("Proceso nocturno completado. Duración: {} segundos",
                java.time.Duration.between(inicio, fin).getSeconds());
        log.info("====================================================");
    }

    // ─── Métodos para ejecución manual (desarrollo / testing) ────────────────

    /**
     * Lanza únicamente el scraping de todas las ciudades activas.
     * Útil para poblar la BD por primera vez sin esperar al cron.
     *
     * Llamar desde un @RestController de admin o desde un test:
     *   scraperScheduler.ejecutarScraperManual();
     */
    public void ejecutarScraperManual() {
        log.info("Ejecución manual del scraper iniciada.");
        ejecutarScrapingCiudades();
        log.info("Ejecución manual del scraper completada.");
    }

    /**
     * Lanza únicamente el recálculo de stats del mes actual.
     * Útil cuando ya tienes datos en la tabla pisos y quieres generar
     * las stats sin volver a hacer scraping.
     */
    public void ejecutarStatsManual() {
        log.info("Ejecución manual del cálculo de stats iniciada.");
        statsCalculator.calcularMesActual();
        log.info("Ejecución manual del cálculo de stats completada.");
    }

    /**
     * Lanza scraping + stats completo, igual que el cron pero bajo demanda.
     */
    public void ejecutarTodoManual() {
        log.info("Ejecución manual completa iniciada.");
        ejecutarScrapingCiudades();
        statsCalculator.calcularMesActual();
        log.info("Ejecución manual completa finalizada.");
    }

    /**
     * Backfill: recalcula las stats para un mes concreto.
     * Útil para regenerar stats de meses pasados si los datos han cambiado.
     *
     * @param mes primer día del mes (ej: LocalDate.of(2026, 3, 1))
     */
    public void ejecutarStatsParaMes(LocalDate mes) {
        log.info("Backfill de stats para el mes: {}", mes);
        statsCalculator.calcularMes(mes);
        log.info("Backfill completado para el mes: {}", mes);
    }

    // ─── Lógica interna de scraping ──────────────────────────────────────────

    private void ejecutarScrapingCiudades() {
        log.info("Ciudades a scrapear: {}", CIUDADES_ACTIVAS);

        for (String slug : CIUDADES_ACTIVAS) {
            log.info("----------------------------------------------------");
            log.info("Scrapeando ciudad: {}", slug);
            try {
                idealistaScraper.scrapeCiudad(slug);
                log.info("Ciudad {} completada.", slug);
            } catch (Exception e) {
                // Un error en una ciudad no para el resto
                log.error("Error scrapeando ciudad {}: {}", slug, e.getMessage(), e);
            }
        }

        log.info("Scraping de todas las ciudades completado.");
    }
}
