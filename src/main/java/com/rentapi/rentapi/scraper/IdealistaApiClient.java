package com.rentapi.rentapi.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentapi.rentapi.model.Barrio;
import com.rentapi.rentapi.model.Ciudad;
import com.rentapi.rentapi.model.Piso;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.PisoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Cliente oficial de la API REST de Idealista.
 *
 * Documentación: https://developers.idealista.com/
 *
 * Flujo OAuth2 Client Credentials:
 *   1. POST https://api.idealista.com/oauth/token  → access_token (expira en 7 días)
 *   2. GET  https://api.idealista.com/3.5/es/search → listado de pisos en JSON
 *
 * Variables de entorno necesarias (application.properties en local, Render en prod):
 *   idealista.api.key=TU_API_KEY
 *   idealista.api.secret=TU_API_SECRET
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdealistaApiClient {

    // ─── Configuración ────────────────────────────────────────────────────────

    @Value("${idealista.api.key:}")
    private String apiKey;

    @Value("${idealista.api.secret:}")
    private String apiSecret;

    private static final String TOKEN_URL  = "https://api.idealista.com/oauth/token";
    private static final String SEARCH_URL = "https://api.idealista.com/3.5/es/search";

    // Cache del token en memoria (expira en 7 días, suficiente para el scheduler nocturno)
    private String cachedToken      = null;
    private long   tokenExpiresAt   = 0;

    // ─── Dependencias ─────────────────────────────────────────────────────────

    private final CiudadRepository ciudadRepo;
    private final BarrioRepository  barrioRepo;
    private final PisoRepository    pisoRepo;
    private final RestTemplate      restTemplate = new RestTemplate();
    private final ObjectMapper      objectMapper = new ObjectMapper();

    // ─── Entrada principal ────────────────────────────────────────────────────

    /**
     * Scraping completo de una ciudad usando la API de Idealista.
     * Equivalente a HabitacliaScraper.scrapeCiudad() pero vía API oficial.
     *
     * @param ciudadSlug  slug de la ciudad (ej: "barcelona", "madrid")
     */
    public void scrapeCiudad(String ciudadSlug) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            log.error("[Idealista] API Key o Secret no configurados. " +
                    "Añade idealista.api.key e idealista.api.secret en application.properties");
            return;
        }

        Optional<Ciudad> ciudadOpt = ciudadRepo.findBySlug(ciudadSlug);
        if (ciudadOpt.isEmpty()) {
            log.warn("[Idealista] Ciudad no encontrada en BD: {}", ciudadSlug);
            return;
        }
        Ciudad ciudad = ciudadOpt.get();

        log.info("[Idealista] Iniciando scraping de {}", ciudadSlug);

        try {
            String token = obtenerToken();
            scrapeTodasLasPaginas(token, ciudad, ciudadSlug);
        } catch (Exception e) {
            log.error("[Idealista] Error en scraping de {}: {}", ciudadSlug, e.getMessage(), e);
        }
    }

    // ─── OAuth2 Token ─────────────────────────────────────────────────────────

    /**
     * Obtiene el access_token de Idealista usando OAuth2 Client Credentials.
     * Cachea el token en memoria para no pedir uno nuevo en cada página.
     */
    private String obtenerToken() throws Exception {
        // Reutilizar token si aún es válido (con 60s de margen)
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) {
            log.debug("[Idealista] Usando token cacheado.");
            return cachedToken;
        }

        log.info("[Idealista] Solicitando nuevo access_token...");

        // Basic Auth: Base64(apiKey:apiSecret)
        String credentials = apiKey + ":" + apiSecret;
        String basicAuth = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + basicAuth);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error obteniendo token: HTTP " + response.getStatusCode());
        }

        JsonNode json = objectMapper.readTree(response.getBody());
        cachedToken    = json.get("access_token").asText();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 604800L; // 7 días default
        tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000);

        log.info("[Idealista] Token obtenido. Expira en {} segundos.", expiresIn);
        return cachedToken;
    }

    // ─── Paginación ───────────────────────────────────────────────────────────

    /**
     * La API de Idealista pagina con el parámetro "numPage".
     * Cada respuesta incluye "totalPages" para saber cuántas hay.
     */
    private void scrapeTodasLasPaginas(String token, Ciudad ciudad, String ciudadSlug) throws Exception {
        int paginaActual = 1;
        int totalPaginas = 1; // se actualiza en la primera respuesta
        int totalPisos   = 0;

        // Coordenadas por ciudad para el parámetro "center" de la API
        String center = obtenerCoordenadas(ciudadSlug);

        while (paginaActual <= totalPaginas) {
            log.info("[Idealista] Página {}/{} → ciudad: {}", paginaActual, totalPaginas, ciudadSlug);

            JsonNode respuesta = llamarApi(token, center, paginaActual);

            // Actualizar total de páginas con la primera respuesta
            if (respuesta.has("totalPages")) {
                totalPaginas = respuesta.get("totalPages").asInt();
            }

            List<Piso> pisos = parsearRespuesta(respuesta, ciudad);
            guardarPisos(pisos);
            totalPisos += pisos.size();

            log.info("[Idealista] Página {}: {} pisos (total: {})", paginaActual, pisos.size(), totalPisos);

            if (pisos.isEmpty()) break; // seguridad extra

            paginaActual++;
            esperarRandom(1_000, 3_000); // delay cortés con la API
        }

        log.info("[Idealista] Scraping completado. Páginas: {}, Pisos totales: {}",
                paginaActual - 1, totalPisos);
    }

    // ─── Llamada a la API ─────────────────────────────────────────────────────

    /**
     * Llama al endpoint de búsqueda de Idealista.
     *
     * Parámetros principales:
     *   country       = es  (España)
     *   operation     = rent
     *   propertyType  = homes
     *   center        = latitud,longitud
     *   distance      = radio en metros
     *   numPage       = página actual
     *   maxItems      = 50 (máximo por página)
     */
    private JsonNode llamarApi(String token, String center, int numPage) throws Exception {
        String url = SEARCH_URL +
                "?country=es" +
                "&operation=rent" +
                "&propertyType=homes" +
                "&center=" + center +
                "&distance=20000" +
                "&numPage=" + numPage +
                "&maxItems=50" +
                "&locale=es";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error en API Idealista: HTTP " + response.getStatusCode());
        }

        return objectMapper.readTree(response.getBody());
    }

    // ─── Parser de respuesta JSON ─────────────────────────────────────────────

    /**
     * La API devuelve un array "elementList" con los pisos.
     * Campos relevantes:
     *   propertyCode  → fuenteId
     *   price         → precioMes
     *   size          → metrosCuadrados
     *   rooms         → habitaciones
     *   neighborhood  → barrio
     *   district      → distrito
     */
    private List<Piso> parsearRespuesta(JsonNode respuesta, Ciudad ciudad) {
        List<Piso> resultado = new ArrayList<>();

        JsonNode elementList = respuesta.get("elementList");
        if (elementList == null || !elementList.isArray()) {
            log.warn("[Idealista] Respuesta sin elementList.");
            return resultado;
        }

        for (JsonNode item : elementList) {
            try {
                Piso piso = parsearPiso(item, ciudad);
                if (piso != null) resultado.add(piso);
            } catch (Exception e) {
                log.warn("[Idealista] Error parseando piso: {}", e.getMessage());
            }
        }

        return resultado;
    }

    private Piso parsearPiso(JsonNode item, Ciudad ciudad) {
        // fuenteId obligatorio
        String fuenteId = item.has("propertyCode") ? item.get("propertyCode").asText() : null;
        if (fuenteId == null || fuenteId.isBlank()) return null;

        // Precio obligatorio
        if (!item.has("price")) return null;
        BigDecimal precio = new BigDecimal(item.get("price").asText());

        // Sanity check precio
        if (precio.compareTo(BigDecimal.valueOf(200)) < 0 ||
                precio.compareTo(BigDecimal.valueOf(30_000)) > 0) return null;

        // Metros cuadrados
        Integer metros = item.has("size") ? item.get("size").asInt() : null;

        // Habitaciones
        Short habitaciones = null;
        if (item.has("rooms")) {
            habitaciones = item.get("rooms").shortValue();
        }
        // Estudios
        if (habitaciones == null && item.has("propertyType")) {
            String tipo = item.get("propertyType").asText("");
            if (tipo.equalsIgnoreCase("studio")) habitaciones = 0;
        }

        // Barrio
        Barrio barrio = null;
        if (item.has("neighborhood")) {
            String nombreBarrio = item.get("neighborhood").asText("");
            barrio = inferirBarrio(nombreBarrio, ciudad);
        }

        // Planta
        String planta = item.has("floor") ? item.get("floor").asText(null) : null;

        return Piso.builder()
                .fuente("idealista")
                .fuenteId(fuenteId)
                .ciudad(ciudad)
                .barrio(barrio)
                .precioMes(precio)
                .metrosCuadrados(metros)
                .habitaciones(habitaciones)
                .planta(planta)
                .amueblado(item.has("hasParkingSpace") ? null : null) // no disponible en listing
                .permiteMascotas(null)
                .activo(true)
                .build();
    }

    // ─── Guardar en BD ────────────────────────────────────────────────────────

    private void guardarPisos(List<Piso> pisos) {
        int nuevos = 0, actualizados = 0;
        for (Piso piso : pisos) {
            try {
                Optional<Piso> existente = pisoRepo.findByFuenteAndFuenteId("idealista", piso.getFuenteId());
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
                log.warn("[Idealista] Error guardando piso fuenteId={}: {}", piso.getFuenteId(), e.getMessage());
            }
        }
        log.info("[Idealista] BD → {} nuevos, {} actualizados", nuevos, actualizados);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    /**
     * Coordenadas centro de cada ciudad para el parámetro "center" de la API.
     * Formato: "latitud,longitud"
     */
    private String obtenerCoordenadas(String ciudadSlug) {
        return switch (ciudadSlug.toLowerCase()) {
            case "madrid"    -> "40.416775,-3.703790";
            case "barcelona" -> "41.385064,2.173403";
            case "valencia"  -> "39.469907,-0.376288";
            case "sevilla"   -> "37.389092,-5.984459";
            case "zaragoza"  -> "41.648823,-0.889085";
            case "malaga"    -> "36.721261,-4.421265";
            case "bilbao"    -> "43.262985,-2.934985";
            case "alicante"  -> "38.345517,-0.481321";
            default          -> "40.416775,-3.703790"; // Madrid por defecto
        };
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