package com.rentapi.rentapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs de respuesta para todos los endpoints /stats.
 * Clases estáticas agrupadas en este archivo para mantener el paquete limpio.
 */
public class StatsDTO {

    // ──────────────────────────────────────────────────────────────
    // Bloque de precios reutilizable (usado en ciudad y barrio)
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class PrecioMes {
        private BigDecimal media;
        private BigDecimal mediana;
        private BigDecimal min;
        private BigDecimal max;
        private BigDecimal percentil25;
        private BigDecimal percentil75;
    }

    @Data
    @Builder
    public static class PrecioM2 {
        private BigDecimal media;
        private BigDecimal mediana;
    }

    @Data
    @Builder
    public static class TipologiaItem {
        private int cantidad;
        private BigDecimal precioMedio;
    }

    @Data
    @Builder
    public static class DistribucionTipologia {
        private TipologiaItem estudios;
        private TipologiaItem unaHabitacion;
        private TipologiaItem dosHabitaciones;
        private TipologiaItem tresHabitaciones;
        private TipologiaItem cuatroOMas;
    }

    @Data
    @Builder
    public static class PuntoTendencia {
        private String mes;          // "2025-11"
        private BigDecimal precioMedio;
        private int totalMuestras;
    }

    @Data
    @Builder
    public static class Periodo {
        private String desde;
        private String hasta;
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/ciudad/{slug}
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class CiudadStatsResponse {
        private String ciudad;
        private Periodo periodo;
        private int totalPisosAnalizados;
        private PrecioMes precioMes;
        private PrecioM2 precioM2;
        private DistribucionTipologia distribucionTipologia;
        private List<PuntoTendencia> tendenciaMensual;
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/barrio/{ciudad_slug}/{barrio_slug}
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BarrioStatsResponse {
        private String barrio;
        private String ciudad;
        private Periodo periodo;
        private int totalPisosAnalizados;
        private PrecioMes precioMes;
        private PrecioM2 precioM2;
        private DistribucionTipologia distribucionTipologia;
        private List<PuntoTendencia> tendenciaMensual;
        private ComparativaCiudad comparativaCiudad;
    }

    @Data
    @Builder
    public static class ComparativaCiudad {
        private BigDecimal precioMedioCiudad;
        private String diferenciaPocentaje;   // ej: "+12.3%" o "-5.1%"
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/tendencia/{slug}
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class TendenciaResponse {
        private String zona;
        private String tipo;                      // "ciudad" o "barrio"
        private List<PuntoTendencia> serieTemporal;
        private String variacionTotalPeriodo;     // "+8.3%"
        private String variacionUltimoMes;        // "+0.9%"
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/comparar
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ComparativaZona {
        private String zona;
        private BigDecimal precioMedioM2;
        private BigDecimal precioMedioMes;
        private int totalMuestras;
    }

    @Data
    @Builder
    public static class ComparativaResponse {
        private List<ComparativaZona> comparativa;
        private String zonaMasCara;
        private String zonaMasBarata;
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/ranking
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class RankingItem {
        private int posicion;
        private String zona;
        private BigDecimal precioMedioM2;
        private BigDecimal precioMedioMes;
    }

    @Data
    @Builder
    public static class RankingResponse {
        private String tipo;     // "ciudad" o "barrio"
        private String orden;    // "asc" o "desc"
        private List<RankingItem> ranking;
    }

    // ──────────────────────────────────────────────────────────────
    // GET /stats/evaluar
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class EvaluacionResponse {
        private BigDecimal precioConsultado;
        private BigDecimal precioMedioZona;
        private BigDecimal precioMedioM2Zona;
        private String valoracion;            // "por_encima", "en_rango", "por_debajo"
        private String diferenciaPorcentaje;  // "+17.2%"
        private int percentilZona;            // 0-100
        private String recomendacion;
    }
}
