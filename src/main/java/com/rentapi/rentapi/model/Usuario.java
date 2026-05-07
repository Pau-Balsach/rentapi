package com.rentapi.rentapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 150)
    private String nombre;

    // UUID v4 generat al registre — és la clau d'autenticació
    @Column(name = "api_key", nullable = false, unique = true, length = 64)
    private String apiKey;

    // 'free' o 'pro'
    @Column(length = 20)
    @Builder.Default
    private String plan = "free";

    @Column
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
