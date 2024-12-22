package de.tum.cit.aet.domain;

import de.tum.cit.aet.util.TimeLogUtil;

public class SimulationResultForSummary {

    public String avgTotal;
    public String avgAuthentication;
    public String avgGetStudentExam;
    public String avgStartStudentExam;
    public String avgSubmitExercise;
    public String avgSubmitStudentExam;
    public String avgClone;
    public String avgPush;

    public static SimulationResultForSummary from(SimulationRun run) {
        SimulationResultForSummary result = new SimulationResultForSummary();
        result.avgTotal = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.TOTAL))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgAuthentication = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.AUTHENTICATION))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgGetStudentExam = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.GET_STUDENT_EXAM))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgStartStudentExam = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.START_STUDENT_EXAM))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgSubmitExercise = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.SUBMIT_EXERCISE))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgSubmitStudentExam = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.SUBMIT_STUDENT_EXAM))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgClone = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.CLONE))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        result.avgPush = TimeLogUtil.formatDuration(
            run
                .getStats()
                .stream()
                .filter((SimulationStats stats) -> stats.getRequestType().equals(RequestType.PUSH))
                .findFirst()
                .orElseThrow()
                .getAvgResponseTime()
        );
        return result;
    }
}
