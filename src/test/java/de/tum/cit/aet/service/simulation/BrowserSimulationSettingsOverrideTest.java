package de.tum.cit.aet.service.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tum.cit.aet.domain.Simulation;
import de.tum.cit.aet.service.artemis.interaction.browser.BrowserSimulationSettings;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * How much of the client bundle a run downloads is a property of the exam being modelled, not of the host the tool
 * runs on, so a simulation may override the server's configured cold-cache share. A simulation that does not set one
 * has to keep behaving exactly as it did before the column existed, which is what the null case pins down.
 */
class BrowserSimulationSettingsOverrideTest {

    private static final int SERVER_DEFAULT = 100;

    private BrowserSimulationSettings settingsFor(Integer simulationOverride) {
        SimulationExecutionService service = new SimulationExecutionService(null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "staticResourcesEnabled", true);
        ReflectionTestUtils.setField(service, "coldCachePercentage", SERVER_DEFAULT);
        ReflectionTestUtils.setField(service, "maxAssets", BrowserSimulationSettings.DEFAULT_MAX_ASSETS);
        ReflectionTestUtils.setField(service, "fetchConcurrency", BrowserSimulationSettings.DEFAULT_FETCH_CONCURRENCY);
        ReflectionTestUtils.setField(service, "autoSavesPerExercise", BrowserSimulationSettings.DEFAULT_AUTO_SAVES_PER_EXERCISE);
        ReflectionTestUtils.setField(service, "assetsPerNavigation", BrowserSimulationSettings.DEFAULT_ASSETS_PER_NAVIGATION);
        ReflectionTestUtils.setField(service, "exerciseSkipPercentage", BrowserSimulationSettings.DEFAULT_EXERCISE_SKIP_PERCENTAGE);
        ReflectionTestUtils.setField(
            service,
            "serverTimeCallsPerNavigation",
            BrowserSimulationSettings.DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION
        );

        Simulation simulation = new Simulation();
        simulation.setColdCachePercentage(simulationOverride);

        return (BrowserSimulationSettings) ReflectionTestUtils.invokeMethod(service, "browserSimulationSettings", simulation);
    }

    @Test
    void aSimulationWithoutAnOverrideUsesTheServerDefault() {
        assertEquals(SERVER_DEFAULT, settingsFor(null).coldCachePercentage());
    }

    @Test
    void aSimulationOverridesTheServerDefault() {
        assertEquals(5, settingsFor(5).coldCachePercentage());
    }

    @Test
    void anOverrideOfZeroIsHonouredRatherThanTreatedAsUnset() {
        // The distinction matters: 0 means "every student arrives warm", which is a legitimate setting and must not
        // silently fall back to the default the way a null does.
        assertEquals(0, settingsFor(0).coldCachePercentage());
    }
}
