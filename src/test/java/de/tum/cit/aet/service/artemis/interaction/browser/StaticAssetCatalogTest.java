package de.tum.cit.aet.service.artemis.interaction.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

class StaticAssetCatalogTest {

    @BeforeEach
    void clearCatalogs() {
        StaticAssetCatalog.clear();
    }

    @Test
    void discoversTheShellFromIndexHtmlAndFollowsChunkReferences() {
        Queue<String> requested = new ConcurrentLinkedQueue<>();
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> {
                requested.add("/");
                return html(
                    """
                    <html><head>
                      <link rel="stylesheet" href="styles-GUNTQYLE.css">
                      <link rel="modulepreload" href="chunk-AAAAAAAA.js">
                      <script src="main-Z6DX4AB6.js" type="module"></script>
                    </head><body></body></html>
                    """
                );
            })
            // main references a chunk that is not preloaded: that is a lazily loaded route.
            .GET("/main-Z6DX4AB6.js", request -> {
                requested.add("/main-Z6DX4AB6.js");
                return javascript("import\"./chunk-AAAAAAAA.js\";const r=()=>import(\"./chunk-LAZYCHNK.js\");");
            })
            .GET("/chunk-AAAAAAAA.js", request -> {
                requested.add("/chunk-AAAAAAAA.js");
                return javascript("export const wasm=\"/assets/embedpdf/pdfium.wasm\";");
            })
            .GET("/chunk-LAZYCHNK.js", request -> {
                requested.add("/chunk-LAZYCHNK.js");
                return javascript("export const x=1;");
            })
            .GET("/styles-GUNTQYLE.css", request -> {
                requested.add("/styles-GUNTQYLE.css");
                return css("@font-face{src:url(fonts/roboto.woff2)}");
            })
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://localhost", webClient(router), 100);

        // The shell is what index.html names plus everything those files import statically, in request order.
        assertEquals(
            List.of("styles-GUNTQYLE.css", "chunk-AAAAAAAA.js", "main-Z6DX4AB6.js", "fonts/roboto.woff2", "assets/embedpdf/pdfium.wasm"),
            catalog.appShell()
        );
        assertTrue(
            catalog.routeBundles().stream().flatMap(List::stream).anyMatch("chunk-LAZYCHNK.js"::equals),
            "a dynamic import is a lazily loaded route"
        );
        assertTrue(catalog.allAssets().contains("assets/embedpdf/pdfium.wasm"), "should pick up assets referenced from JavaScript");
        assertTrue(catalog.allAssets().contains("fonts/roboto.woff2"), "should pick up fonts referenced from CSS");
        assertFalse(catalog.isEmpty());
    }

    @Test
    void doesNotDiscoverTheSameAssetTwice() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request -> javascript("import\"./chunk-BBBBBBBB.js\";import\"./chunk-BBBBBBBB.js\";"))
            .GET("/chunk-BBBBBBBB.js", request -> javascript("import\"./main-AAAAAAAA.js\";"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://localhost", webClient(router), 100);

        assertEquals(List.of("main-AAAAAAAA.js", "chunk-BBBBBBBB.js"), catalog.appShell(), "static imports load with their parent");
        assertEquals(List.of(), catalog.routeBundles());
        assertEquals(2, catalog.allAssets().size(), "a cycle must not add the entry bundle a second time");
    }

    @Test
    void ignoresReferencesThatAreNotRequestsToArtemis() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request ->
                html(
                    """
                    <link rel="stylesheet" href="https://fonts.example.com/other-AAAAAAAA.css">
                    <img src="data:image/png;base64,iVBORw0KGgo=">
                    <script src="main-AAAAAAAA.js"></script>
                    """
                )
            )
            .GET("/main-AAAAAAAA.js", request -> javascript("export const x=1;"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://localhost", webClient(router), 100);

        assertEquals(List.of("main-AAAAAAAA.js"), catalog.appShell());
    }

    @Test
    void stopsAtTheAssetCeiling() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request ->
                javascript("import\"./chunk-BBBBBBBB.js\";import\"./chunk-CCCCCCCC.js\";import\"./chunk-DDDDDDDD.js\";")
            )
            .GET("/chunk-BBBBBBBB.js", request -> javascript("export const x=1;"))
            .GET("/chunk-CCCCCCCC.js", request -> javascript("export const x=1;"))
            .GET("/chunk-DDDDDDDD.js", request -> javascript("export const x=1;"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://localhost", webClient(router), 2);

        assertEquals(2, catalog.allAssets().size());
    }

    @Test
    void returnsAnEmptyCatalogWhenTheServerCannotBeRead() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/nothing", request -> ServerResponse.ok().build())
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://localhost", webClient(router), 100);

        assertTrue(catalog.isEmpty());
    }

    private static reactor.core.publisher.Mono<ServerResponse> html(String body) {
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(body);
    }

    private static reactor.core.publisher.Mono<ServerResponse> javascript(String body) {
        return ServerResponse.ok().contentType(MediaType.valueOf("text/javascript")).bodyValue(body);
    }

    private static reactor.core.publisher.Mono<ServerResponse> css(String body) {
        return ServerResponse.ok().contentType(MediaType.valueOf("text/css")).bodyValue(body);
    }

    static WebClient webClient(RouterFunction<ServerResponse> router) {
        return WebClient.builder()
            .baseUrl("http://localhost")
            .clientConnector(new HttpHandlerConnector(RouterFunctions.toHttpHandler(router)))
            .build();
    }
}
