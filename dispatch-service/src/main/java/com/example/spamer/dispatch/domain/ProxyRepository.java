package com.example.spamer.dispatch.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProxyRepository extends JpaRepository<ProxyEntity, UUID> {

    List<ProxyEntity> findByStatus(ProxyStatus status, Limit limit);
}
