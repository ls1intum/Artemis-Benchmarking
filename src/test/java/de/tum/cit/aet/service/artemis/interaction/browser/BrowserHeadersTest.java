package de.tum.cit.aet.service.artemis.interaction.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class BrowserHeadersTest {

    @Test
    void anAssetWhoseBodyIsDiscardedStillAsksForCompression() {
        // The point of the split: the server compresses and the wire carries the same bytes a real browser would
        // receive, but the client is not configured to inflate, so the tool does not spend CPU on bytes it discards.
        HttpHeaders headers = headersFor(BrowserHeaders.forDiscardedAsset("main-ABCDEFGH.js"));

        assertEquals("gzip", headers.getFirst(HttpHeaders.ACCEPT_ENCODING));
        assertEquals("*/*", headers.getFirst(HttpHeaders.ACCEPT), "a subresource, not a document");
    }

    @Test
    void theDocumentIsRequestedAsADocument() {
        HttpHeaders headers = headersFor(BrowserHeaders.forDiscardedAsset(""));

        assertTrue(headers.getFirst(HttpHeaders.ACCEPT).startsWith("text/html"), "Artemis answers 406 to an API Accept");
        assertEquals("gzip", headers.getFirst(HttpHeaders.ACCEPT_ENCODING));
    }

    @Test
    void anAssetRequestDoesNotCarryTheApiContentType() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        BrowserHeaders.forDiscardedAsset("styles-ABCDEFGH.css").accept(headers);

        assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE), "a GET has no body, and a browser sends no content type");
    }

    @Test
    void aReadAssetDoesNotAskForCompressionItself() {
        // Discovery reads the JavaScript to find what it imports, so it goes through the client that inflates; that
        // client adds the header on its own. Setting it here too would ask for an encoding nobody undoes.
        HttpHeaders headers = headersFor(BrowserHeaders.forAsset("main-ABCDEFGH.js"));

        assertNull(headers.getFirst(HttpHeaders.ACCEPT_ENCODING));
    }

    private static HttpHeaders headersFor(java.util.function.Consumer<HttpHeaders> customizer) {
        HttpHeaders headers = new HttpHeaders();
        customizer.accept(headers);
        return headers;
    }
}
