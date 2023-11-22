package de.tum.cit.ase.web.rest;

import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.security.AuthoritiesConstants;
import de.tum.cit.ase.service.artemis.ArtemisUserService;
import de.tum.cit.ase.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.ase.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.ase.util.ArtemisServer;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/artemis-users")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class ArtemisUserResource {

    private final ArtemisUserService artemisUserService;

    public ArtemisUserResource(ArtemisUserService artemisUserService) {
        this.artemisUserService = artemisUserService;
    }

    @GetMapping("/{server}")
    public ResponseEntity<List<ArtemisUser>> getArtemisUsersByServer(@PathVariable ArtemisServer server) {
        return new ResponseEntity<>(artemisUserService.getArtemisUsersByServer(server), HttpStatus.OK);
    }

    @PostMapping("/{server}")
    public ResponseEntity<ArtemisUser> createArtemisUser(
        @PathVariable ArtemisServer server,
        @RequestBody ArtemisUserForCreationDTO artemisUserDTO
    ) {
        return new ResponseEntity<>(artemisUserService.createArtemisUser(server, artemisUserDTO), HttpStatus.OK);
    }

    @PostMapping("/{server}/create-by-pattern")
    public ResponseEntity<List<ArtemisUser>> createArtemisUsersByPattern(
        @PathVariable ArtemisServer server,
        @RequestBody ArtemisUserPatternDTO pattern
    ) {
        return new ResponseEntity<>(artemisUserService.createArtemisUsersByPattern(server, pattern), HttpStatus.OK);
    }

    @DeleteMapping("/{server}")
    public ResponseEntity<Void> deleteArtemisUsersByServer(@PathVariable ArtemisServer server) {
        artemisUserService.deleteByServer(server);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/{id}/by-id")
    public ResponseEntity<Void> deleteArtemisUser(@PathVariable Long id) {
        artemisUserService.deleteArtemisUser(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
