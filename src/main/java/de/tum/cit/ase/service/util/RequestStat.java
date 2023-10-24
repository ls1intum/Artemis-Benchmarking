package de.tum.cit.ase.service.util;

import java.time.ZonedDateTime;

public record RequestStat(ZonedDateTime dateTime, Long duration, RequestType type, boolean success) {}
