package de.tum.cit.aet.service.simulation;

import de.tum.cit.aet.domain.*;
import de.tum.cit.aet.domain.RequestType;
import de.tum.cit.aet.repository.SimulationStatsRepository;
import de.tum.cit.aet.repository.StatsByMinuteRepository;
import de.tum.cit.aet.repository.StatsBySecondRepository;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SimulationResultService {

    private final SimulationStatsRepository simulationStatsRepository;
    private final StatsByMinuteRepository statsByMinuteRepository;
    private final StatsBySecondRepository statsBySecondRepository;

    public SimulationResultService(
        SimulationStatsRepository simulationStatsRepository,
        StatsByMinuteRepository statsByMinuteRepository,
        StatsBySecondRepository statsBySecondRepository
    ) {
        this.simulationStatsRepository = simulationStatsRepository;
        this.statsByMinuteRepository = statsByMinuteRepository;
        this.statsBySecondRepository = statsBySecondRepository;
    }

    /**
     * Calculate the simulation result and save it to the database.
     * @param simulationRun the simulation run to calculate the result for
     * @param requestStats the list of request stats from the simulation run
     * @return the simulation run with the result
     */
    public SimulationRun calculateAndSaveResult(SimulationRun simulationRun, List<RequestStat> requestStats) {
        SimulationStats totalStats = new SimulationStats();
        totalStats.setSimulationRun(simulationRun);
        totalStats.setNumberOfRequests(requestStats.size());
        totalStats.setAvgResponseTime(getAverage(requestStats));
        totalStats.setRequestType(RequestType.TOTAL);
        Set<StatsByMinute> totalStatsByMinute = calculateStatsByMinute(requestStats);
        Set<StatsBySecond> totalStatsBySecond = calculateStatsBySecond(requestStats);
        simulationStatsRepository.save(totalStats);
        totalStats.setStatsByMinute(totalStatsByMinute);
        totalStatsByMinute.forEach(statsByMinute -> {
            statsByMinute.setSimulationStats(totalStats);
            statsByMinuteRepository.save(statsByMinute);
        });
        totalStats.setStatsBySecond(totalStatsBySecond);
        totalStatsBySecond.forEach(statsByTenSec -> {
            statsByTenSec.setSimulationStats(totalStats);
            statsBySecondRepository.save(statsByTenSec);
        });

        SimulationStats authStats = calculateStatsForRequestType(requestStats, RequestType.AUTHENTICATION, simulationRun);

        SimulationStats getStudentExamsStats = calculateStatsForRequestType(requestStats, RequestType.GET_STUDENT_EXAM, simulationRun);

        SimulationStats startStudentExamStats = calculateStatsForRequestType(requestStats, RequestType.START_STUDENT_EXAM, simulationRun);

        SimulationStats submitExerciseStats = calculateStatsForRequestType(requestStats, RequestType.SUBMIT_EXERCISE, simulationRun);

        SimulationStats submitStudentExamStats = calculateStatsForRequestType(requestStats, RequestType.SUBMIT_STUDENT_EXAM, simulationRun);

        SimulationStats cloneStatsSSH = calculateStatsForRequestType(requestStats, RequestType.CLONE_SSH, simulationRun);
        SimulationStats cloneStatsPassword = calculateStatsForRequestType(requestStats, RequestType.CLONE_PASSWORD, simulationRun);
        SimulationStats cloneStatsToken = calculateStatsForRequestType(requestStats, RequestType.CLONE_TOKEN, simulationRun);

        SimulationStats pushStatsSSH = calculateStatsForRequestType(requestStats, RequestType.PUSH_SSH, simulationRun);
        SimulationStats pushStatsPassword = calculateStatsForRequestType(requestStats, RequestType.PUSH_PASSWORD, simulationRun);
        SimulationStats pushStatsToken = calculateStatsForRequestType(requestStats, RequestType.PUSH_TOKEN, simulationRun);

        SimulationStats programmingExerciseResultStats = calculateStatsForRequestType(
            requestStats,
            RequestType.PROGRAMMING_EXERCISE_RESULT,
            simulationRun
        );

        SimulationStats repositoryInfoStats = calculateStatsForRequestType(requestStats, RequestType.REPOSITORY_INFO, simulationRun);

        SimulationStats repositoryFileStats = calculateStatsForRequestType(requestStats, RequestType.REPOSITORY_FILES, simulationRun);

        SimulationStats miscStats = calculateStatsForRequestType(requestStats, RequestType.MISC, simulationRun);

        simulationRun.setStats(
            Set.of(
                totalStats,
                authStats,
                getStudentExamsStats,
                startStudentExamStats,
                submitExerciseStats,
                submitStudentExamStats,
                programmingExerciseResultStats,
                repositoryInfoStats,
                repositoryFileStats,
                miscStats,
                cloneStatsSSH,
                pushStatsSSH,
                cloneStatsToken,
                pushStatsToken,
                cloneStatsPassword,
                pushStatsPassword
            )
        );
        return simulationRun;
    }

    private SimulationStats calculateStatsForRequestType(List<RequestStat> requestStats, RequestType type, SimulationRun simulationRun) {
        SimulationStats simulationStats = new SimulationStats();
        List<RequestStat> filteredRequestStats = requestStats.stream().filter(stat -> stat.type() == type).toList();
        simulationStats.setNumberOfRequests(filteredRequestStats.size());
        simulationStats.setAvgResponseTime(getAverage(filteredRequestStats));
        simulationStats.setRequestType(type);
        simulationStats.setSimulationRun(simulationRun);
        simulationStatsRepository.save(simulationStats);
        Set<StatsByMinute> statsByMinutes = calculateStatsByMinute(filteredRequestStats);
        simulationStats.setStatsByMinute(statsByMinutes);
        statsByMinutes.forEach(statsByMinute -> {
            statsByMinute.setSimulationStats(simulationStats);
            statsByMinuteRepository.save(statsByMinute);
        });
        Set<StatsBySecond> statsBySecond = calculateStatsBySecond(filteredRequestStats);
        simulationStats.setStatsBySecond(statsBySecond);
        statsBySecond.forEach(statsByTenSec -> {
            statsByTenSec.setSimulationStats(simulationStats);
            statsBySecondRepository.save(statsByTenSec);
        });
        return simulationStats;
    }

    private static long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
    }

    private static Set<StatsByMinute> calculateStatsByMinute(Collection<RequestStat> requestStats) {
        Map<ZonedDateTime, Long> requestsByMinute = calculateRequestsByMinute(requestStats);
        Map<ZonedDateTime, Double> avgResponseTimeByMinute = calculateAvgResponseTimeByMinute(requestStats);

        return requestsByMinute
            .keySet()
            .stream()
            .map(dateTime -> {
                StatsByMinute statsByMinute = new StatsByMinute();
                statsByMinute.setDateTime(dateTime);
                statsByMinute.setNumberOfRequests(requestsByMinute.get(dateTime));
                statsByMinute.setAvgResponseTime(avgResponseTimeByMinute.get(dateTime).longValue());
                return statsByMinute;
            })
            .collect(Collectors.toSet());
    }

    private static Set<StatsBySecond> calculateStatsBySecond(Collection<RequestStat> requestStats) {
        Map<ZonedDateTime, Long> requestsBySecond = calculateRequestsBySecond(requestStats);
        Map<ZonedDateTime, Double> avgResponseTimeBySecond = calculateAvgResponseTimeBySecond(requestStats);

        return requestsBySecond
            .keySet()
            .stream()
            .map(dateTime -> {
                StatsBySecond statsBySecond = new StatsBySecond();
                statsBySecond.setDateTime(dateTime);
                statsBySecond.setNumberOfRequests(requestsBySecond.get(dateTime));
                statsBySecond.setAvgResponseTime(avgResponseTimeBySecond.get(dateTime).longValue());
                return statsBySecond;
            })
            .collect(Collectors.toSet());
    }

    private static Map<ZonedDateTime, Long> calculateRequestsByMinute(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(Collectors.groupingBy(stat -> stat.dateTime().truncatedTo(ChronoUnit.MINUTES), Collectors.counting()));
    }

    private static Map<ZonedDateTime, Double> calculateAvgResponseTimeByMinute(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(
                Collectors.groupingBy(
                    stat -> stat.dateTime().truncatedTo(ChronoUnit.MINUTES),
                    Collectors.averagingLong(RequestStat::duration)
                )
            );
    }

    private static Map<ZonedDateTime, Long> calculateRequestsBySecond(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(Collectors.groupingBy(stat -> stat.dateTime().truncatedTo(ChronoUnit.SECONDS), Collectors.counting()));
    }

    private static Map<ZonedDateTime, Double> calculateAvgResponseTimeBySecond(Collection<RequestStat> requestStats) {
        return requestStats
            .stream()
            .collect(
                Collectors.groupingBy(
                    stat -> stat.dateTime().truncatedTo(ChronoUnit.SECONDS),
                    Collectors.averagingLong(RequestStat::duration)
                )
            );
    }
}
