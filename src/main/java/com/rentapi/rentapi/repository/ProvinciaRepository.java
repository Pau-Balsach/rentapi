package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinciaRepository extends JpaRepository<Provincia, Long> {

    Optional<Provincia> findBySlug(String slug);

    List<Provincia> findByComunidad_Slug(String comunidadSlug);
}
