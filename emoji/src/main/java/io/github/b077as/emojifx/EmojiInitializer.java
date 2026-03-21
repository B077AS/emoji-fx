package io.github.b077as.emojifx;

import com.gluonhq.connect.converter.InputStreamIterableInputConverter;
import com.gluonhq.connect.provider.InputStreamListDataReader;
import com.gluonhq.connect.provider.ListDataReader;
import com.gluonhq.connect.source.BasicInputDataSource;
import com.gluonhq.connect.source.InputDataSource;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class EmojiInitializer {

    private static final Logger LOG = Logger.getLogger(EmojiInitializer.class.getName());

    private static final String FALLBACK_COMMIT = "2771d0b1b3af25c069086e68e38f901c3dda8bdf";

    private static final String GITHUB_API_COMMITS =
            "https://api.github.com/repos/iamcal/emoji-data/commits/master";
    private static final String EMOJI_JSON_URL =
            "https://raw.githubusercontent.com/iamcal/emoji-data/%s/emoji.json";
    private static final String SPRITE_URL =
            "https://raw.githubusercontent.com/iamcal/emoji-data/%s/sheets-clean/sheet_%s_%s_clean.png";

    private static final int[] SIZES = {20, 32, 64};
    private static final String DEFAULT_BASE_DIR =
            System.getProperty("user.home") + "/.emoji-fx";

    private static volatile String resolvedCommit;
    private static volatile EmojiVendor resolvedVendor;
    private static volatile Path resolvedBaseDir;

    private EmojiInitializer() {
    }

    /**
     * Returns the commit SHA used during the last successful initialization.
     */
    public static String getResolvedCommit() {
        return resolvedCommit;
    }

    /**
     * Returns the vendor used during the last successful initialization.
     */
    public static EmojiVendor getResolvedVendor() {
        return resolvedVendor;
    }

    /**
     * Returns the base storage directory used during the last successful initialization.
     */
    public static Path getResolvedBaseDir() {
        return resolvedBaseDir;
    }

    /**
     * Asynchronously initializes the emoji library for the given vendor,
     * using the default storage directory ({@code ~/.emoji-fx}).
     *
     * @param vendor the emoji image set to use
     * @return a future resolving to {@code true} on success, {@code false} on failure
     */
    public static CompletableFuture<Boolean> initialize(EmojiVendor vendor) {
        return initialize(vendor, Paths.get(DEFAULT_BASE_DIR));
    }

    /**
     * Asynchronously initializes the emoji library for the given vendor,
     * storing cached data under {@code baseDir}. Downloads emoji metadata and sprite
     * sheets if not already present, then removes any older cached versions for that vendor.
     *
     * @param vendor  the emoji image set to use
     * @param baseDir root directory under which vendor/commit subdirectories are created;
     *                must be writable (e.g. {@code Paths.get("/data/my-emoji-cache")})
     * @return a future resolving to {@code true} on success, {@code false} on failure
     * @throws IllegalArgumentException if {@code baseDir} is null
     */
    public static CompletableFuture<Boolean> initialize(EmojiVendor vendor, Path baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir must not be null");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String commit = resolveCommit(vendor, baseDir).orElse(null);
                if (commit == null) {
                    LOG.severe("No cached version available and GitHub API unreachable.");
                    return false;
                }

                Path commitDir = baseDir(vendor, commit, baseDir);
                Files.createDirectories(commitDir);

                ensureEmojiJsonAndCsv(commit, commitDir);
                ensureSprites(vendor, commit, commitDir);
                pruneOldCommits(vendor, commit, baseDir);

                resolvedCommit = commit;
                resolvedVendor = vendor;
                resolvedBaseDir = baseDir;

                EmojiLoaderFactory.setLoader(new DynamicEmojiSpriteLoader());
                return true;
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Emoji initialization failed", e);
                return false;
            }
        }, daemonExecutor());
    }

    /**
     * Resolves the commit SHA to use. Tries the GitHub API first, then falls back to
     * the most recently cached local version, and finally to the hardcoded
     * {@link #FALLBACK_COMMIT}.
     */
    private static Optional<String> resolveCommit(EmojiVendor vendor, Path baseDir) {
        try {
            return Optional.of(fetchLatestCommitFromGitHub());
        } catch (Exception e) {
            LOG.warning("GitHub API unreachable: " + e.getMessage()
                    + " — looking for cached version.");
            Optional<String> cached = findLatestCachedCommit(vendor, baseDir);
            if (cached.isPresent()) {
                return cached;
            }
            LOG.warning("No cache found — falling back to hardcoded commit: " + FALLBACK_COMMIT);
            return Optional.of(FALLBACK_COMMIT);
        }
    }

    /**
     * Fetches the latest commit SHA on master from the GitHub API.
     *
     * @return the full commit SHA string
     * @throws Exception if the network request or JSON parsing fails
     */
    private static String fetchLatestCommitFromGitHub() throws Exception {
        java.net.URL url = URI.create(GITHUB_API_COMMITS).toURL();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "emojifx");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try (InputStream in = conn.getInputStream();
             JsonReader reader = Json.createReader(in)) {
            JsonObject root = reader.readObject();
            return root.getString("sha");
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Scans {@code baseDir} for the given vendor and returns the most recently
     * modified commit directory name, if any exist.
     */
    private static Optional<String> findLatestCachedCommit(EmojiVendor vendor, Path baseDir) {
        Path vendorDir = baseDir.resolve(vendor.getId());
        if (!Files.exists(vendorDir)) {
            return Optional.empty();
        }
        try {
            return Files.list(vendorDir)
                    .filter(Files::isDirectory)
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a)
                                    .compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException ex) {
                            return 0;
                        }
                    })
                    .map(p -> p.getFileName().toString());
        } catch (IOException e) {
            LOG.warning("Could not scan cache directory: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deletes all commit directories for the given vendor other than {@code keepCommit}.
     * Called after a successful download to avoid accumulating stale sprite sheets.
     */
    private static void pruneOldCommits(EmojiVendor vendor, String keepCommit, Path baseDir) {
        Path vendorDir = baseDir.resolve(vendor.getId());
        if (!Files.exists(vendorDir)) {
            return;
        }
        try {
            List<Path> toDelete = Files.list(vendorDir)
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(keepCommit))
                    .collect(Collectors.toList());

            for (Path old : toDelete) {
                deleteDirectory(old);
            }
        } catch (IOException e) {
            LOG.warning("Could not prune old commit directories: " + e.getMessage());
        }
    }

    /**
     * Recursively deletes a directory and all of its contents.
     */
    private static void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            LOG.warning("Failed to delete: " + p + " — " + e.getMessage());
                        }
                    });
        }
    }

    /**
     * Ensures {@code emoji.json} and {@code emoji.csv} exist in the commit cache directory,
     * downloading and generating them if necessary.
     */
    private static void ensureEmojiJsonAndCsv(String commit, Path commitDir)
            throws Exception {
        Path jsonPath = commitDir.resolve("emoji.json");
        Path csvPath = commitDir.resolve("emoji.csv");

        if (!Files.exists(jsonPath)) {
            String url = String.format(EMOJI_JSON_URL, commit);
            downloadWithLock(URI.create(url).toURL(), jsonPath,
                    commitDir.resolve("emoji.json.lck"));
        }

        if (!Files.exists(csvPath)) {
            List<EmojiCsv> emojiList = parseEmojiJson(jsonPath);
            writeCsv(emojiList, csvPath);
        }
    }

    /**
     * Parses {@code emoji.json} using Gluon Connect and returns a list of {@link EmojiCsv} objects.
     *
     * @param jsonPath path to the emoji.json file
     * @return parsed list of emoji entries
     */
    private static List<EmojiCsv> parseEmojiJson(Path jsonPath) throws Exception {
        List<EmojiCsv> emojiList = new ArrayList<>();
        try (InputStream stream = new FileInputStream(jsonPath.toFile())) {
            InputDataSource dataSource = new BasicInputDataSource(stream);
            InputStreamIterableInputConverter<EmojiCsv> converter =
                    new EmojiIterableInputConverter(EmojiCsv.class);
            ListDataReader<EmojiCsv> reader =
                    new InputStreamListDataReader<>(dataSource, converter);
            for (Iterator<EmojiCsv> it = reader.iterator(); it.hasNext(); ) {
                emojiList.add(it.next());
            }
        }
        return emojiList;
    }

    /**
     * Serializes the given emoji list to a CSV file.
     *
     * @param emojiList the list to write
     * @param csvPath   destination file path
     */
    private static void writeCsv(List<EmojiCsv> emojiList, Path csvPath)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(csvPath.toFile())) {
            for (EmojiCsv emoji : emojiList) {
                writer.println(emoji.toString());
            }
        }
    }

    /**
     * Ensures all sprite sheets for the given vendor and sizes exist in the commit cache
     * directory, downloading any that are missing.
     */
    private static void ensureSprites(EmojiVendor vendor, String commit, Path commitDir)
            throws Exception {
        for (int size : SIZES) {
            Path spritePath = spritePath(vendor, size, commitDir);
            if (!Files.exists(spritePath)) {
                String url = String.format(SPRITE_URL, commit, vendor.getId(), size);
                downloadWithLock(URI.create(url).toURL(), spritePath,
                        commitDir.resolve(vendor.getId() + "_" + size + ".lck"));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the commit-level cache directory using the default base directory.
     * Layout: {@code ~/.emoji-fx/<vendorId>/<commit>/}
     */
    public static Path baseDir(EmojiVendor vendor, String commit) {
        return baseDir(vendor, commit, Paths.get(DEFAULT_BASE_DIR));
    }

    /**
     * Returns the commit-level cache directory under the given {@code baseDir}.
     * Layout: {@code <baseDir>/<vendorId>/<commit>/}
     */
    public static Path baseDir(EmojiVendor vendor, String commit, Path baseDir) {
        return baseDir.resolve(vendor.getId()).resolve(commit);
    }

    /**
     * Returns the local path for a sprite sheet of the given vendor and size,
     * relative to the provided commit directory.
     */
    public static Path spritePath(EmojiVendor vendor, int size, Path commitDir) {
        return commitDir.resolve(
                "sheet_" + vendor.getId() + "_" + size + "_clean.png");
    }

    /**
     * Returns the local path for the emoji CSV using the default base directory.
     */
    public static Path csvPath(EmojiVendor vendor, String commit) {
        return csvPath(vendor, commit, Paths.get(DEFAULT_BASE_DIR));
    }

    /**
     * Returns the local path for the emoji CSV under the given {@code baseDir}.
     */
    public static Path csvPath(EmojiVendor vendor, String commit, Path baseDir) {
        return baseDir(vendor, commit, baseDir).resolve("emoji.csv");
    }

    // -------------------------------------------------------------------------
    // Internal utilities
    // -------------------------------------------------------------------------

    /**
     * Downloads a file to {@code dest}, guarded by a lock file to detect and recover from
     * partial downloads left behind by a previous crash. If a stale lock is found,
     * any partial file is cleaned up before retrying.
     */
    private static void downloadWithLock(java.net.URL url, Path dest, Path lock)
            throws IOException {
        if (Files.exists(lock)) {
            LOG.warning("Stale lock found — retrying download: " + dest);
            Files.deleteIfExists(dest);
            Files.deleteIfExists(lock);
        }
        Files.createFile(lock);
        try (InputStream in = url.openStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(lock);
        }
    }

    /**
     * Creates a single-thread daemon executor for background initialization.
     */
    private static java.util.concurrent.Executor daemonExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
    }
}