package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseBuildConfigDTO(
    Boolean sequentialTestRuns,
    String branch,
    String buildPlanConfiguration,
    String buildScript,
    boolean checkoutSolutionRepository,
    String testCheckoutPath,
    String assignmentCheckoutPath,
    String solutionCheckoutPath,
    int timeoutSeconds,
    String dockerFlags,
    boolean testwiseCoverageEnabled,
    String theiaImage,
    boolean allowBranching,
    String branchRegex,
    String buildPlanAccessSecret
) {
    public static ProgrammingExerciseBuildConfigDTO forBenchmarking() {
        return new ProgrammingExerciseBuildConfigDTO(
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            0,
            null,
            false,
            null,
            false,
            null,
            null
        );
    }
}
