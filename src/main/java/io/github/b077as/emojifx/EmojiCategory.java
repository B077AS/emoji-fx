package io.github.b077as.emojifx;

public enum EmojiCategory {

    SMILEYS_PEOPLE("Smileys & Emotion, People & Body", "\uD83D\uDE00", "emoji-symbol"),
    NATURE("Animals & Nature", "\uD83D\uDC3B", "emoji-animal-symbol"),
    FOOD_DRINK("Food & Drink", "\uD83C\uDF54", "emoji-food-symbol"),
    ACTIVITY("Activities", "\u26BD", "emoji-activity-symbol"),
    TRAVEL("Travel & Places", "\uD83D\uDE80", "emoji-travel-symbol"),
    OBJECTS("Objects", "\uD83D\uDCA1", "emoji-object-symbol"),
    SYMBOLS("Symbols", "\uD83D\uDC95", "emoji-symbol-symbol"),
    FLAGS("Flags", "\uD83C\uDF8C", "emoji-flag-symbol");

    private final String category;
    private final String unicode;
    private final String styleClass;

    EmojiCategory(String category, String unicode, String styleClass) {
        this.category = category;
        this.unicode = unicode;
        this.styleClass = styleClass;
    }

    public String categoryName() {
        return category;
    }

    public String getUnicode() {
        return unicode;
    }

    public String getStyleClass() {
        return styleClass;
    }
}
