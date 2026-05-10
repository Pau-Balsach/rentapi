package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.PisoDTO;
import com.rentapi.rentapi.model.Piso;
import com.rentapi.rentapi.repository.PisoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PisoService {

    private final PisoRepository pisoRepository;

    // ─────────────────────────────────────────────────────────────
    // Pisos por ciudad
    // ─────────────────────────────────────────────────────────────

    public PisoDTO.PisoListResponse getPisosByCiudad(String ciudadSlug) {
        List<Piso> pisos = pisoRepository.findActivosByCiudadSlug(ciudadSlug);
        if (pisos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron pisos activos para la ciudad: " + ciudadSlug);
        }
        return PisoDTO.PisoListResponse.builder()
                .total(pisos.size())
                .filtroAplicado("ciudad=" + ciudadSlug)
                .pisos(pisos.stream().map(this::toResponse).toList())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Pisos por barrio
    // ─────────────────────────────────────────────────────────────

    public PisoDTO.PisoListResponse getPisosByBarrio(String ciudadSlug, String barrioSlug) {
        List<Piso> pisos = pisoRepository.findActivosByCiudadSlugAndBarrioSlug(ciudadSlug, barrioSlug);
        if (pisos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron pisos activos para el barrio: " + barrioSlug + " en " + ciudadSlug);
        }
        return PisoDTO.PisoListResponse.builder()
                .total(pisos.size())
                .filtroAplicado("ciudad=" + ciudadSlug + ", barrio=" + barrioSlug)
                .pisos(pisos.stream().map(this::toResponse).toList())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Pisos por precio (con ciudad obligatoria y barrio opcional)
    // ─────────────────────────────────────────────────────────────

    /**
     * Devuelve pisos con precio <= precioMax.
     * Si se indica barrioSlug, filtra también por barrio.
     */
    public PisoDTO.PisoListResponse getPisosByPrecioMaximo(String ciudadSlug,
                                                            String barrioSlug,
                                                            BigDecimal precioMax) {
        List<Piso> pisos = pisoRepository.findActivosByPrecioMaximo(ciudadSlug, barrioSlug, precioMax);
        if (pisos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron pisos con precio <= " + precioMax + " en la zona indicada.");
        }
        String filtro = construirDescripcionFiltro(ciudadSlug, barrioSlug)
                + ", precio_max=" + precioMax + "€";
        return PisoDTO.PisoListResponse.builder()
                .total(pisos.size())
                .filtroAplicado(filtro)
                .pisos(pisos.stream().map(this::toResponse).toList())
                .build();
    }

    /**
     * Devuelve pisos con precio >= precioMin.
     * Si se indica barrioSlug, filtra también por barrio.
     */
    public PisoDTO.PisoListResponse getPisosByPrecioMinimo(String ciudadSlug,
                                                            String barrioSlug,
                                                            BigDecimal precioMin) {
        List<Piso> pisos = pisoRepository.findActivosByPrecioMinimo(ciudadSlug, barrioSlug, precioMin);
        if (pisos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron pisos con precio >= " + precioMin + " en la zona indicada.");
        }
        String filtro = construirDescripcionFiltro(ciudadSlug, barrioSlug)
                + ", precio_min=" + precioMin + "€";
        return PisoDTO.PisoListResponse.builder()
                .total(pisos.size())
                .filtroAplicado(filtro)
                .pisos(pisos.stream().map(this::toResponse).toList())
                .build();
    }

    /**
     * Devuelve pisos dentro del rango [precioMin, precioMax].
     * Si se indica barrioSlug, filtra también por barrio.
     */
    public PisoDTO.PisoListResponse getPisosByRangoPrecio(String ciudadSlug,
                                                           String barrioSlug,
                                                           BigDecimal precioMin,
                                                           BigDecimal precioMax) {
        List<Piso> pisos = pisoRepository.findActivosByRangoPrecio(ciudadSlug, barrioSlug, precioMin, precioMax);
        if (pisos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontraron pisos en el rango " + precioMin + "€ - " + precioMax + "€ en la zona indicada.");
        }
        String filtro = construirDescripcionFiltro(ciudadSlug, barrioSlug)
                + ", precio_min=" + precioMin + "€, precio_max=" + precioMax + "€";
        return PisoDTO.PisoListResponse.builder()
                .total(pisos.size())
                .filtroAplicado(filtro)
                .pisos(pisos.stream().map(this::toResponse).toList())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private PisoDTO.PisoResponse toResponse(Piso p) {
        return PisoDTO.PisoResponse.builder()
                .id(p.getId())
                .fuente(p.getFuente())
                .ciudad(p.getCiudad() != null ? p.getCiudad().getNombre() : null)
                .ciudadSlug(p.getCiudad() != null ? p.getCiudad().getSlug() : null)
                .barrio(p.getBarrio() != null ? p.getBarrio().getNombre() : null)
                .barrioSlug(p.getBarrio() != null ? p.getBarrio().getSlug() : null)
                .precioMes(p.getPrecioMes())
                .metrosCuadrados(p.getMetrosCuadrados())
                .habitaciones(p.getHabitaciones())
                .planta(p.getPlanta())
                .amueblado(p.getAmueblado())
                .permiteMascotas(p.getPermiteMascotas())
                .fechaPublicacion(p.getFechaPublicacion())
                .fechaScraping(p.getFechaScraping())
                .build();
    }

    private String construirDescripcionFiltro(String ciudadSlug, String barrioSlug) {
        String filtro = "ciudad=" + ciudadSlug;
        if (barrioSlug != null) filtro += ", barrio=" + barrioSlug;
        return filtro;
    }
}
