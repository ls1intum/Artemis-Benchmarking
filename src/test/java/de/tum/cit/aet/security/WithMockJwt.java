package de.tum.cit.aet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Test annotation that authenticates the security context with a {@link org.springframework.security.oauth2.jwt.Jwt}
 * based {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}.
 *
 * <p>The application secures its API as an OAuth2 resource server ({@code oauth2ResourceServer().jwt()}), so the
 * stateless filter chain only accepts JWT authentication. The standard {@code @WithMockUser} sets up a
 * {@code UsernamePasswordAuthenticationToken}, which this app's filter chain does not honor — hence this drop-in
 * replacement. The JWT {@code subject} carries the login (see {@link SecurityUtils#getCurrentUserLogin()}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwt {
    /** The login of the mock user; mapped to the JWT {@code sub} claim. */
    String value() default "user";

    /** The granted authorities; defaults to {@code ROLE_USER}. */
    String[] authorities() default { AuthoritiesConstants.USER };
}
