package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.scraper.FotocasaScraper;
import com.rentapi.rentapi.scraper.HabitacliaScraper;
import com.rentapi.rentapi.scraper.IdealistaScraper;
import com.rentapi.rentapi.scraper.StatsCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Profile("!prod")
public class DevController {

    private final IdealistaScraper idealistaScraper;

    @PostMapping("/scraper/{ciudad}")
    public String lanzarScraper(@PathVariable String ciudad) {
        new Thread(() -> idealistaScraper.scrapeCiudad(ciudad)).start();
        return "Scraper lanzado para: " + ciudad;
    }

    @PostMapping("/scraper/local")
    public String lanzarScraperLocal(@RequestParam String ruta,
                                     @RequestParam String ciudad) {
        new Thread(() -> idealistaScraper.scrapeDesdeArchivoLocal(ruta, ciudad)).start();
        return "Scraper local lanzado para ciudad: " + ciudad;
    }
    @Autowired  // o añádelo al constructor con @RequiredArgsConstructor
    private final FotocasaScraper fotocasaScraper;

    @PostMapping("/scraper/fotocasa/local")
    public String lanzarFotocasaLocal(@RequestParam String ruta,
                                      @RequestParam String ciudad) {
        new Thread(() -> fotocasaScraper.scrapeDesdeArchivoLocal(ruta, ciudad)).start();
        return "Fotocasa scraper local lanzado para: " + ciudad;
    }

    private final HabitacliaScraper habitacliaScraper;

    @PostMapping("/scraper/habitaclia/local")
    public String lanzarHabitacliaLocal(@RequestParam String ruta,
                                        @RequestParam String ciudad) {
        new Thread(() -> habitacliaScraper.scrapeDesdeArchivoLocal(ruta, ciudad)).start();
        return "Habitaclia scraper local lanzado para: " + ciudad;
    }

    private final StatsCalculator statsCalculator;

    @PostMapping("/stats/calcular")
    public String calcularStats() {
        new Thread(() -> statsCalculator.calcularMes(LocalDate.now())).start();
        return "Cálculo de stats lanzado para: " + LocalDate.now().withDayOfMonth(1);
    }
}