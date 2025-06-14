package de.tum.cit.aet.service.dto;

import com.opencsv.bean.CsvBindByName;

// TODO: convert to record
public class ArtemisUserForCreationDTO {

    @CsvBindByName(column = "username")
    private String username;

    @CsvBindByName(column = "password")
    private String password;

    @CsvBindByName(column = "id")
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
