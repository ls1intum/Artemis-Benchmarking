package de.tum.cit.ase.service.artemis;

import de.tum.cit.ase.domain.ArtemisUser;

public class DuplicatedArtemisUserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicatedArtemisUserException(ArtemisUser artemisUser) {
        super("Artemis user with server wide ID " + artemisUser.getServerWideId() + " already exists on " + artemisUser.getServer());
    }
}
