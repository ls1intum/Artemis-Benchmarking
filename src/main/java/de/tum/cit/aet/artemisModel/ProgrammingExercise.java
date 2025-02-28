package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExercise extends Exercise {

    private String shortName;
    private String packageName;
    private boolean allowOfflineIde = true;
    private String programmingLanguage = "JAVA";
    private String projectType = "PLAIN_GRADLE";
    private boolean staticCodeAnalysisEnabled = false;
    // Required for creating course ProgrammingExercise
    private Course course;
    private ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isAllowOfflineIde() {
        return allowOfflineIde;
    }

    public void setAllowOfflineIde(boolean allowOfflineIde) {
        this.allowOfflineIde = allowOfflineIde;
    }

    public String getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(String programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public boolean isStaticCodeAnalysisEnabled() {
        return staticCodeAnalysisEnabled;
    }

    public void setStaticCodeAnalysisEnabled(boolean staticCodeAnalysisEnabled) {
        this.staticCodeAnalysisEnabled = staticCodeAnalysisEnabled;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public ProgrammingExerciseBuildConfig getBuildConfig() {
        return buildConfig;
    }

    public void setBuildConfig(ProgrammingExerciseBuildConfig buildConfig) {
        this.buildConfig = buildConfig;
    }
}
