package com.rentapi.rentapi.scraper;

import com.rentapi.rentapi.model.Barrio;
import com.rentapi.rentapi.model.Ciudad;
import com.rentapi.rentapi.model.Piso;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.PisoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class HabitacliaScraper {

    private final CiudadRepository  ciudadRepo;
    private final BarrioRepository   barrioRepo;
    private final PisoRepository     pisoRepo;
    private final BrowserHttpClient  httpClient;
    private static final int MAX_PAGINAS = 10;

    @Value("${habitaclia.cookie.awsalb:}")
    private String cookieAwsalb;

    @Value("${habitaclia.cookie.awsalbcors:}")
    private String cookieAwsalbcors;

    @Value("${habitaclia.cookie.borostcf:}")
    private String cookieBorostcf;

    @Value("${habitaclia.cookie.aspsession:}")
    private String cookieAspsession;

    private static final String BASE_URL = "https://www.habitaclia.com";

    // ID del anuncio al final del slug: "-i2277003488164.htm"
    private static final Pattern ID_PATTERN = Pattern.compile("-i(\\d+)\\.htm");

    // ─── Entrada principal ────────────────────────────────────────────────────

    public void scrapeCiudad(String ciudadSlug) {
        if (cookieAwsalb != null && !cookieAwsalb.isBlank()) {
            Map<String, String> cookies = Map.of(
                    "AWSALB",               cookieAwsalb,
                    "AWSALBCORS",           cookieAwsalbcors,
                    "borosTcf",             cookieBorostcf,
                    "ASPSESSIONIDAQRTASDB", cookieAspsession
            );
            log.info("[Habitaclia] Cookies: awsalb='{}' aspsession='{}'",
                    cookieAwsalb, cookieAspsession);
            httpClient.setCookies("www.habitaclia.com", cookies);
            log.info("[Habitaclia] Cookies de sesión inyectadas.");
        }
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("[Habitaclia] Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        Ciudad ciudad = ciudadOpt.get();
        String startUrl = BASE_URL + "/alquiler-" + ciudadSlug + ".htm";

        log.info("[Habitaclia] Iniciando scraping de {} → {}", ciudadSlug, startUrl);
        scrapePaginas(startUrl, ciudad);
    }

    public void scrapeDesdeArchivoLocal(String rutaHtml, String ciudadSlug) {
        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("[Habitaclia] Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        try {
            Document doc = org.jsoup.Jsoup.parse(new java.io.File(rutaHtml), "UTF-8", BASE_URL);
            List<Piso> pisos = parsePisos(doc, ciudadOpt.get());
            guardarPisos(pisos);
            log.info("[Habitaclia] Pisos parseados del archivo local: {}", pisos.size());
        } catch (Exception e) {
            log.error("[Habitaclia] Error leyendo archivo local: {}", e.getMessage());
        }
    }

    // ─── Paginación ───────────────────────────────────────────────────────────

    private void scrapePaginas(String startUrl, Ciudad ciudad) {
        String url     = startUrl;
        String referer = null;
        int pagina     = 1;
        int totalPisos = 0;

        while (url != null) {
            log.info("[Habitaclia] Página {} → {}", pagina, url);
            try {
                // fetchDocument ahora usa BrowserHttpClient (TLS de Chrome + cabeceras reales)
                Document doc     = httpClient.fetchDocument(url, referer);
                List<Piso> pisos = parsePisos(doc, ciudad);
                guardarPisos(pisos);
                totalPisos += pisos.size();
                log.info("[Habitaclia] Página {}: {} pisos (total: {})", pagina, pisos.size(), totalPisos);

                referer = url;
                url = siguientePagina(doc, ciudadSlug(startUrl), pagina);
                pagina++;

                if (pagina > MAX_PAGINAS) {
                    log.info("[Habitaclia] Límite de {} páginas alcanzado.", MAX_PAGINAS);
                    break;
                }

            } catch (ScraperBlockedException e) {
                // WAF nos bloqueó — loguear y parar limpiamente (no reintentar)
                log.error("[Habitaclia] Bloqueado por WAF en página {}: {}", pagina, e.getMessage());
                break;
            } catch (Exception e) {
                log.error("[Habitaclia] Error en página {}: {}", pagina, e.getMessage());
                break;
            }
        }
        log.info("[Habitaclia] Fin. Páginas: {}, Pisos totales: {}", pagina - 1, totalPisos);
    }

    // ─── Parser principal ─────────────────────────────────────────────────────

    private List<Piso> parsePisos(Document doc, Ciudad ciudad) {
        List<Piso> resultado = new ArrayList<>();
        Elements articles = doc.select("article");
        log.debug("[Habitaclia] Artículos encontrados: {}", articles.size());

        for (Element art : articles) {
            try {
                Piso piso = parseArticle(art, ciudad);
                if (piso != null) resultado.add(piso);
            } catch (Exception e) {
                log.warn("[Habitaclia] Error parseando artículo: {}", e.getMessage());
            }
        }
        return resultado;
    }

    private Piso parseArticle(Element art, Ciudad ciudad) {

        // ── 1. fuenteId ───────────────────────────────────────────────────────
        Element enlace = art.selectFirst("a[href]");
        if (enlace == null) return null;

        String href = enlace.attr("href");
        Matcher idMatcher = ID_PATTERN.matcher(href);
        if (!idMatcher.find()) return null;
        String fuenteId = idMatcher.group(1);

        // ── 2. Precio, m², habitaciones ───────────────────────────────────────
        Element notify = art.selectFirst(".js-notify");

        BigDecimal precioMes  = null;
        Integer    metros     = null;
        Short      habitaciones = null;

        if (notify != null) {
            String pvp = notify.attr("data-pvp");
            if (!pvp.isBlank()) {
                try { precioMes = new BigDecimal(pvp.trim()); } catch (Exception ignored) {}
            }

            String sup = notify.attr("data-sup");
            if (!sup.isBlank()) {
                try { metros = Integer.parseInt(sup.trim()); } catch (Exception ignored) {}
            }

            String hab = notify.attr("data-hab");
            if (!hab.isBlank()) {
                try { habitaciones = Short.parseShort(hab.trim()); } catch (Exception ignored) {}
            }
        }

        if (precioMes == null) {
            precioMes = parsePrecioFallback(art);
        }

        if (precioMes == null) return null;

        // ── 3. Barrio ─────────────────────────────────────────────────────────
        Barrio barrio = null;
        Element locEl = art.selectFirst(".list-item-location span, .list-item-location");
        if (locEl != null) {
            String locText = locEl.text().trim();
            String[] partes = locText.split(" - ");
            if (partes.length >= 2) {
                String nombreBarrio = partes[partes.length - 1].trim();
                barrio = inferirBarrio(nombreBarrio, ciudad);
            }
        }

        // ── 4. Estudio ────────────────────────────────────────────────────────
        if (habitaciones == null) {
            String textoArt = art.text().toLowerCase();
            if (textoArt.contains("estudio") || textoArt.contains("loft")) {
                habitaciones = 0;
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
                .planta(null)
                .amueblado(null)
                .permiteMascotas(null)
                .activo(true)
                .build();
    }

    // ─── Fallback precio ──────────────────────────────────────────────────────

    private BigDecimal parsePrecioFallback(Element art) {
        for (Element el : art.select("span, p, strong, div")) {
            String t = el.text().trim();
            if (t.contains("€") && !t.contains("€/m") && t.length() < 30) {
                String limpio = t.replace(".", "").replace(",", "")
                        .replaceAll("[^\\d]", "");
                if (!limpio.isEmpty()) {
                    try {
                        long valor = Long.parseLong(limpio);
                        if (valor >= 200 && valor <= 30_000) {
                            return new BigDecimal(valor);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    // ─── Inferir barrio ───────────────────────────────────────────────────────

    private Barrio inferirBarrio(String nombreBarrio, Ciudad ciudad) {
        if (nombreBarrio == null || nombreBarrio.isBlank()) return null;
        String buscar = nombreBarrio.toLowerCase();
        return barrioRepo.findByCiudad_Slug(ciudad.getSlug()).stream()
                .filter(b -> {
                    String bn = b.getNombre().toLowerCase();
                    return buscar.contains(bn) || bn.contains(buscar);
                })
                .findFirst()
                .orElse(null);
    }

    // ─── Siguiente página ─────────────────────────────────────────────────────

    private String siguientePagina(Document doc, String slug, int paginaActual) {
        Element next = doc.selectFirst(
                "a[rel='next'], " +
                        "a[title='Siguiente página'], " +
                        "a[aria-label='Siguiente'], " +
                        ".pagination a[class*='next'], " +
                        "li.next a"
        );
        if (next != null) {
            String href = next.attr("href");
            if (!href.isBlank()) {
                return href.startsWith("http") ? href : BASE_URL + href;
            }
        }

        int articulosEnPagina = doc.select("article").size();
        if (articulosEnPagina > 0) {
            String urlSiguiente = BASE_URL + "/alquiler-" + slug + "-" + paginaActual + ".htm";
            Element paginadorActivo = doc.selectFirst(
                    ".pagination .active, .pagination [aria-current='page']"
            );
            if (paginadorActivo != null) {
                Element ultimaOpcion = doc.selectFirst(
                        ".pagination li:last-child a, .pagination a:last-child"
                );
                if (ultimaOpcion != null) {
                    String hrefUltima = ultimaOpcion.attr("href");
                    if (hrefUltima.contains("-" + paginaActual + ".htm")) return null;
                }
            }
            return urlSiguiente;
        }

        return null;
    }

    // ─── Guardar en BD ────────────────────────────────────────────────────────

    private void guardarPisos(List<Piso> pisos) {
        int nuevos = 0, actualizados = 0;
        for (Piso piso : pisos) {
            try {
                Optional<Piso> existente = pisoRepo.findByFuenteAndFuenteId("habitaclia", piso.getFuenteId());
                if (existente.isPresent()) {
                    Piso p = existente.get();
                    p.setPrecioMes(piso.getPrecioMes());
                    p.setMetrosCuadrados(piso.getMetrosCuadrados());
                    p.setHabitaciones(piso.getHabitaciones());
                    p.setActivo(true);
                    pisoRepo.save(p);
                    actualizados++;
                } else {
                    pisoRepo.save(piso);
                    nuevos++;
                }
            } catch (Exception e) {
                log.warn("[Habitaclia] Error guardando piso fuenteId={}: {}", piso.getFuenteId(), e.getMessage());
            }
        }
        log.info("[Habitaclia] BD → {} nuevos, {} actualizados", nuevos, actualizados);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String ciudadSlug(String startUrl) {
        return startUrl
                .replace(BASE_URL + "/alquiler-", "")
                .replace(".htm", "");
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