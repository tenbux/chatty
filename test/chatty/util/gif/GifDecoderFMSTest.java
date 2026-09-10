
package chatty.util.gif;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for the dimension bounds added to readLSD()/readImage():
 * width*height computed as a plain int overflows for large 16-bit GIF
 * dimensions (a crafted/corrupt header from a third-party emote/badge CDN),
 * and even without overflow, unbounded dimensions could request huge
 * allocations. Both must now be rejected with STATUS_FORMAT_ERROR instead
 * of decoding further.
 *
 * Same package as GifDecoderFMS so readLSD()/readImage()/the "in" field
 * (all "protected", which in Java also grants same-package access) can be
 * driven directly with hand-built byte sequences, isolating exactly the
 * logical-screen-descriptor and per-frame dimension parsing this fix
 * touches, without needing a full valid GIF byte stream.
 */
public class GifDecoderFMSTest {

    private static GifDecoderFMS decoderWithBytes(byte[] bytes) {
        GifDecoderFMS decoder = new GifDecoderFMS();
        decoder.init();
        decoder.in = new BufferedInputStream(new ByteArrayInputStream(bytes));
        return decoder;
    }

    private static byte[] shortsLE(int... values) {
        byte[] result = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            result[i * 2] = (byte) (values[i] & 0xff);
            result[i * 2 + 1] = (byte) ((values[i] >> 8) & 0xff);
        }
        return result;
    }

    @Test
    public void testReadLSD_reasonableDimensions_accepted() {
        // 100x100 logical screen, followed by the remaining 3 LSD bytes
        // (packed fields, bg index, pixel aspect ratio) readLSD() also
        // consumes on the accepted path.
        byte[] bytes = concat(shortsLE(100, 100), new byte[]{0, 0, 0});
        GifDecoderFMS decoder = decoderWithBytes(bytes);

        decoder.readLSD();

        assertEquals(GifDecoderFMS.STATUS_OK, decoder.status);
        assertEquals(100, decoder.width);
        assertEquals(100, decoder.height);
    }

    @Test
    public void testReadLSD_overflowingDimensions_rejected() {
        // 65535 x 65535: iw*ih computed as a plain int overflows (wraps
        // negative), which used to reach "new byte[npix]" further down the
        // decode path and throw NegativeArraySizeException.
        GifDecoderFMS decoder = decoderWithBytes(shortsLE(65535, 65535));

        decoder.readLSD();

        assertEquals(GifDecoderFMS.STATUS_FORMAT_ERROR, decoder.status);
    }

    @Test
    public void testReadLSD_largeButNonOverflowingDimensions_rejected() {
        // 60000 x 60000 = 3.6 billion pixels: fits in a long (no int
        // overflow concern for THIS value), but is a huge, unreasonable
        // allocation request for a single emote/badge frame.
        GifDecoderFMS decoder = decoderWithBytes(shortsLE(60000, 60000));

        decoder.readLSD();

        assertEquals(GifDecoderFMS.STATUS_FORMAT_ERROR, decoder.status);
    }

    @Test
    public void testReadLSD_zeroDimensions_rejected() {
        GifDecoderFMS decoder = decoderWithBytes(shortsLE(0, 0));

        decoder.readLSD();

        assertEquals(GifDecoderFMS.STATUS_FORMAT_ERROR, decoder.status);
    }

    @Test
    public void testReadImage_overflowingFrameDimensions_rejected() {
        // readImage() reads: image position (x,y), size (iw,ih), then a
        // packed byte (0x00 -> no local color table, so it falls back to
        // "act = gct"). A non-null gct is pre-set so readImage()'s
        // unrelated "act == null" check can't also produce
        // STATUS_FORMAT_ERROR on its own - the dimension check is the only
        // thing that should reject this input before decodeImageData()
        // would otherwise be reached with an overflowed pixel count.
        byte[] bytes = concat(shortsLE(0, 0, 65535, 65535), new byte[]{0x00});
        GifDecoderFMS decoder = decoderWithBytes(bytes);
        decoder.gct = new int[256];

        decoder.readImage();

        assertEquals(GifDecoderFMS.STATUS_FORMAT_ERROR, decoder.status);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

}
