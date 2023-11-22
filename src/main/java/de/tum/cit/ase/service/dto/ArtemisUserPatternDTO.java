package de.tum.cit.ase.service.dto;

import java.io.Serializable;

public class ArtemisUserPatternDTO implements Serializable {

    private String usernamePattern;
    private String passwordPattern;
    private int from;
    private int to;

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
}
