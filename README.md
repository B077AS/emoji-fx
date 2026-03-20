# emoji-fx

> A fork of [Gluon's emoji library](https://github.com/gluonhq/emoji), extended with automatic sprite downloading and multi-vendor support.

## How it works

On first run, the library automatically:

1. Resolves the latest emoji-data commit from GitHub (falls back to cache or a known-good commit if offline).
2. Downloads `emoji.json` for that commit and generates a local `emoji.csv`.
3. Downloads the sprite sheets for the chosen vendor (20, 32, 64 px).
4. Caches everything under `~/.emoji-fx/{vendor}/{commit}/` for subsequent runs.

## Usage

### Initialization

Call once at application startup before using any emoji API:
```java
EmojiInitializer.initialize(EmojiVendor.TWITTER)
    .thenAccept(ok -> {
        if (ok) {
        // library is ready
        }
        });
```

The download directory defaults to `~/.emoji-fx/`, but can be customized by passing a `Path` as the second argument:
```java
EmojiInitializer.initialize(EmojiVendor.FACEBOOK, Path.of("/your/custom/directory"))
        .thenAccept(ok -> {
            if (!ok) log.warn("Emoji assets failed to load");
        })
        .join();
```

### Vendors

Three emoji image sets are supported:

| Vendor | Identifier |
|--------|-----------|
| `EmojiVendor.GOOGLE` | Google |
| `EmojiVendor.TWITTER` | Twitter (Twemoji) |
| `EmojiVendor.FACEBOOK` | Facebook |

### API overview

`EmojiData` contains the public API to create `Emoji` objects from text, unicode, or hex codepoints.

Fetch 👋 from short name:
```java
Optional<Emoji> emoji = EmojiData.emojiFromShortName("wave");
```

Search emojis by partial text:
```java
List<Emoji> emojis = EmojiData.search("wav");
```

Fetch 👋 from unicode:
```java
Optional<Emoji> emoji = EmojiData.emojiFromUnicodeString("\uD83D\uDC4B");
```

Fetch 👋 from hex codepoint:
```java
Optional<Emoji> emoji = EmojiData.emojiFromCodepoints("1F44B-1F3FC");
```

Parse a UTF-8 string into a mixed list of text and emoji objects:
```java
List<Object> nodes = TextUtil.convertToStringAndEmojiObjects("hello \uD83D\uDC4B");
```

## Dependency
```xml
<dependency>
    <groupId>io.github.b077as</groupId>
    <artifactId>emoji-fx</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Contribution

All contributions are welcome!

- Submit [issues](https://github.com/b077as/emoji-fx/issues) for bug reports, questions, or feature requests.
- Contributions can be submitted via [pull request](https://github.com/b077as/emoji-fx/pulls).

## License

[GNU General Public License v3.0](https://opensource.org/licenses/GPL-3.0)