package de.tum.cit.aet.util;

import org.springframework.http.HttpHeaders;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

    private HeaderUtil() {
    }

    public static HttpHeaders createAlert(String applicationName, String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-alert", message);
        headers.add("X-" + applicationName + "-params", URLEncoder.encode(param, StandardCharsets.UTF_8));
        return headers;
    }

    public static HttpHeaders createEntityCreationAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        String message = enableTranslation
            ? applicationName + "." + entityName + ".created"
            : "A new " + entityName + " is created with identifier " + param;
        return createAlert(applicationName, message, param);
    }

    public static HttpHeaders createEntityUpdateAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        String message = enableTranslation
            ? applicationName + "." + entityName + ".updated"
            : "A " + entityName + " is updated with identifier " + param;
        return createAlert(applicationName, message, param);
    }

    public static HttpHeaders createEntityDeletionAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        String message = enableTranslation
            ? applicationName + "." + entityName + ".deleted"
            : "A " + entityName + " is deleted with identifier " + param;
        return createAlert(applicationName, message, param);
    }

    /**
     * Creates an alert HttpHeaders for a failure event.
     *
     * @param applicationName   the name of the application
     * @param enableTranslation whether to enable translation for the error message
     * @param entityName        the name of the entity involved in the failure
     * @param errorKey          the key representing the error type
     * @param defaultMessage    the default message to use if translation is not enabled
     * @return HttpHeaders containing the failure alert information
     */
    public static HttpHeaders createFailureAlert(String applicationName, boolean enableTranslation, String entityName, String errorKey, String defaultMessage) {
        String message = enableTranslation ? "error." + errorKey : defaultMessage;

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-" + applicationName + "-error", message);
        headers.add("X-" + applicationName + "-params", entityName);
        headers.add("X-" + applicationName + "-message", defaultMessage);
        return headers;
    }
}
