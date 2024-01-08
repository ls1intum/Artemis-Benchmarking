package de.tum.cit.ase.artemisModel;

public class ProgrammingExerciseStudentParticipation extends StudentParticipation {

    private String repositoryUri;
    private String buildPlanId;
    private String branch;
    private boolean locked;

    public String getRepositoryUri() {
        return repositoryUri;
    }

    public void setRepositoryUri(String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
