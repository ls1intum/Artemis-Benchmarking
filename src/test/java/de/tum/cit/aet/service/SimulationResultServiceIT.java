package de.tum.cit.aet.service;

import static de.tum.cit.aet.domain.RequestType.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.repository.SimulationStatsRepository;
import de.tum.cit.aet.repository.StatsByMinuteRepository;
import de.tum.cit.aet.repository.StatsBySecondRepository;
import de.tum.cit.aet.service.simulation.SimulationResultService;
import jakarta.transaction.Transactional;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@Transactional
public class SimulationResultServiceIT {

    @Autowired
    private SimulationResultService simulationResultService;

    @MockitoBean
    private SimulationStatsRepository simulationStatsRepository;

    @MockitoBean
    private StatsByMinuteRepository statsByMinuteRepository;

    @MockitoBean
    private StatsBySecondRepository statsBySecondRepository;

    private List<RequestStat> requestStats;

    private SimulationRun simulationRun;

    private ZonedDateTime nowMinute;

    @BeforeEach
    void setUp() {
        nowMinute = now().truncatedTo(ChronoUnit.MINUTES);
        requestStats = List.of(
            new RequestStat(nowMinute, 100L, AUTHENTICATION),
            new RequestStat(nowMinute, 120L, AUTHENTICATION),
            new RequestStat(nowMinute.plus(20, ChronoUnit.MILLIS), 200L, AUTHENTICATION),
            new RequestStat(nowMinute.plus(25, ChronoUnit.MILLIS), 180L, AUTHENTICATION),
            new RequestStat(nowMinute.plus(30, ChronoUnit.MILLIS), 150L, AUTHENTICATION),
            new RequestStat(nowMinute.plusMinutes(1).plus(100, ChronoUnit.MILLIS), 240L, GET_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(1).plus(110, ChronoUnit.MILLIS), 220L, GET_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(1).plus(110, ChronoUnit.MILLIS), 200L, GET_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(1).plus(130, ChronoUnit.MILLIS), 180L, GET_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(1).plus(130, ChronoUnit.MILLIS), 160L, GET_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(2).plus(200, ChronoUnit.MILLIS), 300L, START_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(2).plus(210, ChronoUnit.MILLIS), 280L, START_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(2).plus(210, ChronoUnit.MILLIS), 260L, START_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(2).plus(220, ChronoUnit.MILLIS), 240L, START_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(2).plus(220, ChronoUnit.MILLIS), 220L, START_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(3).plus(300, ChronoUnit.MILLIS), 340L, SUBMIT_EXERCISE),
            new RequestStat(nowMinute.plusMinutes(3).plus(310, ChronoUnit.MILLIS), 320L, SUBMIT_EXERCISE),
            new RequestStat(nowMinute.plusMinutes(3).plus(310, ChronoUnit.MILLIS), 300L, SUBMIT_EXERCISE),
            new RequestStat(nowMinute.plusMinutes(3).plus(320, ChronoUnit.MILLIS), 280L, SUBMIT_EXERCISE),
            new RequestStat(nowMinute.plusMinutes(3).plus(320, ChronoUnit.MILLIS), 260L, SUBMIT_EXERCISE),
            new RequestStat(nowMinute.plusMinutes(4).plus(400, ChronoUnit.MILLIS), 380L, SUBMIT_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(4).plus(410, ChronoUnit.MILLIS), 360L, SUBMIT_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(4).plus(410, ChronoUnit.MILLIS), 340L, SUBMIT_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(5).plus(420, ChronoUnit.MILLIS), 320L, SUBMIT_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(5).plus(420, ChronoUnit.MILLIS), 300L, SUBMIT_STUDENT_EXAM),
            new RequestStat(nowMinute.plusMinutes(5).plus(500, ChronoUnit.MILLIS), 440L, CLONE_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(5).plus(510, ChronoUnit.MILLIS), 420L, CLONE_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(5).plus(510, ChronoUnit.MILLIS), 400L, CLONE_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(5).plus(520, ChronoUnit.MILLIS), 380L, CLONE_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(5).plus(520, ChronoUnit.MILLIS), 360L, CLONE_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(6).plus(600, ChronoUnit.MILLIS), 500L, PUSH_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(6).plus(610, ChronoUnit.MILLIS), 480L, PUSH_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(6).plus(610, ChronoUnit.MILLIS), 460L, PUSH_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(6).plus(620, ChronoUnit.MILLIS), 440L, PUSH_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(6).plus(620, ChronoUnit.MILLIS), 420L, PUSH_PASSWORD),
            new RequestStat(nowMinute.plusMinutes(7).plus(700, ChronoUnit.MILLIS), 560L, MISC),
            new RequestStat(nowMinute.plusMinutes(7).plus(710, ChronoUnit.MILLIS), 540L, MISC),
            new RequestStat(nowMinute.plusMinutes(7).plus(710, ChronoUnit.MILLIS), 520L, MISC),
            new RequestStat(nowMinute.plusMinutes(7).plus(720, ChronoUnit.MILLIS), 500L, MISC),
            new RequestStat(nowMinute.plusMinutes(7).plus(720, ChronoUnit.MILLIS), 480L, MISC)
        );
        simulationRun = new SimulationRun();
    }

