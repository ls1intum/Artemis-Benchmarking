package de.tum.cit.aet.service.dto;

import java.io.Serializable;

public class ArtemisUserPatternDTO implements Serializable {

    private String usernamePattern;
    private String passwordPattern;

    private String firstNamePattern;
    private String lastNamePattern;
    private String emailPattern;
    private int from;
    private int to;
    private boolean createOnArtemis;

    public String getUsernamePattern() {
        return usernamePattern;
    }

    public void setUsernamePattern(String usernamePattern) {
        this.usernamePattern = usernamePattern;
    }

    public String getPasswordPattern() {
        return passwordPattern;
    }

    public void setPasswordPattern(String passwordPattern) {
        this.passwordPattern = passwordPattern;
    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public String getFirstNamePattern() {
        return firstNamePattern;
    }

    public void setFirstNamePattern(String firstNamePattern) {
        this.firstNamePattern = firstNamePattern;
    }

    public String getLastNamePattern() {
        return lastNamePattern;
    }

    public void setLastNamePattern(String lastNamePattern) {
        this.lastNamePattern = lastNamePattern;
    }

    public String getEmailPattern() {
        return emailPattern;
    }

    public void setEmailPattern(String emailPattern) {
        this.emailPattern = emailPattern;
    }

    public boolean isCreateOnArtemis() {
        return createOnArtemis;
    }

    public void setCreateOnArtemis(boolean createOnArtemis) {
        this.createOnArtemis = createOnArtemis;
    }
}
