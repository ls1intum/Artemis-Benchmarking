package de.tum.cit.aet.service.artemis.passkey;

import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * A software WebAuthn authenticator: the cryptographic half of passkey support.
 * <p>
 * This exists because Artemis can require a passkey for administrator features, and a password login cannot
 * satisfy that. A passkey created in a browser is no help either, since its private key never leaves the
 * platform authenticator, so the simulation has to own the key itself. This class produces the two byte
 * structures a relying party validates: the attestation object at registration, and the signed authenticator
 * data at every login.
 * <p>
 * The registration side uses webauthn4j to build the CBOR structures, deliberately the same library and version
 * the Artemis server validates with. The assertion side needs no CBOR at all: authenticator data for an
 * assertion is just {@code rpIdHash || flags || signCount}, so it is assembled here directly.
 */
@Component
public class PasskeyCredentialFactory {

    /** Length of a SHA-256 digest, which is also the length of an rpIdHash. */
    private static final int SHA256_LENGTH = 32;

    /** WebAuthn credential ids are opaque to the relying party; 32 random bytes is what platform authenticators use. */
    private static final int CREDENTIAL_ID_LENGTH = 32;

    /** User Present. Set on every assertion: the ceremony is only valid if the user acted. */
    private static final byte FLAG_USER_PRESENT = 0x01;

    /**
     * User Verified. Artemis asks for {@code userVerification: "preferred"} rather than {@code "required"}, so
     * this is optional, but a platform authenticator that can verify sets it and so do we.
     */
    private static final byte FLAG_USER_VERIFIED = 0x04;

    /** Attested credential data included. Set at registration only, never on an assertion. */
    private static final byte FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;

    private final ObjectConverter objectConverter = new ObjectConverter();

    private final SecureRandom secureRandom = new SecureRandom();

    private final JsonMapper jsonMapper;

    public PasskeyCredentialFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Build the clientDataJSON for a ceremony.
     * <p>
     * The bytes are returned rather than a string because an assertion signature covers them exactly, so the same
     * bytes have to be both signed and sent. Re-serializing would risk a different key order and a signature over
     * something other than what was transmitted.
     *
     * @param type      {@code webauthn.create} for registration, {@code webauthn.get} for an assertion
     * @param challenge the base64url challenge from the options response
     * @param rpId      the relying party id, from which the origin is derived
     * @return the encoded client data
     */
    public byte[] clientDataJson(String type, String challenge, String rpId) {
        Map<String, Object> clientData = new LinkedHashMap<>();
        clientData.put("type", type);
        clientData.put("challenge", challenge);
        // The relying party checks the origin against its allow list, so it has to be the browser-visible URL.
        // This is also why a simulation must reach Artemis through its load balancer rather than a node address.
        clientData.put("origin", "https://" + rpId);
        clientData.put("crossOrigin", false);
        return jsonMapper.writeValueAsBytes(clientData);
    }

