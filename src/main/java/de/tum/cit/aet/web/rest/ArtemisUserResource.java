package de.tum.cit.aet.web.rest;

import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.security.AuthoritiesConstants;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.aet.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.aet.util.ArtemisServer;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/artemis-users")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class ArtemisUserResource {

    private final ArtemisUserService artemisUserService;

    public ArtemisUserResource(ArtemisUserService artemisUserService) {
        this.artemisUserService = artemisUserService;
    }

    /**
     * Get all ArtemisUsers for a given ArtemisServer.
     *
     * @param server the ArtemisServer to get the users for
     * @return a list of all ArtemisUsers
     */
    @GetMapping("/{server}")
    public ResponseEntity<List<ArtemisUser>> getArtemisUsersByServer(@PathVariable ArtemisServer server) {
        return new ResponseEntity<>(artemisUserService.getArtemisUsersByServer(server), HttpStatus.OK);
    }

    /**
     * Create a new ArtemisUser for a given ArtemisServer.
     *
     * @param server the ArtemisServer to create the user for
     * @param artemisUserDTO the ArtemisUser to create
     * @return the created ArtemisUser
     */
    @PostMapping("/{server}")
    public ResponseEntity<ArtemisUser> createArtemisUser(
        @PathVariable ArtemisServer server,
        @RequestBody ArtemisUserForCreationDTO artemisUserDTO
    ) {
        return new ResponseEntity<>(artemisUserService.createArtemisUser(server, artemisUserDTO), HttpStatus.OK);
    }

    /**
     * Create multiple ArtemisUsers for a given ArtemisServer using a pattern.
     *
     * @param server the ArtemisServer to create the users for
     * @param pattern the ArtemisUserPatternDTO to use to create the users
     * @return a list of the created ArtemisUsers
     */
    @PostMapping("/{server}/create-by-pattern")
    public ResponseEntity<List<ArtemisUser>> createArtemisUsersByPattern(
        @PathVariable ArtemisServer server,
        @RequestBody ArtemisUserPatternDTO pattern
    ) {
        return new ResponseEntity<>(artemisUserService.createArtemisUsersByPattern(server, pattern), HttpStatus.OK);
    }

    /**
     * Delete all ArtemisUsers for a given ArtemisServer.
     *
     * @param server the ArtemisServer to delete the users for
     * @return an empty response
     */
    @DeleteMapping("/{server}")
    public ResponseEntity<Void> deleteArtemisUsersByServer(@PathVariable ArtemisServer server) {
        artemisUserService.deleteByServer(server);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Delete an ArtemisUser by its id.
     *
     * @param id the id of the ArtemisUser to delete
     * @return an empty response
     */
    @DeleteMapping("/{id}/by-id")
    public ResponseEntity<Void> deleteArtemisUser(@PathVariable Long id) {
        artemisUserService.deleteArtemisUser(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Update an ArtemisUser by its id.
     *
     * @param id the id of the ArtemisUser to update
     * @param artemisUser the updated ArtemisUser
     * @return the updated ArtemisUser
     */
    @PutMapping("/{id}")
    public ResponseEntity<ArtemisUser> updateArtemisUser(@PathVariable Long id, @RequestBody ArtemisUser artemisUser) {
        if (!id.equals(artemisUser.getId())) {
            throw new IllegalArgumentException("Id in path and body do not match!");
        }
        return new ResponseEntity<>(artemisUserService.updateArtemisUser(id, artemisUser), HttpStatus.OK);
    }

    /**
     * Create multiple ArtemisUsers for a given ArtemisServer using a CSV file.
     *
     * @param server the ArtemisServer to create the users for
     * @param file the CSV file to use to create the users
     * @return a list of the created ArtemisUsers
     */
    @PostMapping("/{server}/csv")
    public ResponseEntity<List<ArtemisUser>> createArtemisUsersFromCSV(
        @PathVariable ArtemisServer server,
        @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().equals("text/csv")) {
            throw new IllegalArgumentException("File must be non-empty and of type text/csv");
        }
        return new ResponseEntity<>(artemisUserService.createArtemisUsersFromCSV(file, server), HttpStatus.OK);
    }
}
