package com.dismal.blockparty;

/**
 * JVM-only checks for the procedural sound clips. There is no way to listen inside a
 * build, so this asserts the properties that make a clip usable: exact duration, real
 * signal, headroom, no hard clipping and silent, click-free edges.
 */
public final class PcmSynthTest {

    private static int failures = 0;

    private static void check(boolean condition, String what) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        verify("place", PcmSynth.place(), 0.19);
        verify("clear", PcmSynth.clear(), 0.34);
        verify("combo", PcmSynth.combo(), 0.62);
        verify("game-over", PcmSynth.gameOver(), 1.05);

        if (failures == 0) {
            System.out.println("all sound clips valid");
            return;
        }
        System.out.println(failures + " sound check(s) failed");
        System.exit(1);
    }

    private static void verify(String name, short[] pcm, double seconds) {
        int expected = (int) (seconds * PcmSynth.SAMPLE_RATE);
        check(pcm.length == expected, name + ": duration is " + expected + " samples");

        double peak = 0;
        double sumSquares = 0;
        int clipped = 0;
        for (short sample : pcm) {
            double normalized = sample / 32767.0;
            peak = Math.max(peak, Math.abs(normalized));
            sumSquares += normalized * normalized;
            if (Math.abs(sample) >= 32766) {
                clipped++;
            }
        }

        double rms = Math.sqrt(sumSquares / pcm.length);
        double clippedFraction = clipped / (double) pcm.length;
        // A click is a jump at the buffer boundary, so the first and last values must be
        // silence. An audible attack ramp inside the clip is intended, not a click.
        double first = Math.abs(pcm[0] / 32767.0);
        double last = Math.abs(pcm[pcm.length - 1] / 32767.0);
        double tail = maxAbs(pcm, Math.max(0, pcm.length - 48), pcm.length);

        System.out.println(String.format(
                java.util.Locale.US,
                "%-9s samples=%5d peak=%.3f rms=%.3f clipped=%.3f%% first=%.3f last=%.3f tail=%.3f",
                name, pcm.length, peak, rms, clippedFraction * 100, first, last, tail));

        check(rms > 0.02, name + ": carries a real signal");
        check(peak > 0.20, name + ": is audible");
        check(peak <= 1.0, name + ": never exceeds full scale");
        check(clippedFraction < 0.005, name + ": has headroom (little hard clipping)");
        check(first < 0.01, name + ": starts at silence");
        check(last < 0.01, name + ": ends at silence");
        check(tail < 0.05, name + ": fades out instead of cutting off");
    }

    private static double maxAbs(short[] pcm, int from, int to) {
        double max = 0;
        for (int i = from; i < to; i++) {
            max = Math.max(max, Math.abs(pcm[i] / 32767.0));
        }
        return max;
    }
}
