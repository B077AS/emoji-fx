package io.github.b077as.emojifx;

import javafx.scene.image.Image;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link EmojiSpriteLoader} backed by assets managed by {@link EmojiInitializer}.
 *
 * <p>Must be constructed <em>after</em> the {@link EmojiInitializer} future
 * has completed successfully:
 * <pre>
 *   EmojiInitializer.initialize(EmojiVendor.GOOGLE, myBaseDir)
 *       .thenAccept(ok -> {
 *           if (ok) {
 *               EmojiSpriteLoader loader = new DynamicEmojiSpriteLoader();
 *               // use loader ...
 *           }
 *       });
 * </pre>
 */
public class DynamicEmojiSpriteLoader implements EmojiSpriteLoader {

    private static final Logger LOG =
            Logger.getLogger(DynamicEmojiSpriteLoader.class.getName());

    private final EmojiVendor vendor;
    private final String commit;
    private final Path baseDir;

    /**
     * Reads the resolved vendor, commit, and base directory from {@link EmojiInitializer}.
     *
     * @throws IllegalStateException if {@link EmojiInitializer#initialize} has
     *                               not completed successfully yet
     */
    public DynamicEmojiSpriteLoader() {
        this.vendor = EmojiInitializer.getResolvedVendor();
        this.commit = EmojiInitializer.getResolvedCommit();
        this.baseDir = EmojiInitializer.getResolvedBaseDir();
        if (vendor == null || commit == null || baseDir == null) {
            throw new IllegalStateException(
                    "EmojiInitializer has not completed. " +
                            "Await the CompletableFuture before constructing DynamicEmojiSpriteLoader.");
        }
    }

    /**
     * Returns {@code true} if all three sprite sheet sizes are present in the cache.
     */
    @Override
    public boolean isInitialized() {
        for (int size : new int[]{20, 32, 64}) {
            if (!Files.exists(EmojiInitializer.spritePath(vendor, size, baseDir))) {
                return false;
            }
        }
        return true;
    }

    /**
     * No-op: initialization is handled by {@link EmojiInitializer}.
     * Returns a completed future reflecting whether the sprite files are present.
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.completedFuture(isInitialized());
    }

    /**
     * Loads the sprite sheet image for the given size from the local cache.
     *
     * @param size the sprite size in pixels (20, 32, or 64)
     * @return the loaded {@link Image}
     * @throws RuntimeException if the sprite file is missing or cannot be read
     */
    @Override
    public Image loadEmojiSprite(int size) {
        Path commitDir = EmojiInitializer.baseDir(vendor, commit, baseDir);
        Path path = EmojiInitializer.spritePath(vendor, size, commitDir);
        if (!Files.exists(path)) {
            throw new RuntimeException(
                    "Sprite not found for size " + size + ": " + path);
        }
        try {
            return new Image(new FileInputStream(path.toFile()));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to load sprite: " + path, e);
            throw new RuntimeException("Unable to load sprite image", e);
        }
    }

    /**
     * Opens an {@link InputStream} for the cached {@code emoji.csv} file.
     *
     * @return input stream for the CSV data
     * @throws RuntimeException if the file is missing or cannot be opened
     */
    @Override
    public InputStream loadCSV() {
        Path csvPath = EmojiInitializer.csvPath(vendor, commit, baseDir); // fix: was csvPath(vendor, commit) which always fell back to DEFAULT_BASE_DIR
        if (!Files.exists(csvPath)) {
            throw new RuntimeException("emoji.csv not found at: " + csvPath);
        }
        try {
            return Files.newInputStream(csvPath);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to open emoji.csv", e);
            throw new RuntimeException("Unable to open emoji.csv", e);
        }
    }
}