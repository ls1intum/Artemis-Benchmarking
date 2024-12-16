package de.tum.cit.ase.service.artemis.util;

import java.time.ZonedDateTime;

public record UserSshPublicKeyDTO(
    Long id,
    String label,
    String publicKey,
    String keyHash,
    ZonedDateTime creationDate,
    ZonedDateTime lastUsedDate,
    ZonedDateTime expiryDate
) {
    public static UserSshPublicKeyDTO of(String publicKey) {
        return new UserSshPublicKeyDTO(null, "Key", publicKey, null, null, null, null);
    }
}
