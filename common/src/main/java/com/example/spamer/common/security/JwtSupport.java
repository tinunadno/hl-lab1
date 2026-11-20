package com.example.spamer.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Сборка кодировщика и декодеров JWT (HS256) и преобразование токена в роли Spring Security.
 * Сервисы на MVC берут {@link #decoder}, сервисы на WebFlux — {@link #reactiveDecoder}.
 */
public final class JwtSupport {

    public static final String ROLE_CLAIM = "role";
    public static final String USERNAME_CLAIM = "username";
    public static final String ROLE_PREFIX = "ROLE_";

    private JwtSupport() {
    }

    public static SecretKey secretKey(JwtProperties props) {
        return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static JwtEncoder encoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    public static JwtDecoder decoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(validator(props));
        return decoder;
    }

    public static ReactiveJwtDecoder reactiveDecoder(JwtProperties props) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(validator(props));
        return decoder;
    }

    /** Claim {@code role} становится единственной authority {@code ROLE_<role>}. */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtSupport::authorities);
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.<GrantedAuthority>of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
    }

    private static OAuth2TokenValidator<Jwt> validator(JwtProperties props) {
        // срок действия + совпадение издателя
        return JwtValidators.createDefaultWithIssuer(props.issuer());
    }
}
