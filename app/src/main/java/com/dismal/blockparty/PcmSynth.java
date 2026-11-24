package com.dismal.blockparty;

import java.util.Random;

/**
 * Pure-Java sound synthesis for the game's effects: mono 16-bit PCM at 44.1 kHz.
 *
 * <p>Deliberately free of Android imports so the generated audio can be checked on a
 * plain JVM. Each effect is a handful of decaying sine voices, a touch of second
 * harmonic and a little noise for the percussive attack.
 */
final class PcmSynth {

    static final int SAMPLE_RATE = 44100;

    private PcmSynth() {
    }

    static short[] place() {
        short[] out = buffer(0.19);
        addNoiseHit(out, 0.000, 0.080, 0.52, 750.0, 44.0, 0.012, 7L);
        addTone(out, 0.000, 0.115, 125.0, 116.0, 0.18, 18.0, 0.15, 0.0, 11L);
        addTone(out, 0.000, 0.100, 320.0, 300.8, 0.25, 24.0, 0.18, 0.0, 23L);
        addTone(out, 0.000, 0.095, 480.0, 465.6, 0.20, 26.0, 0.15, 0.0, 31L);
        addTone(out, 0.000, 0.085, 820.0, 760.0, 0.05, 30.0, 0.10, 0.0, 43L);
        addNoiseHit(out, 0.125, 0.060, 0.26, 630.0, 44.0, 0.006, 13L);
        addTone(out, 0.125, 0.060, 122.0, 115.0, 0.11, 22.0, 0.12, 0.0, 29L);
        addTone(out, 0.125, 0.058, 307.0, 288.0, 0.16, 26.0, 0.15, 0.0, 37L);
        return out;
    }

    /** Rising three-note chime when lines clear. */
    static short[] clear() {
        short[] out = buffer(0.34);
        addTone(out, 0.00, 0.22, 620.0, 700.0, 0.30, 11.0, 0.30, 0.02, 3L);
        addTone(out, 0.05, 0.22, 830.0, 900.0, 0.25, 11.0, 0.28, 0.02, 5L);
        addTone(out, 0.10, 0.22, 1040.0, 1140.0, 0.20, 12.0, 0.25, 0.02, 9L);
        return out;
    }

    /** Four-note fanfare layered over a low body note, for a combo. */
    static short[] combo() {
        short[] out = buffer(0.62);
        double[] notes = {523.25, 659.25, 783.99, 1046.50};
        for (int note = 0; note < notes.length; note++) {
            addTone(out, note * 0.055, 0.34, notes[note], notes[note] * 1.01,
                    0.22, 7.5, 0.35, 0.02, 17L + note);
        }
        addTone(out, 0.0, 0.50, 130.81, 130.81, 0.12, 4.0, 0.15, 0.0, 23L);
        return out;
    }

    /** Descending minor phrase, for the end of a game. */
    static short[] gameOver() {
        short[] out = buffer(1.05);
        addTone(out, 0.00, 0.45, 392.0, 370.0, 0.30, 4.5, 0.30, 0.02, 31L);
        addTone(out, 0.18, 0.50, 311.0, 294.0, 0.28, 4.0, 0.30, 0.02, 37L);
        addTone(out, 0.36, 0.62, 233.0, 196.0, 0.30, 3.2, 0.35, 0.02, 41L);
        return out;
    }

    private static short[] buffer(double seconds) {
        return new short[(int) (seconds * SAMPLE_RATE)];
    }

    /**
     * Mixes a low-passed noise burst into {@code out}, normalised so {@code gain} is
     * roughly its RMS. This is the wooden body of a knock rather than a pitched tone.
     */
    private static void addNoiseHit(short[] out, double startSeconds, double durationSeconds,
                                    double gain, double cutoffHz, double decay,
                                    double attackSeconds, long seed) {
        int start = (int) (startSeconds * SAMPLE_RATE);
        if (start >= out.length) {
            return;
        }
        int length = Math.min((int) (durationSeconds * SAMPLE_RATE), out.length - start);
        if (length <= 0) {
            return;
        }

        double coefficient = 1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / SAMPLE_RATE);
        double rmsScale = Math.sqrt((2.0 - coefficient) / coefficient);
        Random random = new Random(seed);
        double state = 0.0;
        double fadeSamples = Math.min(length * 0.25, 0.012 * SAMPLE_RATE);

        for (int i = 0; i < length; i++) {
            state += coefficient * ((random.nextDouble() * 2.0 - 1.0) - state);

            double seconds = i / (double) SAMPLE_RATE;
            double attack = Math.min(1.0, seconds / attackSeconds);
            double fade = fadeSamples <= 0 ? 1.0 : Math.min(1.0, (length - i) / fadeSamples);
            double envelope = attack * fade * Math.exp(-decay * seconds);

            double mixed = out[start + i] / 32768.0 + state * rmsScale * gain * envelope;
            mixed = Math.max(-1.0, Math.min(1.0, mixed));
            out[start + i] = (short) Math.round(mixed * 32767.0);
        }
    }

    /**
     * Mixes one voiced tone into {@code out}: a sine with a second harmonic, a 4 ms
     * attack, exponential decay, a short end fade to avoid clicks, and optional noise.
     */
    private static void addTone(short[] out, double startSeconds, double durationSeconds,
                                double startHz, double endHz, double gain, double decay,
                                double harmonic2, double noiseAmount, long seed) {
        int start = (int) (startSeconds * SAMPLE_RATE);
        if (start >= out.length) {
            return;
        }
        int length = Math.min((int) (durationSeconds * SAMPLE_RATE), out.length - start);
        if (length <= 0) {
            return;
        }

        Random noise = new Random(seed);
        double phase = 0.0;
        double fadeSamples = Math.min(length * 0.25, 0.012 * SAMPLE_RATE);
        for (int i = 0; i < length; i++) {
            double progress = i / (double) length;
            double hz = startHz + (endHz - startHz) * progress;
            phase += 2.0 * Math.PI * hz / SAMPLE_RATE;

            double seconds = i / (double) SAMPLE_RATE;
            double attack = Math.min(1.0, seconds / 0.004);
            double fade = fadeSamples <= 0 ? 1.0 : Math.min(1.0, (length - i) / fadeSamples);
            double envelope = attack * fade * Math.exp(-decay * seconds);

            double sample = Math.sin(phase) + harmonic2 * Math.sin(2.0 * phase);
            sample += noiseAmount * (noise.nextDouble() * 2.0 - 1.0);

            double mixed = out[start + i] / 32768.0 + sample * gain * envelope;
            mixed = Math.max(-1.0, Math.min(1.0, mixed));
            out[start + i] = (short) Math.round(mixed * 32767.0);
        }
    }
}
