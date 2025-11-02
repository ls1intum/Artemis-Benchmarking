package de.tum.cit.aet.web.rest.errors;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.io.Serial;

public class PasswordViolatesRequirementsException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    public PasswordViolatesRequirementsException() {
        super(ErrorConstants.INVALID_PASSWORD_TYPE, "Incorrect password", Status.BAD_REQUEST);
    }
}
