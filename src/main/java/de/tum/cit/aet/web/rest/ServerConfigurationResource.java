package de.tum.cit.aet.web.rest;

import de.tum.cit.aet.security.AuthoritiesConstants;
import de.tum.cit.aet.service.artemis.ArtemisConfiguration;
import de.tum.cit.aet.service.dto.ArtemisServerConfigurationDTO;
import de.tum.cit.aet.util.ArtemisServer;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/server-configurations")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class ServerConfigurationResource {

    private final ArtemisConfiguration artemisConfiguration;

    public ServerConfigurationResource(ArtemisConfiguration artemisConfiguration) {
        this.artemisConfiguration = artemisConfiguration;
    }

    /**
     * GET /api/admin/server-configurations : Get all configured Artemis server environments.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of server configurations
     */
    @GetMapping
    public ResponseEntity<List<ArtemisServerConfigurationDTO>> getServerConfigurations() {
        var configurations = Arrays.stream(ArtemisServer.values()).map(artemisConfiguration::getServerConfiguration).toList();
        return ResponseEntity.ok(configurations);
    }
}
