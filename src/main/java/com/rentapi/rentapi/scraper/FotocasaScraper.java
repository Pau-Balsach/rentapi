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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FotocasaScraper {

    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;
    private final PisoRepository pisoRepo;

    private static final String BASE_URL   = "https://www.fotocasa.es";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern PRECIO_PATTERN = Pattern.compile("[\\d\\.]+");
    private static final Pattern HAB_PATTERN    = Pattern.compile("(\\d+)\\s+hab");
    private static final Pattern M2_PATTERN     = Pattern.compile("(\\d+)\\s+m²");
    private static final Pattern PLANTA_PATTERN = Pattern.compile("(\\d+)[ªº]\\s+Planta|^(Bajo|Entreplanta|Sótano)");
    private static final Pattern ID_PATTERN     = Pattern.compile("/(\\d+)/d");

    // ─── Entrada principal ────────────────────────────────────────────────────

    public void scrapeCiudad(String ciudadSlug) {
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        Ciudad ciudad = ciudadOpt.get();
        String startUrl = BASE_URL + "/es/alquiler/viviendas/" + ciudadSlug +
                "-capital/todas-las-zonas/l";

        log.info("Iniciando scraping Fotocasa de {} → {}", ciudadSlug, startUrl);
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

    // ─── Paginación ───────────────────────────────────────────────────────────

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

                url = siguientePagina(doc, pagina);
                pagina++;
                esperarRandom(3000, 6000);

            } catch (Exception e) {
                log.error("Error en página {}: {}", pagina, e.getMessage());
                break;
            }
        }
        log.info("Scraping Fotocasa completado. Total páginas: {}", pagina - 1);
    }

    // ─── Fetch ────────────────────────────────────────────────────────────────

    private Document fetchDocument(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(15_000)
                .get();
    }

    // ─── Parser principal ─────────────────────────────────────────────────────

    private List<Piso> parsePisos(Document doc, Ciudad ciudad) {
        List<Piso> resultado = new ArrayList<>();

        // Fotocasa: cada anuncio es un <article class="@container w-full">
        Elements articles = doc.select("article");

        for (Element art : articles) {
            // Saltamos skeletons (sin precio) y anuncios patrocinados sin datos
            if (!art.text().contains("€")) continue;

            try {
                Piso piso = parseArticle(art, ciudad);
                if (piso != null) {
                    resultado.add(piso);
                }
            } catch (Exception e) {
                log.warn("Error parseando artículo Fotocasa: {}", e.getMessage());
            }
        }
        return resultado;
    }

    private Piso parseArticle(Element art, Ciudad ciudad) {
        // ── fuenteId — extraído del href del enlace ──────────────────────────
        Element enlace = art.selectFirst("a[href*='/alquiler/']");
        if (enlace == null) return null;

        String href = enlace.attr("href");
        Matcher idMatcher = ID_PATTERN.matcher(href);
        if (!idMatcher.find()) return null;
        String fuenteId = idMatcher.group(1);

        // ── Textos del artículo ──────────────────────────────────────────────
        // Recogemos todos los textos cortos de span, p y li
        List<String> textos = new ArrayList<>();
        for (Element el : art.select("span, p, li")) {
            String t = el.text().trim();
            if (!t.isEmpty() && t.length() < 80) textos.add(t);
        }

        // ── Precio ───────────────────────────────────────────────────────────
        // Buscamos el texto que contiene "€ /mes"
        String precioText = textos.stream()
                .filter(t -> t.contains("€") && t.contains("mes"))
                .findFirst().orElse(null);
        if (precioText == null) return null;

        // "7.300 € /mes" → eliminamos puntos de miles
        String precioLimpio = precioText.replace(".", "").replace(",", ".");
        Matcher precioMatcher = PRECIO_PATTERN.matcher(precioLimpio);
        if (!precioMatcher.find()) return null;
        BigDecimal precioMes = new BigDecimal(precioMatcher.group());

        // ── Habitaciones ─────────────────────────────────────────────────────
        // "4 habs·" → 4
        Short habitaciones = null;
        for (String t : textos) {
            Matcher m = HAB_PATTERN.matcher(t);
            if (m.find()) {
                habitaciones = Short.parseShort(m.group(1));
                break;
            }
        }

        // ── Metros ───────────────────────────────────────────────────────────
        // "311 m²·" → 311
        Integer metros = null;
        for (String t : textos) {
            Matcher m = M2_PATTERN.matcher(t);
            if (m.find()) {
                metros = Integer.parseInt(m.group(1));
                break;
            }
        }

        // ── Planta ───────────────────────────────────────────────────────────
        // "5ª Planta·" → "5ª Planta"
        String planta = null;
        for (String t : textos) {
            Matcher m = PLANTA_PATTERN.matcher(t);
            if (m.find()) {
                planta = t.replace("·", "").trim();
                if (planta.length() > 20) planta = planta.substring(0, 20);
                break;
            }
        }

        // ── Barrio ───────────────────────────────────────────────────────────
        // "Sant Gervasi- Galvany,  Barcelona Capital"
        Barrio barrio = null;
        for (String t : textos) {
            if (t.contains("Barcelona Capital") || t.contains("Capital")) {
                String posibleBarrio = t.split(",")[0].trim();
                barrio = inferirBarrio(posibleBarrio, ciudad);
                break;
            }
        }

        // ── Amueblado ────────────────────────────────────────────────────────
        // Fotocasa lo indica en el href: "no-amueblado" o "amueblado"
        Boolean amueblado = null;
        if (href.contains("no-amueblado")) {
            amueblado = false;
        } else if (href.contains("amueblado")) {
            amueblado = true;
        }

        // ── Mascotas ─────────────────────────────────────────────────────────
        Boolean permiteMascotas = null;
        if (href.contains("se-aceptan-mascotas")) {
            permiteMascotas = true;
        }

        return Piso.builder()
                .fuente("fotocasa")
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

    private Barrio inferirBarrio(String nombreBarrio, Ciudad ciudad) {
        if (nombreBarrio == null || nombreBarrio.isBlank()) return null;
        return barrioRepo.findByCiudad_Slug(ciudad.getSlug()).stream()
                .filter(b -> nombreBarrio.toLowerCase()
                        .contains(b.getNombre().toLowerCase()
                                .substring(0, Math.min(5, b.getNombre().length()))))
                .findFirst()
                .orElse(null);
    }

    // ─── Paginación ───────────────────────────────────────────────────────────

    /**
     * Fotocasa usa URLs paginadas así:
     *   Página 1: /es/alquiler/viviendas/barcelona-capital/todas-las-zonas/l
     *   Página 2: /es/alquiler/viviendas/barcelona-capital/todas-las-zonas/l?page=2
     */
    private String siguientePagina(Document doc, int paginaActual) {
        // Buscamos el botón/enlace "Siguiente"
        Element next = doc.selectFirst("a[aria-label='Siguiente'], a[rel='next']");
        if (next != null) {
            String href = next.attr("href");
            return href.startsWith("http") ? href : BASE_URL + href;
        }

        // Fallback: construir URL con ?page=N+1 si hay más páginas
        // Detectamos si hay página siguiente buscando el número de página actual en la paginación
        Element paginador = doc.selectFirst("[aria-label='Página " + (paginaActual + 1) + "']");
        if (paginador != null) {
            return BASE_URL + "/es/alquiler/viviendas/barcelona-capital/todas-las-zonas/l?page=" + (paginaActual + 1);
        }

        return null;
    }

    // ─── Guardar en BD ────────────────────────────────────────────────────────

    private void guardarPisos(List<Piso> pisos) {
        for (Piso piso : pisos) {
            try {
                Optional<Piso> existente = pisoRepo.findByFuenteAndFuenteId("fotocasa", piso.getFuenteId());
                if (existente.isPresent()) {
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

    private void esperarRandom(int minMs, int maxMs) {
        try {
            long ms = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}