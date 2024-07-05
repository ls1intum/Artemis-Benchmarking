package de.tum.cit.ase.service.artemis.util;

import java.util.Set;

public record ArtemisServerInfo(Set<String> features, Set<String> activeProfiles) {}
