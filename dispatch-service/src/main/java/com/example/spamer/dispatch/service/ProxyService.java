package com.example.spamer.dispatch.service;

import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.dispatch.client.CatalogClient;
import com.example.spamer.dispatch.domain.ProxyEntity;
import com.example.spamer.dispatch.domain.ProxyRepository;
import com.example.spamer.dispatch.domain.ProxyStatus;
import com.example.spamer.dispatch.dto.CreateProxyRequest;
import com.example.spamer.dispatch.dto.PatchProxyRequest;
import com.example.spamer.dispatch.dto.ProxyResponse;
import com.example.spamer.dispatch.mapper.ProxyMapper;
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
public class ProxyService {

    private final ProxyRepository repo;
    private final CatalogClient catalog;
    private final ProxyMapper mapper;
    private final TransactionTemplate tx;

    public ProxyResponse create(CreateProxyRequest req) {
        checkProvider(req.providerId());
        return tx.execute(status -> {
            ProxyEntity p = new ProxyEntity();
            apply(p, req);
            return mapper.toResponse(repo.save(p));
        });
    }

    @Transactional(readOnly = true)
    public ProxyResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<ProxyResponse> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repo.count();
    }

    public ProxyResponse replace(UUID id, CreateProxyRequest req) {
        checkProvider(req.providerId());
        return tx.execute(status -> {
            ProxyEntity p = find(id);
            apply(p, req);
            return mapper.toResponse(p);
        });
    }

    public ProxyResponse patch(UUID id, PatchProxyRequest req) {
        checkProvider(req.providerId());
        return tx.execute(status -> {
            ProxyEntity p = find(id);
            if (req.host() != null) {
                p.setHost(req.host());
            }
            if (req.port() != null) {
                p.setPort(req.port());
            }
            if (req.protocol() != null) {
                p.setProtocol(req.protocol());
            }
            if (req.country() != null) {
                p.setCountry(req.country());
            }
            if (req.status() != null) {
                p.setStatus(req.status());
            }
            if (req.providerId() != null) {
                p.setProviderId(req.providerId());
            }
            return mapper.toResponse(p);
        });
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw NotFoundException.of("Proxy", id);
        }
        repo.deleteById(id);
    }

    public List<ProxyEntity> findActive(int limit) {
        return repo.findByStatus(ProxyStatus.ACTIVE, Limit.of(Math.max(1, limit)));
    }


    private void checkProvider(UUID providerId) {
        if (providerId != null) {
            catalog.get(providerId);
        }
    }

    private ProxyEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() -> NotFoundException.of("Proxy", id));
    }

    private void apply(ProxyEntity p, CreateProxyRequest req) {
        p.setHost(req.host());
        p.setPort(req.port());
        p.setProtocol(req.protocol());
        p.setCountry(req.country());
        p.setStatus(req.status());
        p.setProviderId(req.providerId());
    }
}
