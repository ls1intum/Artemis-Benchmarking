package de.tum.cit.ase.service;

import static de.tum.cit.ase.util.ArtemisServer.PRODUCTION;
import static de.tum.cit.ase.util.ArtemisServer.TS1;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import de.tum.cit.ase.IntegrationTest;
import de.tum.cit.ase.domain.Simulation;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.LogMessageRepository;
import de.tum.cit.ase.repository.SimulationRepository;
import de.tum.cit.ase.repository.SimulationRunRepository;
import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.simulation.SimulationDataService;
import de.tum.cit.ase.service.simulation.SimulationRunQueueService;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@IntegrationTest
@Transactional
public class SimulationDataServiceIT {

    @Autowired
    @SpyBean
    private SimulationDataService simulationDataService;

    @MockBean
    private SimulationRunQueueService simulationRunQueueService;

    @MockBean
    private SimulationWebsocketService simulationWebsocketService;

    @MockBean
    private SimulationRunRepository simulationRunRepository;

    @MockBean
    private SimulationRepository simulationRepository;

    @MockBean
    private LogMessageRepository logMessageRepository;

    @MockBean
    private ArtemisConfiguration artemisConfiguration;

    private Simulation simulation;

    @BeforeEach
    public void init() {
        simulation = new Simulation();
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfUsers(10);
        simulation.setServer(TS1);
        simulation.setUserRange("4-17");
        simulation.setCustomizeUserRange(false);

        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> {
                var simulation = invocation.getArgument(0, Simulation.class);
                simulation.setId(1L);
                return simulation;
            });

        when(simulationRunRepository.save(any(SimulationRun.class)))
            .thenAnswer(invocation -> {
                var runArg = invocation.getArgument(0, SimulationRun.class);
                runArg.setId(1L);
                return runArg;
            });

        doNothing().when(simulationRunQueueService).queueSimulationRun(any());
        doNothing().when(simulationWebsocketService).sendRunStatusUpdate(any());
    }

    @Test
    public void testCreateSimulation_noCustomRange() {
        var sim = simulationDataService.createSimulation(simulation);

        assertEquals(1L, sim.getId());
        assertNotNull(sim.getCreationDate());
        assertEquals(10, sim.getNumberOfUsers());
        verify(simulationRepository).save(any(Simulation.class));
    }

    @Test
    public void testCreateSimulation_customRange() {
        simulation.setCustomizeUserRange(true);

        var sim = simulationDataService.createSimulation(simulation);

        assertEquals(1L, sim.getId());
        assertNotNull(sim.getCreationDate());
        assertEquals(14, sim.getNumberOfUsers());
        verify(simulationRepository).save(any(Simulation.class));
    }

    @Test
    public void testDeleteSimulation_success() {
        doNothing().when(simulationRepository).deleteById(anyLong());
        when(simulationRunRepository.findAllBySimulationId(1L)).thenReturn(List.of());
        simulationDataService.deleteSimulation(1L);
        verify(simulationRepository).deleteById(1L);
    }

    @Test
    public void testDeleteSimulation_throwOnRunning() {
        var run1 = new SimulationRun();
        run1.setStatus(SimulationRun.Status.RUNNING);

        var run2 = new SimulationRun();
        run2.setStatus(SimulationRun.Status.FINISHED);

        when(simulationRunRepository.findAllBySimulationId(1L)).thenReturn(List.of(run1, run2));
        doNothing().when(simulationRepository).deleteById(anyLong());

        assertThrows(IllegalArgumentException.class, () -> simulationDataService.deleteSimulation(1L));
        verify(simulationRepository, times(0)).deleteById(anyLong());
    }

    @Test
    public void createAndQueueSimulationRun_success() {
        simulation.setId(1L);
        when(simulationRepository.findById(1L)).thenReturn(java.util.Optional.of(simulation));

        when(simulationRunRepository.save(any(SimulationRun.class)))
            .thenAnswer(invocation -> {
                var runArg = invocation.getArgument(0, SimulationRun.class);
                runArg.setId(1L);
                return runArg;
            });

        var queuedRun = simulationDataService.createAndQueueSimulationRun(1L, null);

        assertEquals(1L, queuedRun.getId());
        verify(simulationRunRepository).save(queuedRun);
        verify(simulationRunQueueService).queueSimulationRun(queuedRun);
        verify(simulationWebsocketService).sendRunStatusUpdate(queuedRun);
    }

    @Test
    public void createAndQueueSimulationRun_fail_prodWithoutAccount() {
        simulation.setId(1L);
        simulation.setServer(PRODUCTION);
        when(simulationRepository.findById(1L)).thenReturn(java.util.Optional.of(simulation));

        when(simulationRunRepository.save(any(SimulationRun.class)))
            .thenAnswer(invocation -> {
                var runArg = invocation.getArgument(0, SimulationRun.class);
                runArg.setId(1L);
                return runArg;
            });
        assertThrows(IllegalArgumentException.class, () -> simulationDataService.createAndQueueSimulationRun(1L, null));

        verify(simulationRunRepository, times(0)).save(any());
        verify(simulationRunQueueService, times(0)).queueSimulationRun(any());
        verify(simulationWebsocketService, times(0)).sendRunStatusUpdate(any());
    }
}
