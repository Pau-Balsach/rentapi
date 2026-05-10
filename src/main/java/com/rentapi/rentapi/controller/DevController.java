package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.scraper.HabitacliaScraper;
import com.rentapi.rentapi.scraper.IdealistaApiClient;
import com.rentapi.rentapi.scraper.StatsCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Endpoints de desarrollo/testing. Solo activos fuera del perfil "prod".
 * Accesibles sin API Key — usar solo en local o en Render con cuidado.
 *
 * Ejemplos Postman:
 *   POST /dev/scraper/idealista/barcelona      → scraping real vía API Idealista
 *   POST /dev/scraper/habitaclia/barcelona     → scraping real Habitaclia (legacy)
 *   POST /dev/stats/calcular                   → recalcular stats mes actual
 */
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Profile("!prod")
public class DevController {

    private final IdealistaApiClient idealistaApiClient;
    private final HabitacliaScraper  habitacliaScraper;
    private final StatsCalculator    statsCalculator;

    // ─── Idealista API ────────────────────────────────────────────────────────

    /**
     * Lanza el scraping real de Idealista vía API oficial.
     * POST /dev/scraper/idealista/barcelona
     * POST /dev/scraper/idealista/madrid
     */
    @PostMapping("/scraper/idealista/{ciudad}")
    public String lanzarIdealista(@PathVariable String ciudad) {
        new Thread(() -> idealistaApiClient.scrapeCiudad(ciudad)).start();
        return "Idealista API scraper lanzado para: " + ciudad + ". Revisa los logs.";
    }

    // ─── Habitaclia (legacy — por si las cookies vuelven a funcionar) ─────────

    @PostMapping("/scraper/habitaclia/{ciudad}")
    public String lanzarHabitaclia(@PathVariable String ciudad) {
        new Thread(() -> habitacliaScraper.scrapeCiudad(ciudad)).start();
        return "Habitaclia scraper lanzado para: " + ciudad + ". Revisa los logs.";
    }

    @PostMapping("/scraper/habitaclia/local")
    public String lanzarHabitacliaLocal(@RequestParam String ruta,
                                        @RequestParam String ciudad) {
        new Thread(() -> habitacliaScraper.scrapeDesdeArchivoLocal(ruta, ciudad)).start();
        return "Habitaclia scraper local lanzado para: " + ciudad;
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @PostMapping("/stats/calcular")
    public String calcularStats() {
        LocalDate mes = LocalDate.now().withDayOfMonth(1);
        new Thread(() -> statsCalculator.calcularMes(mes)).start();
        return "Cálculo de stats lanzado para: " + mes;
    }

    @PostMapping("/stats/calcular/{anio}/{mes}")
    public String calcularStatsMes(@PathVariable int anio, @PathVariable int mes) {
        LocalDate fecha = LocalDate.of(anio, mes, 1);
        new Thread(() -> statsCalculator.calcularMes(fecha)).start();
        return "Cálculo de stats lanzado para: " + fecha;
    }
}