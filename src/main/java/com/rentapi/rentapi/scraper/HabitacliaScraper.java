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
public class HabitacliaScraper {

    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;
    private final PisoRepository pisoRepo;

    private static final String BASE_URL   = "https://www.habitaclia.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";

    // "311m2 - 4 habitaciones - 4 baños - 23,47€/m2"
    private static final Pattern M2_PATTERN     = Pattern.compile("(\\d+)m2");
    private static final Pattern HAB_PATTERN    = Pattern.compile("(\\d+)\\s+habitacion");
    private static final Pattern PRECIO_PATTERN = Pattern.compile("[\\d\\.]+");
    // fuenteId: número al final del slug antes de .htm → "-i2277003488164.htm"
    private static final Pattern ID_PATTERN     = Pattern.compile("-i(\\d+)\\.htm");

    // ─── Entrada principal ────────────────────────────────────────────────────

    public void scrapeCiudad(String ciudadSlug) {
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        Ciudad ciudad = ciudadOpt.get();
        // URL de Habitaclia: /alquiler-barcelona.htm, /alquiler-madrid.htm
        String startUrl = BASE_URL + "/alquiler-" + ciudadSlug + ".htm";

        log.info("Iniciando scraping Habitaclia de {} → {}", ciudadSlug, startUrl);
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

                url = siguientePagina(doc);
                pagina++;
                esperarRandom(3000, 6000);

            } catch (Exception e) {
                log.error("Error en página {}: {}", pagina, e.getMessage());
                break;
            }
        }
        log.info("Scraping Habitaclia completado. Total páginas: {}", pagina - 1);
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

        Elements articles = doc.select("article");

        for (Element art : articles) {
            if (!art.text().contains("€")) continue;
            try {
                Piso piso = parseArticle(art, ciudad);
                if (piso != null) resultado.add(piso);
            } catch (Exception e) {
                log.warn("Error parseando artículo Habitaclia: {}", e.getMessage());
            }
        }
        return resultado;
    }

    private Piso parseArticle(Element art, Ciudad ciudad) {
        // ── fuenteId — del href del enlace ───────────────────────────────────
        Element enlace = art.selectFirst("a[href*='habitaclia.com']");
        if (enlace == null) enlace = art.selectFirst("a[href]");
        if (enlace == null) return null;

        String href = enlace.attr("href");
        Matcher idMatcher = ID_PATTERN.matcher(href);
        if (!idMatcher.find()) return null;
        String fuenteId = idMatcher.group(1);

        // ── Textos del artículo ──────────────────────────────────────────────
        List<String> textos = new ArrayList<>();
        for (Element el : art.select("span, p, li, a")) {
            String t = el.text().trim();
            if (!t.isEmpty() && t.length() < 100) textos.add(t);
        }

        // ── Precio ───────────────────────────────────────────────────────────
        // "7.300 €" — buscamos texto que contenga € pero NO €/m2
        String precioText = textos.stream()
                .filter(t -> t.contains("€") && !t.contains("€/m2") && !t.contains("€/m²"))
                .filter(t -> !t.contains("-"))  // excluir línea de detalles
                .findFirst().orElse(null);
        if (precioText == null) return null;

        String precioLimpio = precioText.replace(".", "").replace(",", ".").replaceAll("[^\\d.]", "");
        if (precioLimpio.isEmpty()) return null;
        BigDecimal precioMes;
        try {
            precioMes = new BigDecimal(precioLimpio.split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }

        // ── Detalles: m2, habitaciones ───────────────────────────────────────
        // "311m2 - 4 habitaciones - 4 baños - 23,47€/m2"
        String detalles = textos.stream()
                .filter(t -> t.contains("m2") && t.contains("habitacion"))
                .findFirst().orElse("");

        Integer metros = null;
        Matcher m2M = M2_PATTERN.matcher(detalles);
        if (m2M.find()) metros = Integer.parseInt(m2M.group(1));

        Short habitaciones = null;
        Matcher habM = HAB_PATTERN.matcher(detalles);
        if (habM.find()) habitaciones = Short.parseShort(habM.group(1));

        // Estudio: sin habitaciones mencionadas o 0
        if (habitaciones == null && detalles.contains("estudio")) habitaciones = 0;

        // ── Barrio ───────────────────────────────────────────────────────────
        // "Barcelona - Sant Gervasi - Galvany"
        Barrio barrio = null;
        String barrioText = textos.stream()
                .filter(t -> t.contains(" - ") && t.toLowerCase().contains(ciudad.getNombre().toLowerCase()))
                .findFirst().orElse(null);

        if (barrioText != null) {
            String[] partes = barrioText.split(" - ");
            // La última parte suele ser el barrio más específico
            if (partes.length >= 2) {
                String posibleBarrio = partes[partes.length - 1].trim();
                barrio = inferirBarrio(posibleBarrio, ciudad);
            }
        }

        return Piso.builder()
                .fuente("habitaclia")
                .fuenteId(fuenteId)
                .ciudad(ciudad)
                .barrio(barrio)
                .precioMes(precioMes)
                .metrosCuadrados(metros)
                .habitaciones(habitaciones)
                .planta(null)  // Habitaclia no muestra planta en el listado
                .amueblado(null)
                .permiteMascotas(null)
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
     * Habitaclia pagina así:
     *   Página 1: /alquiler-barcelona.htm
     *   Página 2: /alquiler-barcelona-2.htm
     */
    private String siguientePagina(Document doc) {
        Element next = doc.selectFirst("a[rel='next'], a[aria-label='Siguiente'], .next a, [class*='next'] a");
        if (next != null) {
            String href = next.attr("href");
            return href.startsWith("http") ? href : BASE_URL + href;
        }
        return null;
    }

    // ─── Guardar en BD ────────────────────────────────────────────────────────

    private void guardarPisos(List<Piso> pisos) {
        for (Piso piso : pisos) {
            try {
                Optional<Piso> existente = pisoRepo.findByFuenteAndFuenteId("habitaclia", piso.getFuenteId());
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