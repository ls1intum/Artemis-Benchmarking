package de.tum.cit.ase.domain;

import java.time.ZonedDateTime;

public record RequestStat(ZonedDateTime dateTime, Long duration, RequestType type) {}
