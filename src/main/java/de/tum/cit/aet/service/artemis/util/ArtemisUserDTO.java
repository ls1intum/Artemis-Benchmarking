package de.tum.cit.aet.service.artemis.util;

import java.util.List;

public record ArtemisUserDTO(
    boolean activated,
    List<String> authorities,
    List<String> groups,
    List<String> guidedTourSettings,
    String email,
    String firstName,
    String lastName,
    String login,
    String password,
    String visibleRegistrationNumber
) {
    public static ArtemisUserDTO forCreation(String login, String password, String firstName, String lastName, String email) {
        return new ArtemisUserDTO(
            true,
            List.of("ROLE_USER"),
            List.of(),
            List.of(),
            email,
            firstName,
            lastName,
            login,
            password,
            null
        );
    }
}
