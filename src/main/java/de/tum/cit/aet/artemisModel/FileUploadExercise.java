package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FileUploadExercise extends Exercise {
   private String filePattern;

   public FileUploadExercise() {
      super();
   }

   public FileUploadExercise(ExerciseGroup exerciseGroup, String title, Double maxPoints, String filePattern) {
      super(exerciseGroup, title, maxPoints);
      this.filePattern = filePattern;
   }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }
}
