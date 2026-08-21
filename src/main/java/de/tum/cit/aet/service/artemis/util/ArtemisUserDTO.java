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
    boolean internal,
    String visibleRegistrationNumber
) {
    /**
     * Build the payload for creating a simulation user on Artemis.
     * <p>
     * {@code internal} has to be true. Artemis only stores a password for internal users
     * ({@code UserCreationService.createUser} keeps the password hash behind that flag), so an externally managed
     * account is created without one and every later login is rejected with a 401. The simulation then skips those
     * students and reports a completed run in which nobody actually participated.
     *
     * @param login     the username
     * @param password  the password the simulation will log in with
     * @param firstName the first name
     * @param lastName  the last name
     * @param email     the email address
     * @return the creation payload
     */
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
            true,
            null
        );
    }
}
