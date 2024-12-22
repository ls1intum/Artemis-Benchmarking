package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opencsv.bean.CsvBindByName;
import de.tum.cit.aet.util.ArtemisServer;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "artemis_user")
public class ArtemisUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_wide_id")
    @CsvBindByName(column = "id")
    private int serverWideId; // Needs to be unique within one server, could potentially be replaced by a composite key

    @CsvBindByName(column = "username")
    private String username;

    @CsvBindByName(column = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    private ArtemisServer server;

    @Column(name = "jwt_token")
    @JsonIgnore
    private String jwtToken;

    @Column(name = "token_expiration_date")
    @JsonIgnore
    private ZonedDateTime tokenExpirationDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getServerWideId() {
        return serverWideId;
    }

    public void setServerWideId(int serverWideId) {
        this.serverWideId = serverWideId;
    }

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

    public ArtemisServer getServer() {
        return server;
    }

    public void setServer(ArtemisServer server) {
        this.server = server;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public ZonedDateTime getTokenExpirationDate() {
        return tokenExpirationDate;
    }

    public void setTokenExpirationDate(ZonedDateTime tokenExpirationDate) {
        this.tokenExpirationDate = tokenExpirationDate;
    }
}
