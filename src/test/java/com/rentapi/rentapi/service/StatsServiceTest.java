package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.StatsDTO;
import com.rentapi.rentapi.exception.ResourceNotFoundException;
import com.rentapi.rentapi.model.*;
import com.rentapi.rentapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private StatsCiudadMensualRepository ciudadStatsRepo;
    @Mock private StatsBarrioMensualRepository barrioStatsRepo;
    @Mock private CiudadRepository ciudadRepo;
    @Mock private BarrioRepository barrioRepo;

    @InjectMocks
    private StatsService statsService;

    private Ciudad barcelona;
    private Barrio gracia;
    private StatsCiudadMensual statsBcnMayo;
    private StatsCiudadMensual statsBcnAbril;
    private StatsBarrioMensual statsGraciaMayo;

    @BeforeEach
    void setUp() {
        barcelona = Ciudad.builder()
                .id(1L).nombre("Barcelona").slug("barcelona").build();

        gracia = Barrio.builder()
                .id(10L).nombre("Gràcia").slug("gracia").ciudad(barcelona).build();

        statsBcnMayo = StatsCiudadMensual.builder()
                .id(1L)
                .ciudad(barcelona)
                .mes(LocalDate.of(2026, 5, 1))
                .habitaciones(null)
                .totalMuestras(500)
                .precioMedio(new BigDecimal("1500.00"))
                .precioMediana(new BigDecimal("1400.00"))
                .precioMin(new BigDecimal("600.00"))
                .precioMax(new BigDecimal("5000.00"))
                .percentil25(new BigDecimal("1100.00"))
                .percentil75(new BigDecimal("1900.00"))
                .precioMedioM2(new BigDecimal("17.50"))
                .build();

        statsBcnAbril = StatsCiudadMensual.builder()
                .id(2L)
                .ciudad(barcelona)
                .mes(LocalDate.of(2026, 4, 1))
                .habitaciones(null)
                .totalMuestras(480)
                .precioMedio(new BigDecimal("1450.00"))
                .precioMediana(new BigDecimal("1350.00"))
                .precioMin(new BigDecimal("580.00"))
                .precioMax(new BigDecimal("4800.00"))
                .percentil25(new BigDecimal("1050.00"))
                .percentil75(new BigDecimal("1850.00"))
                .precioMedioM2(new BigDecimal("16.80"))
                .build();

        statsGraciaMayo = StatsBarrioMensual.builder()
                .id(1L)
                .barrio(gracia)
                .mes(LocalDate.of(2026, 5, 1))
                .habitaciones(null)
                .totalMuestras(80)
                .precioMedio(new BigDecimal("1700.00"))
                .precioMediana(new BigDecimal("1650.00"))
                .precioMin(new BigDecimal("900.00"))
                .precioMax(new BigDecimal("3500.00"))
                .percentil25(new BigDecimal("1300.00"))
                .percentil75(new BigDecimal("2100.00"))
                .precioMedioM2(new BigDecimal("19.20"))
                .build();
    }

    // ─── getStatsCiudad ──────────────────────────────────────────

    @Test
    void getStatsCiudad_slugValido_devuelveStats() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of(statsBcnAbril, statsBcnMayo));
        when(ciudadStatsRepo.findByCiudad_SlugAndMesAndHabitaciones(any(), any(), any()))
                .thenReturn(List.of());

        StatsDTO.CiudadStatsResponse result =
                statsService.getStatsCiudad("barcelona", null, null, null);

        assertThat(result.getCiudad()).isEqualTo("Barcelona");
        assertThat(result.getTotalPisosAnalizados()).isEqualTo(980); // 500 + 480
        assertThat(result.getPrecioMes().getMedia()).isNotNull();
        assertThat(result.getTendenciaMensual()).hasSize(2);
    }

    @Test
    void getStatsCiudad_ciudadNoExiste_lanzaNotFoundException() {
        when(ciudadRepo.findBySlug("noexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getStatsCiudad("noexiste", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("noexiste");
    }

    @Test
    void getStatsCiudad_sinDatos_lanzaNotFoundException() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> statsService.getStatsCiudad("barcelona", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStatsCiudad_tendenciaMensualOrdenada() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of(statsBcnAbril, statsBcnMayo));
        when(ciudadStatsRepo.findByCiudad_SlugAndMesAndHabitaciones(any(), any(), any()))
                .thenReturn(List.of());

        StatsDTO.CiudadStatsResponse result =
                statsService.getStatsCiudad("barcelona", null, null, null);

        List<StatsDTO.PuntoTendencia> tendencia = result.getTendenciaMensual();
        assertThat(tendencia.get(0).getMes()).isEqualTo("2026-04");
        assertThat(tendencia.get(1).getMes()).isEqualTo("2026-05");
    }

    // ─── getStatsBarrio ──────────────────────────────────────────

    @Test
    void getStatsBarrio_slugValido_devuelveStatsConComparativa() {
        when(barrioRepo.findBySlugAndCiudad_Slug("gracia", "barcelona"))
                .thenReturn(Optional.of(gracia));
        when(barrioStatsRepo.findByBarrio_SlugAndHabitacionesOrderByMesAsc("gracia", null))
                .thenReturn(List.of(statsGraciaMayo));
        when(barrioStatsRepo.findByBarrio_Ciudad_SlugAndMesAndHabitaciones(any(), any(), any()))
                .thenReturn(List.of());
        when(ciudadStatsRepo.findByCiudad_SlugAndMesAndHabitaciones(any(), any(), any()))
                .thenReturn(List.of(statsBcnMayo));

        StatsDTO.BarrioStatsResponse result =
                statsService.getStatsBarrio("barcelona", "gracia", null, null, null);

        assertThat(result.getBarrio()).isEqualTo("Gràcia");
        assertThat(result.getCiudad()).isEqualTo("Barcelona");
        assertThat(result.getComparativaCiudad()).isNotNull();
        assertThat(result.getComparativaCiudad().getPrecioMedioCiudad())
                .isEqualByComparingTo("1500.00");
        // Gràcia (1700) está por encima de BCN (1500) → diferencia positiva
        assertThat(result.getComparativaCiudad().getDiferenciaPocentaje()).startsWith("+");
    }

    @Test
    void getStatsBarrio_barrioNoExiste_lanzaNotFoundException() {
        when(barrioRepo.findBySlugAndCiudad_Slug("noexiste", "barcelona"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                statsService.getStatsBarrio("barcelona", "noexiste", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getTendencia ────────────────────────────────────────────

    @Test
    void getTendencia_ciudad_devuelveSerieTemporal() {
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of(statsBcnAbril, statsBcnMayo));

        StatsDTO.TendenciaResponse result =
                statsService.getTendencia("barcelona", "ciudad", 6, null);

        assertThat(result.getZona()).isEqualTo("barcelona");
        assertThat(result.getTipo()).isEqualTo("ciudad");
        assertThat(result.getSerieTemporal()).hasSize(2);
        assertThat(result.getVariacionTotalPeriodo()).isNotBlank();
        assertThat(result.getVariacionUltimoMes()).isNotBlank();
    }

    @Test
    void getTendencia_variacionPositiva_incluyeSignoMas() {
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of(statsBcnAbril, statsBcnMayo)); // 1450 → 1500, subida

        StatsDTO.TendenciaResponse result =
                statsService.getTendencia("barcelona", "ciudad", 6, null);

        assertThat(result.getVariacionTotalPeriodo()).startsWith("+");
        assertThat(result.getVariacionUltimoMes()).startsWith("+");
    }

    @Test
    void getTendencia_sinDatos_lanzaNotFoundException() {
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("vacio", null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> statsService.getTendencia("vacio", "ciudad", 6, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── comparar ────────────────────────────────────────────────

    @Test
    void comparar_dosCiudades_devuelveComparativa() {
        StatsCiudadMensual statsMadrid = StatsCiudadMensual.builder()
                .id(3L)
                .ciudad(Ciudad.builder().id(2L).nombre("Madrid").slug("madrid").build())
                .mes(LocalDate.of(2026, 5, 1))
                .totalMuestras(600)
                .precioMedio(new BigDecimal("1350.00"))
                .precioMedioM2(new BigDecimal("15.80"))
                .percentil25(new BigDecimal("1000.00"))
                .percentil75(new BigDecimal("1700.00"))
                .precioMin(new BigDecimal("500.00"))
                .precioMax(new BigDecimal("4500.00"))
                .build();

        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadRepo.findBySlug("madrid"))
                .thenReturn(Optional.of(statsMadrid.getCiudad()));
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("barcelona", null))
                .thenReturn(List.of(statsBcnMayo));
        when(ciudadStatsRepo.findByCiudad_SlugAndHabitacionesOrderByMesAsc("madrid", null))
                .thenReturn(List.of(statsMadrid));

        StatsDTO.ComparativaResponse result =
                statsService.comparar(List.of("barcelona", "madrid"), "ciudad", null, null, null);

        assertThat(result.getComparativa()).hasSize(2);
        assertThat(result.getZonaMasCara()).isEqualTo("Barcelona");
        assertThat(result.getZonaMasBarata()).isEqualTo("Madrid");
    }

    // ─── evaluar ─────────────────────────────────────────────────

    @Test
    void evaluar_precioPorEncima_devuelveVaoracionCorrecta() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_IdAndMesAndHabitaciones(eq(1L), any(), isNull()))
                .thenReturn(Optional.of(statsBcnMayo));

        // 2500€ está por encima del percentil 75 (1900€)
        StatsDTO.EvaluacionResponse result =
                statsService.evaluar("barcelona", null, new BigDecimal("2500"), null, null);

        assertThat(result.getValoracion()).isEqualTo("por_encima");
        assertThat(result.getDiferenciaPorcentaje()).startsWith("+");
        assertThat(result.getPercentilZona()).isGreaterThanOrEqualTo(75);
    }

    @Test
    void evaluar_precioEnRango_devuelveEnRango() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_IdAndMesAndHabitaciones(eq(1L), any(), isNull()))
                .thenReturn(Optional.of(statsBcnMayo));

        // 1500€ está entre p25 (1100) y p75 (1900)
        StatsDTO.EvaluacionResponse result =
                statsService.evaluar("barcelona", null, new BigDecimal("1500"), null, null);

        assertThat(result.getValoracion()).isEqualTo("en_rango");
    }

    @Test
    void evaluar_precioPorDebajo_devuelvePorDebajo() {
        when(ciudadRepo.findBySlug("barcelona")).thenReturn(Optional.of(barcelona));
        when(ciudadStatsRepo.findByCiudad_IdAndMesAndHabitaciones(eq(1L), any(), isNull()))
                .thenReturn(Optional.of(statsBcnMayo));

        // 800€ está por debajo del percentil 25 (1100€)
        StatsDTO.EvaluacionResponse result =
                statsService.evaluar("barcelona", null, new BigDecimal("800"), null, null);

        assertThat(result.getValoracion()).isEqualTo("por_debajo");
        assertThat(result.getDiferenciaPorcentaje()).startsWith("-");
    }

    @Test
    void evaluar_ciudadNoExiste_lanzaNotFoundException() {
        when(ciudadRepo.findBySlug("noexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                statsService.evaluar("noexiste", null, new BigDecimal("1500"), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}