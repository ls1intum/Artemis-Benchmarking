package de.tum.cit.aet.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Builds a {@link SecurityContext} holding a {@link JwtAuthenticationToken} for {@link WithMockJwt}.
 */
public class WithMockJwtSecurityContextFactory implements WithSecurityContextFactory<WithMockJwt> {

    @Override
    public SecurityContext createSecurityContext(WithMockJwt annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<GrantedAuthority> authorities = Arrays.stream(annotation.authorities())
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(annotation.value())
            .claim(SecurityUtils.AUTHORITIES_KEY, annotation.authorities())
            .build();
        context.setAuthentication(new JwtAuthenticationToken(jwt, authorities, annotation.value()));
        return context;
    }
}
