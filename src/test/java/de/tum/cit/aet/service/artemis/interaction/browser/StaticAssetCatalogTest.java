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
            .GET("/fonts/roboto.woff2", request -> ServerResponse.ok().bodyValue("font"))
            .GET("/assets/embedpdf/pdfium.wasm", request -> ServerResponse.ok().bodyValue("wasm"))
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
    void leavesOutRoutesAStudentCannotOpen() {
        // Angular names its lazily loaded routes after their source file, so the readable half of the chunk name says
        // which view it is. Against staging1 the heaviest route in the whole bundle is exam-management, an instructor
        // console of 296 files and 3.69 MB, which the old weight ordering handed to every simulated student first.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request ->
                javascript(
                    "import(\"./courses.route-BBBBBBBB.js\");" +
                        "import(\"./exam-management.route-CCCCCCCC.js\");" +
                        "import(\"./admin.routes-DDDDDDDD.js\");"
                )
            )
            .GET("/courses.route-BBBBBBBB.js", request -> javascript("// the student's own course area"))
            .GET("/exam-management.route-CCCCCCCC.js", request -> javascript("// the instructor's console"))
            .GET("/admin.routes-DDDDDDDD.js", request -> javascript("// the admin area"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://student-routes", webClient(router), 100);

        List<String> routes = catalog.routeBundles().stream().flatMap(List::stream).toList();
        assertEquals(List.of("courses.route-BBBBBBBB.js"), routes, "only the view a student can reach");
        assertFalse(catalog.allAssets().contains("exam-management.route-CCCCCCCC.js"));
        assertFalse(catalog.allAssets().contains("admin.routes-DDDDDDDD.js"));
    }

    @Test
    void keepsChunksAnExcludedRouteHappensToShareWithAStudentRoute() {
        // The crawl gives each file to the first bundle that reaches it, so if an excluded route is discovered first it
        // claims everything it imports — including chunks a student route needs too. Dropping that bundle afterwards
        // would take the shared chunk with it and leave the student unable to load a view they can reach.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request ->
                javascript(
                    // The instructor route first, so it is the one that reaches the shared chunk first.
                    "import(\"./exam-management.route-CCCCCCCC.js\");" + "import(\"./courses.route-BBBBBBBB.js\");"
                )
            )
            .GET("/exam-management.route-CCCCCCCC.js", request -> javascript("import\"./shared-EEEEEEEE.js\";"))
            .GET("/courses.route-BBBBBBBB.js", request -> javascript("import\"./shared-EEEEEEEE.js\";"))
            .GET("/shared-EEEEEEEE.js", request -> javascript("// used by both"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://shared-chunk", webClient(router), 100);

        assertTrue(
            catalog.allAssets().contains("shared-EEEEEEEE.js"),
            "the student needs this chunk for their own view, whoever else imports it"
        );
        assertFalse(catalog.allAssets().contains("exam-management.route-CCCCCCCC.js"));
    }

    @Test
    void doesNotSpendTheAssetCeilingOnRoutesAStudentCannotOpen() {
        // The ceiling bounds how much one discovery may collect. Spending it on an instructor console — exam-management
        // alone is 296 files on staging1 — can truncate discovery before the student's own views are reached.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request ->
                javascript("import(\"./exam-management.route-CCCCCCCC.js\");" + "import(\"./courses.route-BBBBBBBB.js\");")
            )
            .GET("/exam-management.route-CCCCCCCC.js", request -> javascript("import\"./bulky-FFFFFFFF.js\";"))
            .GET("/bulky-FFFFFFFF.js", request -> javascript("// only the instructor needs this"))
            .GET("/courses.route-BBBBBBBB.js", request -> javascript("// the student's own course area"))
            .build();

        // Room for index.html, main, and three more files. The instructor route and its chunk must not consume it.
        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://ceiling", webClient(router), 3);

        assertTrue(
            catalog.allAssets().contains("courses.route-BBBBBBBB.js"),
            "the student's own view must survive the ceiling, whatever the instructor's views cost"
        );
    }

    @Test
    void keepsEveryRouteWhenNothingIsExcluded() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request -> javascript("import(\"./exam-management.route-CCCCCCCC.js\");"))
            .GET("/exam-management.route-CCCCCCCC.js", request -> javascript("// the instructor's console"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://everything", webClient(router), 100, List.of());

        assertEquals(1, catalog.routeBundles().size(), "an explicit empty exclusion list keeps the instructor views");
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
    void leavesOutFilesTheServerDoesNotServe() {
        // Angular names component stylesheets inside the bundle but compiles them into the JavaScript, so a chunk is
        // full of ".css" strings that are not files. Asking for them would spend a third of the run on 404s.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request -> javascript("import\"./chunk-BBBBBBBB.js\";const s=\"widget.component-CCCCCCCC.css\";"))
            .GET("/chunk-BBBBBBBB.js", request -> javascript("export const x=1;"))
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://partial", webClient(router), 100);

        assertEquals(List.of("main-AAAAAAAA.js", "chunk-BBBBBBBB.js"), catalog.appShell());
        assertFalse(
            catalog.allAssets().contains("widget.component-CCCCCCCC.css"),
            "a stylesheet the server does not serve is not an asset"
        );
    }

    @Test
    void keepsAnAssetTheServerFailsToServeForSomeOtherReason() {
        // Only an explicit 404 means absent. A transient 500 must not cost the catalog a file that is really there.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> html("<img src=\"logo/favicon.svg\"><script src=\"main-AAAAAAAA.js\"></script>"))
            .GET("/main-AAAAAAAA.js", request -> javascript("export const x=1;"))
            .GET("/logo/favicon.svg", request -> ServerResponse.status(500).build())
            .build();

        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://flaky", webClient(router), 100);

        assertTrue(catalog.allAssets().contains("logo/favicon.svg"));
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
