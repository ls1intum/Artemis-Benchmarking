package de.tum.cit.aet.service.dto;

import de.tum.cit.aet.util.ArtemisServer;
import java.util.List;

public record ArtemisServerConfigurationDTO(
    ArtemisServer server,
    String url,
    boolean cleanupEnabled,
    boolean isLocal,
    List<String> prometheusInstancesArtemis,
    List<String> prometheusInstancesVcs,
    List<String> prometheusInstancesCi
) {}
