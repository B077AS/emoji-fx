package io.github.b077as.emojifx;

import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public interface EmojiSpriteLoader {

    boolean isInitialized();

    CompletableFuture<Boolean> initialize();

    Image loadEmojiSprite(int size);

    InputStream loadCSV();
}
