package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.GeoDTO;
import com.rentapi.rentapi.repository.BarrioRepository;
import com.rentapi.rentapi.repository.CiudadRepository;
import com.rentapi.rentapi.repository.ComunidadAutonomaRepository;
import com.rentapi.rentapi.repository.ProvinciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeoService {

    private final ComunidadAutonomaRepository comunidadRepo;
    private final ProvinciaRepository provinciaRepo;
    private final CiudadRepository ciudadRepo;
    private final BarrioRepository barrioRepo;

    public List<GeoDTO.ComunidadDTO> getComunidades() {
        return comunidadRepo.findAll().stream()
                .map(c -> new GeoDTO.ComunidadDTO(c.getId(), c.getNombre(), c.getSlug()))
                .toList();
    }

    public List<GeoDTO.ProvinciaDTO> getProvincias(String comunidadSlug) {
        var provincias = (comunidadSlug != null && !comunidadSlug.isBlank())
                ? provinciaRepo.findByComunidad_Slug(comunidadSlug)
                : provinciaRepo.findAll();

        return provincias.stream()
                .map(p -> new GeoDTO.ProvinciaDTO(
                        p.getId(),
                        p.getNombre(),
                        p.getSlug(),
                        p.getComunidad() != null ? p.getComunidad().getNombre() : null))
                .toList();
    }

    public List<GeoDTO.CiudadDTO> getCiudades(String provinciaSlug) {
        var ciudades = (provinciaSlug != null && !provinciaSlug.isBlank())
                ? ciudadRepo.findByProvincia_Slug(provinciaSlug)
                : ciudadRepo.findAll();

        return ciudades.stream()
                .map(c -> new GeoDTO.CiudadDTO(
                        c.getId(),
                        c.getNombre(),
                        c.getSlug(),
                        c.getProvincia() != null ? c.getProvincia().getNombre() : null,
                        ciudadRepo.countBarriosByCiudadId(c.getId())))
                .toList();
    }

    public List<GeoDTO.BarrioDTO> getBarrios(String ciudadSlug) {
        return barrioRepo.findByCiudad_Slug(ciudadSlug).stream()
                .map(b -> new GeoDTO.BarrioDTO(
                        b.getId(),
                        b.getNombre(),
                        b.getSlug(),
                        b.getCiudad() != null ? b.getCiudad().getNombre() : null,
                        barrioRepo.countPisosActivosByBarrioId(b.getId())))
                .toList();
    }
}
