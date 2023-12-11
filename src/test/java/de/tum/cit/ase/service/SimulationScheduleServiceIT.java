package de.tum.cit.ase.service;

import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import de.tum.cit.ase.IntegrationTest;
import de.tum.cit.ase.domain.Simulation;
import de.tum.cit.ase.domain.SimulationSchedule;
import de.tum.cit.ase.repository.SimulationScheduleRepository;
import de.tum.cit.ase.service.simulation.SimulationDataService;
import de.tum.cit.ase.service.simulation.SimulationScheduleService;
import jakarta.transaction.Transactional;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@IntegrationTest
@Transactional
public class SimulationScheduleServiceIT {

    @Autowired
    private SimulationScheduleService simulationScheduleService;

    @MockBean
    private SimulationScheduleRepository simulationScheduleRepository;

    @MockBean
    private SimulationDataService simulationDataService;

    private Simulation simulation;
    private SimulationSchedule simulationSchedule;

    @BeforeEach
    public void setUp() {
        simulation = new Simulation();
        simulation.setId(1L);

        when(simulationScheduleRepository.save(any()))
            .thenAnswer(invocation -> {
                var schedule = invocation.getArgument(0, SimulationSchedule.class);
                schedule.setId(1L);
                return schedule;
            });

        doNothing().when(simulationScheduleRepository).delete(any());

        when(simulationDataService.getSimulation(1L)).thenReturn(simulation);

        when(simulationDataService.createAndQueueSimulationRun(anyLong(), any())).thenReturn(null);
    }

    @Test
    public void testCreateSimulationSchedule_success_dailyLaterToday() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.plusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusHours(1), result.getNextRun());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_dailyTomorrow() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(2));

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusDays(1).minusHours(1), result.getNextRun());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyToday() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.WEEKLY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(10));
        schedule.setDayOfWeek(now().getDayOfWeek());

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.plusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusHours(1), result.getNextRun());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyNextWeek() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.WEEKLY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(10));
        schedule.setDayOfWeek(now().getDayOfWeek());

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.minusHours(1).plusWeeks(1), result.getNextRun());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyTomorrow() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.WEEKLY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(10));
        schedule.setDayOfWeek(now().plusDays(1).getDayOfWeek());

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time);

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusDays(1), result.getNextRun());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyTimeOver() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.WEEKLY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(6));
        schedule.setDayOfWeek(now().getDayOfWeek());

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertNull(result);
        verify(simulationScheduleRepository, times(1)).delete(any());
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_dailyTimeOver() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1).minusHours(2));

        ZonedDateTime time = now();
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertNull(result);
        verify(simulationScheduleRepository, times(1)).delete(any());
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_fail_onScheduleNull() {
        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, null));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onIdSet() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());
        schedule.setId(1L);

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onSimulationSet() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());
        schedule.setSimulation(simulation);

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onStartTimeNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(null);
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onEndBeforeStart() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().minusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onCycleNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(null);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onAlreadyOver() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now().minusDays(2));
        schedule.setEndDateTime(now().minusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(now().getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onTimeOfDayNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.DAILY);
        schedule.setTimeOfDay(null);
        schedule.setDayOfWeek(now().getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onDayOfWeekNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(now());
        schedule.setEndDateTime(now().plusDays(1));
        schedule.setCycle(SimulationSchedule.Cycle.WEEKLY);
        schedule.setTimeOfDay(now());
        schedule.setDayOfWeek(null);

        assertThrows(IllegalArgumentException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }
}
