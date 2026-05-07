package com.rentapi.rentapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Entity
@Table(name = "api_usage_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "endpoint", "fecha"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 200)
    private String endpoint;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column
    @Builder.Default
    private Integer peticiones = 1;
}
