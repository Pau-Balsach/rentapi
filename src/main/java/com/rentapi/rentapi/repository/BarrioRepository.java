package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.Barrio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarrioRepository extends JpaRepository<Barrio, Long> {

    // Usado en GeoService — listar barrios de una ciudad
    List<Barrio> findByCiudad_Slug(String ciudadSlug);

    // Usado en StatsCalculator — listar barrios de una ciudad por id
    List<Barrio> findByCiudad_Id(Long ciudadId);

    // Usado en StatsService.getStatsBarrio() y evaluar()
    Optional<Barrio> findBySlugAndCiudad_Slug(String slug, String ciudadSlug);

    // Usado en GeoService.getBarrios() para el campo pisos_indexados
    @Query("SELECT COUNT(p) FROM Piso p WHERE p.barrio.id = :barrioId AND p.activo = true")
    Long countPisosActivosByBarrioId(@Param("barrioId") Long barrioId);
}