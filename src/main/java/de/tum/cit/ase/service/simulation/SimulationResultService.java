package de.tum.cit.ase.service.simulation;

import de.tum.cit.ase.domain.*;
import de.tum.cit.ase.domain.RequestType;
import de.tum.cit.ase.repository.SimulationStatsRepository;
import de.tum.cit.ase.repository.StatsByMinuteRepository;
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

    public SimulationResultService(SimulationStatsRepository simulationStatsRepository, StatsByMinuteRepository statsByMinuteRepository) {
        this.simulationStatsRepository = simulationStatsRepository;
        this.statsByMinuteRepository = statsByMinuteRepository;
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
        simulationStatsRepository.save(totalStats);
        totalStats.setStatsByMinute(totalStatsByMinute);
        totalStatsByMinute.forEach(statsByMinute -> {
            statsByMinute.setSimulationStats(totalStats);
            statsByMinuteRepository.save(statsByMinute);
        });

        SimulationStats authStats = calculateStatsForRequestType(requestStats, RequestType.AUTHENTICATION, simulationRun);

        SimulationStats getStudentExamsStats = calculateStatsForRequestType(requestStats, RequestType.GET_STUDENT_EXAM, simulationRun);

        SimulationStats startStudentExamStats = calculateStatsForRequestType(requestStats, RequestType.START_STUDENT_EXAM, simulationRun);

        SimulationStats submitExerciseStats = calculateStatsForRequestType(requestStats, RequestType.SUBMIT_EXERCISE, simulationRun);

        SimulationStats submitStudentExamStats = calculateStatsForRequestType(requestStats, RequestType.SUBMIT_STUDENT_EXAM, simulationRun);

        SimulationStats cloneStats = calculateStatsForRequestType(requestStats, RequestType.CLONE, simulationRun);

        SimulationStats pushStats = calculateStatsForRequestType(requestStats, RequestType.PUSH, simulationRun);

        SimulationStats miscStats = calculateStatsForRequestType(requestStats, RequestType.MISC, simulationRun);

        simulationRun.setStats(
            Set.of(
                totalStats,
                authStats,
                getStudentExamsStats,
                startStudentExamStats,
                submitExerciseStats,
                submitStudentExamStats,
                cloneStats,
                pushStats,
                miscStats
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
}
