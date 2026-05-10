package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.dto.PisoDTO;
import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.service.PisoService;
import com.rentapi.rentapi.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/pisos")
@RequiredArgsConstructor
@Tag(name = "Pisos", description = "Consulta de pisos individuales indexados por ciudad, barrio y rango de precio")
@SecurityRequirement(name = "ApiKeyAuth")
public class PisoController {

    private final PisoService pisoService;
    private final RateLimitService rateLimitService;

    // ─────────────────────────────────────────────────────────────
    // GET /pisos/ciudad/{slug}
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/ciudad/{slug}")
    @Operation(
            summary = "Pisos de una ciudad",
            description = "Devuelve todos los pisos activos indexados para una ciudad, ordenados por precio ASC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pisos devuelta correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Ciudad sin datos disponibles")
    })
    public ResponseEntity<PisoDTO.PisoListResponse> getPisosByCiudad(
            @Parameter(description = "Slug de la ciudad. Ej: barcelona, madrid", required = true)
            @PathVariable String slug,
            HttpServletRequest request) {

        registrar(request, "/api/v1/pisos/ciudad/{slug}");
        return ResponseEntity.ok(pisoService.getPisosByCiudad(slug));
    }

    // ─────────────────────────────────────────────────────────────
    // GET /pisos/barrio/{ciudadSlug}/{barrioSlug}
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/barrio/{ciudadSlug}/{barrioSlug}")
    @Operation(
            summary = "Pisos de un barrio",
            description = "Devuelve todos los pisos activos de un barrio concreto dentro de una ciudad, ordenados por precio ASC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pisos devuelta correctamente"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "Barrio o ciudad sin datos disponibles")
    })
    public ResponseEntity<PisoDTO.PisoListResponse> getPisosByBarrio(
            @Parameter(description = "Slug de la ciudad. Ej: barcelona", required = true)
            @PathVariable String ciudadSlug,
            @Parameter(description = "Slug del barrio. Ej: gracia", required = true)
            @PathVariable String barrioSlug,
            HttpServletRequest request) {

        registrar(request, "/api/v1/pisos/barrio/{ciudad}/{barrio}");
        return ResponseEntity.ok(pisoService.getPisosByBarrio(ciudadSlug, barrioSlug));
    }

    // ─────────────────────────────────────────────────────────────
    // GET /pisos/precio
    // Filtro flexible: precio_min, precio_max, o ambos (rango).
    // Ciudad obligatoria. Barrio opcional.
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/precio")
    @Operation(
            summary = "Pisos por rango de precio",
            description = """
                Devuelve pisos filtrados por precio mensual. Ciudad obligatoria.
                
                Combinaciones posibles:
                - Solo `precio_max` → pisos con precio ≤ X
                - Solo `precio_min` → pisos con precio ≥ X
                - Ambos            → pisos dentro del rango [precio_min, precio_max]
                
                El parámetro `barrio` es opcional: si se omite, se busca en toda la ciudad.
                Resultados ordenados por precio ASC.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pisos devuelta correctamente"),
            @ApiResponse(responseCode = "400", description = "Faltan parámetros requeridos o el rango es inválido"),
            @ApiResponse(responseCode = "401", description = "API Key no válida o ausente"),
            @ApiResponse(responseCode = "404", description = "No hay pisos que cumplan el filtro")
    })
    public ResponseEntity<PisoDTO.PisoListResponse> getPisosByPrecio(
            @Parameter(description = "Slug de la ciudad (requerido). Ej: sevilla", required = true)
            @RequestParam String ciudad,
            @Parameter(description = "Slug del barrio (opcional). Ej: triana")
            @RequestParam(required = false) String barrio,
            @Parameter(description = "Precio mínimo mensual en euros (inclusive)")
            @RequestParam(required = false) BigDecimal precio_min,
            @Parameter(description = "Precio máximo mensual en euros (inclusive)")
            @RequestParam(required = false) BigDecimal precio_max,
            HttpServletRequest request) {

        // Validaciones
        if (precio_min == null && precio_max == null) {
            throw new IllegalArgumentException(
                    "Debes indicar al menos uno de los parámetros: precio_min o precio_max.");
        }
        if (precio_min != null && precio_min.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("precio_min debe ser un valor positivo.");
        }
        if (precio_max != null && precio_max.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("precio_max debe ser un valor positivo.");
        }
        if (precio_min != null && precio_max != null && precio_min.compareTo(precio_max) > 0) {
            throw new IllegalArgumentException("precio_min no puede ser mayor que precio_max.");
        }

        registrar(request, "/api/v1/pisos/precio");

        PisoDTO.PisoListResponse respuesta;

        if (precio_min != null && precio_max != null) {
            respuesta = pisoService.getPisosByRangoPrecio(ciudad, barrio, precio_min, precio_max);
        } else if (precio_max != null) {
            respuesta = pisoService.getPisosByPrecioMaximo(ciudad, barrio, precio_max);
        } else {
            respuesta = pisoService.getPisosByPrecioMinimo(ciudad, barrio, precio_min);
        }

        return ResponseEntity.ok(respuesta);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private void registrar(HttpServletRequest request, String endpoint) {
        Usuario usuario = (Usuario) request.getAttribute("usuario");
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado.");
        }
        rateLimitService.registrar(usuario, endpoint);
    }
}
