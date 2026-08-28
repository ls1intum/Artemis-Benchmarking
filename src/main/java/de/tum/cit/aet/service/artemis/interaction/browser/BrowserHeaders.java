package de.tum.cit.aet.service.artemis.interaction.browser;

import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * The request headers a browser sends for a static file, rather than the ones a REST client sends.
 * <p>
 * This is not cosmetic. The simulated student's {@link org.springframework.web.reactive.function.client.WebClient} is
 * built for the Artemis API and defaults to {@code Accept: application/json}, and Artemis answers
 * {@code 406 Not Acceptable} when index.html is requested that way. A student that keeps the API headers therefore
 * downloads nothing at all and silently measures no client traffic.
 */
final class BrowserHeaders {

    /** What Chrome sends when it navigates to a page. */
    private static final String DOCUMENT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

    /** What Chrome sends for a script, stylesheet, font or image. */
    private static final String SUBRESOURCE_ACCEPT = "*/*";

    /**
     * Only gzip, though a browser also offers br and zstd: this is set for requests whose body is discarded, so the
     * encoding never has to be undone, and gzip is the one every server in front of Artemis supports.
     */
    private static final String ACCEPT_ENCODING = "gzip";

    private BrowserHeaders() {}

    /**
     * Headers for one asset.
     *
     * @param path path relative to the server root; the empty string means the document itself
     * @return a customizer that replaces the API defaults with browser ones
     */
    /**
     * Headers for an asset whose body will be discarded rather than read.
     * <p>
     * Adds the {@code Accept-Encoding} a browser sends, which the client would otherwise only send if it were also
     * going to inflate the response. Asking for compression without inflating is exactly what a load generator wants:
     * the server does the same work and the wire carries the same bytes, while the tool skips decompressing megabytes
     * it is about to throw away.
     *
     * @param path path relative to the server root; the empty string means the document itself
     * @return a customizer that makes the request look like a browser's
     */
    static Consumer<HttpHeaders> forDiscardedAsset(String path) {
        Consumer<HttpHeaders> asset = forAsset(path);
        return headers -> {
            asset.accept(headers);
            headers.set(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING);
        };
    }

    static Consumer<HttpHeaders> forAsset(String path) {
        boolean document = path.isEmpty() || path.endsWith(".html");
        return headers -> {
            headers.set(HttpHeaders.ACCEPT, document ? DOCUMENT_ACCEPT : SUBRESOURCE_ACCEPT);
            // A GET has no body, and sending a content type for one makes this look unlike a browser to any
            // middlebox that inspects it.
            headers.remove(HttpHeaders.CONTENT_TYPE);
        };
    }
}
