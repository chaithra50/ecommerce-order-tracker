package com.ordertracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * Immutable audit trail of every order status change.
 * Written on every updateOrderStatus() call.
 * Never updated or deleted — append-only for compliance.
 */
@Entity
@Table(name = "order_audit_log", indexes = {
    @Index(name = "idx_audit_order_id",   columnList = "order_id"),
    @Index(name = "idx_audit_changed_at", columnList = "changed_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
private Order order;

    @Column(length = 30)
    private String oldStatus;

    @Column(nullable = false, length = 30)
    private String newStatus;

    @Column(nullable = false, length = 100)
    private String changedBy;   // email of the user who triggered the change

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) changedAt = LocalDateTime.now();
    }
}
