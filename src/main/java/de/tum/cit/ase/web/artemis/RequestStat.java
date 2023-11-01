package de.tum.cit.ase.web.artemis;

import java.time.ZonedDateTime;

public record RequestStat(ZonedDateTime dateTime, Long duration, RequestType type) {}
