package com.example.spamer.dispatch.mapper;

import com.example.spamer.dispatch.domain.ProxyEntity;
import com.example.spamer.dispatch.dto.ProxyResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProxyMapper {

    ProxyResponse toResponse(ProxyEntity entity);
}
