package de.tum.cit.ase.util;

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

public class SshUtils {

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
