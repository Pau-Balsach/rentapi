package com.rentapi.rentapi.scraper;

import com.rentapi.rentapi.model.Barrio;
import com.rentapi.rentapi.model.Ciudad;
import com.rentapi.rentapi.model.Piso;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.PisoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdealistaScraper {

    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;
    private final PisoRepository pisoRepo;

    private static final String BASE_URL  = "https://www.idealista.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";

    // Patrón para extraer el precio: "1.250€/mes" o "1.250<span>€/mes</span>"
    private static final Pattern PRECIO_PATTERN   = Pattern.compile("[\\d\\.]+");
    // Patrón para habitaciones: "2 hab." o "3 hab."
    private static final Pattern HAB_PATTERN      = Pattern.compile("^(\\d+)\\s+hab");
    // Patrón para metros: "71 m²"
    private static final Pattern M2_PATTERN       = Pattern.compile("(\\d+)\\s+m");
    // Patrón para planta: "Planta 3ª" / "Bajo" / "Entreplanta"
    private static final Pattern PLANTA_PATTERN   = Pattern.compile("Planta\\s+(\\S+)|^(Bajo|Entreplanta|Sótano)");

    /**
     * Punto de entrada principal. Scrapea todas las páginas de una ciudad.
     *
     * @param ciudadSlug slug de la ciudad (ej: "barcelona")
     */
    public void scrapeCiudad(String ciudadSlug) {
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        Ciudad ciudad = ciudadOpt.get();

        String startUrl = BASE_URL + "/alquiler-viviendas/" + ciudadSlug + "-" +
                obtenerSufijoCiudad(ciudadSlug) + "/";

        log.info("Iniciando scraping de {} → {}", ciudadSlug, startUrl);
        scrapePaginas(startUrl, ciudad);
    }

    public void scrapeDesdeArchivoLocal(String rutaHtml, String ciudadSlug) {
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        try {
            Document doc = Jsoup.parse(new java.io.File(rutaHtml), "UTF-8", BASE_URL);
            List<Piso> pisos = parsePisos(doc, ciudadOpt.get());
            guardarPisos(pisos);
            log.info("Pisos parseados del archivo local: {}", pisos.size());
        } catch (Exception e) {
            log.error("Error leyendo archivo local: {}", e.getMessage());
        }
    }

    // ─── Bucle de paginación ─────────────────────────────────────────────────

    private void scrapePaginas(String startUrl, Ciudad ciudad) {
        String url = startUrl;
        int pagina = 1;

        while (url != null) {
            log.info("Scrapeando página {} → {}", pagina, url);
            try {
                Document doc = fetchDocument(url);
                List<Piso> pisos = parsePisos(doc, ciudad);
                guardarPisos(pisos);
                log.info("Página {}: {} pisos procesados", pagina, pisos.size());

                url = siguientePagina(doc);
                pagina++;

                // Delay entre páginas: 3-6 segundos
                esperarRandom(3000, 6000);

            } catch (Exception e) {
                log.error("Error en página {}: {}", pagina, e.getMessage());
                break;
            }
        }
        log.info("Scraping completado. Total páginas: {}", pagina - 1);
    }

    // ─── Fetch ───────────────────────────────────────────────────────────────

    private Document fetchDocument(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(15_000)
                .get();
    }

    // ─── Parser principal ────────────────────────────────────────────────────

    private List<Piso> parsePisos(Document doc, Ciudad ciudad) {
        List<Piso> resultado = new ArrayList<>();

        // Cada anuncio es un <article class="item ..."> con data-element-id
        Elements articles = doc.select("article.item[data-element-id]");

        for (Element art : articles) {
            try {
                Piso piso = parseArticle(art, ciudad);
                if (piso != null) {
                    resultado.add(piso);
                }
            } catch (Exception e) {
                log.warn("Error parseando article id={}: {}",
                        art.attr("data-element-id"), e.getMessage());
            }
        }
        return resultado;
    }

    private Piso parseArticle(Element art, Ciudad ciudad) {
        String fuenteId = art.attr("data-element-id");
        if (fuenteId.isBlank()) return null;

        // ── Precio ──────────────────────────────────────────────────────────
        Element precioEl = art.selectFirst(".item-price");
        if (precioEl == null) return null;

        // El texto del elemento contiene "1.250€/mes", eliminamos separadores de miles
        String precioText = precioEl.text().replace(".", "").replace(",", ".");
        Matcher precioMatcher = PRECIO_PATTERN.matcher(precioText);
        if (!precioMatcher.find()) return null;
        BigDecimal precioMes = new BigDecimal(precioMatcher.group());

        // ── Habitaciones y metros ────────────────────────────────────────────
        Elements detalles = art.select(".item-detail-char .item-detail");
        Short habitaciones = null;
        Integer metros     = null;
        String planta      = null;

        for (Element det : detalles) {
            String txt = det.text().trim();

            Matcher habM = HAB_PATTERN.matcher(txt);
            if (habM.find() && habitaciones == null) {
                habitaciones = Short.parseShort(habM.group(1));
                continue;
            }

            // Estudios: "0 habitaciones (estudios)"
            if (txt.contains("studio") || txt.contains("studio") || txt.contains("estudio")) {
                habitaciones = 0;
                continue;
            }

            Matcher m2M = M2_PATTERN.matcher(txt);
            if (m2M.find() && metros == null) {
                metros = Integer.parseInt(m2M.group(1));
                continue;
            }

            Matcher plantaM = PLANTA_PATTERN.matcher(txt);
            if (plantaM.find() && planta == null) {
                planta = txt.length() > 20 ? txt.substring(0, 20) : txt;
            }
        }

        // ── Barrio (del título del anuncio) ──────────────────────────────────
        Element tituloEl = art.selectFirst("a.item-link");
        Barrio barrio = null;
        if (tituloEl != null) {
            barrio = inferirBarrio(tituloEl.attr("title"), ciudad);
        }

        // ── Amueblado y mascotas (del texto descriptivo) ─────────────────────
        String descripcion = art.select(".item-description").text().toLowerCase();
        Boolean amueblado      = descripcion.contains("amuebla") ? Boolean.TRUE : null;
        Boolean permiteMascotas = null;
        if (descripcion.contains("mascotas")) {
            permiteMascotas = !descripcion.contains("no se aceptan animales") &&
                    !descripcion.contains("no mascotas");
        }

        return Piso.builder()
                .fuente("idealista")
                .fuenteId(fuenteId)
                .ciudad(ciudad)
                .barrio(barrio)
                .precioMes(precioMes)
                .metrosCuadrados(metros)
                .habitaciones(habitaciones)
                .planta(planta)
                .amueblado(amueblado)
                .permiteMascotas(permiteMascotas)
                .activo(true)
                .build();
    }

    // ─── Inferir barrio ───────────────────────────────────────────────────────

    /**
     * El título del anuncio tiene forma:
     *   "Piso en Calle del Rosselló, La Dreta de l'Eixample, Barcelona"
     * Intentamos extraer la parte del medio como barrio.
     */
    private Barrio inferirBarrio(String titulo, Ciudad ciudad) {
        if (titulo == null || titulo.isBlank()) return null;

        String[] partes = titulo.split(",");
        if (partes.length < 2) return null;

        // La penúltima parte suele ser el barrio (la última es la ciudad)
        String posibleBarrio = partes[partes.length - 2].trim();

        // Buscamos en BD por nombre aproximado
        return barrioRepo.findByCiudad_Slug(ciudad.getSlug()).stream()
                .filter(b -> posibleBarrio.toLowerCase()
                        .contains(b.getNombre().toLowerCase().substring(0, Math.min(5, b.getNombre().length()))))
                .findFirst()
                .orElse(null);
    }

    // ─── Paginación ───────────────────────────────────────────────────────────

    /**
     * Busca el enlace "Siguiente" en la paginación.
     * El HTML tiene: <li class="next"><a href="/alquiler-viviendas/.../pagina-2.htm">
     */
    private String siguientePagina(Document doc) {
        Element next = doc.selectFirst(".pagination .next a");
        if (next == null) return null;
        String href = next.attr("href");
        return href.startsWith("http") ? href : BASE_URL + href;
    }

    // ─── Guardar en BD ────────────────────────────────────────────────────────

    private void guardarPisos(List<Piso> pisos) {
        for (Piso piso : pisos) {
            try {
                Optional<Piso> existente = pisoRepo.findByFuenteAndFuenteId("idealista", piso.getFuenteId());
                if (existente.isPresent()) {
                    // Actualizar precio si ha cambiado
                    Piso p = existente.get();
                    p.setPrecioMes(piso.getPrecioMes());
                    p.setActivo(true);
                    pisoRepo.save(p);
                } else {
                    pisoRepo.save(piso);
                }
            } catch (Exception e) {
                log.warn("Error guardando piso fuenteId={}: {}", piso.getFuenteId(), e.getMessage());
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Idealista usa URLs como:
     *   /alquiler-viviendas/barcelona-barcelona/       → ciudad Barcelona
     *   /alquiler-viviendas/madrid-madrid/             → ciudad Madrid
     *   /alquiler-viviendas/valencia-valencia/
     *   /alquiler-viviendas/sevilla-sevilla/
     * El sufijo suele ser el mismo slug de la ciudad.
     */
    private String obtenerSufijoCiudad(String ciudadSlug) {
        return ciudadSlug; // ej: "barcelona" → "barcelona-barcelona"
    }

    private void esperarRandom(int minMs, int maxMs) {
        try {
            long ms = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}