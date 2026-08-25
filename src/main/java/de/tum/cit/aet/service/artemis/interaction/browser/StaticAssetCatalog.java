package de.tum.cit.aet.service.artemis.interaction.browser;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The list of static files an Artemis client downloads, discovered from the running server.
 * <p>
 * The list is <em>never</em> hard-coded. Angular emits content-hashed filenames such as
 * {@code chunk-w-tWreXj.js}, which change on every Artemis build, so a checked-in list would be wrong the day after it
 * was written. Instead the catalog starts at {@code index.html}, takes the scripts and stylesheets it references, and
 * then follows the references those files make to further chunks, exactly as the browser's module loader does. The
 * result is whatever that particular Artemis version happens to ship.
 * <p>
 * Discovery runs once per server per tool lifetime, not once per student: the parsing is the benchmark's own work and
 * says nothing about Artemis. The downloading is what students do, and that is {@link StaticResourceFetcher}'s job.
 */
public final class StaticAssetCatalog {

    private static final Logger log = LoggerFactory.getLogger(StaticAssetCatalog.class);

    private static final Map<String, StaticAssetCatalog> CATALOGS = new ConcurrentHashMap<>();

    /** {@code src=} / {@code href=} of a resource-carrying tag in index.html, media included. */
    private static final Pattern HTML_REFERENCE = Pattern.compile(
        "<(?:script|link|img|source|video|audio|embed)\\b[^>]*?\\b(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * A content-hashed bundle file as emitted by the Angular CLI, for example {@code chunk-w-tWreXj.js},
     * {@code main-Z6DX4AB6.js} or {@code styles-GUNTQYLE.css}. Matched inside already downloaded JavaScript, which is
     * where the lazily loaded route chunks are referenced.
     */
    private static final Pattern HASHED_BUNDLE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*-[A-Za-z0-9_-]{6,12}\\.(?:js|css)");

    /**
     * A dynamic {@code import("./x.js")}, which is how the bundler expresses a lazily loaded route: the file is
     * fetched only when the student navigates there, not with its parent.
     */
    private static final Pattern DYNAMIC_IMPORT = Pattern.compile("import\\(\\s*[\"'`]\\.?/?([A-Za-z0-9._-]+\\.js)[\"'`]");

    /** An asset served from a well-known Artemis directory, for example {@code /assets/embedpdf/pdfium.wasm}. */
    private static final Pattern ASSET_PATH = Pattern.compile("[\"'](/?(?:assets|content|i18n|public)/[A-Za-z0-9._/-]+)[\"']");

    /** {@code url(...)} inside a stylesheet, which is how fonts and background images are pulled in. */
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)");

    /** Enough to hold the largest chunk Artemis ships, which is a few megabytes. */
    private static final int MAX_ASSET_SIZE_BYTES = 32 * 1024 * 1024;

    /** Files worth following for further references. Anything else is a leaf. */
    private static final Set<String> CRAWLABLE_SUFFIXES = Set.of(".js", ".css");

    private final List<String> appShell;
    private final List<List<String>> routeBundles;

    private StaticAssetCatalog(List<String> appShell, List<List<String>> routeBundles) {
        this.appShell = List.copyOf(appShell);
        this.routeBundles = routeBundles.stream().map(List::copyOf).toList();
    }

    /**
     * The catalog for one Artemis server, discovering it on first use.
     * <p>
     * Discovery failures are not fatal: a simulation that cannot read the client bundle should still measure the REST
     * traffic rather than abort, so this falls back to an empty catalog and says so in the log.
     *
     * @param artemisUrl the server the catalog belongs to, used as the cache key
     * @param webClient  a client pointed at that server; only used on the first call
     * @param maxAssets  ceiling on how many assets one discovery run may collect
     * @return the catalog, possibly empty
     */
    public static StaticAssetCatalog forServer(String artemisUrl, WebClient webClient, int maxAssets) {
        return CATALOGS.computeIfAbsent(artemisUrl, url -> {
            try {
                StaticAssetCatalog catalog = discover(webClient, maxAssets);
                log.info(
                    "Discovered client bundle of {}: {} shell files, {} lazily loaded routes ({} files in total)",
                    url,
                    catalog.appShell.size(),
                    catalog.routeBundles.size(),
                    catalog.allAssets().size()
                );
                return catalog;
            } catch (Exception e) {
                log.warn(
                    "Could not discover the client bundle of {}, students will not download static resources: {}",
                    url,
                    e.getMessage()
                );
                return new StaticAssetCatalog(List.of(), List.of());
            }
        });
    }

