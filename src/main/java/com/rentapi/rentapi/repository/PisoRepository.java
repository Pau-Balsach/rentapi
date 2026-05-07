package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.Piso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PisoRepository extends JpaRepository<Piso, Long> {

    Optional<Piso> findByFuenteAndFuenteId(String fuente, String fuenteId);

    @Query(value = """
        SELECT AVG(p.precio_mes) FROM pisos p
        WHERE p.ciudad_id = :ciudadId
          AND p.activo = true
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
    """, nativeQuery = true)
    BigDecimal avgPrecioMesByCiudad(@Param("ciudadId") Long ciudadId,
                                    @Param("habitaciones") Short habitaciones,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT COUNT(*) FROM pisos p
        WHERE p.ciudad_id = :ciudadId
          AND p.activo = true
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
    """, nativeQuery = true)
    Long countByCiudad(@Param("ciudadId") Long ciudadId,
                       @Param("habitaciones") Short habitaciones,
                       @Param("desde") LocalDateTime desde,
                       @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT AVG(p.precio_mes / p.metros_cuadrados) FROM pisos p
        WHERE p.ciudad_id = :ciudadId
          AND p.activo = true
          AND p.metros_cuadrados IS NOT NULL
          AND p.metros_cuadrados > 0
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
    """, nativeQuery = true)
    BigDecimal avgPrecioM2ByCiudad(@Param("ciudadId") Long ciudadId,
                                   @Param("habitaciones") Short habitaciones,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT p.precio_mes FROM pisos p
        WHERE p.ciudad_id = :ciudadId
          AND p.activo = true
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
        ORDER BY p.precio_mes ASC
    """, nativeQuery = true)
    List<BigDecimal> findPreciosMesByCiudadOrdered(@Param("ciudadId") Long ciudadId,
                                                   @Param("habitaciones") Short habitaciones,
                                                   @Param("desde") LocalDateTime desde,
                                                   @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT AVG(p.precio_mes) FROM pisos p
        WHERE p.barrio_id = :barrioId
          AND p.activo = true
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
    """, nativeQuery = true)
    BigDecimal avgPrecioMesByBarrio(@Param("barrioId") Long barrioId,
                                    @Param("habitaciones") Short habitaciones,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT AVG(p.precio_mes / p.metros_cuadrados) FROM pisos p
        WHERE p.barrio_id = :barrioId
          AND p.activo = true
          AND p.metros_cuadrados IS NOT NULL
          AND p.metros_cuadrados > 0
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
    """, nativeQuery = true)
    BigDecimal avgPrecioM2ByBarrio(@Param("barrioId") Long barrioId,
                                   @Param("habitaciones") Short habitaciones,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta);

    @Query(value = """
        SELECT p.precio_mes FROM pisos p
        WHERE p.barrio_id = :barrioId
          AND p.activo = true
          AND (CAST(:habitaciones AS smallint) IS NULL OR p.habitaciones = CAST(:habitaciones AS smallint))
          AND (CAST(:desde AS timestamp) IS NULL OR p.fecha_scraping >= CAST(:desde AS timestamp))
          AND (CAST(:hasta AS timestamp) IS NULL OR p.fecha_scraping <= CAST(:hasta AS timestamp))
        ORDER BY p.precio_mes ASC
    """, nativeQuery = true)
    List<BigDecimal> findPreciosMesByBarrioOrdered(@Param("barrioId") Long barrioId,
                                                   @Param("habitaciones") Short habitaciones,
                                                   @Param("desde") LocalDateTime desde,
                                                   @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COUNT(p) FROM Piso p WHERE p.barrio.id = :barrioId AND p.activo = true")
    Long countActivosByBarrio(@Param("barrioId") Long barrioId);
}