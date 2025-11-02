package de.tum.cit.aet.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import de.tum.cit.aet.web.rest.errors.EntityNotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deprecation: please throw exceptions instead of using the below methods,
 * use e.g. AccessForbiddenException, EntityNotFoundException, BadRequestAlertException, ConflictException
 */
public interface ResponseUtil {

    /**
     * Wrap the optional into a {@link org.springframework.http.ResponseEntity} with an {@link org.springframework.http.HttpStatus#OK} status, or if it's empty, it
     * returns a {@link org.springframework.http.ResponseEntity} with {@link org.springframework.http.HttpStatus#NOT_FOUND}.
     *
     * @param <X>           type of the response
     * @param maybeResponse response to return if present
     * @return response containing {@code maybeResponse} if present or {@link org.springframework.http.HttpStatus#NOT_FOUND}
     */
    static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse) {
        return wrapOrNotFound(maybeResponse, null);
    }

    /**
     * Wrap the optional into a {@link org.springframework.http.ResponseEntity} with an {@link org.springframework.http.HttpStatus#OK} status with the headers, or if it's
     * empty, throws a {@link org.springframework.web.server.ResponseStatusException} with status {@link org.springframework.http.HttpStatus#NOT_FOUND}.
     *
     * @param <X>           type of the response
     * @param maybeResponse response to return if present
     * @param header        headers to be added to the response
     * @return response containing {@code maybeResponse} if present
     */
    static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse, HttpHeaders header) {
        return maybeResponse.map(response -> ResponseEntity.ok().headers(header).body(response)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
