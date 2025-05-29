package de.tum.cit.aet.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.data.util.Pair;

/**
 * Utility class providing methods for SSH key management.
 */
public class SshUtils {

    /**
     * Generates a new SSH RSA key pair.
     * <p>
     * This method:
     * <ul>
     *   <li>Creates a 2048-bit RSA key pair.</li>
     *   <li>Exports the public key in OpenSSH format and the private key in PEM format.</li>
     * </ul>
     * <p>
     * <strong>Rationale:</strong> Encapsulates SSH key generation logic, providing a convenient and secure way to produce key pairs for authentication or encryption purposes.
     *
     * @return a {@link Pair} where the left is the public key (OpenSSH format) and the right is the private key (PEM format)
     * @throws RuntimeException if the key generation or export fails
     */
    public static Pair<String, String> generateSshKeyPair() {
        try {
            // Generate RSA key pair
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(2048); // Use 2048-bit key size
            KeyPair keyPair = keyPairGen.generateKeyPair();

            var privateKey = exportPrivateKey(keyPair.getPrivate());
            var publicKey = exportPublicKey(keyPair);
            return Pair.of(publicKey, privateKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String exportPrivateKey(java.security.PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(privateKey);
        }
        return stringWriter.toString();
    }

    private static String exportPublicKey(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return "ssh-rsa " + Base64.getEncoder().encodeToString(encodePublicKey(publicKey));
    }

    private static byte[] encodePublicKey(RSAPublicKey publicKey) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeString(out, "ssh-rsa");
            writeBigInteger(out, publicKey.getPublicExponent());
            writeBigInteger(out, publicKey.getModulus());

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error encoding public key", e);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String data) throws IOException {
        byte[] bytes = data.getBytes();
        writeInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeBigInteger(ByteArrayOutputStream out, BigInteger value) throws IOException {
        byte[] bytes = value.toByteArray();
        writeInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
