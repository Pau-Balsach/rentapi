package com.rentapi.rentapi.service;

import com.rentapi.rentapi.dto.GeoDTO;
import com.rentapi.rentapi.model.*;
import com.rentapi.rentapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock private ComunidadAutonomaRepository comunidadRepo;
    @Mock private ProvinciaRepository provinciaRepo;
    @Mock private CiudadRepository ciudadRepo;
    @Mock private BarrioRepository barrioRepo;

    @InjectMocks
    private GeoService geoService;

    private ComunidadAutonoma cataluna;
    private Provincia barcelona;
    private Ciudad ciudadBcn;
    private Barrio gracia;

    @BeforeEach
    void setUp() {
        cataluna = ComunidadAutonoma.builder()
                .id(1L).nombre("Cataluña").slug("cataluna").build();

        barcelona = Provincia.builder()
                .id(31L).nombre("Barcelona").slug("barcelona").comunidad(cataluna).build();

        ciudadBcn = Ciudad.builder()
                .id(1L).nombre("Barcelona").slug("barcelona").provincia(barcelona).build();

        gracia = Barrio.builder()
                .id(10L).nombre("Gràcia").slug("gracia").ciudad(ciudadBcn).build();
    }

    // ─── getComunidades ──────────────────────────────────────────

    @Test
    void getComunidades_devuelveLista() {
        when(comunidadRepo.findAll()).thenReturn(List.of(cataluna));

        List<GeoDTO.ComunidadDTO> result = geoService.getComunidades();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombre()).isEqualTo("Cataluña");
        assertThat(result.get(0).slug()).isEqualTo("cataluna");
    }

    @Test
    void getComunidades_listaVacia_devuelveVacio() {
        when(comunidadRepo.findAll()).thenReturn(List.of());

        List<GeoDTO.ComunidadDTO> result = geoService.getComunidades();

        assertThat(result).isEmpty();
    }

    // ─── getProvincias ───────────────────────────────────────────

    @Test
    void getProvincias_conFiltro_filtraPorComunidad() {
        when(provinciaRepo.findByComunidad_Slug("cataluna")).thenReturn(List.of(barcelona));

        List<GeoDTO.ProvinciaDTO> result = geoService.getProvincias("cataluna");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombre()).isEqualTo("Barcelona");
        assertThat(result.get(0).comunidad()).isEqualTo("Cataluña");
        verify(provinciaRepo).findByComunidad_Slug("cataluna");
        verify(provinciaRepo, never()).findAll();
    }

    @Test
    void getProvincias_sinFiltro_devuelveTodas() {
        when(provinciaRepo.findAll()).thenReturn(List.of(barcelona));

        List<GeoDTO.ProvinciaDTO> result = geoService.getProvincias(null);

        assertThat(result).hasSize(1);
        verify(provinciaRepo).findAll();
        verify(provinciaRepo, never()).findByComunidad_Slug(any());
    }

    @Test
    void getProvincias_filtroVacio_devuelveTodas() {
        when(provinciaRepo.findAll()).thenReturn(List.of(barcelona));

        List<GeoDTO.ProvinciaDTO> result = geoService.getProvincias("  ");

        verify(provinciaRepo).findAll();
    }

    // ─── getCiudades ─────────────────────────────────────────────

    @Test
    void getCiudades_conFiltro_filtraPorProvincia() {
        when(ciudadRepo.findByProvincia_Slug("barcelona")).thenReturn(List.of(ciudadBcn));
        when(ciudadRepo.countBarriosByCiudadId(1L)).thenReturn(73L);

        List<GeoDTO.CiudadDTO> result = geoService.getCiudades("barcelona");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombre()).isEqualTo("Barcelona");
        assertThat(result.get(0).barriosDisponibles()).isEqualTo(73L);
        verify(ciudadRepo).findByProvincia_Slug("barcelona");
    }

    @Test
    void getCiudades_sinFiltro_devuelveTodas() {
        when(ciudadRepo.findAll()).thenReturn(List.of(ciudadBcn));
        when(ciudadRepo.countBarriosByCiudadId(any())).thenReturn(0L);

        List<GeoDTO.CiudadDTO> result = geoService.getCiudades(null);

        verify(ciudadRepo).findAll();
        verify(ciudadRepo, never()).findByProvincia_Slug(any());
    }

    @Test
    void getCiudades_incluyeConteoBarrios() {
        when(ciudadRepo.findAll()).thenReturn(List.of(ciudadBcn));
        when(ciudadRepo.countBarriosByCiudadId(1L)).thenReturn(42L);

        List<GeoDTO.CiudadDTO> result = geoService.getCiudades(null);

        assertThat(result.get(0).barriosDisponibles()).isEqualTo(42L);
    }

    // ─── getBarrios ──────────────────────────────────────────────

    @Test
    void getBarrios_devuelveBarriosDeCiudad() {
        when(barrioRepo.findByCiudad_Slug("barcelona")).thenReturn(List.of(gracia));
        when(barrioRepo.countPisosActivosByBarrioId(10L)).thenReturn(342L);

        List<GeoDTO.BarrioDTO> result = geoService.getBarrios("barcelona");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombre()).isEqualTo("Gràcia");
        assertThat(result.get(0).slug()).isEqualTo("gracia");
        assertThat(result.get(0).pisosIndexados()).isEqualTo(342L);
    }

    @Test
    void getBarrios_sinBarrios_devuelveVacio() {
        when(barrioRepo.findByCiudad_Slug("ciudad-sin-barrios")).thenReturn(List.of());

        List<GeoDTO.BarrioDTO> result = geoService.getBarrios("ciudad-sin-barrios");

        assertThat(result).isEmpty();
    }

    @Test
    void getBarrios_incluyeNombreCiudad() {
        when(barrioRepo.findByCiudad_Slug("barcelona")).thenReturn(List.of(gracia));
        when(barrioRepo.countPisosActivosByBarrioId(any())).thenReturn(0L);

        List<GeoDTO.BarrioDTO> result = geoService.getBarrios("barcelona");

        assertThat(result.get(0).ciudad()).isEqualTo("Barcelona");
    }
}
