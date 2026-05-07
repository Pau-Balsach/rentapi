package com.rentapi.rentapi.repository;

import com.rentapi.rentapi.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByApiKey(String apiKey);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
