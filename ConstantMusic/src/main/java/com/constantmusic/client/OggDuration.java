package com.constantmusic.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Works out the length of an Ogg Vorbis stream without decoding it.
 * <p>
 * Minecraft doesn't expose how long a streamed sound is, and we need that to know when to
 * start fading. An Ogg file is a sequence of pages; every page header carries a "granule
 * position" (for Vorbis: the number of PCM samples played up to that page). The last page's
 * granule position divided by the sample rate (stored in the first page) is the duration.
 * Only page headers are read; page bodies are skipped.
 */
public final class OggDuration {

    private OggDuration() {
    }

    /** @return length in seconds, or -1 if it could not be determined. */
    public static double readSeconds(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(source, 64 * 1024));
        byte[] header = new byte[27];
        int sampleRate = -1;
        long lastGranule = -1L;
        boolean firstPage = true;

        try {
            while (true) {
                in.readFully(header);
                if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
                    break; // lost sync / not an Ogg file
                }

                long granule = ByteBuffer.wrap(header, 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                int segmentCount = header[26] & 0xFF;
                byte[] segmentTable = new byte[segmentCount];
                in.readFully(segmentTable);

                int bodyLength = 0;
                for (byte segment : segmentTable) {
                    bodyLength += segment & 0xFF;
                }

                if (firstPage) {
                    firstPage = false;
                    byte[] body = new byte[bodyLength];
                    in.readFully(body);
                    sampleRate = vorbisSampleRate(body);
                } else {
                    in.skipNBytes(bodyLength);
                }

                if (granule != -1L) { // -1 means "no packet finishes on this page"
                    lastGranule = granule;
                }
            }
        } catch (EOFException endOfStream) {
            // normal: we simply ran out of pages
        }

        if (sampleRate <= 0 || lastGranule <= 0) {
            return -1.0D;
        }
        return (double) lastGranule / sampleRate;
    }

    /** Reads the sample rate out of a Vorbis identification header packet. */
    private static int vorbisSampleRate(byte[] packet) {
        if (packet.length < 16
                || packet[0] != 0x01
                || packet[1] != 'v' || packet[2] != 'o' || packet[3] != 'r'
                || packet[4] != 'b' || packet[5] != 'i' || packet[6] != 's') {
            return -1;
        }
        return (packet[12] & 0xFF)
                | (packet[13] & 0xFF) << 8
                | (packet[14] & 0xFF) << 16
                | (packet[15] & 0xFF) << 24;
    }
}
