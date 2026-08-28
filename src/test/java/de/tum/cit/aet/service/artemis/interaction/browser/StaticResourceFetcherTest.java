package de.tum.cit.aet.service.artemis.interaction.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.domain.RequestType;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

class StaticResourceFetcherTest {

    private final Queue<String> requested = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void clearCatalogs() {
        StaticAssetCatalog.clear();
        requested.clear();
    }

    @Test
    void aColdBrowserDownloadsTheWholeBundleOverTheJourney() {
        StaticAssetCatalog catalog = catalog();
        WebClient webClient = webClient();
        // Discovering the bundle reads the same files; only what the student then downloads is under test here.
        requested.clear();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient, catalog, BrowserSimulationSettings.defaults(), true);

        List<RequestStat> shell = fetcher.loadAppShell();

        // index.html plus every asset it references
        assertEquals(1 + catalog.appShell().size(), shell.size());
        assertTrue(shell.stream().allMatch(stat -> stat.type() == RequestType.STATIC_RESOURCE));

        int lazyDownloads = 0;
        for (int navigation = 0; navigation < catalog.routeBundles().size(); navigation++) {
            lazyDownloads += fetcher.loadRouteChunks().size();
        }

        assertEquals(
            catalog.routeBundles().stream().mapToInt(List::size).sum(),
            lazyDownloads,
            "visiting every route downloads every route bundle"
        );
        assertEquals(1 + catalog.allAssets().size(), requested.size(), "every file is requested exactly once");
    }

    @Test
    void loadingTheJourneyUpFrontCostsTheSameAsLoadingItAsYouGo() {
        StaticAssetCatalog catalog = catalog();
        WebClient webClient = webClient();
        requested.clear();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient, catalog, BrowserSimulationSettings.defaults(), true);

        List<RequestStat> upFront = fetcher.loadWholeJourney(catalog.routeBundles().size());

        assertEquals(1 + catalog.allAssets().size(), upFront.size(), "the shell and every route bundle the journey reaches");
        assertEquals(1 + catalog.allAssets().size(), requested.size(), "every file is requested exactly once");
        assertEquals(List.of(), fetcher.loadRouteChunks(), "navigating afterwards finds everything in the cache");
    }

    @Test
    void loadingTheJourneyUpFrontDoesNotReachPastIt() {
        // The catalogue holds more views than one journey opens, which is the real case: a traced session touches 538
        // of the bundle's roughly thousand files. The navigations that follow the preload must revisit what was
        // downloaded, not carry on into views the student never opens.
        StaticAssetCatalog catalog = catalog();
        BrowserSimulationSettings oneFilePerNavigation = new BrowserSimulationSettings(true, 100, 100, 6, 4, 1, List.of(), 0, 0);
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient(), catalog, oneFilePerNavigation, true);
        requested.clear();

        assertEquals(4, catalog.routeBundles().size(), "more routes than the two this journey navigates");
        List<RequestStat> upFront = fetcher.loadWholeJourney(2);
        int afterPreload = requested.size();

        assertEquals(1 + catalog.appShell().size() + 2, upFront.size(), "the shell plus the two routes the journey opens");
        assertEquals(List.of(), fetcher.loadRouteChunks(), "the first navigation revisits the first preloaded route");
        assertEquals(List.of(), fetcher.loadRouteChunks(), "and the second revisits the second");
        assertEquals(afterPreload, requested.size(), "no file is fetched after the preload");
    }

    @Test
    void loadingTheJourneyUpFrontStopsAtTheEndOfTheBundle() {
        StaticAssetCatalog catalog = catalog();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient(), catalog, BrowserSimulationSettings.defaults(), true);
        requested.clear();

        // More navigations than the bundle has routes: the extra ones find nothing rather than wrapping around.
        List<RequestStat> upFront = fetcher.loadWholeJourney(catalog.routeBundles().size() + 5);

        assertEquals(1 + catalog.allAssets().size(), upFront.size());
    }

    @Test
    void aWarmBrowserOnlyAsksForIndexHtml() {
        StaticAssetCatalog catalog = catalog();
        WebClient webClient = webClient();
        requested.clear();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient, catalog, BrowserSimulationSettings.defaults(), false);

        List<RequestStat> shell = fetcher.loadAppShell();
        List<RequestStat> chunks = fetcher.loadRouteChunks();

        assertEquals(1, shell.size(), "a warm cache still revalidates index.html, which is never cached");
        assertEquals(List.of(), chunks);
        assertEquals(List.of("/"), List.copyOf(requested));
    }

    @Test
    void anAssetIsNeverDownloadedTwice() {
        StaticAssetCatalog catalog = catalog();
        WebClient webClient = webClient();
        requested.clear();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient, catalog, BrowserSimulationSettings.defaults(), true);

        fetcher.loadAppShell();
        int afterShell = requested.size();
        // A second page load re-requests index.html but nothing else: the bundle is in the cache now.
        List<RequestStat> secondLoad = fetcher.loadAppShell();

        assertEquals(1, secondLoad.size());
        assertEquals(afterShell + 1, requested.size());
    }

    @Test
    void oneNavigationPullsAboutTheConfiguredNumberOfFiles() {
        StaticAssetCatalog catalog = catalog();
        BrowserSimulationSettings twoFilesPerNavigation = new BrowserSimulationSettings(true, 100, 100, 6, 4, 2, List.of(), 0, 0);
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient(), catalog, twoFilesPerNavigation, true);
        fetcher.loadAppShell();

        // Four single-file routes and a budget of two files: two navigations cover them, a third finds nothing left.
        assertEquals(4, catalog.routeBundles().size());
        assertEquals(2, fetcher.loadRouteChunks().size());
        assertEquals(2, fetcher.loadRouteChunks().size());
        assertEquals(0, fetcher.loadRouteChunks().size(), "no routes left to visit");
    }

    @Test
    void aNavigationStopsAtTheLastRouteRatherThanRunningOff() {
        StaticAssetCatalog catalog = catalog();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient(), catalog, BrowserSimulationSettings.defaults(), true);
        fetcher.loadAppShell();

        // The default budget is larger than everything left, so one navigation takes the remaining four routes.
        assertEquals(4, fetcher.loadRouteChunks().size());
        assertEquals(0, fetcher.loadRouteChunks().size());
    }

    @Test
    void anAssetThatFailsDuringTheRunStillCountsAsARequest() {
        // The catalog only contains files the server served at discovery time, so this is the redeploy case: the file
        // was there when the bundle was read and is gone when the student asks for it. That is still a request Artemis
        // handled, so it has to be measured rather than quietly dropped.
        RouterFunction<ServerResponse> serving = RouterFunctions.route()
            .GET("/", request ->
                ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue("<script src=\"main-AAAAAAAA.js\"></script>")
            )
            .GET("/main-AAAAAAAA.js", request ->
                ServerResponse.ok().contentType(MediaType.valueOf("text/javascript")).bodyValue("export const x=1;")
            )
            .build();
        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://redeployed", StaticAssetCatalogTest.webClient(serving), 100);
        assertEquals(List.of("main-AAAAAAAA.js"), catalog.appShell());

        RouterFunction<ServerResponse> gone = RouterFunctions.route()
            .GET("/", request ->
                ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue("<script src=\"main-BBBBBBBB.js\"></script>")
            )
            .build();
        StaticResourceFetcher fetcher = new StaticResourceFetcher(
            StaticAssetCatalogTest.webClient(gone),
            catalog,
            BrowserSimulationSettings.defaults(),
            true
        );

        List<RequestStat> stats = fetcher.loadAppShell();

        assertEquals(2, stats.size(), "index.html plus the file that has since disappeared");
    }

    @Test
    void downloadsAssetsInParallelRatherThanOneAfterTheOther() {
        // Twelve assets behind a 120 ms server. Six at a time should take about two rounds; one at a time would take
        // twelve, so a generous ceiling still separates the two conclusively.
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request -> slow("/", MediaType.TEXT_HTML, shellReferencing(12)))
            .GET("/asset-{name}.js", request ->
                slow("/asset-" + request.pathVariable("name") + ".js", MediaType.valueOf("text/javascript"), "export const x=1;")
            )
            .build();
        WebClient webClient = StaticAssetCatalogTest.webClient(router);
        StaticAssetCatalog catalog = StaticAssetCatalog.forServer("http://parallel", webClient, 100);
        assertEquals(12, catalog.appShell().size());
        StaticResourceFetcher fetcher = new StaticResourceFetcher(webClient, catalog, BrowserSimulationSettings.defaults(), true);

        long start = System.nanoTime();
        List<RequestStat> stats = fetcher.loadAppShell();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(13, stats.size());
        assertTrue(elapsedMillis < 900, "twelve assets six at a time should not take " + elapsedMillis + " ms");
    }

    /** An index.html naming the given number of shell assets. */
    private static String shellReferencing(int count) {
        StringBuilder html = new StringBuilder();
        for (int index = 0; index < count; index++) {
            html.append("<script src=\"asset-AAAAAA")
                .append((char) ('a' + index))
                .append(".js\"></script>");
        }
        return html.toString();
    }

    private reactor.core.publisher.Mono<ServerResponse> slow(String path, MediaType type, String body) {
        requested.add(path);
        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(120)).then(
            ServerResponse.ok().contentType(type).bodyValue(body)
        );
    }

    /** index.html referencing one shell script, which in turn references four lazily loaded chunks. */
    private StaticAssetCatalog catalog() {
        return StaticAssetCatalog.forServer("http://localhost", webClient(), 100);
    }

    private WebClient webClient() {
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .GET("/", request ->
                record("/", ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue("<script src=\"main-AAAAAAAA.js\"></script>"))
            )
            .GET("/main-AAAAAAAA.js", request ->
                record(
                    "/main-AAAAAAAA.js",
                    ServerResponse.ok()
                        .contentType(MediaType.valueOf("text/javascript"))
                        .bodyValue(
                            "import(\"./chunk-BBBBBBBB.js\");import(\"./chunk-CCCCCCCC.js\");import(\"./chunk-DDDDDDDD.js\");import(\"./chunk-EEEEEEEE.js\");"
                        )
                )
            )
            .GET("/chunk-{name}", request ->
                record(
                    "/chunk-" + request.pathVariable("name"),
                    ServerResponse.ok().contentType(MediaType.valueOf("text/javascript")).bodyValue("export const x=1;")
                )
            )
            .build();
        return StaticAssetCatalogTest.webClient(router);
    }

    private reactor.core.publisher.Mono<ServerResponse> record(String path, reactor.core.publisher.Mono<ServerResponse> response) {
        requested.add(path);
        return response;
    }
}
