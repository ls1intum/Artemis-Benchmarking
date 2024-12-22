package de.tum.cit.aet.service.artemis.util;

import java.util.List;

public class ArtemisUserDTO {

    public boolean activated = true;
    public List<String> authorities = List.of("ROLE_USER");
    public List<String> groups = List.of();
    public List<String> guidedTourSettings = List.of();
    public String email;
    public String firstName;
    public String lastName;
    public String login;
    public String password;
    public String visibleRegistrationNumber;

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getGuidedTourSettings() {
        return guidedTourSettings;
    }

    public void setGuidedTourSettings(List<String> guidedTourSettings) {
        this.guidedTourSettings = guidedTourSettings;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVisibleRegistrationNumber() {
        return visibleRegistrationNumber;
    }

    public void setVisibleRegistrationNumber(String visibleRegistrationNumber) {
        this.visibleRegistrationNumber = visibleRegistrationNumber;
    }
}
