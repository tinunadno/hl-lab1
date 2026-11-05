package com.example.spamer.catalog.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {

    boolean existsByName(String name);
}
