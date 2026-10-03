"""
Makes the "Time's up" chimes: struck-metal sounds, synthesised from scratch, that loop without a seam.

    python3 tools/make_chimes.py        # needs numpy; writes docs/chimes/*.wav

A chime sounds like metal rather than a beep because its overtones aren't whole multiples of the
note (a tube's are about 2.76x and 5.40x), the higher ones die away faster, and each note rings on
under the next. Everything sits in 1-8 kHz, where a watch's small speaker is loudest.
"""
import os
import wave

import numpy as np

RATE = 32_000
OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "chimes")


def strike(freq, length, partials, decay, detune=0.0):
    """One struck note: (ratio, level, decay multiplier) partials, each fading on its own."""
    t = np.arange(int(RATE * length)) / RATE
    note = np.zeros_like(t)
    for ratio, level, faster in partials:
        f = freq * ratio
        if f > RATE / 2 - 500:
            continue
        env = np.exp(-t * decay * faster)
        tone = np.sin(2 * np.pi * f * t)
        if detune:
            # A second, slightly sharp copy: the slow beating is the shimmer of a real chime.
            tone = 0.5 * (tone + np.sin(2 * np.pi * f * (1 + detune) * t + 0.7))
        note += level * env * tone
    attack = np.minimum(1.0, t / 0.002)  # 2 ms: a strike, not a click
    return note * attack


def place(loop_len, events):
    """Mixes notes into one loop; anything ringing past the end wraps to the start, so it loops cleanly."""
    n = int(RATE * loop_len)
    out = np.zeros(n)
    for start, note in events:
        i = int(RATE * start)
        for k in range(0, len(note), n):
            chunk = note[k:k + n]
            idx = (np.arange(len(chunk)) + i + k) % n
            np.add.at(out, idx, chunk)
    return out


def finish(name, signal):
    peak = np.max(np.abs(signal))
    signal = signal / peak * 0.89  # about -1 dB
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((signal * 32767).astype("<i2").tobytes())
    print(f"{path}: {len(signal) / RATE:.2f} s")


# A tube's partials: (ratio to the note, level, how much faster it fades).
TUBE = [(1.0, 1.0, 1.0), (2.756, 0.55, 1.8), (5.404, 0.28, 3.2), (8.933, 0.10, 5.0)]

# A small bell's: a minor third, fifth and octave above, with a quiet hum below.
BELL = [(0.5, 0.25, 0.6), (1.0, 1.0, 1.0), (1.19, 0.6, 1.3), (1.5, 0.45, 1.5),
        (2.0, 0.55, 1.9), (2.51, 0.25, 2.6), (3.01, 0.18, 3.2)]


def glass():
    """A: C-E-G rising, each note ringing under the next; then a breath before it comes round."""
    c6, e6, g6 = 1046.5, 1318.5, 1568.0
    notes = [strike(f, 2.4, TUBE, decay=2.6) for f in (c6, e6, g6)]
    return place(2.4, [(0.00, notes[0]), (0.16, 0.9 * notes[1]), (0.32, 0.85 * notes[2])])


def wind():
    """B: pentatonic notes at uneven gaps, shimmering - wind chimes, but in a hurry."""
    scale = [1318.5, 1568.0, 1760.0, 2093.0, 2349.3]  # E6 G6 A6 C7 D7
    order = [(0.00, 2), (0.13, 4), (0.31, 1), (0.42, 3), (0.66, 0)]
    events = [(at, 0.8 * strike(scale[i], 2.8, TUBE, decay=1.8, detune=0.0025)) for at, i in order]
    return place(2.8, events)


def bell():
    """C: a small bell struck twice - ding... ding - with a long shimmering tail."""
    note = strike(1174.7, 3.0, BELL, decay=1.5, detune=0.0015)  # D6
    return place(3.0, [(0.0, note), (0.55, 0.8 * note)])


if __name__ == "__main__":
    finish("a_glass.wav", glass())
    finish("b_wind.wav", wind())
    finish("c_bell.wav", bell())
