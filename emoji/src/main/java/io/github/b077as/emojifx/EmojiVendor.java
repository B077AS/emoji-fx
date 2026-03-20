package io.github.b077as.emojifx;

/**
 * Supported emoji image vendors.
 * The sprite sheet file naming on GitHub follows: {@code sheet_{vendor}_{size}.png}.
 */
public enum EmojiVendor {

    /**
     * Google emoji image set.
     */
    GOOGLE("google"),

    /**
     * Twitter (Twemoji) emoji image set.
     */
    TWITTER("twitter"),

    /**
     * Facebook emoji image set.
     */
    FACEBOOK("facebook");

    private final String id;

    EmojiVendor(String id) {
        this.id = id;
    }

    /**
     * Returns the lowercase vendor identifier used in file and URL names.
     */
    public String getId() {
        return id;
    }
}