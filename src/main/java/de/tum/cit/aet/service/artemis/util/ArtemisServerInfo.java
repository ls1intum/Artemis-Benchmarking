package de.tum.cit.aet.service.artemis.util;

import java.util.Set;

public record ArtemisServerInfo(Set<String> features, Set<String> activeProfiles) {}
