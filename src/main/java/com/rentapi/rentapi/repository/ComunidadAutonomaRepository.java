package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.ComunidadAutonoma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComunidadAutonomaRepository extends JpaRepository<ComunidadAutonoma, Long> {

    Optional<ComunidadAutonoma> findBySlug(String slug);
}
