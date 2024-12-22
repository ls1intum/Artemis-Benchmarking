package de.tum.cit.aet.artemisModel;

import jakarta.annotation.Nullable;

public class ProgrammingExerciseBuildConfig extends DomainObject {

    private Boolean sequentialTestRuns;

    private String branch;

    private String buildPlanConfiguration;

    private String buildScript;

    private boolean checkoutSolutionRepository = false;

    private String testCheckoutPath;

    private String assignmentCheckoutPath;

    private String solutionCheckoutPath;

    private int timeoutSeconds;

    private String dockerFlags;

    private ProgrammingExercise programmingExercise;

    private boolean testwiseCoverageEnabled;

    @Nullable
    private String theiaImage;

    private boolean allowBranching = false; // default value

    private String branchRegex;

    @Nullable
    private String buildPlanAccessSecret;

    public String getBuildPlanAccessSecret() {
        return buildPlanAccessSecret;
    }

    public void setBuildPlanAccessSecret(String buildPlanAccessSecret) {
        this.buildPlanAccessSecret = buildPlanAccessSecret;
    }

    public String getBranchRegex() {
        return branchRegex;
    }

    public void setBranchRegex(String branchRegex) {
        this.branchRegex = branchRegex;
    }

    public boolean isAllowBranching() {
        return allowBranching;
    }

    public void setAllowBranching(boolean allowBranching) {
        this.allowBranching = allowBranching;
    }

    public String getTheiaImage() {
        return theiaImage;
    }

    public void setTheiaImage(String theiaImage) {
        this.theiaImage = theiaImage;
    }

    public boolean isTestwiseCoverageEnabled() {
        return testwiseCoverageEnabled;
    }

    public void setTestwiseCoverageEnabled(boolean testwiseCoverageEnabled) {
        this.testwiseCoverageEnabled = testwiseCoverageEnabled;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    public String getDockerFlags() {
        return dockerFlags;
    }

    public void setDockerFlags(String dockerFlags) {
        this.dockerFlags = dockerFlags;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getSolutionCheckoutPath() {
        return solutionCheckoutPath;
    }

    public void setSolutionCheckoutPath(String solutionCheckoutPath) {
        this.solutionCheckoutPath = solutionCheckoutPath;
    }

    public String getAssignmentCheckoutPath() {
        return assignmentCheckoutPath;
    }

    public void setAssignmentCheckoutPath(String assignmentCheckoutPath) {
        this.assignmentCheckoutPath = assignmentCheckoutPath;
    }

    public String getTestCheckoutPath() {
        return testCheckoutPath;
    }

    public void setTestCheckoutPath(String testCheckoutPath) {
        this.testCheckoutPath = testCheckoutPath;
    }

    public boolean isCheckoutSolutionRepository() {
        return checkoutSolutionRepository;
    }

    public void setCheckoutSolutionRepository(boolean checkoutSolutionRepository) {
        this.checkoutSolutionRepository = checkoutSolutionRepository;
    }

    public String getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(String buildScript) {
        this.buildScript = buildScript;
    }

    public String getBuildPlanConfiguration() {
        return buildPlanConfiguration;
    }

    public void setBuildPlanConfiguration(String buildPlanConfiguration) {
        this.buildPlanConfiguration = buildPlanConfiguration;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Boolean getSequentialTestRuns() {
        return sequentialTestRuns;
    }

    public void setSequentialTestRuns(Boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
    }
}
