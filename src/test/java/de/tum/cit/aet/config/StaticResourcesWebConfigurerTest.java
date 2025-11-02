package de.tum.cit.aet.config;

import static de.tum.cit.aet.config.StaticResourcesWebConfiguration.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

class StaticResourcesWebConfigurerTest {

    public static final int MAX_AGE_TEST = 5;
    public StaticResourcesWebConfiguration staticResourcesWebConfiguration;
    private ResourceHandlerRegistry resourceHandlerRegistry;

    @BeforeEach
    void setUp() {
        MockServletContext servletContext = spy(new MockServletContext());
        WebApplicationContext applicationContext = mock(WebApplicationContext.class);
        resourceHandlerRegistry = spy(new ResourceHandlerRegistry(applicationContext, servletContext));
        staticResourcesWebConfiguration = spy(new StaticResourcesWebConfiguration());
    }

    @Test
    void shouldAppendResourceHandlerAndInitializeIt() {
        staticResourcesWebConfiguration.addResourceHandlers(resourceHandlerRegistry);

        verify(resourceHandlerRegistry, times(1)).addResourceHandler(RESOURCE_PATHS);
        verify(staticResourcesWebConfiguration, times(1)).initializeResourceHandler(any(ResourceHandlerRegistration.class));
        for (String testingPath : RESOURCE_PATHS) {
            assertThat(resourceHandlerRegistry.hasMappingForPattern(testingPath)).isTrue();
        }
    }

    @Test
    void shouldInitializeResourceHandlerWithCacheControlAndLocations() {
        CacheControl ccExpected = CacheControl.maxAge(5, TimeUnit.DAYS).cachePublic();
        when(staticResourcesWebConfiguration.getCacheControl()).thenReturn(ccExpected);
        ResourceHandlerRegistration resourceHandlerRegistration = spy(new ResourceHandlerRegistration(RESOURCE_PATHS));

        staticResourcesWebConfiguration.initializeResourceHandler(resourceHandlerRegistration);

        verify(staticResourcesWebConfiguration, times(1)).getCacheControl();
        verify(resourceHandlerRegistration, times(1)).setCacheControl(ccExpected);
        verify(resourceHandlerRegistration, times(1)).addResourceLocations(RESOURCE_LOCATIONS);
    }

    @Test
    void shouldCreateCacheControlBasedOnDefaultProperties() {
        CacheControl cacheExpected = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic();
        assertThat(staticResourcesWebConfiguration.getCacheControl())
            .extracting(CacheControl::getHeaderValue)
            .isEqualTo(cacheExpected.getHeaderValue());
    }
}
