package com.rentapi.rentapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pisos",
        indexes = {
                @Index(name = "idx_pisos_ciudad", columnList = "ciudad_id"),
                @Index(name = "idx_pisos_barrio", columnList = "barrio_id"),
                @Index(name = "idx_pisos_fecha", columnList = "fecha_scraping"),
                @Index(name = "idx_pisos_habitaciones", columnList = "habitaciones")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Piso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Portal d'origen: 'idealista', 'fotocasa', 'habitaclia'
    @Column(nullable = false, length = 50)
    private String fuente;

    // ID de l'anunci al portal original (per evitar duplicats)
    @Column(name = "fuente_id", length = 100)
    private String fuenteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_id")
    private Ciudad ciudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barrio_id")
    private Barrio barrio;

    // Preu mensual en euros
    @Column(name = "precio_mes", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioMes;

    @Column(name = "metros_cuadrados")
    private Integer metrosCuadrados;

    // 0 = estudi, 1, 2, 3, 4 = 4 o més
    @Column
    private Short habitaciones;

    @Column(length = 20)
    private String planta;

    @Column
    private Boolean amueblado;

    @Column(name = "permite_mascotas")
    private Boolean permiteMascotas;

    @Column(name = "fecha_scraping", nullable = false)
    private LocalDateTime fechaScraping;

    @Column(name = "fecha_publicacion")
    private LocalDate fechaPublicacion;

    // false si l'anunci ja no apareix al portal
    @Column
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.fechaScraping == null) {
            this.fechaScraping = LocalDateTime.now();
        }
    }
}