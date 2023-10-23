package de.tum.cit.ase.artemisModel;

public class TemplateProgrammingExerciseParticipation extends Participation {

    private String repositoryUrl;
    private String buildPlanId;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }
}
