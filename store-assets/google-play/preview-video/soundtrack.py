"""Original soundtrack for the Google Play preview video.

Everything is synthesized from oscillators and filtered noise with a fixed
random seed: no samples, loops or third-party recordings, and the same output on
every run. render-preview-video.py calls write_soundtrack() with the same
TIMELINE that drives preview-video/template.html, so music and effects follow
the scene changes:

- hook: a soft pad swell, a whoosh while the phone unfolds, a small tick when it
  is fully open and a rising sparkle while the ring fills;
- scenes: one scene is exactly two bars (about 130 BPM for 3.7 s scenes) with
  pad, bass, arpeggio and a light beat, plus a swipe on every scene change;
- end card: a resolved chord and a bell as the logo appears, then a fade-out.
"""

from __future__ import annotations

import wave

import numpy as np
from scipy.signal import butter, sosfilt

SR = 44100

# MIDI chords: one per scene (reused cyclically), plus the hook and the ending.
SCENE_CHORDS = [
    [53, 57, 60, 64],  # Fmaj7
    [55, 59, 62, 64],  # G6
    [52, 55, 59, 62],  # Em7
    [57, 60, 64, 67],  # Am7
    [53, 57, 60, 64],  # Fmaj7
    [55, 60, 62, 67],  # Gsus4, resolving into the ending
]
HOOK_CHORD = [48, 55, 60, 62]      # Cadd9
ENDING_CHORD = [48, 55, 59, 62, 64]  # Cmaj9


def _midi(note: float) -> float:
    return 440.0 * 2 ** ((note - 69) / 12)


def _filter(signal, kind, cutoff):
    return sosfilt(butter(2, cutoff, kind, fs=SR, output="sos"), signal)


class _Mixer:
    def __init__(self, duration: float, seed: int = 7):
        self.buffer = np.zeros(int(SR * duration))
        self.rng = np.random.default_rng(seed)

    def add(self, start: float, signal, gain: float) -> None:
        begin = int(start * SR)
        if begin >= len(self.buffer) or begin < 0:
            return
        end = min(len(self.buffer), begin + len(signal))
        self.buffer[begin:end] += gain * signal[: end - begin]

    # --- instruments -----------------------------------------------------
    def envelope(self, n, attack, decay, sustain, release, hold):
        a, d, r = int(attack * SR), int(decay * SR), int(release * SR)
        s = max(0, int(hold * SR) - a - d)
        env = np.concatenate([
            np.linspace(0, 1, a, endpoint=False),
            np.linspace(1, sustain, d, endpoint=False),
            np.full(s, sustain),
            np.linspace(sustain, 0, r),
        ])
        return env[:n] if len(env) >= n else np.pad(env, (0, n - len(env)))

    def pad(self, start, notes, length, gain, attack=0.35, release=0.6, cutoff=1400):
        n = int((length + release) * SR)
        tt = np.arange(n) / SR
        signal = np.zeros(n)
        for note in notes:
            for detune in (-0.08, 0.0, 0.07):
                freq = _midi(note) * 2 ** (detune / 12)
                for harmonic, amp in ((1, 1.0), (2, 0.35), (3, 0.18), (4, 0.08)):
                    phase = self.rng.uniform(0, 2 * np.pi)
                    signal += amp * np.sin(2 * np.pi * freq * harmonic * tt + phase) / 3
        signal = _filter(signal, "low", cutoff)
        signal *= self.envelope(n, attack, 0.4, 0.8, release, length)
        self.add(start, signal, gain)

    @staticmethod
    def pluck(freq, length=0.5):
        tt = np.arange(int(length * SR)) / SR
        signal = (np.sin(2 * np.pi * freq * tt) + 0.35 * np.sin(4 * np.pi * freq * tt)
                  + 0.12 * np.sin(6 * np.pi * freq * tt))
        return _filter(signal * np.exp(-tt * 9) * np.minimum(1, tt * 400), "low", 4500)

    def bass(self, freq, length):
        n = int(length * SR)
        tt = np.arange(n) / SR
        signal = np.sin(2 * np.pi * freq * tt) + 0.25 * np.sin(4 * np.pi * freq * tt)
        return signal * self.envelope(n, 0.01, 0.15, 0.6, 0.12, length - 0.12)

    @staticmethod
    def kick():
        tt = np.arange(int(0.35 * SR)) / SR
        freq = 50 + 90 * np.exp(-tt * 30)
        return np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-tt * 9)

    def hat(self):
        tt = np.arange(int(0.06 * SR)) / SR
        return _filter(self.rng.standard_normal(len(tt)), "high", 7000) * np.exp(-tt * 70)

    def clap(self):
        tt = np.arange(int(0.25 * SR)) / SR
        return _filter(self.rng.standard_normal(len(tt)), "band", [900, 3500]) * np.exp(-tt * 22)

    def whoosh(self, length, low, high, rising):
        n = int(length * SR)
        noise = self.rng.standard_normal(n)
        out = np.zeros(n)
        block = 512
        for i in range(0, n, block):
            progress = i / n if rising else 1 - i / n
            centre = low * (high / low) ** progress
            band = [centre * 0.7, min(centre * 1.4, SR / 2 - 100)]
            filtered = _filter(noise[max(0, i - 2048): i + block], "band", band)
            out[i:i + block] = filtered[-min(block, n - i):]
        shape = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 1.5
        return out * shape

    @staticmethod
    def bell(freq, length=2.5):
        tt = np.arange(int(length * SR)) / SR
        signal = np.zeros(len(tt))
        for ratio, amp, decay in ((1, 1, 2.2), (2.0, .5, 3.0), (2.76, .35, 4.0), (5.4, .15, 6.0)):
            signal += amp * np.sin(2 * np.pi * freq * ratio * tt) * np.exp(-tt * decay)
        return signal * np.minimum(1, tt * 300)

    @staticmethod
    def tick():
        tt = np.arange(int(0.05 * SR)) / SR
        return np.sin(2 * np.pi * 1800 * tt) * np.exp(-tt * 90)


