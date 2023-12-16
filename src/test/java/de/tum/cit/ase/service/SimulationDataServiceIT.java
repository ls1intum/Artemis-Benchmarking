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
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
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
    public void testDeleteSimulationRun_success() {
        var run = new SimulationRun();
        run.setStatus(SimulationRun.Status.FINISHED);

        doNothing().when(simulationRunRepository).deleteById(anyLong());
        when(simulationRunRepository.findById(1L)).thenReturn(java.util.Optional.of(run));
        simulationDataService.deleteSimulationRun(1L);
        verify(simulationRunRepository).deleteById(1L);
    }

    @Test
    public void testDeleteSimulationRun_throwOnRunning() {
        var run = new SimulationRun();
        run.setStatus(SimulationRun.Status.RUNNING);

        doNothing().when(simulationRunRepository).deleteById(anyLong());
        when(simulationRunRepository.findById(1L)).thenReturn(java.util.Optional.of(run));
        assertThrows(IllegalArgumentException.class, () -> simulationDataService.deleteSimulationRun(1L));
        verify(simulationRunRepository, times(0)).deleteById(anyLong());
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

        var queuedRun = simulationDataService.createAndQueueSimulationRun(1L, null, null);

        assertEquals(1L, queuedRun.getId());
        verify(simulationRunRepository).save(queuedRun);
        verify(simulationRunQueueService).queueSimulationRun(queuedRun);
        verify(simulationWebsocketService).sendNewRun(queuedRun);
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
        assertThrows(IllegalArgumentException.class, () -> simulationDataService.createAndQueueSimulationRun(1L, null, null));

        verify(simulationRunRepository, times(0)).save(any());
        verify(simulationRunQueueService, times(0)).queueSimulationRun(any());
        verify(simulationWebsocketService, times(0)).sendRunStatusUpdate(any());
    }

    @Test
    public void testValidateSimulation_success_fixedUsers() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertTrue(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_success_userRange() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(-1);
        simulation.setCustomizeUserRange(true);
        simulation.setUserRange("2-5,7,10-13");
        simulation.setName("Test");
        assertTrue(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onMode() {
        simulation.setServer(TS1);
        simulation.setMode(null);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onServer() {
        simulation.setServer(null);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onName() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName(null);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onPushesSmaller() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(2);
        simulation.setNumberOfCommitsAndPushesTo(1);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onPushesNegative() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(-1);
        simulation.setNumberOfCommitsAndPushesTo(1);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onUsersZero() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(2);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(0);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_fail_onCustomRange() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(2);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(-1);
        simulation.setCustomizeUserRange(true);
        simulation.setUserRange("1--2");
        simulation.setName("Test");
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCourseUnpreparedExam_success() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(1L);
        simulation.setExamId(1L);
        assertTrue(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCoursePreparedExam_success() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(1L);
        simulation.setExamId(1L);
        assertTrue(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCourseUnpreparedExam_fail_onCourseId() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(0);
        simulation.setExamId(1L);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCoursePreparedExam_fail_onCourseId() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(0);
        simulation.setExamId(1L);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCourseUnpreparedExam_fail_onExamId() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(1);
        simulation.setExamId(0);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCoursePreparedExam_fail_onExamId() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(1);
        simulation.setExamId(0);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCourseCreateExam_success() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(1L);
        simulation.setExamId(0);
        assertTrue(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void testValidateSimulation_existingCourseCreateExam_fail_onCourseId() {
        simulation.setServer(TS1);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setNumberOfCommitsAndPushesFrom(1);
        simulation.setNumberOfCommitsAndPushesTo(4);
        simulation.setNumberOfUsers(10);
        simulation.setCustomizeUserRange(false);
        simulation.setName("Test");
        simulation.setCourseId(0);
        simulation.setExamId(0);
        assertFalse(simulationDataService.validateSimulation(simulation));
    }

    @Test
    public void cancelActiveRun_success() {
        var run = new SimulationRun();
        run.setLogMessages(new HashSet<>());
        run.setStatus(SimulationRun.Status.RUNNING);

        when(simulationRunRepository.findById(1L)).thenReturn(java.util.Optional.of(run));
        doNothing().when(simulationRunQueueService).abortSimulationExecution();
        doNothing().when(simulationWebsocketService).sendRunStatusUpdate(any());
        doNothing().when(simulationWebsocketService).sendRunLogMessage(any(), any());

        simulationDataService.cancelActiveRun(1L);

        verify(simulationRunQueueService).abortSimulationExecution();
        verify(simulationWebsocketService).sendRunStatusUpdate(run);
        verify(simulationWebsocketService).sendRunLogMessage(eq(run), any());
        verify(simulationRunRepository).save(run);
        verify(logMessageRepository).save(any());
        verify(simulationRunQueueService).restartSimulationExecution();
        assertEquals(SimulationRun.Status.CANCELLED, run.getStatus());
    }

    @Test
    public void cancelActiveRun_fail_notFound() {
        when(simulationRunRepository.findById(1L)).thenReturn(java.util.Optional.empty());
        assertThrows(NoSuchElementException.class, () -> simulationDataService.cancelActiveRun(1L));
    }

    @Test
    public void cancelActiveRun_fail_notRunning() {
        var run = new SimulationRun();
        run.setLogMessages(new HashSet<>());
        run.setStatus(SimulationRun.Status.FINISHED);

        when(simulationRunRepository.findById(1L)).thenReturn(java.util.Optional.of(run));
        assertThrows(IllegalArgumentException.class, () -> simulationDataService.cancelActiveRun(1L));
    }
}
