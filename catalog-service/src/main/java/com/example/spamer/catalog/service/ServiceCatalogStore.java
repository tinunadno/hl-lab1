package com.example.spamer.catalog.service;

import com.example.spamer.catalog.domain.ServiceEntity;
import com.example.spamer.catalog.domain.ServiceRepository;
import com.example.spamer.catalog.dto.CreateServiceRequest;
import com.example.spamer.catalog.dto.PatchServiceRequest;
import com.example.spamer.catalog.dto.ServiceResponse;
import com.example.spamer.catalog.mapper.ServiceMapper;
import com.example.spamer.common.error.ConflictException;
import com.example.spamer.common.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
public class ServiceCatalogStore {

    private final ServiceRepository repo;
    private final ServiceMapper mapper;

    @Transactional
    public ServiceResponse create(CreateServiceRequest req) {
        if (repo.existsByName(req.name())) {
            throw new ConflictException("Service name already exists: " + req.name());
        }
        ServiceEntity s = new ServiceEntity();
        apply(s, req);
        return mapper.toResponse(repo.save(s));
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repo.count();
    }

    @Transactional
    public ServiceResponse replace(UUID id, CreateServiceRequest req) {
        ServiceEntity s = find(id);
        if (!s.getName().equals(req.name()) && repo.existsByName(req.name())) {
            throw new ConflictException("Service name already exists: " + req.name());
        }
        apply(s, req);
        return mapper.toResponse(s);
    }

    @Transactional
    public ServiceResponse patch(UUID id, PatchServiceRequest req) {
        ServiceEntity s = find(id);
        if (req.name() != null && !req.name().equals(s.getName())) {
            if (repo.existsByName(req.name())) {
                throw new ConflictException("Service name already exists: " + req.name());
            }
            s.setName(req.name());
        }
        if (req.description() != null) {
            s.setDescription(req.description());
        }
        if (req.pricePerMessage() != null) {
            s.setPricePerMessage(req.pricePerMessage());
        }
        if (req.active() != null) {
            s.setActive(req.active());
        }
        return mapper.toResponse(s);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw NotFoundException.of("Service", id);
        }
        repo.deleteById(id);
    }

    private ServiceEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() -> NotFoundException.of("Service", id));
    }

    private void apply(ServiceEntity s, CreateServiceRequest req) {
        s.setName(req.name());
        s.setDescription(req.description());
        s.setPricePerMessage(req.pricePerMessage());
        s.setActive(req.active() == null || req.active());
    }
}
