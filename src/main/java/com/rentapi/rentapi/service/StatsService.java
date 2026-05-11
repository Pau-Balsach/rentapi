package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.StatsDTO;
import com.rentapi.rentapi.dto.StatsDTO.*;
import com.rentapi.rentapi.exception.ResourceNotFoundException;
import com.rentapi.rentapi.model.Barrio;
import com.rentapi.rentapi.model.Ciudad;
import com.rentapi.rentapi.model.StatsBarrioMensual;
import com.rentapi.rentapi.model.StatsCiudadMensual;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.StatsBarrioMensualRepository;
import com.rentapi.rentapi.repository.StatsCiudadMensualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter MES_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatsCiudadMensualRepository ciudadStatsRepo;
    private final StatsBarrioMensualRepository barrioStatsRepo;
    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;

    // ─────────────────────────────────────────────────────────────
    // GET /stats/ciudad/{slug}
    // ─────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────
    // GET /stats/ciudad/{slug}
    // ─────────────────────────────────────────────────────────────

    public CiudadStatsResponse getStatsCiudad(String slug,
                                              Short habitaciones,
                                              LocalDate fechaInicio,
                                              LocalDate fechaFin) {

        Ciudad ciudad = ciudadRepo.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad no encontrada: " + slug));

        List<StatsCiudadMensual> filas = ciudadStatsRepo
                .findByCiudad_SlugAndHabitacionesOrderByMesAsc(slug, habitaciones);

        filas = filtrarPorFechas(filas, fechaInicio, fechaFin,
                stat -> stat.getMes(), stat -> stat.getMes());

        // FIX: si el job insertó varias filas para el mismo mes, nos quedamos
        // con la más reciente (mayor created_at) para cada mes.
        filas = deduplicarPorMesCiudad(filas);

        if (filas.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No hay estadísticas disponibles para: " + ciudad.getNombre());
        }

        StatsCiudadMensual agregado = agregarCiudad(filas);
        DistribucionTipologia distribucion = calcularDistribucionCiudad(slug, ultimoMes(filas));
        List<PuntoTendencia> tendencia = filas.stream()
                .map(f -> PuntoTendencia.builder()
                        .mes(f.getMes().format(MES_FMT))
                        .precioMedio(f.getPrecioMedio())
                        .totalMuestras(f.getTotalMuestras())
                        .build())
                .collect(Collectors.toList());

        return CiudadStatsResponse.builder()
                .ciudad(ciudad.getNombre())
                .periodo(buildPeriodo(filas.get(0).getMes(), ultimoMes(filas)))
                .totalPisosAnalizados(filas.stream().mapToInt(StatsCiudadMensual::getTotalMuestras).sum())
                .precioMes(buildPrecioMes(agregado))
                .precioM2(PrecioM2.builder()
                        .media(agregado.getPrecioMedioM2())
                        .mediana(null)
                        .build())
                .distribucionTipologia(distribucion)
                .tendenciaMensual(tendencia)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/barrio/{ciudad_slug}/{barrio_slug}
    // ─────────────────────────────────────────────────────────────

    public BarrioStatsResponse getStatsBarrio(String ciudadSlug,
                                              String barrioSlug,
                                              Short habitaciones,
                                              LocalDate fechaInicio,
                                              LocalDate fechaFin) {

        Barrio barrio = barrioRepo.findBySlugAndCiudad_Slug(barrioSlug, ciudadSlug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Barrio no encontrado: " + barrioSlug + " en " + ciudadSlug));

        List<StatsBarrioMensual> filas = barrioStatsRepo
                .findByBarrio_SlugAndHabitacionesOrderByMesAsc(barrioSlug, habitaciones);

        filas = filtrarPorFechasBarrio(filas, fechaInicio, fechaFin);

        // FIX: deduplicar por mes por si el job insertó varias filas para el mismo mes.
        filas = deduplicarPorMesBarrio(filas);

        if (filas.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No hay estadísticas disponibles para: " + barrio.getNombre());
        }

        StatsBarrioMensual agregado = agregarBarrio(filas);
        DistribucionTipologia distribucion = calcularDistribucionBarrio(barrioSlug, ciudadSlug, ultimoMesBarrio(filas));
        List<PuntoTendencia> tendencia = filas.stream()
                .map(f -> PuntoTendencia.builder()
                        .mes(f.getMes().format(MES_FMT))
                        .precioMedio(f.getPrecioMedio())
                        .totalMuestras(f.getTotalMuestras())
                        .build())
                .collect(Collectors.toList());

        ComparativaCiudad comparativa = calcularComparativaCiudad(
                ciudadSlug, habitaciones, ultimoMesBarrio(filas), agregado.getPrecioMedio());

        return BarrioStatsResponse.builder()
                .barrio(barrio.getNombre())
                .ciudad(barrio.getCiudad().getNombre())
                .periodo(buildPeriodo(filas.get(0).getMes(), ultimoMesBarrio(filas)))
                .totalPisosAnalizados(filas.stream().mapToInt(StatsBarrioMensual::getTotalMuestras).sum())
                .precioMes(buildPrecioMesBarrio(agregado))
                .precioM2(PrecioM2.builder()
                        .media(agregado.getPrecioMedioM2())
                        .mediana(null)
                        .build())
                .distribucionTipologia(distribucion)
                .tendenciaMensual(tendencia)
                .comparativaCiudad(comparativa)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/tendencia/{slug}
    // ─────────────────────────────────────────────────────────────

    public TendenciaResponse getTendencia(String slug,
                                          String tipo,
                                          int meses,
                                          Short habitaciones) {

        int maxMeses = Math.min(meses, 24);
        LocalDate desde = LocalDate.now().withDayOfMonth(1).minusMonths(maxMeses - 1L);

        List<PuntoTendencia> serie;

        if ("barrio".equalsIgnoreCase(tipo)) {
            List<StatsBarrioMensual> filas = barrioStatsRepo
                    .findByBarrio_SlugAndHabitacionesOrderByMesAsc(slug, habitaciones)
                    .stream()
                    .filter(f -> !f.getMes().isBefore(desde))
                    .collect(Collectors.toList());

            // FIX: agrupar por mes y quedarse con la fila más reciente de cada mes.
            // Esto hace que la serie sea correcta aunque el job haya insertado
            // múltiples filas para el mismo mes (en lugar de hacer UPSERT).
            filas = deduplicarPorMesBarrio(filas);

            serie = filas.stream()
                    .map(f -> PuntoTendencia.builder()
                            .mes(f.getMes().format(MES_FMT))
                            .precioMedio(f.getPrecioMedio())
                            .totalMuestras(f.getTotalMuestras())
                            .build())
                    .collect(Collectors.toList());

        } else {
            List<StatsCiudadMensual> filas = ciudadStatsRepo
                    .findByCiudad_SlugAndHabitacionesOrderByMesAsc(slug, habitaciones)
                    .stream()
                    .filter(f -> !f.getMes().isBefore(desde))
                    .collect(Collectors.toList());

            // FIX: mismo problema — deduplicar por mes antes de construir la serie.
            filas = deduplicarPorMesCiudad(filas);

            serie = filas.stream()
                    .map(f -> PuntoTendencia.builder()
                            .mes(f.getMes().format(MES_FMT))
                            .precioMedio(f.getPrecioMedio())
                            .totalMuestras(f.getTotalMuestras())
                            .build())
                    .collect(Collectors.toList());
        }

        if (serie.isEmpty()) {
            throw new ResourceNotFoundException("No hay datos de tendencia para: " + slug);
        }

        String variacionTotal = calcularVariacion(serie.get(0).getPrecioMedio(),
                serie.get(serie.size() - 1).getPrecioMedio());
        String variacionUltimo = serie.size() >= 2
                ? calcularVariacion(serie.get(serie.size() - 2).getPrecioMedio(),
                serie.get(serie.size() - 1).getPrecioMedio())
                : "N/A";

        return TendenciaResponse.builder()
                .zona(slug)
                .tipo(tipo)
                .serieTemporal(serie)
                .variacionTotalPeriodo(variacionTotal)
                .variacionUltimoMes(variacionUltimo)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/comparar
    // ─────────────────────────────────────────────────────────────

    public ComparativaResponse comparar(List<String> slugs,
                                        String tipo,
                                        Short habitaciones,
                                        LocalDate fechaInicio,
                                        LocalDate fechaFin) {

        List<ComparativaZona> zonas = new ArrayList<>();

        for (String slug : slugs) {
            try {
                if ("barrio".equalsIgnoreCase(tipo)) {
                    // slug esperado: "ciudad-slug/barrio-slug"
                    String[] partes = slug.split("/");
                    if (partes.length != 2) continue;
                    List<StatsBarrioMensual> filas = barrioStatsRepo
                            .findByBarrio_SlugAndHabitacionesOrderByMesAsc(partes[1], habitaciones);
                    filas = filtrarPorFechasBarrio(filas, fechaInicio, fechaFin);
                    if (filas.isEmpty()) continue;
                    StatsBarrioMensual agr = agregarBarrio(filas);
                    zonas.add(ComparativaZona.builder()
                            .zona(partes[1])
                            .precioMedioM2(agr.getPrecioMedioM2())
                            .precioMedioMes(agr.getPrecioMedio())
                            .totalMuestras(filas.stream().mapToInt(StatsBarrioMensual::getTotalMuestras).sum())
                            .build());
                } else {
                    List<StatsCiudadMensual> filas = ciudadStatsRepo
                            .findByCiudad_SlugAndHabitacionesOrderByMesAsc(slug, habitaciones);
                    filas = filtrarPorFechas(filas, fechaInicio, fechaFin,
                            f -> f.getMes(), f -> f.getMes());
                    if (filas.isEmpty()) continue;
                    StatsCiudadMensual agr = agregarCiudad(filas);
                    // Buscar nombre real
                    String nombre = ciudadRepo.findBySlug(slug)
                            .map(Ciudad::getNombre).orElse(slug);
                    zonas.add(ComparativaZona.builder()
                            .zona(nombre)
                            .precioMedioM2(agr.getPrecioMedioM2())
                            .precioMedioMes(agr.getPrecioMedio())
                            .totalMuestras(filas.stream().mapToInt(StatsCiudadMensual::getTotalMuestras).sum())
                            .build());
                }
            } catch (Exception ignored) {
                // Si una zona falla, continuamos con las demás
            }
        }

        if (zonas.isEmpty()) {
            throw new ResourceNotFoundException("No se encontraron datos para las zonas indicadas.");
        }

        String masCara   = zonas.stream().max(Comparator.comparing(z -> z.getPrecioMedioM2() != null
                ? z.getPrecioMedioM2() : BigDecimal.ZERO)).map(ComparativaZona::getZona).orElse(null);
        String masBarata = zonas.stream().min(Comparator.comparing(z -> z.getPrecioMedioM2() != null
                ? z.getPrecioMedioM2() : BigDecimal.ZERO)).map(ComparativaZona::getZona).orElse(null);

        return ComparativaResponse.builder()
                .comparativa(zonas)
                .zonaMasCara(masCara)
                .zonaMasBarata(masBarata)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/ranking
    // ─────────────────────────────────────────────────────────────
    public RankingResponse getRanking(String tipo,
                                      String orden,
                                      String provinciaSlug,
                                      int limite) {

        int limiteReal = Math.min(limite, 200);

        // Buscar el mes más reciente con datos disponibles
        LocalDate mesActual = LocalDate.now().withDayOfMonth(1);
        LocalDate mesEfectivo = ciudadStatsRepo.findAll().stream()
                .map(StatsCiudadMensual::getMes)
                .filter(m -> !m.isAfter(mesActual))
                .max(Comparator.naturalOrder())
                .orElse(mesActual);

        List<RankingItem> items = new ArrayList<>();

        if ("barrio".equalsIgnoreCase(tipo)) {
            List<StatsBarrioMensual> filas = barrioStatsRepo
                    .findByBarrio_Ciudad_SlugAndMesAndHabitaciones(
                            provinciaSlug != null ? provinciaSlug : "", mesEfectivo, null);

            if (provinciaSlug == null || provinciaSlug.isBlank()) {
                filas = barrioStatsRepo.findAll().stream()
                        .filter(f -> f.getMes().equals(mesEfectivo) && f.getHabitaciones() == null)
                        .collect(Collectors.toList());
            }

            filas.sort(Comparator.comparing(f -> f.getPrecioMedioM2() != null
                    ? f.getPrecioMedioM2() : BigDecimal.ZERO));

            if ("desc".equalsIgnoreCase(orden)) Collections.reverse(filas);

            for (int i = 0; i < Math.min(limiteReal, filas.size()); i++) {
                StatsBarrioMensual f = filas.get(i);
                items.add(RankingItem.builder()
                        .posicion(i + 1)
                        .zona(f.getBarrio().getNombre())
                        .precioMedioM2(f.getPrecioMedioM2())
                        .precioMedioMes(f.getPrecioMedio())
                        .build());
            }
        } else {
            List<StatsCiudadMensual> filas = ciudadStatsRepo.findAll().stream()
                    .filter(f -> f.getMes().equals(mesEfectivo) && f.getHabitaciones() == null)
                    .collect(Collectors.toList());

            if (provinciaSlug != null && !provinciaSlug.isBlank()) {
                filas = filas.stream()
                        .filter(f -> f.getCiudad().getProvincia() != null &&
                                provinciaSlug.equals(f.getCiudad().getProvincia().getSlug()))
                        .collect(Collectors.toList());
            }

            filas.sort(Comparator.comparing(f -> f.getPrecioMedioM2() != null
                    ? f.getPrecioMedioM2() : BigDecimal.ZERO));

            if ("desc".equalsIgnoreCase(orden)) Collections.reverse(filas);

            for (int i = 0; i < Math.min(limiteReal, filas.size()); i++) {
                StatsCiudadMensual f = filas.get(i);
                items.add(RankingItem.builder()
                        .posicion(i + 1)
                        .zona(f.getCiudad().getNombre())
                        .precioMedioM2(f.getPrecioMedioM2())
                        .precioMedioMes(f.getPrecioMedio())
                        .build());
            }
        }

        return RankingResponse.builder()
                .tipo(tipo)
                .orden(orden != null ? orden : "asc")
                .ranking(items)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /stats/evaluar
    // ─────────────────────────────────────────────────────────────

    public StatsDTO.EvaluacionResponse evaluar(String ciudadSlug,
                                               String barrioSlug,
                                               BigDecimal precio,
                                               Short habitaciones,
                                               Integer m2) {

        BigDecimal precioMedio;
        BigDecimal precioMedioM2;
        BigDecimal percentil25;
        BigDecimal percentil75;
        String nombreZona;

        LocalDate mesActual = LocalDate.now().withDayOfMonth(1);

        if (barrioSlug != null && !barrioSlug.isBlank()) {
            // Evaluar contra estadísticas del barrio
            Barrio barrio = barrioRepo.findBySlugAndCiudad_Slug(barrioSlug, ciudadSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("Barrio no encontrado: " + barrioSlug));

            StatsBarrioMensual stat = barrioStatsRepo
                    .findByBarrio_IdAndMesAndHabitaciones(barrio.getId(), mesActual, habitaciones)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No hay datos recientes para este barrio."));

            precioMedio   = stat.getPrecioMedio();
            precioMedioM2 = stat.getPrecioMedioM2();
            percentil25   = stat.getPercentil25();
            percentil75   = stat.getPercentil75();
            nombreZona    = barrio.getNombre();
        } else {
            // Evaluar contra estadísticas de la ciudad
            Ciudad ciudad = ciudadRepo.findBySlug(ciudadSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("Ciudad no encontrada: " + ciudadSlug));

            StatsCiudadMensual stat = ciudadStatsRepo
                    .findByCiudad_IdAndMesAndHabitaciones(ciudad.getId(), mesActual, habitaciones)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No hay datos recientes para esta ciudad."));

            precioMedio   = stat.getPrecioMedio();
            precioMedioM2 = stat.getPrecioMedioM2();
            percentil25   = stat.getPercentil25();
            percentil75   = stat.getPercentil75();
            nombreZona    = ciudad.getNombre();
        }

        // Diferencia porcentual respecto a la media
        BigDecimal diferencia = precio.subtract(precioMedio)
                .divide(precioMedio, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        String diferenciaStr = (diferencia.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                + diferencia.setScale(1, RoundingMode.HALF_UP) + "%";

        // Valoración
        String valoracion;
        if (precio.compareTo(percentil75) > 0) {
            valoracion = "por_encima";
        } else if (precio.compareTo(percentil25) < 0) {
            valoracion = "por_debajo";
        } else {
            valoracion = "en_rango";
        }

        // Percentil aproximado: posición lineal entre p25 y p75
        int percentilZona = calcularPercentilAproximado(precio, percentil25, percentil75);

        // Texto de recomendación
        String tipologia = habitaciones == null ? "pisos"
                : habitaciones == 0 ? "estudios"
                : habitaciones == 1 ? "pisos de 1 habitación"
                : "pisos de " + habitaciones + " habitaciones";

        String recomendacion = String.format(
                "Este precio está un %s respecto a la media de %s para %s en %s.",
                diferenciaStr,
                precioMedio.setScale(0, RoundingMode.HALF_UP) + "€",
                tipologia,
                nombreZona
        );

        return StatsDTO.EvaluacionResponse.builder()
                .precioConsultado(precio)
                .precioMedioZona(precioMedio)
                .precioMedioM2Zona(precioMedioM2)
                .valoracion(valoracion)
                .diferenciaPorcentaje(diferenciaStr)
                .percentilZona(percentilZona)
                .recomendacion(recomendacion)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────
    private List<StatsBarrioMensual> deduplicarPorMesBarrio(List<StatsBarrioMensual> filas) {
        return new ArrayList<>(filas.stream()
                .collect(Collectors.toMap(
                        StatsBarrioMensual::getMes,
                        f -> f,
                        (a, b) -> a.getId() > b.getId() ? a : b,
                        TreeMap::new
                ))
                .values());
    }
    /**
     * Para cada mes, conserva la fila con el id más alto (= la más reciente
     * si el job hace INSERT en lugar de UPSERT). El resultado sale ordenado
     * por mes ASC, igual que la query original.
     */
    private List<StatsCiudadMensual> deduplicarPorMesCiudad(List<StatsCiudadMensual> filas) {
        return new ArrayList<>(filas.stream()
                .collect(Collectors.toMap(
                        StatsCiudadMensual::getMes,
                        f -> f,
                        // si hay dos filas para el mismo mes, nos quedamos con la de id mayor
                        (a, b) -> a.getId() > b.getId() ? a : b,
                        TreeMap::new   // mantiene orden natural de LocalDate (ASC)
                ))
                .values());
    }

    /** Agrega varias filas mensuales de ciudad en una sola (usa la última como referencia para p25/p75). */
    private StatsCiudadMensual agregarCiudad(List<StatsCiudadMensual> filas) {
        // Ponderamos la media por total_muestras
        BigDecimal sumaPrecio = BigDecimal.ZERO;
        BigDecimal sumaM2     = BigDecimal.ZERO;
        int totalMuestras     = 0;
        BigDecimal precioMin  = filas.get(0).getPrecioMin();
        BigDecimal precioMax  = filas.get(0).getPrecioMax();

        for (StatsCiudadMensual f : filas) {
            int n = f.getTotalMuestras();
            sumaPrecio = sumaPrecio.add(f.getPrecioMedio().multiply(BigDecimal.valueOf(n)));
            if (f.getPrecioMedioM2() != null)
                sumaM2 = sumaM2.add(f.getPrecioMedioM2().multiply(BigDecimal.valueOf(n)));
            totalMuestras += n;
            if (f.getPrecioMin() != null && f.getPrecioMin().compareTo(precioMin) < 0)
                precioMin = f.getPrecioMin();
            if (f.getPrecioMax() != null && f.getPrecioMax().compareTo(precioMax) > 0)
                precioMax = f.getPrecioMax();
        }

        BigDecimal mediaGlobal = sumaPrecio.divide(BigDecimal.valueOf(totalMuestras), 2, RoundingMode.HALF_UP);
        BigDecimal m2Global    = totalMuestras > 0
                ? sumaM2.divide(BigDecimal.valueOf(totalMuestras), 2, RoundingMode.HALF_UP) : null;

        // Tomamos percentiles y mediana del mes más reciente (mejor aproximación disponible)
        StatsCiudadMensual ultimo = filas.get(filas.size() - 1);

        return StatsCiudadMensual.builder()
                .precioMedio(mediaGlobal)
                .precioMediana(ultimo.getPrecioMediana())
                .precioMin(precioMin)
                .precioMax(precioMax)
                .percentil25(ultimo.getPercentil25())
                .percentil75(ultimo.getPercentil75())
                .precioMedioM2(m2Global)
                .totalMuestras(totalMuestras)
                .build();
    }

    private StatsBarrioMensual agregarBarrio(List<StatsBarrioMensual> filas) {
        BigDecimal sumaPrecio = BigDecimal.ZERO;
        BigDecimal sumaM2     = BigDecimal.ZERO;
        int totalMuestras     = 0;
        BigDecimal precioMin  = filas.get(0).getPrecioMin();
        BigDecimal precioMax  = filas.get(0).getPrecioMax();

        for (StatsBarrioMensual f : filas) {
            int n = f.getTotalMuestras();
            sumaPrecio = sumaPrecio.add(f.getPrecioMedio().multiply(BigDecimal.valueOf(n)));
            if (f.getPrecioMedioM2() != null)
                sumaM2 = sumaM2.add(f.getPrecioMedioM2().multiply(BigDecimal.valueOf(n)));
            totalMuestras += n;
            if (f.getPrecioMin() != null && f.getPrecioMin().compareTo(precioMin) < 0)
                precioMin = f.getPrecioMin();
            if (f.getPrecioMax() != null && f.getPrecioMax().compareTo(precioMax) > 0)
                precioMax = f.getPrecioMax();
        }

        BigDecimal mediaGlobal = sumaPrecio.divide(BigDecimal.valueOf(totalMuestras), 2, RoundingMode.HALF_UP);
        BigDecimal m2Global    = totalMuestras > 0
                ? sumaM2.divide(BigDecimal.valueOf(totalMuestras), 2, RoundingMode.HALF_UP) : null;

        StatsBarrioMensual ultimo = filas.get(filas.size() - 1);

        return StatsBarrioMensual.builder()
                .precioMedio(mediaGlobal)
                .precioMediana(ultimo.getPrecioMediana())
                .precioMin(precioMin)
                .precioMax(precioMax)
                .percentil25(ultimo.getPercentil25())
                .percentil75(ultimo.getPercentil75())
                .precioMedioM2(m2Global)
                .totalMuestras(totalMuestras)
                .build();
    }

    private PrecioMes buildPrecioMes(StatsCiudadMensual s) {
        return PrecioMes.builder()
                .media(s.getPrecioMedio())
                .mediana(s.getPrecioMediana())
                .min(s.getPrecioMin())
                .max(s.getPrecioMax())
                .percentil25(s.getPercentil25())
                .percentil75(s.getPercentil75())
                .build();
    }

    private PrecioMes buildPrecioMesBarrio(StatsBarrioMensual s) {
        return PrecioMes.builder()
                .media(s.getPrecioMedio())
                .mediana(s.getPrecioMediana())
                .min(s.getPrecioMin())
                .max(s.getPrecioMax())
                .percentil25(s.getPercentil25())
                .percentil75(s.getPercentil75())
                .build();
    }

    private Periodo buildPeriodo(LocalDate desde, LocalDate hasta) {
        return Periodo.builder()
                .desde(desde.toString())
                .hasta(hasta.toString())
                .build();
    }

    private LocalDate ultimoMes(List<StatsCiudadMensual> filas) {
        return filas.get(filas.size() - 1).getMes();
    }

    private LocalDate ultimoMesBarrio(List<StatsBarrioMensual> filas) {
        return filas.get(filas.size() - 1).getMes();
    }

    /** Distribución de tipologías para una ciudad en un mes concreto. */
    private DistribucionTipologia calcularDistribucionCiudad(String slug, LocalDate mes) {
        return DistribucionTipologia.builder()
                .estudios(tipologiaCiudad(slug, mes, (short) 0))
                .unaHabitacion(tipologiaCiudad(slug, mes, (short) 1))
                .dosHabitaciones(tipologiaCiudad(slug, mes, (short) 2))
                .tresHabitaciones(tipologiaCiudad(slug, mes, (short) 3))
                .cuatroOMas(tipologiaCiudad(slug, mes, (short) 4))
                .build();
    }

    private DistribucionTipologia calcularDistribucionBarrio(String barrioSlug,
                                                             String ciudadSlug,
                                                             LocalDate mes) {
        return DistribucionTipologia.builder()
                .estudios(tipologiaBarrio(barrioSlug, ciudadSlug, mes, (short) 0))
                .unaHabitacion(tipologiaBarrio(barrioSlug, ciudadSlug, mes, (short) 1))
                .dosHabitaciones(tipologiaBarrio(barrioSlug, ciudadSlug, mes, (short) 2))
                .tresHabitaciones(tipologiaBarrio(barrioSlug, ciudadSlug, mes, (short) 3))
                .cuatroOMas(tipologiaBarrio(barrioSlug, ciudadSlug, mes, (short) 4))
                .build();
    }

    private TipologiaItem tipologiaCiudad(String slug, LocalDate mes, short hab) {
        List<StatsCiudadMensual> filas = ciudadStatsRepo
                .findByCiudad_SlugAndMesAndHabitaciones(slug, mes, Short.valueOf(hab));
        if (filas.isEmpty()) {
            return TipologiaItem.builder().cantidad(0).precioMedio(null).build();
        }
        StatsCiudadMensual s = filas.get(0);
        return TipologiaItem.builder()
                .cantidad(s.getTotalMuestras())
                .precioMedio(s.getPrecioMedio())
                .build();
    }

    private TipologiaItem tipologiaBarrio(String barrioSlug, String ciudadSlug,
                                          LocalDate mes, short hab) {
        return barrioStatsRepo
                .findByBarrio_Ciudad_SlugAndMesAndHabitaciones(ciudadSlug, mes, Short.valueOf(hab))
                .stream()
                .filter(s -> s.getBarrio().getSlug().equals(barrioSlug))
                .findFirst()
                .map(s -> (TipologiaItem) TipologiaItem.builder()
                        .cantidad(s.getTotalMuestras())
                        .precioMedio(s.getPrecioMedio())
                        .build())
                .orElse(TipologiaItem.builder().cantidad(0).precioMedio(null).build());
    }

    private ComparativaCiudad calcularComparativaCiudad(String ciudadSlug,
                                                        Short habitaciones,
                                                        LocalDate mes,
                                                        BigDecimal precioBarrio) {
        List<StatsCiudadMensual> filas = ciudadStatsRepo
                .findByCiudad_SlugAndMesAndHabitaciones(ciudadSlug, mes, habitaciones);
        if (filas.isEmpty()) return null;
        BigDecimal mediaCiudad = filas.get(0).getPrecioMedio();
        BigDecimal diferencia = precioBarrio.subtract(mediaCiudad)
                .divide(mediaCiudad, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        String difStr = (diferencia.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                + diferencia.setScale(1, RoundingMode.HALF_UP) + "%";
        return ComparativaCiudad.builder()
                .precioMedioCiudad(mediaCiudad)
                .diferenciaPocentaje(difStr)
                .build();
    }

    private String calcularVariacion(BigDecimal inicio, BigDecimal fin) {
        if (inicio == null || fin == null || inicio.compareTo(BigDecimal.ZERO) == 0)
            return "N/A";
        BigDecimal variacion = fin.subtract(inicio)
                .divide(inicio, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return (variacion.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                + variacion.setScale(1, RoundingMode.HALF_UP) + "%";
    }

    /**
     * Percentil aproximado mediante interpolación lineal entre p25 y p75.
     * Valores fuera del rango se clampean a [5, 95].
     */
    private int calcularPercentilAproximado(BigDecimal precio, BigDecimal p25, BigDecimal p75) {
        if (p25 == null || p75 == null) return 50;
        if (precio.compareTo(p25) <= 0) return Math.max(5, 25);
        if (precio.compareTo(p75) >= 0) return Math.min(95, 75);
        // Interpolación lineal entre p25 (25) y p75 (75)
        double t = precio.subtract(p25).doubleValue()
                / p75.subtract(p25).doubleValue();
        return (int) Math.round(25 + t * 50);
    }

    // Filtros por fecha — generics funcionales inline
    @FunctionalInterface
    private interface MesExtractor<T> {
        LocalDate getMes(T item);
    }

    private <T> List<T> filtrarPorFechas(List<T> lista,
                                         LocalDate desde, LocalDate hasta,
                                         MesExtractor<T> mesGetter,
                                         MesExtractor<T> ignored) {
        if (desde == null && hasta == null) return lista;
        return lista.stream()
                .filter(item -> {
                    LocalDate mes = mesGetter.getMes(item);
                    boolean ok = true;
                    if (desde != null) ok = ok && !mes.isBefore(desde);
                    if (hasta != null) ok = ok && !mes.isAfter(hasta);
                    return ok;
                })
                .collect(Collectors.toList());
    }

    private List<StatsBarrioMensual> filtrarPorFechasBarrio(List<StatsBarrioMensual> lista,
                                                            LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) return lista;
        return lista.stream()
                .filter(f -> {
                    boolean ok = true;
                    if (desde != null) ok = ok && !f.getMes().isBefore(desde);
                    if (hasta != null) ok = ok && !f.getMes().isAfter(hasta);
                    return ok;
                })
                .collect(Collectors.toList());
    }
}