    /**
     * Forget every discovered catalog, so the next call re-reads the server.
     * <p>
     * Artemis is redeployed between benchmark runs often enough that a stale list of hashed filenames would produce a
     * wall of 404s. Callers that know a run is starting should drop the cache first.
     */
    public static void clear() {
        CATALOGS.clear();
    }

    private static StaticAssetCatalog discover(WebClient client, int maxAssets) {
        // The student's client is built for JSON responses and buffers at most 256 KB, but Artemis ships single chunks
        // of several megabytes. Reading one with the default limit fails, and a failed read looks exactly like a chunk
        // with no references, which silently shrinks the discovered bundle to almost nothing.
        WebClient webClient = client
            .mutate()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_ASSET_SIZE_BYTES))
            .build();
        String indexHtml = body(webClient, "");
        if (indexHtml == null) {
            throw new IllegalStateException("index.html could not be read");
        }

        Crawl crawl = new Crawl(webClient, maxAssets);
        // Everything index.html names is loaded before the app can render anything, together with whatever those files
        // import statically. Dynamic imports found on the way are routes: candidates for later, not part of the shell.
        List<String> shell = crawl.closureOf(referencesIn(indexHtml, HTML_REFERENCE, 1));

        // Each lazily loaded route costs its own file plus that file's static imports, which is exactly what the
        // browser fetches when the student opens the view.
        // Routes reached from a route count too: opening the exam view offers the exercise views, which offer their
        // editors. Walking the list as it grows finds those, where iterating a snapshot would stop after one level.
        List<List<String>> routeBundles = new ArrayList<>();
        for (int index = 0; index < crawl.routes().size() && !crawl.exhausted(); index++) {
            List<String> bundle = crawl.closureOf(List.of(crawl.routes().get(index)));
            if (!bundle.isEmpty()) {
                routeBundles.add(bundle);
            }
        }

        if (crawl.exhausted()) {
            log.warn("Stopped discovering the client bundle at the ceiling of {} assets", maxAssets);
        }

        // Heaviest first. A student sitting an exam opens the views that carry the editors — the code editor, the
        // diagram editor — and those are by far the largest chunks Artemis ships. Discovery order, by contrast, is an
        // artefact of how the bundler happened to emit the graph, and following it makes the simulated student download
        // the same number of files as a real one but only half the bytes.
        routeBundles.sort(Comparator.comparingLong(crawl::weightOf).reversed());

        return new StaticAssetCatalog(shell, routeBundles);
    }

    /**
     * One pass over the bundle's module graph, remembering what it has already seen so no file lands in two bundles.
     */
    private static final class Crawl {

        private final WebClient webClient;
        private final int maxAssets;
        private final Set<String> seen = new LinkedHashSet<>();
        private final List<String> routes = new ArrayList<>();
        private final Map<String, Integer> sizes = new HashMap<>();

        private Crawl(WebClient webClient, int maxAssets) {
            this.webClient = webClient;
            this.maxAssets = maxAssets;
        }

        /**
         * The given files plus everything they import statically, in the order a browser would request them.
         * Dynamic imports encountered on the way are recorded as routes instead of followed.
         *
         * @param roots where to start
         * @return the files to download for these roots, excluding anything an earlier call already claimed
         */
        private List<String> closureOf(List<String> roots) {
            List<String> closure = new ArrayList<>();
            Deque<String> queue = new ArrayDeque<>();
            for (String root : roots) {
                if (seen.add(root)) {
                    closure.add(root);
                    queue.add(root);
                }
            }
            while (!queue.isEmpty() && !exhausted()) {
                String asset = queue.poll();
                if (!isCrawlable(asset)) {
                    continue;
                }
                String content = body(webClient, asset);
                if (content == null) {
                    continue;
                }
                sizes.put(asset, content.length());
                for (String route : referencesIn(content, DYNAMIC_IMPORT, 1)) {
                    if (!routes.contains(route)) {
                        routes.add(route);
                    }
                }
                for (String reference : staticReferencesIn(content, asset)) {
                    if (exhausted()) {
                        break;
                    }
                    if (seen.add(reference)) {
                        closure.add(reference);
                        queue.add(reference);
                    }
                }
            }
            return closure;
        }

        /** Routes discovered so far, in the order the bundle mentions them. Grows while the crawl runs. */
        private List<String> routes() {
            return routes;
        }

        private boolean exhausted() {
            return seen.size() >= maxAssets;
        }

        /** Total size of a bundle, for ordering; files that could not be read count as nothing. */
        private long weightOf(List<String> bundle) {
            return bundle
                .stream()
                .mapToLong(asset -> sizes.getOrDefault(asset, 0))
                .sum();
        }
    }

    /**
     * References a file pulls in together with itself: static imports of other chunks, plus assets and stylesheet
     * urls. Dynamic imports are deliberately excluded — those are routes and are fetched on navigation.
     */
    private static List<String> staticReferencesIn(String content, String origin) {
        Set<String> dynamic = new LinkedHashSet<>(referencesIn(content, DYNAMIC_IMPORT, 1));
        List<String> found = new ArrayList<>();
        for (String reference : referencesIn(content, origin)) {
            if (!dynamic.contains(reference)) {
                found.add(reference);
            }
        }
        return found;
    }

    private static List<String> referencesIn(String content, String origin) {
        List<String> found = new ArrayList<>();
        // Bundle files are referenced by bare filename relative to the document root.
        Matcher bundles = HASHED_BUNDLE.matcher(content);
        while (bundles.find()) {
            found.add(normalize(bundles.group()));
        }
        found.addAll(referencesIn(content, ASSET_PATH, 1));
        if (origin.endsWith(".css")) {
            found.addAll(referencesIn(content, CSS_URL, 1));
        }
        return found;
    }

    private static List<String> referencesIn(String content, Pattern pattern, int group) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String reference = normalize(matcher.group(group));
            if (reference != null) {
                found.add(reference);
            }
        }
        return found;
    }

    /**
     * Turns a reference as written in the page into a path relative to the server root, or {@code null} for references
     * that are not a request to Artemis at all (inline data, another host, a bare fragment).
     */
    private static String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String trimmed = reference.trim();
        if (trimmed.startsWith("data:") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.contains("://")) {
            return null;
        }
        // Strip the query string: a cache-busting parameter does not make it a different file to fetch.
        int query = trimmed.indexOf('?');
        if (query >= 0) {
            trimmed = trimmed.substring(0, query);
        }
        while (trimmed.startsWith("./")) {
            trimmed = trimmed.substring(2);
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private static boolean isCrawlable(String asset) {
        return CRAWLABLE_SUFFIXES.stream().anyMatch(asset::endsWith);
    }

    private static String body(WebClient webClient, String path) {
        try {
            return webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(path.isEmpty() ? "/" : "/" + path).build())
                .headers(BrowserHeaders.forAsset(path))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.debug("Could not read {} while discovering the client bundle: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * The files index.html itself references and everything those import statically: the entry bundle, the global
     * stylesheet, the module preloads and their dependencies. Nothing renders until these have arrived.
     *
     * @return the app shell files, in the order a browser requests them
     */
    public List<String> appShell() {
        return appShell;
    }

    /**
     * The lazily loaded routes, each with the files that route pulls in. A student downloads one of these per
     * navigation, not all of them: nobody opens every view Artemis has.
     *
     * @return the route bundles, in the order the bundle mentions them
     */
    public List<List<String>> routeBundles() {
        return routeBundles;
    }

    /**
     * Every file in the bundle.
     *
     * @return the shell and all route bundles together
     */
    public List<String> allAssets() {
        List<String> all = new ArrayList<>(appShell);
        routeBundles.forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }

    /**
     * Whether discovery found anything at all.
     *
     * @return true if there is nothing to download
     */
    public boolean isEmpty() {
        return appShell.isEmpty() && routeBundles.isEmpty();
    }
}
