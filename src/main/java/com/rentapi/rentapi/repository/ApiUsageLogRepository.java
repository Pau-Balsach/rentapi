package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    @Query("SELECT COALESCE(SUM(l.peticiones), 0) FROM ApiUsageLog l " +
            "WHERE l.usuario.id = :usuarioId AND l.fecha = :fecha")
    Integer sumPeticionesByUsuarioAndFecha(@Param("usuarioId") Long usuarioId,
                                           @Param("fecha") LocalDate fecha);

    Optional<ApiUsageLog> findByUsuario_IdAndEndpointAndFecha(
            Long usuarioId, String endpoint, LocalDate fecha);

    @Modifying
    @Query("UPDATE ApiUsageLog l SET l.peticiones = l.peticiones + 1 " +
            "WHERE l.usuario.id = :usuarioId AND l.endpoint = :endpoint AND l.fecha = :fecha")
    int incrementPeticiones(@Param("usuarioId") Long usuarioId,
                            @Param("endpoint") String endpoint,
                            @Param("fecha") LocalDate fecha);
}