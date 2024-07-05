package de.tum.cit.ase.service.artemis.util;

public record ScienceEventDTO(ScienceEventType type, Long resourceId) {
    /**
     * Types of events that can be logged for scientific purposes.
     * <p>
     * Important: Please follow the naming convention <category>__<detailed event name>
     */
    public enum ScienceEventType {
        LECTURE__OPEN,
        LECTURE__OPEN_UNIT,
        EXERCISE__OPEN,
    }
}
