package de.tum.cit.aet.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;

/**
 * Bridges {@link de.tum.cit.aet.security.WithMockJwt} (and any test that sets a
 * {@link JwtAuthenticationToken} on the {@link TestSecurityContextHolder}) into the MockMvc
 * request via the {@code jwt()} request post-processor.
 *
 * <p>The application is a stateless OAuth2 resource server, so a security context set purely on the
 * holder (as {@code @WithMockUser}/{@code @WithSecurityContext} do) is not honored by the filter
 * chain — only the {@code jwt()} request post-processor is. Applying it as a MockMvc default request
 * makes annotation-driven authentication work without per-request boilerplate.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockMvcJwtTestConfiguration {

    @Bean
    MockMvcBuilderCustomizer jwtAuthenticationMockMvcCustomizer() {
        return builder ->
            builder.defaultRequest(
                get("/").with(request -> {
                    Authentication authentication = TestSecurityContextHolder.getContext().getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                        return jwt()
                            .jwt(jwtAuthentication.getToken())
                            .authorities(jwtAuthentication.getAuthorities())
                            .postProcessRequest(request);
                    }
                    return request;
                })
            );
    }
}
