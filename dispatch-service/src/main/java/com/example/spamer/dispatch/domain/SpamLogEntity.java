package com.example.spamer.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "spam_log")
public class SpamLogEntity {

    @Id
    private UUID id;

    // User in user-service (another database), so a plain id, not a relation.
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String victimContact;

    @Column(columnDefinition = "text")
    private String messageBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SpamStatus status;

    private Instant sentAt;

    // Saga operation of /spam/send; null for rows created through the CRUD API.
    @Column(unique = true)
    private UUID operationId;

    // The money for this failed send has not been confirmed as returned yet.
    @Column(nullable = false)
    private boolean refundPending;

    // Implicit names would be spam_log_proxies and proxies_id.
    @ManyToMany
    @JoinTable(
            name = "spam_log_proxy",
            joinColumns = @JoinColumn(name = "spam_log_id"),
            inverseJoinColumns = @JoinColumn(name = "proxy_id"))
    private Set<ProxyEntity> proxies = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
