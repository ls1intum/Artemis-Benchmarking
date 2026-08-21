package de.tum.cit.aet.service.artemis.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the software authenticator against webauthn4j's own relying-party verifier.
 * <p>
 * This is the point of the test rather than an implementation detail: webauthn4j is the library the Artemis
 * server validates passkeys with, so if its verifier accepts what we produce, Artemis will too. That makes the
 * cryptography testable offline, without a running Artemis and without a browser.
 */
class PasskeyCredentialFactoryTest {

    private static final String RP_ID = "artemis.example.com";

    private static final String CHALLENGE = "FnhYNCj_jsTZOD0SbU6VwxppESxUrHRwBUQI03CnbZ4";

    private final PasskeyCredentialFactory factory = new PasskeyCredentialFactory(JsonMapper.builder().build());

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();

    @Test
    void registrationIsAcceptedByTheRelyingParty() {
        var credential = factory.createCredential(RP_ID);
        byte[] clientDataJson = factory.clientDataJson("webauthn.create", CHALLENGE, RP_ID);

        RegistrationData registrationData = verifyRegistration(credential, clientDataJson);

        assertThat(registrationData.getAttestationObject()).isNotNull();
        assertThat(registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData().getCredentialId())
            .isEqualTo(base64UrlDecode(credential.credentialId()));
    }

    @Test
    void assertionIsAcceptedByTheRelyingParty() {
        var credential = factory.createCredential(RP_ID);
        RegistrationData registrationData = verifyRegistration(credential, factory.clientDataJson("webauthn.create", CHALLENGE, RP_ID));
        CredentialRecord credentialRecord = credentialRecord(registrationData);

        // A real login: fetch a challenge, sign it, and let the relying party check the result.
        String loginChallenge = "ADtv3n_K1AeaUtT7kRb2a88xHV9MIAnGvcRdxxhNilY";
        byte[] clientDataJson = factory.clientDataJson("webauthn.get", loginChallenge, RP_ID);
        var assertion = factory.signAssertion(RP_ID, clientDataJson, 1L, credential.encodedCoseKey());

        var authenticationData = webAuthnManager.verify(
            new AuthenticationRequest(
                base64UrlDecode(credential.credentialId()),
                base64UrlDecode(assertion.authenticatorData()),
                clientDataJson,
                base64UrlDecode(assertion.signature())
            ),
            new AuthenticationParameters(serverProperty(loginChallenge), credentialRecord, null, false)
        );

        assertThat(authenticationData.getAuthenticatorData()).isNotNull();
        assertThat(authenticationData.getAuthenticatorData().getSignCount()).isEqualTo(1L);
    }

    /**
     * The counter has to advance, because a relying party treats a counter that stands still as evidence of a
     * cloned authenticator. This is what makes persisting it in the database load-bearing rather than cosmetic.
     */
    @Test
    void assertionWithAStaleSignatureCounterIsRejected() {
        var credential = factory.createCredential(RP_ID);
        RegistrationData registrationData = verifyRegistration(credential, factory.clientDataJson("webauthn.create", CHALLENGE, RP_ID));

        String loginChallenge = "ADtv3n_K1AeaUtT7kRb2a88xHV9MIAnGvcRdxxhNilY";
        byte[] clientDataJson = factory.clientDataJson("webauthn.get", loginChallenge, RP_ID);
        var assertion = factory.signAssertion(RP_ID, clientDataJson, 5L, credential.encodedCoseKey());

        // The stored record has already seen a higher counter than this assertion carries.
        CredentialRecord recordAtHigherCounter = new CredentialRecordImpl(
            registrationData.getAttestationObject().getAttestationStatement(),
            null,
            null,
            null,
            9L,
            registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData(),
            registrationData.getAttestationObject().getAuthenticatorData().getExtensions(),
            registrationData.getCollectedClientData(),
            registrationData.getClientExtensions(),
            registrationData.getTransports()
        );

        assertThatThrownBy(() ->
            webAuthnManager.verify(
                new AuthenticationRequest(
                    base64UrlDecode(credential.credentialId()),
                    base64UrlDecode(assertion.authenticatorData()),
                    clientDataJson,
                    base64UrlDecode(assertion.signature())
                ),
                new AuthenticationParameters(serverProperty(loginChallenge), recordAtHigherCounter, null, false)
            )
        )
            .isInstanceOf(com.webauthn4j.verifier.exception.VerificationException.class);
    }

