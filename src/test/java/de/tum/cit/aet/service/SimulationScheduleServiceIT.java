package de.tum.cit.aet.service;

import static de.tum.cit.aet.domain.SimulationSchedule.Cycle.DAILY;
import static de.tum.cit.aet.domain.SimulationSchedule.Cycle.WEEKLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.domain.Simulation;
import de.tum.cit.aet.domain.SimulationSchedule;
import de.tum.cit.aet.repository.SimulationScheduleRepository;
import de.tum.cit.aet.service.simulation.SimulationDataService;
import de.tum.cit.aet.service.simulation.SimulationScheduleService;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@IntegrationTest
@Transactional
public class SimulationScheduleServiceIT {

    @Autowired
    private SimulationScheduleService simulationScheduleService;

    @MockitoBean
    private SimulationScheduleRepository simulationScheduleRepository;

    @MockitoBean
    private SimulationDataService simulationDataService;

    /**
     * The instant every test in this class reasons from: a Wednesday at midday UTC.
     * <p>
     * Fixed rather than taken from the real clock because these tests describe behaviour in terms of "later today",
     * "tomorrow" and "next week". Expressed as an offset from whatever time the suite happens to run, those phrases
     * stop being true near midnight - "an hour from now" at 23:30 is tomorrow - and four of these tests failed every
     * night between 23:00 and 01:00 UTC. Midday leaves twelve hours of headroom either way.
     */
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 3, 18, 12, 0, 0, 0, ZoneOffset.UTC);

    @MockitoBean
    private Clock clock;

    private Simulation simulation;
    private SimulationSchedule simulationSchedule;

    @BeforeEach
    public void setUp() {
        when(clock.instant()).thenReturn(NOW.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        simulation = new Simulation();
        simulation.setId(1L);

        when(simulationScheduleRepository.save(any())).thenAnswer(invocation -> {
            var schedule = invocation.getArgument(0, SimulationSchedule.class);
            schedule.setId(1L);
            return schedule;
        });

        doNothing().when(simulationScheduleRepository).delete(any());

        when(simulationDataService.getSimulation(1L)).thenReturn(simulation);

        when(simulationDataService.createAndQueueSimulationRun(anyLong(), any(), any())).thenReturn(null);
    }

    @Test
    public void testCreateSimulationSchedule_success_dailyLaterToday() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.plusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusHours(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_dailyTomorrow() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(2));

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusDays(1).minusHours(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyToday() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(WEEKLY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(10));
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.plusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusHours(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyNextWeek() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(WEEKLY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(10));
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.minusHours(1).plusWeeks(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyTomorrow() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(WEEKLY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(10));
        schedule.setDayOfWeek(NOW.plusDays(1).getDayOfWeek());

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time);

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertEquals(time.plusDays(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        verify(simulationScheduleRepository, times(1)).save(result);
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_success_weeklyTimeOver() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(WEEKLY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(6));
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        ZonedDateTime time = NOW;
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
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1).minusHours(2));

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.minusHours(1));

        var result = simulationScheduleService.createSimulationSchedule(1L, schedule);
        assertNull(result);
        verify(simulationScheduleRepository, times(1)).delete(any());
        verify(simulationDataService, times(1)).getSimulation(1L);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testCreateSimulationSchedule_fail_onScheduleNull() {
        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, null));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onIdSet() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());
        schedule.setId(1L);

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onSimulationSet() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());
        schedule.setSimulation(simulation);

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onStartTimeNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(null);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onEndBeforeStart() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.minusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onCycleNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(null);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onAlreadyOver() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW.minusDays(2));
        schedule.setEndDateTime(NOW.minusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onTimeOfDayNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(DAILY);
        schedule.setTimeOfDay(null);
        schedule.setDayOfWeek(NOW.getDayOfWeek());

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testCreateSimulationSchedule_fail_onDayOfWeekNull() {
        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setCycle(WEEKLY);
        schedule.setTimeOfDay(NOW);
        schedule.setDayOfWeek(null);

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.createSimulationSchedule(1L, schedule));
        verifyNoMoreInteractions(simulationDataService);
        verify(simulationScheduleRepository, times(0)).save(any());
    }

    @Test
    public void testUpdateSimulationSchedule_success_dailyLaterToday() {
        SimulationSchedule existingSchedule = new SimulationSchedule();
        existingSchedule.setCycle(WEEKLY);
        existingSchedule.setStartDateTime(NOW);
        existingSchedule.setEndDateTime(NOW.plusDays(10));
        existingSchedule.setDayOfWeek(NOW.getDayOfWeek());
        existingSchedule.setTimeOfDay(NOW);
        existingSchedule.setId(1L);
        existingSchedule.setSimulation(simulation);

        when(simulationScheduleRepository.findById(1L)).thenReturn(java.util.Optional.of(existingSchedule));

        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setId(1L);
        schedule.setSimulation(null);

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.plusHours(1));

        var result = simulationScheduleService.updateSimulationSchedule(1L, schedule);
        assertEquals(time.plusHours(1), result.getNextRun());
        assertEquals(simulation, result.getSimulation());
        assertEquals(DAILY, result.getCycle());
        verify(simulationScheduleRepository, times(1)).save(result);
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testUpdateSimulationSchedule_fail_onScheduleNull() {
        SimulationSchedule existingSchedule = new SimulationSchedule();
        existingSchedule.setCycle(WEEKLY);
        existingSchedule.setStartDateTime(NOW);
        existingSchedule.setEndDateTime(NOW.plusDays(10));
        existingSchedule.setDayOfWeek(NOW.getDayOfWeek());
        existingSchedule.setTimeOfDay(NOW);
        existingSchedule.setId(1L);
        existingSchedule.setSimulation(simulation);

        when(simulationScheduleRepository.findById(1L)).thenReturn(java.util.Optional.of(existingSchedule));

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.updateSimulationSchedule(1L, null));
        verify(simulationScheduleRepository, times(0)).save(any());
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testUpdateSimulationSchedule_fail_onIdWrong() {
        SimulationSchedule existingSchedule = new SimulationSchedule();
        existingSchedule.setCycle(WEEKLY);
        existingSchedule.setStartDateTime(NOW);
        existingSchedule.setEndDateTime(NOW.plusDays(10));
        existingSchedule.setDayOfWeek(NOW.getDayOfWeek());
        existingSchedule.setTimeOfDay(NOW);
        existingSchedule.setId(1L);
        existingSchedule.setSimulation(simulation);

        when(simulationScheduleRepository.findById(1L)).thenReturn(java.util.Optional.of(existingSchedule));

        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setId(1L);
        schedule.setSimulation(null);

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.plusHours(1));

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.updateSimulationSchedule(42L, schedule));
        verify(simulationScheduleRepository, times(0)).save(any());
        verifyNoMoreInteractions(simulationDataService);
    }

    @Test
    public void testUpdateSimulationSchedule_fail_onSimulationSet() {
        SimulationSchedule existingSchedule = new SimulationSchedule();
        existingSchedule.setCycle(WEEKLY);
        existingSchedule.setStartDateTime(NOW);
        existingSchedule.setEndDateTime(NOW.plusDays(10));
        existingSchedule.setDayOfWeek(NOW.getDayOfWeek());
        existingSchedule.setTimeOfDay(NOW);
        existingSchedule.setId(1L);
        existingSchedule.setSimulation(simulation);

        when(simulationScheduleRepository.findById(1L)).thenReturn(java.util.Optional.of(existingSchedule));

        SimulationSchedule schedule = new SimulationSchedule();
        schedule.setCycle(DAILY);
        schedule.setStartDateTime(NOW);
        schedule.setEndDateTime(NOW.plusDays(1));
        schedule.setId(1L);
        schedule.setSimulation(new Simulation());

        ZonedDateTime time = NOW;
        schedule.setTimeOfDay(time.plusHours(1));

        assertThrows(ResponseStatusException.class, () -> simulationScheduleService.updateSimulationSchedule(1L, schedule));
        verify(simulationScheduleRepository, times(0)).save(any());
        verifyNoMoreInteractions(simulationDataService);
    }
}
