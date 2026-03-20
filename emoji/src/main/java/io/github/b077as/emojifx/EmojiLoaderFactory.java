package io.github.b077as.emojifx;

/**
 * Factory for obtaining the application-wide {@link EmojiSpriteLoader} instance.
 *
 * <p>Ensure {@link EmojiInitializer#initialize} has completed before the first
 * call to {@link #getEmojiImageLoader()}.
 */
public class EmojiLoaderFactory {

    private static EmojiSpriteLoader emojiSpriteLoader;

    private EmojiLoaderFactory() {
    }

    /**
     * Returns the singleton {@link EmojiSpriteLoader}, creating it on first call.
     *
     * @return the shared {@link DynamicEmojiSpriteLoader} instance
     */
    public static EmojiSpriteLoader getEmojiImageLoader() {
        if (emojiSpriteLoader == null) {
            emojiSpriteLoader = new DynamicEmojiSpriteLoader();
        }
        return emojiSpriteLoader;
    }

    public static void setLoader(EmojiSpriteLoader loader) {
        emojiSpriteLoader = loader;
    }
}