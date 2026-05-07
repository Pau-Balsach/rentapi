package com.rentapi.rentapi.dto;

// ─── GEO DTOs ────────────────────────────────────────────────────────────────

public class GeoDTO {

    public record ComunidadDTO(Long id, String nombre, String slug) {}

    public record ProvinciaDTO(Long id, String nombre, String slug, String comunidad) {}

    public record CiudadDTO(Long id, String nombre, String slug,
                            String provincia, long barriosDisponibles) {}

    public record BarrioDTO(Long id, String nombre, String slug,
                            String ciudad, long pisosIndexados) {}
}
