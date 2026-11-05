package com.example.spamer.catalog.mapper;

import com.example.spamer.catalog.domain.ServiceEntity;
import com.example.spamer.catalog.dto.ServiceResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceMapper {

    ServiceResponse toResponse(ServiceEntity entity);
}
