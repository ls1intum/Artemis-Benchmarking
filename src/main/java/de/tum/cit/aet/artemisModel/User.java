package de.tum.cit.aet.artemisModel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class User extends DomainObject {

    private String login;

    @JsonIgnore
    private String password;

    private String firstName;
    private String lastName;

    @JsonIgnore
    private String registrationNumber;

    private String email;
    private boolean activated = false;
    private boolean isDeleted = false; // default value
    private String langKey;
    private String imageUrl;

    @JsonIgnore
    private String activationKey;

    @JsonIgnore
    private String resetKey;

    private Instant resetDate = null;
    private ZonedDateTime lastNotificationRead = null;
    private ZonedDateTime hideNotificationsUntil = null;
    private boolean isInternal = true; // default value
    private String vcsAccessToken = null;
    private Set<String> groups = new HashSet<>();
    private Set<String> authorities = new HashSet<>();

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

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public ZonedDateTime getLastNotificationRead() {
        return lastNotificationRead;
    }

    public void setLastNotificationRead(ZonedDateTime lastNotificationRead) {
        this.lastNotificationRead = lastNotificationRead;
    }

    public ZonedDateTime getHideNotificationsUntil() {
        return hideNotificationsUntil;
    }

    public void setHideNotificationsUntil(ZonedDateTime hideNotificationsUntil) {
        this.hideNotificationsUntil = hideNotificationsUntil;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void setInternal(boolean internal) {
        isInternal = internal;
    }

    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    public void setVcsAccessToken(String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities;
    }
}
