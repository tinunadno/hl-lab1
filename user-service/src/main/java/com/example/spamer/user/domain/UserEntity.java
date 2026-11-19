package com.example.spamer.user.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("users")
public class UserEntity {

    @Id
    private UUID id;

    private String username;

    private String email;


    private UserRole role;

    private BigDecimal balance = BigDecimal.ZERO;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
