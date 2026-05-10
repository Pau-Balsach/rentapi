package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.dto.StatsDTO;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.service.RateLimitService;
import com.rentapi.rentapi.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Estadísticas", description = "Núcleo de la API: estadísticas de alquiler por zona geográfica")
@SecurityRequirement(name = "ApiKeyAuth")
public class StatsController {

    private final StatsService statsService;
    private final RateLimitService rateLimitService;

    // ─────────────────────────────────────────────────────────────
    // GET /stats/ciudad/{slug}
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/ciudad/{slug}")
    @Operation(
            summary = "Estadísticas de una ciudad",
            description = "Devuelve estadísticas agregadas de alquiler para una ciudad completa: " +
                    "precios, distribución por tipología y tendencia mensual."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas devueltas correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Ciudad no encontrada")
    })
    public ResponseEntity<StatsDTO.CiudadStatsResponse> getStatsCiudad(
            @PathVariable String slug,
            @Parameter(description = "Tipología: 0=estudio, 1, 2, 3, 4 o más habitaciones")
            @RequestParam(required = false) Short habitaciones,
            @Parameter(description = "Fecha inicio del periodo (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha fin del periodo (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletRequest request) {

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/ciudad/{slug}");

        StatsDTO.CiudadStatsResponse respuesta =
                statsService.getStatsCiudad(slug, habitaciones, fechaInicio, fechaFin);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/barrio/{ciudad_slug}/{barrio_slug}
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/barrio/{ciudadSlug}/{barrioSlug}")
    @Operation(
            summary = "Estadísticas de un barrio",
            description = "Devuelve estadísticas detalladas para un barrio concreto, " +
                    "incluyendo comparativa porcentual respecto a la media de la ciudad."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas devueltas correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Barrio o ciudad no encontrados")
    })
    public ResponseEntity<StatsDTO.BarrioStatsResponse> getStatsBarrio(
            @PathVariable String ciudadSlug,
            @PathVariable String barrioSlug,
            @RequestParam(required = false) Short habitaciones,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletRequest request) {

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/barrio/{ciudad}/{barrio}");

        StatsDTO.BarrioStatsResponse respuesta =
                statsService.getStatsBarrio(ciudadSlug, barrioSlug, habitaciones, fechaInicio, fechaFin);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/tendencia/{slug}
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/tendencia/{slug}")
    @Operation(
            summary = "Evolución histórica de precios",
            description = "Serie temporal mensual del precio de alquiler en una ciudad o barrio. " +
                    "Incluye variación total del periodo y del último mes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Serie temporal devuelta correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Zona no encontrada o sin datos")
    })
    public ResponseEntity<StatsDTO.TendenciaResponse> getTendencia(
            @PathVariable String slug,
            @Parameter(description = "Tipo de zona: 'ciudad' (default) o 'barrio'")
            @RequestParam(defaultValue = "ciudad") String tipo,
            @Parameter(description = "Número de meses hacia atrás (default: 6, máx: 24)")
            @RequestParam(defaultValue = "6") int meses,
            @RequestParam(required = false) Short habitaciones,
            HttpServletRequest request) {

        if (meses < 1 || meses > 24) {
            throw new IllegalArgumentException("El parámetro 'meses' debe estar entre 1 y 24.");
        }

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/tendencia/{slug}");

        StatsDTO.TendenciaResponse respuesta =
                statsService.getTendencia(slug, tipo, meses, habitaciones);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/comparar
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/comparar")
    @Operation(
            summary = "Comparar zonas",
            description = "Compara estadísticas de alquiler entre 2 y 5 ciudades o barrios. " +
                    "Para barrios, usar el formato 'ciudad-slug/barrio-slug' en cada zona."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparativa generada correctamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros incorrectos"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente")
    })
    public ResponseEntity<StatsDTO.ComparativaResponse> comparar(
            @Parameter(description = "Slugs separados por comas, máx. 5. Ej: barcelona,madrid,valencia")
            @RequestParam String zonas,
            @Parameter(description = "Tipo: 'ciudad' (default) o 'barrio'")
            @RequestParam(defaultValue = "ciudad") String tipo,
            @RequestParam(required = false) Short habitaciones,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletRequest request) {

        List<String> slugs = Arrays.asList(zonas.split(","));
        if (slugs.size() < 2 || slugs.size() > 5) {
            throw new IllegalArgumentException("Debes indicar entre 2 y 5 zonas para comparar.");
        }

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/comparar");

        StatsDTO.ComparativaResponse respuesta =
                statsService.comparar(slugs, tipo, habitaciones, fechaInicio, fechaFin);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/ranking
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/ranking")
    @Operation(
            summary = "Ranking de zonas por precio",
            description = "Devuelve un ranking de ciudades o barrios ordenado por precio medio por m². " +
                    "Opcionalmente filtrable por provincia."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking generado correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente")
    })
    public ResponseEntity<StatsDTO.RankingResponse> getRanking(
            @Parameter(description = "Tipo: 'ciudad' (default) o 'barrio'")
            @RequestParam(defaultValue = "ciudad") String tipo,
            @Parameter(description = "Orden: 'desc' más caras primero (default) o 'asc' más baratas")
            @RequestParam(defaultValue = "desc") String orden,
            @Parameter(description = "Slug de provincia para filtrar el ranking (opcional)")
            @RequestParam(required = false) String provincia,
            @Parameter(description = "Número de resultados (default: 10, máx: 50)")
            @RequestParam(defaultValue = "10") int limite,
            HttpServletRequest request) {

        if (!orden.equals("asc") && !orden.equals("desc")) {
            throw new IllegalArgumentException("El parámetro 'orden' debe ser 'asc' o 'desc'.");
        }

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/ranking");

        StatsDTO.RankingResponse respuesta =
                statsService.getRanking(tipo, orden, provincia, limite);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/evaluar
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/evaluar")
    @Operation(
            summary = "Evaluar un precio de alquiler",
            description = "Dado un precio y una zona, indica si está por encima, en rango o por debajo " +
                    "del mercado. Calcula el percentil del precio consultado en su zona."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluación realizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Faltan parámetros requeridos"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Zona sin datos disponibles")
    })
    public ResponseEntity<StatsDTO.EvaluacionResponse> evaluar(
            @Parameter(description = "Slug de la ciudad (requerido)", required = true)
            @RequestParam String ciudad,
            @Parameter(description = "Slug del barrio (opcional — si se omite, compara con la ciudad)")
            @RequestParam(required = false) String barrio,
            @Parameter(description = "Precio mensual en euros (requerido)", required = true)
            @RequestParam BigDecimal precio,
            @Parameter(description = "Tipología: 0=estudio, 1, 2, 3, 4+ habitaciones")
            @RequestParam(required = false) Short habitaciones,
            @Parameter(description = "Metros cuadrados del piso (opcional, informativo)")
            @RequestParam(required = false) Integer m2,
            HttpServletRequest request) {

        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser un valor positivo.");
        }

        Usuario usuario = getUsuario(request);
        rateLimitService.registrar(usuario, "/api/v1/stats/evaluar");

        StatsDTO.EvaluacionResponse respuesta =
                statsService.evaluar(ciudad, barrio, precio, habitaciones, m2);

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: extrae el Usuario del atributo de la request
    // (lo pone ApiKeyAuthFilter después de validar la API Key)
    // ─────────────────────────────────────────────────────────────

    private Usuario getUsuario(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getAttribute("usuario");
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado.");
        }
        return usuario;
    }
}