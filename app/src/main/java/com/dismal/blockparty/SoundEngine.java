package com.dismal.blockparty;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Plays the procedurally generated clips from {@link PcmSynth} through static
 * {@link AudioTrack}s: no audio files, no media dependencies.
 *
 * <p>All calls are safe on a device that refuses audio: failures are swallowed and the
 * game keeps working silently.
 */
final class SoundEngine {

    private AudioTrack placeTrack;
    private AudioTrack clearTrack;
    private AudioTrack comboTrack;
    private AudioTrack gameOverTrack;

    private boolean muted;
    private boolean prepared;
    private boolean failed;

    void setMuted(boolean value) {
        muted = value;
        if (value) {
            stopAll();
        }
    }

    void playPlace() {
        play(trackPlace());
    }

    void playClear() {
        play(trackClear());
    }

    void playCombo() {
        play(trackCombo());
    }

    void playGameOver() {
        play(trackGameOver());
    }

    /** Frees the hardware tracks; a later play rebuilds them lazily. */
    void release() {
        releaseTracks();
        prepared = false;
    }

    // --- playback ------------------------------------------------------------

    private AudioTrack trackPlace() {
        prepare();
        return placeTrack;
    }

    private AudioTrack trackClear() {
        prepare();
        return clearTrack;
    }

    private AudioTrack trackCombo() {
        prepare();
        return comboTrack;
    }

    private AudioTrack trackGameOver() {
        prepare();
        return gameOverTrack;
    }

    private void prepare() {
        if (prepared || failed || muted) {
            return;
        }
        prepared = true;
        try {
            placeTrack = build(PcmSynth.place());
            clearTrack = build(PcmSynth.clear());
            comboTrack = build(PcmSynth.combo());
            gameOverTrack = build(PcmSynth.gameOver());
        } catch (RuntimeException e) {
            failed = true;
            releaseTracks();
        }
    }

    private static AudioTrack build(short[] pcm) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(PcmSynth.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        AudioTrack track = new AudioTrack(attributes, format, pcm.length * 2,
                AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        track.write(pcm, 0, pcm.length);
        track.setVolume(0.85f);
        return track;
    }

    private void play(AudioTrack track) {
        if (muted || track == null) {
            return;
        }
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop();
            }
            track.setPlaybackHeadPosition(0);
            track.play();
        } catch (RuntimeException e) {
            // This device will not replay a static buffer; stay silent rather than retry.
            failed = true;
            releaseTracks();
        }
    }

    private void stopAll() {
        stop(placeTrack);
        stop(clearTrack);
        stop(comboTrack);
        stop(gameOverTrack);
    }

    private void stop(AudioTrack track) {
        if (track == null) {
            return;
        }
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop();
            }
        } catch (RuntimeException ignored) {
            // Nothing to do: the track is going quiet either way.
        }
    }

    private void releaseTracks() {
        placeTrack = release(placeTrack);
        clearTrack = release(clearTrack);
        comboTrack = release(comboTrack);
        gameOverTrack = release(gameOverTrack);
    }

    private static AudioTrack release(AudioTrack track) {
        if (track != null) {
            try {
                track.release();
            } catch (RuntimeException ignored) {
                // Already released.
            }
        }
        return null;
    }
}
