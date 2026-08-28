package de.tum.cit.aet.service.artemis.interaction.browser;

import static de.tum.cit.aet.domain.RequestType.STATIC_RESOURCE;
import static java.time.ZonedDateTime.now;

import de.tum.cit.aet.domain.RequestStat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Downloads the client bundle for one simulated student, the way that student's browser would.
 * <p>
 * Each student owns one of these and it keeps their browser cache: an asset downloaded when the student entered the
 * course is not downloaded again when they enter the exam. Artemis serves content-hashed filenames with a one-year
 * cache lifetime, so a browser that already holds a chunk makes no request for it at all — which is why a warm student
 * costs almost nothing and a cold one costs about 20 MB.
 * <p>
 * The bundle is consumed in slices rather than all at once. A real browser downloads the shell on the first page load
 * and then pulls route chunks as the student navigates: entering the exam loads the conduction view, opening the
 * modeling exercise loads the diagram editor, and so on. Handing out the discovered assets a slice at a time
 * reproduces that spread over the session without needing to know which chunk belongs to which Artemis route — a
 * mapping that only exists inside the compiled bundle and would need re-deriving for every Artemis release.
 * <p>
 * The slices are equal, which a browser's are not: traced sessions put most of the lazily loaded files on two of the
 * eight navigations and nothing at all on the exercise views. The session totals match; the profile within it is
 * smoother than a real client's.
 */
public class StaticResourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceFetcher.class);

    private static final Duration ASSET_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient webClient;
    private final StaticAssetCatalog catalog;
    private final int fetchConcurrency;
    private final int assetsPerNavigation;
    private final boolean coldCache;

    /** Assets this student's browser already holds. */
    private final Set<String> cached = Collections.synchronizedSet(new HashSet<>());

    /** How many of {@link StaticAssetCatalog#routeBundles()} this student has navigated to. */
    private int routeCursor = 0;

    public StaticResourceFetcher(WebClient webClient, StaticAssetCatalog catalog, BrowserSimulationSettings settings) {
        this(webClient, catalog, settings, ThreadLocalRandom.current().nextInt(100) < settings.coldCachePercentage());
    }

    StaticResourceFetcher(WebClient webClient, StaticAssetCatalog catalog, BrowserSimulationSettings settings, boolean coldCache) {
        this.webClient = webClient;
        this.catalog = catalog;
        this.fetchConcurrency = settings.fetchConcurrency();
        this.assetsPerNavigation = settings.assetsPerNavigation();
        this.coldCache = coldCache;
        if (!coldCache) {
            // A returning student already holds every hashed file, so their browser asks for none of them.
            this.cached.addAll(catalog.allAssets());
        }
    }

    /**
     * Whether this student arrived with an empty cache and therefore downloads the whole bundle.
     *
     * @return true for a cold browser
     */
    public boolean isColdCache() {
        return coldCache;
    }

    /**
     * Loads the application shell, as a browser does on the first page it opens.
     * <p>
     * index.html itself is requested every time, even by a warm browser: it is the one file Artemis must serve
     * uncached, because it is what points at the current bundle.
     *
     * @return one stat per request made
     */
    public List<RequestStat> loadAppShell() {
        List<RequestStat> stats = new ArrayList<>();
        stats.add(fetch("").block(ASSET_TIMEOUT));
        stats.addAll(fetchAll(catalog.appShell()));
        return stats;
    }

    /**
     * Loads everything the journey ahead will need, in one go.
     * <p>
     * A browser interleaves this with the student's clicks, and the simulation used to do the same. At a few hundred
     * students that put the bundle downloads on top of the exam's own requests, so the two could not be told apart:
     * the REST timings carried the queueing of ten gigabytes of JavaScript. Downloading first and measuring afterwards
     * keeps each phase readable, and the student's cache means the navigations that follow cost nothing extra.
     *
     * @param navigations how many views the journey will open, which decides how much of the bundle it would reach
     * @return one stat per request made
     */
    public List<RequestStat> loadWholeJourney(int navigations) {
        List<RequestStat> stats = new ArrayList<>(loadAppShell());
        for (int navigation = 0; navigation < navigations; navigation++) {
            stats.addAll(loadRouteChunks());
        }
        // Rewind, so that the student then walks the very views that were just downloaded. Without this the
        // navigations during the exam carry the cursor onwards into bundles the journey never reaches, and the run
        // downloads the whole catalogue instead of a session's worth of it: a 500-user run against staging1 fetched
        // 974 files per student where a traced browser fetches 538.
        routeCursor = 0;
        return stats;
    }

    /**
     * Loads one lazily loaded route, as a browser does when the student navigates to a view it has not opened yet.
     * <p>
     * One navigation costs a view's worth of files, not a share of the whole application: a student opens a handful of
     * Artemis' views, and downloading the rest would overstate the load rather than reproduce it.
     *
     * @return one stat per request made, empty once the student has visited every route the bundle has
     */
    public List<RequestStat> loadRouteChunks() {
        List<List<String>> routes = catalog.routeBundles();
        List<String> navigation = new ArrayList<>();
        // Whole bundles, until the navigation has pulled about as many files as one costs in a browser. Stopping mid
        // bundle would leave a view half loaded, which no browser does.
        while (routeCursor < routes.size() && navigation.size() < assetsPerNavigation) {
            navigation.addAll(routes.get(routeCursor++));
        }
        return navigation.isEmpty() ? List.of() : fetchAll(navigation);
    }

    /**
     * Downloads assets that are not in this browser's cache yet, several at a time.
     *
     * @param assets the assets to consider
     * @return one stat per request actually made
     */
    private List<RequestStat> fetchAll(List<String> assets) {
        List<String> toFetch = assets.stream().filter(cached::add).toList();
        if (toFetch.isEmpty()) {
            return List.of();
        }
        try {
            List<RequestStat> stats = Flux.fromIterable(toFetch)
                .flatMap(this::fetch, fetchConcurrency)
                .collectList()
                .block(ASSET_TIMEOUT.multipliedBy(2));
            return stats == null ? List.of() : stats;
        } catch (Exception e) {
            log.debug("Downloading static resources failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Requests a single asset and discards the body, which is what matters here: the point is the bytes leaving the
     * server, not what they contain.
     * <p>
     * Deferred rather than assembled eagerly, for two reasons. The request must not start until the caller has a free
     * connection slot, so that {@code fetchConcurrency} really is the number in flight; and the clock has to start
     * then too, or an asset that waited its turn would be recorded with the wait included and the measurement would
     * describe the queue instead of the server.
     *
     * @param asset path relative to the server root
     * @return the stat for the request, whether it succeeded or not
     */
    private Mono<RequestStat> fetch(String asset) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return (
                webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path(asset.isEmpty() ? "/" : "/" + asset).build())
                    .headers(BrowserHeaders.forDiscardedAsset(asset))
                    .retrieve()
                    .toBodilessEntity()
                    .onErrorResume(error -> {
                        log.debug("Could not download {}: {}", asset, error.getMessage());
                        return Mono.empty();
                    })
                    // A failed download is still a request the server handled, so it is measured either way.
                    .then(Mono.fromSupplier(() -> new RequestStat(now(), System.nanoTime() - start, STATIC_RESOURCE)))
            );
        });
    }
}
