package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.StatsCiudadMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatsCiudadMensualRepository extends JpaRepository<StatsCiudadMensual, Long> {

    Optional<StatsCiudadMensual> findByCiudad_IdAndMesAndHabitaciones(
            Long ciudadId, LocalDate mes, Short habitaciones);

    List<StatsCiudadMensual> findByCiudad_SlugAndHabitacionesOrderByMesAsc(
            String slug, Short habitaciones);

    // Devuelve lista para poder usarlo con .get(0) en distribución de tipologías
    List<StatsCiudadMensual> findByCiudad_SlugAndMesAndHabitaciones(
            String slug, LocalDate mes, Short habitaciones);

    // Para el ranking — mes actual todas las ciudades
    List<StatsCiudadMensual> findByMesAndHabitaciones(LocalDate mes, Short habitaciones);

    // Para el endpoint /stats/ranking
    List<StatsCiudadMensual> findByMesAndHabitacionesOrderByPrecioMedioM2Desc(
            LocalDate mes, Short habitaciones);

    List<StatsCiudadMensual> findByMesAndHabitacionesOrderByPrecioMedioM2Asc(
            LocalDate mes, Short habitaciones);
}