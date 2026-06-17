package de.tum.cit.aet;

import de.tum.cit.aet.config.AsyncSyncConfiguration;
import de.tum.cit.aet.config.EmbeddedSQL;
import de.tum.cit.aet.config.MockMvcJwtTestConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base composite annotation for integration tests.
 *
 * <p>The {@code testprod} profile supplies the Testcontainers datasource/JPA configuration
 * (see {@code src/test/resources/config/application-testprod.yml}); the SQL container itself
 * is started by {@link de.tum.cit.aet.config.SqlTestContainersSpringContextCustomizerFactory}
 * via {@link EmbeddedSQL}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = { ArtemisBenchmarkingApp.class, AsyncSyncConfiguration.class, MockMvcJwtTestConfiguration.class })
@EmbeddedSQL
@ActiveProfiles("testprod")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface IntegrationTest {}
