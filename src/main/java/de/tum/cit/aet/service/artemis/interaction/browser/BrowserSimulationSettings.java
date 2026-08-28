package de.tum.cit.aet.service.artemis.interaction.browser;

import java.util.List;

/**
 * How closely a simulated student should imitate a real browser.
 *
 * @param staticResourcesEnabled  whether students download the client bundle (JavaScript, CSS, fonts, images) the way a
 *                                browser does. A trace of a real exam session showed 547 of its 632 requests and
 *                                20 of its 20.5 MB were static assets, so a simulation without them reproduces the
 *                                REST mix but almost none of the bytes.
 * @param coldCachePercentage     the share of students who arrive with an empty browser cache and therefore download
 *                                the whole bundle. The remainder start warm and fetch only what a browser revalidates,
 *                                because Angular emits content-hashed filenames that are cached for a year.
 * @param maxAssets               safety ceiling on how many assets one discovery run may collect
 * @param fetchConcurrency        how many assets a student downloads at once, mirroring a browser's per-origin
 *                                connection limit
 * @param autoSavesPerExercise    how often the open submission is written back while the student works on an exercise.
 *                                The real client saves every 30 s while the submission is dirty; four saves matches a
 *                                two-minute stay on one exercise.
 * @param assetsPerNavigation     roughly how many files one navigation pulls in. Angular splits a view across several
 *                                chunks — a sidebar, its cards, an editor — so opening one view costs far more than one
 *                                chunk, and bundles vary from one file to dozens. Budgeting in files rather than in
 *                                bundles keeps a navigation the same size whatever the bundler emitted.
 *                                <p>
                                <p>
 *                                Derived from two browser traces of one exam, taken against a <em>local</em> Artemis:
 *                                both put a session at 538 distinct static files and 20.4 MB, measured from the load
 *                                balancer's access log rather than the browser's idea of what it fetched. Working back
 *                                from a 141-file shell and eight navigations, and allowing the quarter a navigation
 *                                overshoots by because it takes whole bundles, put the value at 39.
 *                                <p>
 *                                Treat that number as a ceiling rather than a calibration. It came from a different
 *                                server than the ones this tool points at — staging1 ships 802 files and 11.89 MB
 *                                gzipped — and since routes a student cannot open are no longer downloaded at all, a
 *                                journey now reaches every route left in the catalog before the budget binds. Re-derive
 *                                it from a trace of the server under test before relying on it again.
 *                                <p>
 *                                The <em>total</em> is what this reproduces. A real client front-loads far more sharply
 *                                than an even spread: the traces put 203 files on entering the exams tab and 128 on
 *                                entering the conduction view, with the exercise views costing nothing at all because
 *                                their editors already arrived. Which chunk belongs to which route cannot be derived
 *                                without executing the router, so the simulation spreads them evenly and produces a
 *                                smoother profile over the session than a browser does.
 *                                <p>
 *                                The file names themselves are always discovered from the running server.
 * @param nonStudentRoutes        name fragments of top-level routes a student cannot open, left out of the bundle a
 *                                student downloads. Angular names its lazy routes after their source file, so these
 *                                stay valid across Artemis builds even though the hashes do not.
 * @param exerciseSkipPercentage  the share of exercises a student opens without answering. Every simulated student
 *                                used to submit to every exercise the same number of times, which no cohort does: some
 *                                students run out of time, some skip a question, some read one and move on. The
 *                                student still navigates to the exercise, because looking at it is what they do; only
 *                                the submission is left unwritten. Programming exercises are never skipped, since the
 *                                clone and push they cause are the most expensive path a run measures and dropping
 *                                them at random would quietly change what the benchmark reports.
 * @param serverTimeCallsPerNavigation how many times opening a view asks the server for the time. The client has no
 *                                single clock component: several of them ask on initialisation, so the calls arrive in
 *                                a burst per view rather than on a timer. The traces show 31 calls per session,
 *                                clustered on the six page loads at four to seven each and nothing at all while the
 *                                student sits idle. Three per navigation puts a simulated session at 28.
 */
