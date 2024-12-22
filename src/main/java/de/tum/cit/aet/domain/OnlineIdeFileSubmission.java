package de.tum.cit.aet.domain;

import java.io.Serial;
import java.io.Serializable;

public class OnlineIdeFileSubmission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fileName;

    private String fileContent;

    public String getFileName() {
        return fileName;
    }

    public String getFileContent() {
        return fileContent;
    }

    public OnlineIdeFileSubmission(String fileName, String fileContent) {
        this.fileName = fileName;
        this.fileContent = fileContent;
    }
}
