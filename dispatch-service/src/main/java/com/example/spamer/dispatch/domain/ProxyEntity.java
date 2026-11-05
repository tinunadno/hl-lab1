package com.example.spamer.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "proxy")
public class ProxyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String host;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProxyProtocol protocol;

    @Column(length = 64)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProxyStatus status;

    // Service in catalog-service (another database), so a plain id, not a relation.
    // nullable: a proxy may exist before it's attributed to a provider service.
    private UUID providerId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