    /**
     * The origin is derived from the relying party id, and the relying party checks it. Signing for one origin and
     * presenting to another must fail, which is the property that stops a stolen assertion being replayed
     * elsewhere.
     */
    @Test
    void assertionForAnotherOriginIsRejected() {
        var credential = factory.createCredential(RP_ID);
        RegistrationData registrationData = verifyRegistration(credential, factory.clientDataJson("webauthn.create", CHALLENGE, RP_ID));
        CredentialRecord credentialRecord = credentialRecord(registrationData);

        String loginChallenge = "ADtv3n_K1AeaUtT7kRb2a88xHV9MIAnGvcRdxxhNilY";
        byte[] clientDataJson = factory.clientDataJson("webauthn.get", loginChallenge, "attacker.example.com");
        var assertion = factory.signAssertion("attacker.example.com", clientDataJson, 1L, credential.encodedCoseKey());

        assertThatThrownBy(() ->
            webAuthnManager.verify(
                new AuthenticationRequest(
                    base64UrlDecode(credential.credentialId()),
                    base64UrlDecode(assertion.authenticatorData()),
                    clientDataJson,
                    base64UrlDecode(assertion.signature())
                ),
                new AuthenticationParameters(serverProperty(loginChallenge), credentialRecord, null, false)
            )
        )
            .isInstanceOf(com.webauthn4j.verifier.exception.VerificationException.class);
    }

    @Test
    void aStoredKeyRoundTripsThroughItsEncodedForm() {
        var credential = factory.createCredential(RP_ID);
        byte[] clientDataJson = factory.clientDataJson("webauthn.get", CHALLENGE, RP_ID);

        // Signing twice from the same stored form must produce a usable signature both times: the encoded key is
        // what survives a restart, so this is the property the database column relies on.
        var first = factory.signAssertion(RP_ID, clientDataJson, 1L, credential.encodedCoseKey());
        var second = factory.signAssertion(RP_ID, clientDataJson, 2L, credential.encodedCoseKey());

        assertThat(first.signature()).isNotBlank();
        assertThat(second.signature()).isNotBlank();
        assertThat(first.authenticatorData()).isNotEqualTo(second.authenticatorData());
    }

    @Test
    void signingWithoutAStoredKeyFails() {
        byte[] clientDataJson = factory.clientDataJson("webauthn.get", CHALLENGE, RP_ID);
        String notAKey = Base64.getEncoder().encodeToString("not a cose key".getBytes());

        assertThatThrownBy(() -> factory.signAssertion(RP_ID, clientDataJson, 1L, notAKey)).isInstanceOf(RuntimeException.class);
    }

    private RegistrationData verifyRegistration(PasskeyCredentialFactory.NewPasskeyCredential credential, byte[] clientDataJson) {
        return webAuthnManager.verify(
            new RegistrationRequest(base64UrlDecode(credential.attestationObject()), clientDataJson),
            new RegistrationParameters(serverProperty(CHALLENGE), false)
        );
    }

    private CredentialRecord credentialRecord(RegistrationData registrationData) {
        return new CredentialRecordImpl(
            registrationData.getAttestationObject(),
            registrationData.getCollectedClientData(),
            registrationData.getClientExtensions(),
            registrationData.getTransports()
        );
    }

    private ServerProperty serverProperty(String challenge) {
        return new ServerProperty(new Origin("https://" + RP_ID), RP_ID, new DefaultChallenge(base64UrlDecode(challenge)));
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
