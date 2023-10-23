package de.tum.cit.ase.service.util;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class Simulation {

    private final Logger log = LoggerFactory.getLogger(Simulation.class);

    private String testUserUsernameTemplate;
    private String testUserPasswordTemplate;

    private static final int maxNumberOfThreads = 20;
    private static final String courseId = "3";
    private static final String examId = "15";

    private final int numberOfUsers;
    private final List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());
    private SyntheticArtemisUser[] users;

    public Simulation(int numberOfUsers, String testUserPasswordTemplate, String testUserUsernameTemplate) {
        this.numberOfUsers = numberOfUsers;
        this.testUserPasswordTemplate = testUserPasswordTemplate;
        this.testUserUsernameTemplate = testUserUsernameTemplate;
        initializeUsers();
    }

    private void initializeUsers() {
        users = new SyntheticArtemisUser[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            var username = testUserUsernameTemplate.replace("{i}", String.valueOf(i + 1));
            var password = testUserPasswordTemplate.replace("{i}", String.valueOf(i + 1));
            users[i] = new SyntheticArtemisUser(username, password);
        }
    }

    public void simulateExam() {
        performActionWithAll(20, i -> users[i].login());
        performActionWithAll(Integer.min(maxNumberOfThreads, numberOfUsers), i -> users[i].performInitialCalls());
        performActionWithAll(Integer.min(maxNumberOfThreads, numberOfUsers), i -> users[i].participateInExam(courseId, examId));
        logRequestStatsPerMinute();
    }

    private void performActionWithAll(int threadCount, Function<Integer, List<RequestStat>> action) {
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadCount);
        Scheduler scheduler = Schedulers.from(threadPoolExecutor);

        Flowable
            .range(0, numberOfUsers)
            .parallel(threadCount)
            .runOn(scheduler)
            .doOnNext(i -> {
                requestStats.addAll(action.apply(i));
            })
            .sequential()
            .count()
            .blockingGet();

        threadPoolExecutor.shutdownNow();
        scheduler.shutdown();
    }

    private void logRequestStatsPerMinute() {
        log.info(
            "Average time for {}x {}: {}, rates: {}",
            requestStats.size(),
            "request",
            TimeLogUtil.formatDuration(getAverage(requestStats)),
            getRatesPerMinute(requestStats)
        );
    }

    private static String getRatesPerMinute(List<RequestStat> requestStats) {
        var formatter = DateTimeFormatter.ofPattern("HH:mm");
        return requestStats
            .stream()
            .map(RequestStat::dateTime)
            .collect(Collectors.groupingBy(date -> date.format(formatter), TreeMap::new, Collectors.counting()))
            .toString();
    }

    private static String getRatesPerSecond(List<RequestStat> requestStats) {
        var formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return requestStats
            .stream()
            .map(RequestStat::dateTime)
            .collect(Collectors.groupingBy(date -> date.format(formatter), TreeMap::new, Collectors.counting()))
            .toString();
    }

    private static long getAverage(Collection<RequestStat> times) {
        if (times.isEmpty()) {
            return 0;
        }
        return times.stream().map(RequestStat::duration).reduce(0L, Long::sum) / times.size();
    }
}
