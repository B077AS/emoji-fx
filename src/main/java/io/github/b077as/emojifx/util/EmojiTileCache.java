package io.github.b077as.emojifx.util;

import io.github.b077as.emojifx.Emoji;
import io.github.b077as.emojifx.EmojiInitializer;
import io.github.b077as.emojifx.EmojiLoaderFactory;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Per-emoji images without keeping a whole sprite sheet in memory.
 *
 * <p>A decoded sheet is big (20 px: ~7 MB, 32 px: ~18 MB, 64 px: ~75 MB of heap, plus the same
 * again as a GPU texture), yet a UI only ever shows a few dozen emojis. So each sheet is decoded
 * once, split into its cells and written to a small tile file next to the sprites (every cell
 * compressed on its own, with an index), and the sheet is dropped. Afterwards a tile is one
 * random-access read plus an inflate of a few KB. Tiles are shared by every ImageView showing that
 * emoji and only weakly cached, so memory follows what is actually on screen.
 *
 * <p>The tile file is rebuilt when missing, corrupt, or from another sheet layout. Any failure
 * makes {@link #tile} return {@code null}; callers fall back to the sheet + viewport path.
 */
final class EmojiTileCache {

    private static final Logger LOG = Logger.getLogger(EmojiTileCache.class.getName());
    private static final int MAGIC = 0x454D5449; // "EMTI"
    private static final int VERSION = 1;
    private static final WritablePixelFormat<ByteBuffer> FORMAT = PixelFormat.getByteBgraPreInstance();

    /** One open tile file per sprite size (20 / 32 / 64). */
    private static final Map<Integer, TileFile> FILES = new HashMap<>();
    private static final Map<Long, WeakReference<Image>> CACHE = new HashMap<>();
    private static int putsSinceSweep;

    private EmojiTileCache() {}

    /**
     * The emoji's full sheet cell (sprite size + 2 px padding, i.e. what the old viewport showed)
     * as its own small image, or {@code null} if tiles are unavailable.
     */
    static synchronized Image tile(Emoji emoji, int spriteSize) {
        long key = ((long) spriteSize << 40) | ((long) emoji.getSheetY() << 20) | emoji.getSheetX();
        WeakReference<Image> ref = CACHE.get(key);
        Image cached = ref == null ? null : ref.get();
        if (cached != null) return cached;

        TileFile file = FILES.get(spriteSize);
        if (file == null) {
            if (FILES.containsKey(spriteSize)) return null; // failed before, don't retry every call
            file = TileFile.openOrBuild(spriteSize);
            FILES.put(spriteSize, file);
            if (file == null) return null;
        }
        Image image = file.read(emoji.getSheetX(), emoji.getSheetY());
        if (image == null) return null;
        CACHE.put(key, new WeakReference<>(image));
        if (++putsSinceSweep >= 512) sweep();
        return image;
    }

    private static void sweep() {
        putsSinceSweep = 0;
        for (Iterator<WeakReference<Image>> it = CACHE.values().iterator(); it.hasNext(); ) {
            if (it.next().get() == null) it.remove();
        }
    }

    /** Where tile files live: next to the vendor/commit sprites, else the temp dir. */
    private static Path tileDir() {
        Path base = EmojiInitializer.getResolvedBaseDir();
        if (base != null && EmojiInitializer.getResolvedVendor() != null && EmojiInitializer.getResolvedCommit() != null) {
            return EmojiInitializer.baseDir(EmojiInitializer.getResolvedVendor(), EmojiInitializer.getResolvedCommit(), base);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "emojifx-tiles");
    }

    /** Index + random access over one tile file. */
    private static final class TileFile {
        final RandomAccessFile raf;
        final int cell, cols, rows;
        final long[] offsets;
        final int[] lengths;

        private TileFile(RandomAccessFile raf, int cell, int cols, int rows, long[] offsets, int[] lengths) {
            this.raf = raf;
            this.cell = cell;
            this.cols = cols;
            this.rows = rows;
            this.offsets = offsets;
            this.lengths = lengths;
        }

        static TileFile openOrBuild(int spriteSize) {
            Path path = tileDir().resolve("tiles_" + spriteSize + ".bin");
            try {
                TileFile f = open(path, spriteSize + 2);
                if (f != null) return f;
                build(path, spriteSize);
                return open(path, spriteSize + 2);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Emoji tiles unavailable for size " + spriteSize + ", using the sprite sheet", t);
                return null;
            }
        }

        /** Opens and validates an existing tile file; null if missing or not usable. */
        static TileFile open(Path path, int expectedCell) throws IOException {
            if (!Files.isRegularFile(path)) return null;
            RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
            try {
                if (raf.readInt() != MAGIC || raf.readInt() != VERSION) { raf.close(); return null; }
                int cell = raf.readInt(), cols = raf.readInt(), rows = raf.readInt();
                if (cell != expectedCell || cols <= 0 || rows <= 0) { raf.close(); return null; }
                int n = cols * rows;
                long[] offsets = new long[n];
                int[] lengths = new int[n];
                for (int i = 0; i < n; i++) { offsets[i] = raf.readLong(); lengths[i] = raf.readInt(); }
                return new TileFile(raf, cell, cols, rows, offsets, lengths);
            } catch (IOException e) {
                raf.close();
                return null;
            }
        }

        /** Decodes the sheet once, writes every cell compressed, then lets the sheet go. */
        static void build(Path path, int spriteSize) throws IOException {
            long t0 = System.nanoTime();
            Image sheet = EmojiLoaderFactory.getEmojiImageLoader().loadEmojiSprite(spriteSize);
            int cell = spriteSize + 2;
            int cols = (int) sheet.getWidth() / cell;
            int rows = (int) sheet.getHeight() / cell;
            PixelReader reader = sheet.getPixelReader();
            int n = cols * rows;
            byte[][] packed = new byte[n][];
            byte[] raw = new byte[cell * cell * 4];
            byte[] out = new byte[raw.length + 64];
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    reader.getPixels(x * cell, y * cell, cell, cell, FORMAT, raw, 0, cell * 4);
                    if (isBlank(raw)) continue; // empty sheet slot
                    deflater.reset();
                    deflater.setInput(raw);
                    deflater.finish();
                    int len = deflater.deflate(out);
                    byte[] p = new byte[len];
                    System.arraycopy(out, 0, p, 0, len);
                    packed[y * cols + x] = p;
                }
            }
            deflater.end();

            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
                dos.writeInt(MAGIC);
                dos.writeInt(VERSION);
                dos.writeInt(cell);
                dos.writeInt(cols);
                dos.writeInt(rows);
                long offset = 20L + (long) n * 12;
                for (int i = 0; i < n; i++) {
                    int len = packed[i] == null ? 0 : packed[i].length;
                    dos.writeLong(offset);
                    dos.writeInt(len);
                    offset += len;
                }
                for (byte[] p : packed) if (p != null) dos.write(p);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            LOG.info(String.format("Built emoji tiles for size %d (%dx%d cells) in %d ms -> %s",
                    spriteSize, cols, rows, (System.nanoTime() - t0) / 1_000_000, path));
        }

        private static boolean isBlank(byte[] bgraPre) {
            for (int i = 3; i < bgraPre.length; i += 4) if (bgraPre[i] != 0) return false;
            return true;
        }

        Image read(int sheetX, int sheetY) {
            if (sheetX < 0 || sheetY < 0 || sheetX >= cols || sheetY >= rows) return null;
            int idx = sheetY * cols + sheetX;
            byte[] pixels = new byte[cell * cell * 4];
            int len = lengths[idx];
            if (len > 0) {
                try {
                    byte[] packed = new byte[len];
                    raf.seek(offsets[idx]);
                    raf.readFully(packed);
                    Inflater inflater = new Inflater();
                    inflater.setInput(packed);
                    inflater.inflate(pixels);
                    inflater.end();
                } catch (IOException | DataFormatException e) {
                    LOG.log(Level.WARNING, "Corrupt emoji tile " + sheetX + "," + sheetY, e);
                    return null;
                }
            }
            WritableImage image = new WritableImage(cell, cell);
            image.getPixelWriter().setPixels(0, 0, cell, cell, FORMAT, pixels, 0, cell * 4);
            return image;
        }
    }
}
