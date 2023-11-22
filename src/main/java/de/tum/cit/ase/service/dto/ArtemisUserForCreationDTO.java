package de.tum.cit.ase.service.dto;

import java.io.Serializable;

public class ArtemisUserForCreationDTO implements Serializable {

    private String username;
    private String password;
    private Integer serverWideId;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getServerWideId() {
        return serverWideId;
    }

    public void setServerWideId(Integer serverWideId) {
        this.serverWideId = serverWideId;
    }
}
