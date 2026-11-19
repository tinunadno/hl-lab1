package com.example.spamer.dispatch.service;

import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.domain.SpamLogEntity;
import com.example.spamer.dispatch.domain.SpamLogRepository;
import com.example.spamer.dispatch.domain.SpamStatus;
import com.example.spamer.dispatch.dto.CreateSpamLogRequest;
import com.example.spamer.dispatch.dto.CursorPage;
import com.example.spamer.dispatch.dto.PatchSpamLogRequest;
import com.example.spamer.dispatch.dto.SpamLogResponse;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import com.example.spamer.dispatch.mapper.SpamLogMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SpamLogService {

    private final SpamLogRepository repo;
    private final UserClient users;
    private final ProxyService proxies;
    private final SpamLogMapper mapper;
    private final TransactionTemplate tx;

    public SpamLogResponse create(CreateSpamLogRequest req) {

        users.get(req.userId());
        return tx.execute(status -> {
            SpamLogEntity log = new SpamLogEntity();
            apply(log, req);
            return mapper.toResponse(repo.save(log));
        });
    }

    @Transactional(readOnly = true)
    public SpamLogResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<SpamLogResponse> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repo.count();
    }


    @Transactional(readOnly = true)
    public CursorPage<SpamLogResponse> feed(UUID cursor, int size) {
        List<SpamLogEntity> rows = cursor == null
                ? repo.findAllByOrderByIdAsc(Limit.of(size + 1))
                : repo.findByIdGreaterThanOrderByIdAsc(cursor, Limit.of(size + 1));

        String next = null;
        if (rows.size() > size) {
            SpamLogEntity last = rows.get(size - 1);
            next = last.getId().toString();
            rows = rows.subList(0, size);
        }
        List<SpamLogResponse> items = rows.stream().map(mapper::toResponse).toList();
        return new CursorPage<>(items, next);
    }

    public SpamLogResponse replace(UUID id, CreateSpamLogRequest req) {
        users.get(req.userId());
        return tx.execute(status -> {
            SpamLogEntity log = find(id);
            apply(log, req);
            return mapper.toResponse(log);
        });
    }

    @Transactional
    public SpamLogResponse patch(UUID id, PatchSpamLogRequest req) {
        SpamLogEntity log = find(id);
        if (req.victimContact() != null) {
            log.setVictimContact(req.victimContact());
        }
        if (req.messageBody() != null) {
            log.setMessageBody(req.messageBody());
        }
        if (req.status() != null) {
            log.setStatus(req.status());
        }
        return mapper.toResponse(log);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw NotFoundException.of("SpamLog", id);
        }
        repo.deleteById(id);
    }


    @Transactional
    public UUID createQueued(SpamSendRequest req, UUID operationId) {
        SpamLogEntity log = new SpamLogEntity();
        log.setUserId(req.userId());
        log.setVictimContact(req.victimContact());
        log.setMessageBody(req.messageBody());
        log.setStatus(SpamStatus.QUEUED);
        log.setOperationId(operationId);
        log.setProxies(new HashSet<>(proxies.findActive(req.messageCount())));
        return repo.save(log).getId();
    }

    @Transactional
    public void markSent(UUID id) {
        SpamLogEntity log = find(id);
        log.setStatus(SpamStatus.SENT);
        log.setSentAt(Instant.now());
    }

    @Transactional
    public void markFailedWithRefundPending(UUID id) {
        SpamLogEntity log = find(id);
        log.setStatus(SpamStatus.FAILED);
        log.setRefundPending(true);
    }

    @Transactional
    public void clearRefundPending(UUID id) {
        find(id).setRefundPending(false);
    }

    @Transactional
    public void discard(UUID id) {
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PendingRefund> findPendingRefunds() {
        return repo.findTop50ByRefundPendingTrue().stream()
                .map(log -> new PendingRefund(log.getId(), log.getUserId(), log.getOperationId()))
                .toList();
    }

    private SpamLogEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() -> NotFoundException.of("SpamLog", id));
    }

    private void apply(SpamLogEntity log, CreateSpamLogRequest req) {
        log.setUserId(req.userId());
        log.setVictimContact(req.victimContact());
        log.setMessageBody(req.messageBody());
        log.setStatus(req.status());
    }

    public record PendingRefund(UUID spamLogId, UUID userId, UUID operationId) {
    }
}
