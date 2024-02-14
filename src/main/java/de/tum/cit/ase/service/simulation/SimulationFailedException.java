package de.tum.cit.ase.service.simulation;

/**
 * Exception thrown when a simulation fails.
 */
public class SimulationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SimulationFailedException() {
        super("Simulation failed");
    }

    public SimulationFailedException(String message) {
        super(message);
    }

    public SimulationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