    @Test
    public void calculateAndSaveResult() {
        simulationResultService.calculateAndSaveResult(simulationRun, requestStats);
        var totalStats = simulationRun.getStats().stream().filter(stats -> stats.getRequestType() == TOTAL).findFirst().orElseThrow();
        var authStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == AUTHENTICATION)
            .findFirst()
            .orElseThrow();
        var getStudentExamsStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == GET_STUDENT_EXAM)
            .findFirst()
            .orElseThrow();
        var startStudentExamStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == START_STUDENT_EXAM)
            .findFirst()
            .orElseThrow();
        var submitExerciseStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == SUBMIT_EXERCISE)
            .findFirst()
            .orElseThrow();
        var submitStudentExamStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == SUBMIT_STUDENT_EXAM)
            .findFirst()
            .orElseThrow();
        var clonePasswordStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == CLONE_PASSWORD)
            .findFirst()
            .orElseThrow();
        var pushPasswordStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == PUSH_PASSWORD)
            .findFirst()
            .orElseThrow();
        var cloneTokenStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == CLONE_TOKEN)
            .findFirst()
            .orElseThrow();
        var pushTokenStats = simulationRun
            .getStats()
            .stream()
            .filter(stats -> stats.getRequestType() == PUSH_TOKEN)
            .findFirst()
            .orElseThrow();
        simulationRun.getStats().stream().filter(stats -> stats.getRequestType() == CLONE_SSH).findFirst().orElseThrow();
        simulationRun.getStats().stream().filter(stats -> stats.getRequestType() == PUSH_SSH).findFirst().orElseThrow();
        var miscStats = simulationRun.getStats().stream().filter(stats -> stats.getRequestType() == MISC).findFirst().orElseThrow();

        // TOTAL
        assertEquals(40, totalStats.getNumberOfRequests());
        assertEquals(328, totalStats.getAvgResponseTime());
        assertEquals(simulationRun, totalStats.getSimulationRun());
        assertEquals(8, totalStats.getStatsByMinute().size());

        // AUTHENTICATION
        assertEquals(5, authStats.getNumberOfRequests());
        assertEquals(150, authStats.getAvgResponseTime());
        assertEquals(simulationRun, authStats.getSimulationRun());
        assertEquals(1, authStats.getStatsByMinute().size());

        // GET_STUDENT_EXAM
        assertEquals(5, getStudentExamsStats.getNumberOfRequests());
        assertEquals(200, getStudentExamsStats.getAvgResponseTime());
        assertEquals(simulationRun, getStudentExamsStats.getSimulationRun());
        assertEquals(1, getStudentExamsStats.getStatsByMinute().size());

        // START_STUDENT_EXAM
        assertEquals(5, startStudentExamStats.getNumberOfRequests());
        assertEquals(260, startStudentExamStats.getAvgResponseTime());
        assertEquals(simulationRun, startStudentExamStats.getSimulationRun());
        assertEquals(1, startStudentExamStats.getStatsByMinute().size());

        // SUBMIT_EXERCISE
        assertEquals(5, submitExerciseStats.getNumberOfRequests());
        assertEquals(300, submitExerciseStats.getAvgResponseTime());
        assertEquals(simulationRun, submitExerciseStats.getSimulationRun());
        assertEquals(1, submitExerciseStats.getStatsByMinute().size());

        // SUBMIT_STUDENT_EXAM
        assertEquals(5, submitStudentExamStats.getNumberOfRequests());
        assertEquals(340, submitStudentExamStats.getAvgResponseTime());
        assertEquals(simulationRun, submitStudentExamStats.getSimulationRun());
        assertEquals(2, submitStudentExamStats.getStatsByMinute().size());

        var stats1 = submitStudentExamStats
            .getStatsByMinute()
            .stream()
            .filter(stats -> stats.getDateTime().equals(nowMinute.plusMinutes(4).truncatedTo(ChronoUnit.MINUTES)))
            .findFirst()
            .orElseThrow();
        var stats2 = submitStudentExamStats
            .getStatsByMinute()
            .stream()
            .filter(stats -> stats.getDateTime().equals(nowMinute.plusMinutes(5).truncatedTo(ChronoUnit.MINUTES)))
            .findFirst()
            .orElseThrow();

        assertEquals(3, stats1.getNumberOfRequests());
        assertEquals(360, stats1.getAvgResponseTime());
        assertEquals(submitStudentExamStats, stats1.getSimulationStats());

        assertEquals(2, stats2.getNumberOfRequests());
        assertEquals(310, stats2.getAvgResponseTime());
        assertEquals(submitStudentExamStats, stats2.getSimulationStats());

        // CLONE over password
        assertEquals(5, clonePasswordStats.getNumberOfRequests());
        assertEquals(400, clonePasswordStats.getAvgResponseTime());
        assertEquals(simulationRun, clonePasswordStats.getSimulationRun());
        assertEquals(1, clonePasswordStats.getStatsByMinute().size());

        // PUSH over password
        assertEquals(5, pushPasswordStats.getNumberOfRequests());
        assertEquals(460, pushPasswordStats.getAvgResponseTime());
        assertEquals(simulationRun, pushPasswordStats.getSimulationRun());
        assertEquals(1, pushPasswordStats.getStatsByMinute().size());

        // PUSH and CLONE via token
        assertEquals(0, pushTokenStats.getNumberOfRequests());
        assertEquals(0, cloneTokenStats.getNumberOfRequests());

        // MISC
        assertEquals(5, miscStats.getNumberOfRequests());
        assertEquals(520, miscStats.getAvgResponseTime());
        assertEquals(simulationRun, miscStats.getSimulationRun());
        assertEquals(1, miscStats.getStatsByMinute().size());
    }
}
