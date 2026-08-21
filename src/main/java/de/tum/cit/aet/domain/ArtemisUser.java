package de.tum.cit.aet.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opencsv.bean.CsvBindByName;
import de.tum.cit.aet.util.ArtemisServer;
import jakarta.persistence.*;
import java.security.*;
import java.time.ZonedDateTime;
import org.springframework.data.util.Pair;

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

    @Column(name = "public_ssh_key")
    @JsonIgnore
    private String publicKey;

    @Column(name = "private_ssh_key")
    @JsonIgnore
    private String privateKey;

    /**
     * Base64url-encoded credential id of the passkey registered for this user on Artemis.
     * <p>
     * Only set for users that went through passkey registration. Artemis can require a passkey for
     * administrator features, and a password login cannot satisfy that, so an admin or instructor used by a
     * simulation needs one.
     */
    @Column(name = "passkey_credential_id")
    @JsonIgnore
    private String passkeyCredentialId;

    /**
     * The passkey's private key, as a base64-encoded CBOR COSE key.
     * <p>
     * A passkey created in a browser cannot be used here, because its private key never leaves the platform
     * authenticator. The tool therefore generates the key itself and keeps it, which is also why this column is
     * as sensitive as {@link #password}.
     */
    @Column(name = "passkey_cose_key", length = 2048)
    @JsonIgnore
    private String passkeyCoseKey;

    /**
     * Base64url-encoded WebAuthn user handle returned during registration.
     */
    @Column(name = "passkey_user_handle")
    @JsonIgnore
    private String passkeyUserHandle;

    /**
     * The authenticator's signature counter.
     * <p>
     * Must strictly increase across assertions: the relying party rejects a counter that fails to advance once
     * the stored value is above zero, which is how cloned authenticators are detected.
     */
    @Column(name = "passkey_signature_count")
    @JsonIgnore
    private long passkeySignatureCount;

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

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public void setKeyPair(Pair<String, String> keyPair) {
        this.publicKey = keyPair.getFirst();
        this.privateKey = keyPair.getSecond();
    }

    public String getPasskeyCredentialId() {
        return passkeyCredentialId;
    }

    public void setPasskeyCredentialId(String passkeyCredentialId) {
        this.passkeyCredentialId = passkeyCredentialId;
    }

    public String getPasskeyCoseKey() {
        return passkeyCoseKey;
    }

    public void setPasskeyCoseKey(String passkeyCoseKey) {
        this.passkeyCoseKey = passkeyCoseKey;
    }

    public String getPasskeyUserHandle() {
        return passkeyUserHandle;
    }

    public void setPasskeyUserHandle(String passkeyUserHandle) {
        this.passkeyUserHandle = passkeyUserHandle;
    }

    public long getPasskeySignatureCount() {
        return passkeySignatureCount;
    }

    public void setPasskeySignatureCount(long passkeySignatureCount) {
        this.passkeySignatureCount = passkeySignatureCount;
    }

    /**
     * @return true if this user has a passkey credential that can be used to authenticate
     */
    @JsonIgnore
    public boolean hasPasskey() {
        return passkeyCredentialId != null && !passkeyCredentialId.isBlank() && passkeyCoseKey != null && !passkeyCoseKey.isBlank();
    }
}
