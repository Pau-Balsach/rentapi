package com.rentapi.rentapi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PisoDTO {

    /**
     * Respuesta de un piso individual.
     */
    @Data
    @Builder
    public static class PisoResponse {
        private Long id;
        private String fuente;
        private String ciudad;
        private String ciudadSlug;
        private String barrio;
        private String barrioSlug;
        private BigDecimal precioMes;
        private Integer metrosCuadrados;
        private Short habitaciones;
        private String planta;
        private Boolean amueblado;
        private Boolean permiteMascotas;
        private LocalDate fechaPublicacion;
        private LocalDateTime fechaScraping;
    }

    /**
     * Respuesta paginada de la lista de pisos con metadatos del filtro aplicado.
     */
    @Data
    @Builder
    public static class PisoListResponse {
        private int total;
        private String filtroAplicado;   // descripción legible del filtro activo
        private List<PisoResponse> pisos;
    }
}
