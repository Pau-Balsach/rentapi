package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

    Optional<Ciudad> findBySlug(String slug);

    List<Ciudad> findByProvincia_Slug(String provinciaSlug);

    // Conta quants barris té cada ciutat (per al endpoint /geo/ciudades)
    @Query("SELECT COUNT(b) FROM Barrio b WHERE b.ciudad.id = :ciudadId")
    long countBarriosByCiudadId(@Param("ciudadId") Long ciudadId);
}
