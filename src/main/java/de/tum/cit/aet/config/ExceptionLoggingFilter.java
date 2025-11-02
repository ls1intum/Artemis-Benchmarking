package de.tum.cit.aet.config;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ExceptionLoggingFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(ExceptionLoggingFilter.class);

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse res, @Nonnull FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        }
        catch (Throwable t) {
            log.error("Request failed: {} {} -> {}", req.getMethod(), req.getRequestURI(), t, t);
            throw t;
        }
    }
}
