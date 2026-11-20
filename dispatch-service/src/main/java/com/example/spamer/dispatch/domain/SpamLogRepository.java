package com.example.spamer.dispatch.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpamLogRepository extends JpaRepository<SpamLogEntity, UUID> {

    List<SpamLogEntity> findByIdGreaterThanOrderByIdAsc(UUID id, Limit limit);

    List<SpamLogEntity> findAllByOrderByIdAsc(Limit limit);

    List<SpamLogEntity> findTop50ByRefundPendingTrue();

    Page<SpamLogEntity> findAllByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    List<SpamLogEntity> findByUserIdOrderByIdAsc(UUID userId, Limit limit);

    List<SpamLogEntity> findByUserIdAndIdGreaterThanOrderByIdAsc(UUID userId, UUID id, Limit limit);
}
