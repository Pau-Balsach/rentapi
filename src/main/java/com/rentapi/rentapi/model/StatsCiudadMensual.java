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
@Table(name = "stats_ciudad_mensual",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ciudad_id", "mes", "habitaciones"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsCiudadMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_id")
    private Ciudad ciudad;

    // Primer dia del mes: 2026-05-01
    @Column(nullable = false)
    private LocalDate mes;

    // NULL = totes les tipologies
    @Column
    private Short habitaciones;

    @Column(name = "total_muestras", nullable = false)
    private Integer totalMuestras;

    @Column(name = "precio_medio", precision = 10, scale = 2)
    private BigDecimal precioMedio;

    @Column(name = "precio_mediana", precision = 10, scale = 2)
    private BigDecimal precioMediana;

    @Column(name = "precio_min", precision = 10, scale = 2)
    private BigDecimal precioMin;

    @Column(name = "precio_max", precision = 10, scale = 2)
    private BigDecimal precioMax;

    @Column(name = "percentil_25", precision = 10, scale = 2)
    private BigDecimal percentil25;

    @Column(name = "percentil_75", precision = 10, scale = 2)
    private BigDecimal percentil75;

    @Column(name = "precio_medio_m2", precision = 8, scale = 2)
    private BigDecimal precioMedioM2;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}