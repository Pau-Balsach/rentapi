package com.rentapi.rentapi.scraper;

import com.rentapi.rentapi.model.Barrio;
import com.rentapi.rentapi.model.Ciudad;
import com.rentapi.rentapi.model.StatsBarrioMensual;
import com.rentapi.rentapi.model.StatsCiudadMensual;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.PisoRepository;
import com.rentapi.rentapi.repository.StatsBarrioMensualRepository;
import com.rentapi.rentapi.repository.StatsCiudadMensualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Recalcula y persiste las estadísticas agregadas en stats_ciudad_mensual
 * y stats_barrio_mensual para un mes dado.
 *
 * Flujo:
 *   1. ScraperScheduler llama a calcularMesActual() después del scraping.
 *   2. Se itera sobre todas las ciudades activas y todas las tipologías.
 *   3. Para cada combinación ciudad × tipología, se calculan las stats
 *      a partir de los precios reales de la tabla pisos.
 *   4. Se hace UPSERT: si ya existe el registro del mes se actualiza,
 *      si no existe se crea.
 *   5. Lo mismo se repite para cada barrio con datos suficientes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCalculator {

    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;
    private final PisoRepository pisoRepo;
    private final StatsCiudadMensualRepository statsCiudadRepo;
    private final StatsBarrioMensualRepository statsBarrioRepo;

    /** Tipologías que calculamos: null = todas, 0-4 = nº habitaciones */
    private static final Short[] TIPOLOGIAS = {null, 0, 1, 2, 3, 4};

    /** Muestras mínimas para considerar un cálculo estadísticamente válido */
    private static final int MIN_MUESTRAS = 5;

    // ─── Punto de entrada ────────────────────────────────────────────────────

    /**
     * Calcula las stats del mes actual para todas las ciudades y barrios.
     * Llamado por ScraperScheduler tras cada ejecución del scraper.
     */
    public void calcularMesActual() {
        LocalDate mes = LocalDate.now().withDayOfMonth(1);
        log.info("Iniciando cálculo de stats para el mes: {}", mes);
        calcularMes(mes);
        log.info("Cálculo de stats completado para el mes: {}", mes);
    }

    /**
     * Calcula las stats para un mes concreto (útil para backfill manual).
     *
     * @param mes primer día del mes a calcular (ej: 2026-05-01)
     */
    public void calcularMes(LocalDate mes) {
        // Rango temporal: todo el mes
        LocalDateTime desde = mes.atStartOfDay();
        LocalDateTime hasta = mes.plusMonths(1).atStartOfDay();

        List<Ciudad> ciudades = ciudadRepo.findAll();
        log.info("Ciudades a procesar: {}", ciudades.size());

        for (Ciudad ciudad : ciudades) {
            calcularStatsCiudad(ciudad, mes, desde, hasta);

            List<Barrio> barrios = barrioRepo.findByCiudad_Id(ciudad.getId());
            for (Barrio barrio : barrios) {
                calcularStatsBarrio(barrio, mes, desde, hasta);
            }
        }
    }

    // ─── Stats por ciudad ────────────────────────────────────────────────────

    private void calcularStatsCiudad(Ciudad ciudad, LocalDate mes,
                                     LocalDateTime desde, LocalDateTime hasta) {
        for (Short tipologia : TIPOLOGIAS) {
            try {
                List<BigDecimal> precios = pisoRepo.findPreciosMesByCiudadOrdered(
                        ciudad.getId(), tipologia, desde, hasta);

                if (precios.size() < MIN_MUESTRAS) continue;

                BigDecimal precioMedioM2 = pisoRepo.avgPrecioM2ByCiudad(
                        ciudad.getId(), tipologia, desde, hasta);

                StatsCiudadMensual stats = buildStatsCiudad(ciudad, mes, tipologia, precios, precioMedioM2);
                upsertStatsCiudad(stats);

                log.debug("Ciudad {} | tipología {} | {} muestras | media {}€",
                        ciudad.getSlug(), tipologia, precios.size(), stats.getPrecioMedio());

            } catch (Exception e) {
                log.error("Error calculando stats ciudad={} tipología={}: {}",
                        ciudad.getSlug(), tipologia, e.getMessage());
            }
        }
    }

    private StatsCiudadMensual buildStatsCiudad(Ciudad ciudad, LocalDate mes,
                                                Short habitaciones,
                                                List<BigDecimal> preciosOrdenados,
                                                BigDecimal precioMedioM2) {
        return StatsCiudadMensual.builder()
                .ciudad(ciudad)
                .mes(mes)
                .habitaciones(habitaciones)
                .totalMuestras(preciosOrdenados.size())
                .precioMedio(calcularMedia(preciosOrdenados))
                .precioMediana(calcularPercentil(preciosOrdenados, 50))
                .precioMin(preciosOrdenados.get(0))
                .precioMax(preciosOrdenados.get(preciosOrdenados.size() - 1))
                .percentil25(calcularPercentil(preciosOrdenados, 25))
                .percentil75(calcularPercentil(preciosOrdenados, 75))
                .precioMedioM2(precioMedioM2)
                .build();
    }

    private void upsertStatsCiudad(StatsCiudadMensual nuevo) {
        Optional<StatsCiudadMensual> existente = statsCiudadRepo
                .findByCiudad_IdAndMesAndHabitaciones(
                        nuevo.getCiudad().getId(), nuevo.getMes(), nuevo.getHabitaciones());

        if (existente.isPresent()) {
            StatsCiudadMensual s = existente.get();
            s.setTotalMuestras(nuevo.getTotalMuestras());
            s.setPrecioMedio(nuevo.getPrecioMedio());
            s.setPrecioMediana(nuevo.getPrecioMediana());
            s.setPrecioMin(nuevo.getPrecioMin());
            s.setPrecioMax(nuevo.getPrecioMax());
            s.setPercentil25(nuevo.getPercentil25());
            s.setPercentil75(nuevo.getPercentil75());
            s.setPrecioMedioM2(nuevo.getPrecioMedioM2());
            statsCiudadRepo.save(s);
        } else {
            statsCiudadRepo.save(nuevo);
        }
    }

    // ─── Stats por barrio ────────────────────────────────────────────────────

    private void calcularStatsBarrio(Barrio barrio, LocalDate mes,
                                     LocalDateTime desde, LocalDateTime hasta) {
        for (Short tipologia : TIPOLOGIAS) {
            try {
                List<BigDecimal> precios = pisoRepo.findPreciosMesByBarrioOrdered(
                        barrio.getId(), tipologia, desde, hasta);

                if (precios.size() < MIN_MUESTRAS) continue;

                // precio/m2 para barrio: reutilizamos lógica del repo de pisos
                BigDecimal precioMedioM2 = calcularPrecioM2Barrio(barrio, tipologia, desde, hasta);

                StatsBarrioMensual stats = buildStatsBarrio(barrio, mes, tipologia, precios, precioMedioM2);
                upsertStatsBarrio(stats);

                log.debug("Barrio {} | tipología {} | {} muestras | media {}€",
                        barrio.getSlug(), tipologia, precios.size(), stats.getPrecioMedio());

            } catch (Exception e) {
                log.error("Error calculando stats barrio={} tipología={}: {}",
                        barrio.getSlug(), tipologia, e.getMessage());
            }
        }
    }

    private StatsBarrioMensual buildStatsBarrio(Barrio barrio, LocalDate mes,
                                                Short habitaciones,
                                                List<BigDecimal> preciosOrdenados,
                                                BigDecimal precioMedioM2) {
        return StatsBarrioMensual.builder()
                .barrio(barrio)
                .mes(mes)
                .habitaciones(habitaciones)
                .totalMuestras(preciosOrdenados.size())
                .precioMedio(calcularMedia(preciosOrdenados))
                .precioMediana(calcularPercentil(preciosOrdenados, 50))
                .precioMin(preciosOrdenados.get(0))
                .precioMax(preciosOrdenados.get(preciosOrdenados.size() - 1))
                .percentil25(calcularPercentil(preciosOrdenados, 25))
                .percentil75(calcularPercentil(preciosOrdenados, 75))
                .precioMedioM2(precioMedioM2)
                .build();
    }

    private void upsertStatsBarrio(StatsBarrioMensual nuevo) {
        Optional<StatsBarrioMensual> existente = statsBarrioRepo
                .findByBarrio_IdAndMesAndHabitaciones(
                        nuevo.getBarrio().getId(), nuevo.getMes(), nuevo.getHabitaciones());

        if (existente.isPresent()) {
            StatsBarrioMensual s = existente.get();
            s.setTotalMuestras(nuevo.getTotalMuestras());
            s.setPrecioMedio(nuevo.getPrecioMedio());
            s.setPrecioMediana(nuevo.getPrecioMediana());
            s.setPrecioMin(nuevo.getPrecioMin());
            s.setPrecioMax(nuevo.getPrecioMax());
            s.setPercentil25(nuevo.getPercentil25());
            s.setPercentil75(nuevo.getPercentil75());
            s.setPrecioMedioM2(nuevo.getPrecioMedioM2());
            statsBarrioRepo.save(s);
        } else {
            statsBarrioRepo.save(nuevo);
        }
    }

    // ─── Helpers estadísticos ─────────────────────────────────────────────────

    /**
     * Media aritmética de una lista de precios.
     */
    private BigDecimal calcularMedia(List<BigDecimal> precios) {
        BigDecimal suma = precios.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(precios.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Percentil por interpolación lineal sobre lista ya ordenada.
     * percentil=50 → mediana, percentil=25 → Q1, percentil=75 → Q3.
     */
    private BigDecimal calcularPercentil(List<BigDecimal> preciosOrdenados, int percentil) {
        int n = preciosOrdenados.size();
        if (n == 1) return preciosOrdenados.get(0);

        double rank = (percentil / 100.0) * (n - 1);
        int lower  = (int) Math.floor(rank);
        int upper  = (int) Math.ceil(rank);
        double fraccion = rank - lower;

        if (lower == upper) return preciosOrdenados.get(lower).setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorLower = preciosOrdenados.get(lower);
        BigDecimal valorUpper = preciosOrdenados.get(upper);
        BigDecimal diferencia = valorUpper.subtract(valorLower);
        BigDecimal interpolado = valorLower.add(
                diferencia.multiply(BigDecimal.valueOf(fraccion)));

        return interpolado.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Precio medio por m² para un barrio.
     * Delega al PisoRepository filtrando por barrio_id y excluyendo metros nulos.
     */
    private BigDecimal calcularPrecioM2Barrio(Barrio barrio, Short tipologia,
                                              LocalDateTime desde, LocalDateTime hasta) {
        // Reutilizamos la query nativa del repo pero a nivel barrio
        // Si no tienes avgPrecioM2ByBarrio en el repo, añádela (ver nota abajo)
        return pisoRepo.avgPrecioM2ByBarrio(barrio.getId(), tipologia, desde, hasta);
    }
}