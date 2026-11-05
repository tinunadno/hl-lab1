package com.example.spamer.dispatch.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpamLogRepository extends JpaRepository<SpamLogEntity, UUID> {

    List<SpamLogEntity> findByIdGreaterThanOrderByIdAsc(UUID id, Limit limit);

    List<SpamLogEntity> findAllByOrderByIdAsc(Limit limit);

    List<SpamLogEntity> findTop50ByRefundPendingTrue();
}
