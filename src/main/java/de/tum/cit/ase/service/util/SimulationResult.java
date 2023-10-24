package de.tum.cit.ase.service.util;

import static de.tum.cit.ase.service.util.RequestType.*;

import java.util.Collection;
import java.util.stream.Collectors;

public class SimulationResult {

    private SimulationStats authenticationStats;
    private SimulationStats getExamStats;
    private SimulationStats startExamStats;
    private SimulationStats submitExerciseStats;
    private SimulationStats submitExamStats;
    private SimulationStats cloneStats;
    private SimulationStats pushStats;
    private SimulationStats miscStats;

    public SimulationResult(Collection<RequestStat> requestStats) {
        authenticationStats =
            new SimulationStats(requestStats.stream().filter(stat -> stat.type() == AUTHENTICATION).collect(Collectors.toList()));
        getExamStats =
            new SimulationStats(requestStats.stream().filter(stat -> stat.type() == GET_STUDENT_EXAM).collect(Collectors.toList()));
        startExamStats =
            new SimulationStats(requestStats.stream().filter(stat -> stat.type() == START_STUDENT_EXAM).collect(Collectors.toList()));
        submitExerciseStats =
            new SimulationStats(requestStats.stream().filter(stat -> stat.type() == SUBMIT_EXERCISE).collect(Collectors.toList()));
        submitExamStats =
            new SimulationStats(requestStats.stream().filter(stat -> stat.type() == SUBMIT_STUDENT_EXAM).collect(Collectors.toList()));
        cloneStats = new SimulationStats(requestStats.stream().filter(stat -> stat.type() == CLONE).collect(Collectors.toList()));
        pushStats = new SimulationStats(requestStats.stream().filter(stat -> stat.type() == PUSH).collect(Collectors.toList()));
        miscStats = new SimulationStats(requestStats.stream().filter(stat -> stat.type() == MISC).collect(Collectors.toList()));
    }

    public SimulationResult() {}

    public SimulationStats getAuthenticationStats() {
        return authenticationStats;
    }

    public void setAuthenticationStats(SimulationStats authenticationStats) {
        this.authenticationStats = authenticationStats;
    }

    public SimulationStats getGetExamStats() {
        return getExamStats;
    }

    public void setGetExamStats(SimulationStats getExamStats) {
        this.getExamStats = getExamStats;
    }

    public SimulationStats getStartExamStats() {
        return startExamStats;
    }

    public void setStartExamStats(SimulationStats startExamStats) {
        this.startExamStats = startExamStats;
    }

    public SimulationStats getSubmitExerciseStats() {
        return submitExerciseStats;
    }

    public void setSubmitExerciseStats(SimulationStats submitExerciseStats) {
        this.submitExerciseStats = submitExerciseStats;
    }

    public SimulationStats getSubmitExamStats() {
        return submitExamStats;
    }

    public void setSubmitExamStats(SimulationStats submitExamStats) {
        this.submitExamStats = submitExamStats;
    }

    public SimulationStats getCloneStats() {
        return cloneStats;
    }

    public void setCloneStats(SimulationStats cloneStats) {
        this.cloneStats = cloneStats;
    }

    public SimulationStats getPushStats() {
        return pushStats;
    }

    public void setPushStats(SimulationStats pushStats) {
        this.pushStats = pushStats;
    }

    public SimulationStats getMiscStats() {
        return miscStats;
    }

    public void setMiscStats(SimulationStats miscStats) {
        this.miscStats = miscStats;
    }

    @Override
    public String toString() {
        return (
            "Simulation Result:\n\tAuthentication:\n" +
            authenticationStats +
            "\tGet Exam:\n" +
            getExamStats +
            "\tStart Exam\n" +
            startExamStats +
            "\tSubmit Exercise:\n" +
            submitExerciseStats +
            "\tSubmit Exam:\n" +
            submitExamStats +
            "\tClone Repo:\n" +
            cloneStats +
            "\tPush Repo:\n" +
            pushStats +
            "\tMisc:\n" +
            miscStats
        );
    }
}
