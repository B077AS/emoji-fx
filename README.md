<h1 align="center">
  <img src=".github/assets/logo.svg" alt="emoji-fx logo" width="96"><br>
  emoji-fx
</h1>

<p align="center">
  <b>Emoji support for JavaFX.</b><br>
  Search, look up and render emojis - sprite sheets are downloaded and cached for you · Google, Twitter &amp; Facebook artwork
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.b077as.emojifx/emoji"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/io.github.b077as.emojifx/emoji?logo=apachemaven&logoColor=white&color=C71A36"></a>
  <img alt="Java 11+" src="https://img.shields.io/badge/Java-11%2B-orange?logo=openjdk&logoColor=white">
  <img alt="JavaFX 22" src="https://img.shields.io/badge/JavaFX-22-1B6AC6">
  <a href="LICENSE"><img alt="License: GPL v3" src="https://img.shields.io/badge/License-GPLv3-blue"></a>
</p>

---

emoji-fx started as a fork of [Gluon's emoji library](https://github.com/gluonhq/emoji). The main differences are that sprite sheets no longer need to be bundled, and that you can pick the emoji artwork (Google, Twitter or Facebook).

## Installation

```xml
<dependency>
    <groupId>io.github.b077as.emojifx</groupId>
    <artifactId>emoji</artifactId>
    <version>1.0.2</version>
</dependency>
```

The library is a named Java module (`io.github.b077as.emojifx`). JavaFX itself is not pulled in transitively, so your application must provide `javafx-controls`.

## Getting started

Initialize the library once at startup, before touching any other emoji API. You choose the artwork set:

```java
EmojiInitializer.initialize(EmojiVendor.TWITTER)
        .thenAccept(ready -> {
            if (!ready) {
                System.err.println("Emoji assets could not be loaded");
            }
        });
```

`initialize` returns a `CompletableFuture<Boolean>`, so call `.join()` on it if you would rather block until everything is in place.

Assets are stored in `~/.emoji-fx/` by default. To use another location, pass a `Path`:

```java
EmojiInitializer.initialize(EmojiVendor.FACEBOOK, Path.of("/your/custom/directory")).join();
```

### What happens during initialization

1. The newest emoji-data commit is looked up on GitHub. If you are offline, the cached commit is used, or a known-good one if nothing is cached yet.
2. `emoji.json` for that commit is downloaded and converted into a local `emoji.csv`.
3. The sprite sheets (20, 32 and 64 px) for your vendor are downloaded.
4. Everything is cached in `~/.emoji-fx/{vendor}/{commit}/`, so later launches start without downloading anything.

### Vendors

| Constant | Artwork |
|----------|---------|
| `EmojiVendor.GOOGLE` | Google |
| `EmojiVendor.TWITTER` | Twitter (Twemoji) |
| `EmojiVendor.FACEBOOK` | Facebook |

## Using emojis

`EmojiData` is the entry point for finding `Emoji` objects.

```java
// by short name
Optional<Emoji> wave = EmojiData.emojiFromShortName("wave");

// by partial text
List<Emoji> matches = EmojiData.search("wav");

// by unicode string
Optional<Emoji> fromUnicode = EmojiData.emojiFromUnicodeString("👋");

// by hex codepoints (here: waving hand, medium-light skin tone)
Optional<Emoji> fromHex = EmojiData.emojiFromCodepoints("1F44B-1F3FC");
```

To split a string into plain text and emoji objects, for example to lay it out in a chat bubble:

```java
List<Object> parts = TextUtils.convertToStringAndEmojiObjects("hello 👋");
```

## Contributing

Bug reports, questions and pull requests are welcome. Please open an [issue](https://github.com/b077as/emoji-fx/issues) or a [pull request](https://github.com/b077as/emoji-fx/pulls). See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

[GNU General Public License v3.0](https://opensource.org/licenses/GPL-3.0). See [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md) for third-party credits.
