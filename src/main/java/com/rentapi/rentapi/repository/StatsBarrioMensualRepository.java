package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.StatsBarrioMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatsBarrioMensualRepository extends JpaRepository<StatsBarrioMensual, Long> {

    // Usado en StatsCalculator (upsert)
    Optional<StatsBarrioMensual> findByBarrio_IdAndMesAndHabitaciones(
            Long barrioId, LocalDate mes, Short habitaciones);

    // Usado en StatsService.getStatsBarrio() y getTendencia()
    List<StatsBarrioMensual> findByBarrio_SlugAndHabitacionesOrderByMesAsc(
            String barrioSlug, Short habitaciones);

    // Usado en StatsService.tipologiaBarrio() — todos los barrios de una ciudad en un mes y tipología
    List<StatsBarrioMensual> findByBarrio_Ciudad_SlugAndMesAndHabitaciones(
            String ciudadSlug, LocalDate mes, Short habitaciones);

    // Usado en StatsService.getRanking() para barrios de una ciudad concreta
    List<StatsBarrioMensual> findByBarrio_Ciudad_SlugAndMesAndHabitacionesOrderByPrecioMedioM2Desc(
            String ciudadSlug, LocalDate mes, Short habitaciones);
}