def write_soundtrack(path, timeline: dict, scene_count: int) -> None:
    """Write a 16-bit stereo WAV that follows the video timeline."""
    duration = timeline["duration"]
    scene0, scene_length = timeline["scene0"], timeline["sceneLength"]
    end_start = timeline["endStart"]
    fold_start, fold_end = timeline["foldOpen"]
    ring_start, _ring_end = timeline["ringFill"]
    beat = scene_length / 8  # two 4/4 bars per scene
    scenes = [scene0 + scene_length * k for k in range(scene_count)]
    ending = scene0 + scene_length * scene_count

    music, effects = _Mixer(duration, seed=7), _Mixer(duration, seed=11)

    # Hook
    music.pad(0.0, HOOK_CHORD, scene0, 0.030, attack=1.2, cutoff=1000)
    for i, note in enumerate([72, 74, 76, 79, 81, 84]):
        music.add(ring_start + i * 0.17, music.pluck(_midi(note), 0.6), 0.06)
    effects.add(fold_start - 0.05, effects.whoosh(fold_end - fold_start + 0.1, 250, 2500, True), 0.10)
    effects.add(fold_end, effects.tick(), 0.10)

    # Scenes
    for k, start in enumerate(scenes):
        chord = SCENE_CHORDS[k % len(SCENE_CHORDS)]
        music.pad(start, chord, scene_length, 0.024)
        root = _midi(chord[0] - 12)
        for b in range(8):
            at = start + b * beat
            if b % 4 in (0, 2):
                music.add(at, music.bass(root, beat * 1.8), 0.16)
                music.add(at, music.kick(), 0.19)
            else:
                music.add(at, music.clap(), 0.045)
            music.add(at, music.hat(), 0.018)
            music.add(at + beat / 2, music.hat(), 0.030)
        arpeggio = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[3] + 12,
                    chord[2] + 12, chord[1] + 12, chord[3] + 12, chord[2] + 24]
        for i in range(16):
            music.add(start + i * beat / 2, music.pluck(_midi(arpeggio[i % 8]), 0.45), 0.045)
    for change in scenes[1:] + [end_start]:
        effects.add(change - 0.3, effects.whoosh(0.55, 500, 4000, False), 0.06)

    # End card: resolved chord, bell with the logo (logo pops at endStart + 0.3 s)
    music.pad(ending, ENDING_CHORD, duration - ending - 0.6, 0.028, attack=0.2, release=1.2, cutoff=1800)
    music.add(ending, music.bass(_midi(36), 2.5), 0.16)
    music.add(ending, music.kick(), 0.19)
    effects.add(end_start + 0.3, effects.bell(_midi(84)), 0.10)
    effects.add(end_start + 0.45, effects.bell(_midi(91)), 0.05)

    mix = music.buffer + effects.buffer
    mix = _filter(_filter(mix, "high", 30), "low", 16000)
    tt = np.arange(len(mix)) / SR
    mix *= np.minimum(1, tt / 0.15) * np.clip((duration - tt) / 1.6, 0, 1)
    mix *= 0.89 / (np.max(np.abs(mix)) + 1e-9)

    right = mix.copy()
    delay = int(0.012 * SR)
    right[delay:] = 0.85 * mix[delay:] + 0.15 * mix[:-delay]
    pcm = (np.clip(np.stack([mix, right], axis=1), -1, 1) * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(pcm.tobytes())