public record BrowserSimulationSettings(
    boolean staticResourcesEnabled,
    int coldCachePercentage,
    int maxAssets,
    int fetchConcurrency,
    int autoSavesPerExercise,
    int assetsPerNavigation,
    List<String> nonStudentRoutes,
    int exerciseSkipPercentage,
    int serverTimeCallsPerNavigation
) {
    public static final boolean DEFAULT_STATIC_RESOURCES_ENABLED = true;
    public static final int DEFAULT_COLD_CACHE_PERCENTAGE = 100;
    public static final int DEFAULT_MAX_ASSETS = 2000;
    public static final int DEFAULT_FETCH_CONCURRENCY = 6;
    public static final int DEFAULT_AUTO_SAVES_PER_EXERCISE = 4;
    public static final int DEFAULT_ASSETS_PER_NAVIGATION = 39;
    public static final int DEFAULT_EXERCISE_SKIP_PERCENTAGE = 10;

    /** @see StaticAssetCatalog for why these are excluded and why matching on the name is safe. */
    public static final List<String> DEFAULT_NON_STUDENT_ROUTES = StaticAssetCatalog.DEFAULT_NON_STUDENT_ROUTES;
    public static final int DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION = 3;

    public BrowserSimulationSettings {
        if (coldCachePercentage < 0 || coldCachePercentage > 100) {
            throw new IllegalArgumentException("coldCachePercentage must be between 0 and 100, was " + coldCachePercentage);
        }
        if (maxAssets < 1) {
            throw new IllegalArgumentException("maxAssets must be positive, was " + maxAssets);
        }
        if (fetchConcurrency < 1) {
            throw new IllegalArgumentException("fetchConcurrency must be positive, was " + fetchConcurrency);
        }
        if (autoSavesPerExercise < 1) {
            throw new IllegalArgumentException("autoSavesPerExercise must be at least one, was " + autoSavesPerExercise);
        }
        if (assetsPerNavigation < 1) {
            throw new IllegalArgumentException("assetsPerNavigation must be at least one, was " + assetsPerNavigation);
        }
        if (nonStudentRoutes == null) {
            throw new IllegalArgumentException("nonStudentRoutes must not be null");
        }
        if (exerciseSkipPercentage < 0 || exerciseSkipPercentage > 100) {
            throw new IllegalArgumentException("exerciseSkipPercentage must be between 0 and 100, was " + exerciseSkipPercentage);
        }
        if (serverTimeCallsPerNavigation < 0) {
            throw new IllegalArgumentException("serverTimeCallsPerNavigation must not be negative, was " + serverTimeCallsPerNavigation);
        }
    }

    /**
     * The settings a simulation uses when nothing is configured.
     *
     * @return the default settings
     */
    public static BrowserSimulationSettings defaults() {
        return new BrowserSimulationSettings(
            DEFAULT_STATIC_RESOURCES_ENABLED,
            DEFAULT_COLD_CACHE_PERCENTAGE,
            DEFAULT_MAX_ASSETS,
            DEFAULT_FETCH_CONCURRENCY,
            DEFAULT_AUTO_SAVES_PER_EXERCISE,
            DEFAULT_ASSETS_PER_NAVIGATION,
            DEFAULT_NON_STUDENT_ROUTES,
            DEFAULT_EXERCISE_SKIP_PERCENTAGE,
            DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION
        );
    }

    /**
     * Settings that leave the client bundle alone, for callers that only want the REST traffic.
     *
     * @return settings with static resource fetching switched off
     */
    public static BrowserSimulationSettings withoutStaticResources() {
        return new BrowserSimulationSettings(
            false,
            DEFAULT_COLD_CACHE_PERCENTAGE,
            DEFAULT_MAX_ASSETS,
            DEFAULT_FETCH_CONCURRENCY,
            DEFAULT_AUTO_SAVES_PER_EXERCISE,
            DEFAULT_ASSETS_PER_NAVIGATION,
            DEFAULT_NON_STUDENT_ROUTES,
            DEFAULT_EXERCISE_SKIP_PERCENTAGE,
            DEFAULT_SERVER_TIME_CALLS_PER_NAVIGATION
        );
    }
}
