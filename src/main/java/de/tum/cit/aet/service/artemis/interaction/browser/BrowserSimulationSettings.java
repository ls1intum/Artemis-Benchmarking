package de.tum.cit.aet.service.artemis.interaction.browser;

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
 *                                Calibrated against the load balancer's own access log, which is what Artemis actually
 *                                served, rather than the browser's idea of what it fetched: two independent traces of
 *                                the same exam both put a session at 538 distinct static files and 20.4 MB. A
 *                                simulated exam journey navigates eight times — dashboard, course, exam, one per
 *                                exercise, summary — and its app shell is 141 files, leaving 397 for those eight.
 *                                Because a navigation takes whole bundles it overshoots its budget by about a quarter,
 *                                so the two measured points (a budget of 30 produced 419 files per student, 37
 *                                produced 509) put the value for 538 at 39.
 *                                <p>
 *                                The <em>total</em> is what this reproduces. A real client front-loads far more sharply
 *                                than an even spread: the traces put 203 files on entering the exams tab and 128 on
 *                                entering the conduction view, with the exercise views costing nothing at all because
 *                                their editors already arrived. Which chunk belongs to which route cannot be derived
 *                                without executing the router, so the simulation spreads them evenly and produces a
 *                                smoother profile over the session than a browser does.
 *                                <p>
 *                                The file names themselves are always discovered from the running server.
 */
public record BrowserSimulationSettings(
    boolean staticResourcesEnabled,
    int coldCachePercentage,
    int maxAssets,
    int fetchConcurrency,
    int autoSavesPerExercise,
    int assetsPerNavigation
) {
    public static final boolean DEFAULT_STATIC_RESOURCES_ENABLED = true;
    public static final int DEFAULT_COLD_CACHE_PERCENTAGE = 100;
    public static final int DEFAULT_MAX_ASSETS = 2000;
    public static final int DEFAULT_FETCH_CONCURRENCY = 6;
    public static final int DEFAULT_AUTO_SAVES_PER_EXERCISE = 4;
    public static final int DEFAULT_ASSETS_PER_NAVIGATION = 39;

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
            DEFAULT_ASSETS_PER_NAVIGATION
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
            DEFAULT_ASSETS_PER_NAVIGATION
        );
    }
}
