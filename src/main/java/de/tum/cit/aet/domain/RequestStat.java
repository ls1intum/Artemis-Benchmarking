package de.tum.cit.aet.domain;

import java.time.ZonedDateTime;

public record RequestStat(ZonedDateTime dateTime, Long duration, RequestType type) {}
