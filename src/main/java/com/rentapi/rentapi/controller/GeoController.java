package com.rentapi.rentapi.controller;

import com.rentapi.rentapi.dto.GeoDTO;
import com.rentapi.rentapi.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Geografía", description = "Endpoints públicos para explorar zonas geográficas disponibles")
public class GeoController {

    private final GeoService geoService;

    @GetMapping("/comunidades")
    @Operation(summary = "Lista todas las comunidades autónomas disponibles")
    public ResponseEntity<List<GeoDTO.ComunidadDTO>> getComunidades() {
        return ResponseEntity.ok(geoService.getComunidades());
    }

    @GetMapping("/provincias")
    @Operation(summary = "Lista provincias, filtrando opcionalmente por comunidad")
    public ResponseEntity<List<GeoDTO.ProvinciaDTO>> getProvincias(
            @Parameter(description = "Slug de la comunidad autónoma (opcional)")
            @RequestParam(required = false) String comunidad) {
        return ResponseEntity.ok(geoService.getProvincias(comunidad));
    }

    @GetMapping("/ciudades")
    @Operation(summary = "Lista ciudades con datos disponibles")
    public ResponseEntity<List<GeoDTO.CiudadDTO>> getCiudades(
            @Parameter(description = "Slug de la provincia (opcional)")
            @RequestParam(required = false) String provincia) {
        return ResponseEntity.ok(geoService.getCiudades(provincia));
    }

    @GetMapping("/barrios")
    @Operation(summary = "Lista barrios de una ciudad con datos disponibles")
    public ResponseEntity<List<GeoDTO.BarrioDTO>> getBarrios(
            @Parameter(description = "Slug de la ciudad (requerido)", required = true)
            @RequestParam String ciudad) {
        return ResponseEntity.ok(geoService.getBarrios(ciudad));
    }
}