    /**
     * Create a fresh credential: an EC P-256 key pair plus a random credential id, and the attestation object
     * that registers it.
     * <p>
     * ES256 is chosen from the algorithms Artemis offers because it is the one the JDK signs with out of the box,
     * with no provider to add. Artemis requests {@code attestation: "none"}, so there is no attestation
     * statement to produce and nothing to forge.
     *
     * @param rpId the relying party id from the registration options, e.g. {@code artemis.example.com}
     * @return the new credential, ready to be posted to Artemis and then persisted
     */
    public NewPasskeyCredential createCredential(String rpId) {
        KeyPair keyPair = generateEcKeyPair();
        byte[] credentialId = new byte[CREDENTIAL_ID_LENGTH];
        secureRandom.nextBytes(credentialId);

        EC2COSEKey publicKey = EC2COSEKey.create((java.security.interfaces.ECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.ES256);
        EC2COSEKey privateKey = EC2COSEKey.create((java.security.interfaces.ECPrivateKey) keyPair.getPrivate(), COSEAlgorithmIdentifier.ES256);

        // AAGUID zero identifies an authenticator model that declines to identify itself, which is what a
        // software authenticator honestly is, and is accepted under "none" attestation.
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(AAGUID.ZERO, credentialId, publicKey);
        byte flags = (byte) (FLAG_USER_PRESENT | FLAG_USER_VERIFIED | FLAG_ATTESTED_CREDENTIAL_DATA);
        AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authenticatorData = new AuthenticatorData<>(sha256(rpId.getBytes()), flags, 0L,
                attestedCredentialData);

        AttestationObject attestationObject = new AttestationObject(authenticatorData, new NoneAttestationStatement());
        String attestationObjectBase64Url = new AttestationObjectConverter(objectConverter).convertToBase64urlString(attestationObject);

        return new NewPasskeyCredential(base64Url(credentialId), attestationObjectBase64Url, encodeCoseKey(privateKey));
    }

    /**
     * Build the authenticator data for an assertion and sign it.
     * <p>
     * The signature covers {@code authenticatorData || SHA-256(clientDataJSON)}, which is what binds the
     * assertion to this particular challenge and origin.
     *
     * @param rpId             the relying party id
     * @param clientDataJson   the exact clientDataJSON bytes that will be sent, since the signature covers them
     * @param signatureCount   the counter for this assertion; must be higher than the last one used
     * @param encodedCoseKey   the credential's private key as stored by {@link #encodeCoseKey}
     * @return the base64url-encoded authenticator data and signature
     */
    public SignedAssertion signAssertion(String rpId, byte[] clientDataJson, long signatureCount, String encodedCoseKey) {
        byte flags = (byte) (FLAG_USER_PRESENT | FLAG_USER_VERIFIED);
        byte[] authenticatorData = ByteBuffer.allocate(SHA256_LENGTH + 1 + 4).put(sha256(rpId.getBytes())).put(flags).putInt((int) signatureCount).array();

        byte[] signedData = ByteBuffer.allocate(authenticatorData.length + SHA256_LENGTH).put(authenticatorData).put(sha256(clientDataJson)).array();

        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(decodeCoseKey(encodedCoseKey));
            signature.update(signedData);
            return new SignedAssertion(base64Url(authenticatorData), base64Url(signature.sign()));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not sign the passkey assertion", exception);
        }
    }

    /**
     * @param coseKey the private key to persist
     * @return the key as base64-encoded CBOR, suitable for a database column
     */
    public String encodeCoseKey(EC2COSEKey coseKey) {
        return Base64.getEncoder().encodeToString(objectConverter.getCborMapper().writeValueAsBytes(coseKey));
    }

    private PrivateKey decodeCoseKey(String encodedCoseKey) {
        EC2COSEKey coseKey = objectConverter.getCborMapper().readValue(Base64.getDecoder().decode(encodedCoseKey), EC2COSEKey.class);
        if (coseKey == null || !coseKey.hasPrivateKey()) {
            throw new IllegalStateException("The stored passkey credential does not contain a private key");
        }
        return coseKey.getPrivateKey();
    }

    private KeyPair generateEcKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
            return generator.generateKeyPair();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not generate an EC P-256 key pair for the passkey", exception);
        }
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * WebAuthn encodes every binary field as base64url without padding.
     *
     * @param bytes the bytes to encode
     * @return the base64url representation
     */
    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A newly created credential, before it has been registered with Artemis.
     *
     * @param credentialId      base64url credential id
     * @param attestationObject base64url attestation object to post to Artemis
     * @param encodedCoseKey    the private key, to persist once registration succeeds
     */
    public record NewPasskeyCredential(String credentialId, String attestationObject, String encodedCoseKey) {
    }

    /**
     * The signed halves of an assertion.
     *
     * @param authenticatorData base64url authenticator data
     * @param signature         base64url signature over the authenticator data and client data hash
     */
    public record SignedAssertion(String authenticatorData, String signature) {
    }
}
