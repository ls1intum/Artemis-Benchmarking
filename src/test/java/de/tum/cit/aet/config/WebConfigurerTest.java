package de.tum.cit.aet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Unit tests for the {@link WebConfigurer} class.
 */
class WebConfigurerTest {

    private WebConfigurer webConfigurer;

    private CorsConfiguration corsConfiguration;

    private MockEnvironment env;

    @BeforeEach
    public void setup() {
        MockServletContext servletContext = spy(new MockServletContext());
        doReturn(mock(FilterRegistration.Dynamic.class)).when(servletContext).addFilter(anyString(), any(Filter.class));
        doReturn(mock(ServletRegistration.Dynamic.class)).when(servletContext).addServlet(anyString(), any(Servlet.class));

        env = new MockEnvironment();

        webConfigurer = new WebConfigurer(env);
        corsConfiguration = new CorsConfiguration();
    }

    @Test
    void shouldCustomizeServletContainer() {
        env.setActiveProfiles(Constants.SPRING_PROFILE_PRODUCTION);
        TomcatServletWebServerFactory container = new TomcatServletWebServerFactory();
        webConfigurer.customize(container);
        assertThat(container.getSettings().getMimeMappings().get("abs")).isEqualTo("audio/x-mpeg");
        assertThat(container.getSettings().getMimeMappings().get("html")).isEqualTo("text/html");
        assertThat(container.getSettings().getMimeMappings().get("json")).isEqualTo("application/json");
        if (container.getSettings().getDocumentRoot() != null) {
            assertThat(container.getSettings().getDocumentRoot()).isEqualTo(Path.of("build", "resources", "main", "static").toFile());
        }
    }

    @Test
    void shouldCorsFilterOnOtherPath() throws Exception {
        corsConfiguration.setAllowedOrigins(Collections.singletonList("*"));
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        corsConfiguration.setAllowedHeaders(Collections.singletonList("*"));
        corsConfiguration.setMaxAge(1800L);
        corsConfiguration.setAllowCredentials(true);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc
            .perform(get("/test/test-cors").header(HttpHeaders.ORIGIN, "other.domain.com"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldCorsFilterDeactivatedForNullAllowedOrigins() throws Exception {
        corsConfiguration.setAllowedOrigins(null);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc
            .perform(get("/api/test-cors").header(HttpHeaders.ORIGIN, "other.domain.com"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldCorsFilterDeactivatedForEmptyAllowedOrigins() throws Exception {
        corsConfiguration.setAllowedOrigins(new ArrayList<>());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc
            .perform(get("/api/test-cors").header(HttpHeaders.ORIGIN, "other.domain.com"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
