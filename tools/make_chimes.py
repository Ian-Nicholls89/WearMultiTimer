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


def finish(name, signal, compress=False):
    peak = np.max(np.abs(signal))
    signal = signal / peak
    if compress:
        # Gentle saturation: the quieter parts come up, as modern alert sounds are mixed.
        signal = np.tanh(signal * 1.8) / np.tanh(1.8)
    signal = signal * 0.89  # about -1 dB
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


def space(signal, seconds=0.45, wet=0.35, echoes=((0.14, 0.32), (0.29, 0.16))):
    """
    A soft room and a couple of echoes, worked out round the loop (circular), so the tail of the
    last note carries into the first and the loop stays seamless.
    """
    n = len(signal)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(7)
    room = rng.standard_normal(n) * np.exp(-t / (seconds / 6.9))  # -60 dB after `seconds`
    room = np.convolve(room, np.ones(6) / 6, mode="same")  # take the hiss off the top
    room[0] = 0
    ir = np.zeros(n)
    ir[0] = 1.0
    for at, level in echoes:
        ir[int(RATE * at)] += level
    ir += wet * room / np.max(np.abs(room))
    return np.real(np.fft.ifft(np.fft.fft(signal) * np.fft.fft(ir)))


# A mallet on a bar: rounder than a tube - softer upper partials, which fade fast.
MALLET = [(1.0, 1.0, 1.0), (3.99, 0.22, 4.0), (9.85, 0.05, 7.0)]


def soft(note_strike, attack):
    """Softens a strike's start: a felt mallet rather than a hard hammer."""
    t = np.arange(len(note_strike)) / RATE
    return note_strike * np.minimum(1.0, t / attack)


def layered_bell():
    """C2: C's double strike, softer, with a warm body an octave down and glassy sparkle above."""
    d6 = 1174.7
    bell_note = soft(strike(d6, 3.0, BELL, decay=1.5, detune=0.0015), 0.006)
    body = soft(strike(d6 / 2, 3.0, [(1.0, 1.0, 1.0), (2.0, 0.3, 1.5)], decay=1.1), 0.02)
    sparkle = strike(d6 * 3, 3.0, TUBE[:2], decay=4.0, detune=0.004)
    layer = bell_note + 0.35 * body + 0.18 * sparkle
    return space(place(3.0, [(0.0, layer), (0.55, 0.8 * layer)]))


def bell_chord():
    """C3: D rings, then a D-major-add-9 chord answers above it - a modern ding-dong."""
    def tone(f, level):
        return level * soft(strike(f, 3.2, BELL, decay=1.6, detune=0.0015), 0.005)
    ding = tone(1174.7, 1.0)  # D6
    dong = tone(1480.0, 0.55) + tone(1760.0, 0.5) + tone(2637.0, 0.3)  # F#6 A6 E7
    sparkle = 0.15 * strike(3520.0, 3.2, TUBE[:2], decay=4.5, detune=0.004)
    return space(place(3.2, [(0.0, ding), (0.42, dong), (0.44, sparkle)]))


def soft_mallet():
    """C4: the same chord on felt mallets - rounded, warm, more phone than church."""
    def tone(f, level):
        return level * soft(strike(f, 3.2, MALLET, decay=2.4, detune=0.002), 0.008)
    notes = [(0.00, tone(1174.7, 1.0)), (0.11, tone(1480.0, 0.7)), (0.22, tone(1760.0, 0.7)),
             (0.42, tone(2349.3, 0.75)), (0.44, tone(2637.0, 0.35))]
    return space(place(3.2, notes), seconds=0.6, wet=0.45)


def mallet(f, length, level, decay):
    return level * soft(strike(f, length, MALLET, decay=decay, detune=0.002), 0.008)


def five_note(pattern, gap=0.11, last_gap=0.13, loop=3.0, pickup=None, instrument=mallet):
    """
    The tune in Wear OS's shape (as the user describes it): five quick notes, the first and
    last high and the middle three lower, the last ringing out longest. A [pickup] note, softer,
    leads into the first. [instrument] plays each note: (frequency, length, level, decay).
    """
    lead = gap if pickup else 0.0
    starts = [lead + at for at in (0.0, gap, 2 * gap, 3 * gap, 3 * gap + last_gap)]
    levels = [0.9, 0.65, 0.6, 0.65, 1.0]
    # Short notes, so each is heard and the dip in the middle comes through; the last rings
    # longest but is gone (-60 dB) well before the loop comes round.
    decays = [6.0, 7.0, 7.0, 7.0, 3.5]
    notes = [(at, instrument(f, loop, lv, dc)) for at, f, lv, dc in zip(starts, pattern, levels, decays)]
    if pickup:
        notes.insert(0, (0.0, instrument(pickup, loop, 0.55, 7.0)))
    return space(place(loop, notes), seconds=0.5, wet=0.3, echoes=((0.14, 0.2), (0.29, 0.09)))


B6, A6, G6, D7, E7 = 1975.5, 1760.0, 1568.0, 2349.3, 2637.0


# --- The same tune on other instruments, each made from scratch. ---

def _t(length):
    return np.arange(int(RATE * length)) / RATE


def marimba(f, length, level, decay):
    """Wood: the bar's overtones (about 4x and 10x) die almost at once, leaving a warm, round note."""
    partials = [(1.0, 1.0, 1.0), (3.93, 0.3, 5.0), (9.2, 0.06, 9.0)]
    return level * soft(strike(f, length, partials, decay=decay * 0.9), 0.004)


def vibraphone(f, length, level, decay):
    """Metal bars with the motor's slow wobble (about 5 Hz), ringing longer than wood."""
    partials = [(1.0, 1.0, 1.0), (4.0, 0.25, 3.0), (10.0, 0.04, 6.0)]
    note = soft(strike(f, length, partials, decay=decay * 0.6), 0.004)
    t = _t(length)
    return level * note * (1 - 0.3 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.2 * t)))


def electric_piano(f, length, level, decay):
    """A Rhodes-like tine: one tone bending another, the bark fading fast to a mellow ring."""
    t = _t(length)
    index = 1.6 * np.exp(-t * decay * 2.0)
    tone = np.sin(2 * np.pi * f * t + index * np.sin(2 * np.pi * f * t))
    return level * soft(tone * np.exp(-t * decay * 0.8), 0.003)


def music_box(f, length, level, decay):
    """A steel comb: bright and tinkly, quick to fade."""
    partials = [(1.0, 1.0, 1.0), (3.0, 0.3, 2.5), (5.8, 0.12, 4.0)]
    return level * strike(f, length, partials, decay=decay * 1.3)


def kalimba(f, length, level, decay):
    """A thumb piano: a soft tine with a high ping and a little click of the thumb."""
    partials = [(1.0, 1.0, 1.0), (6.0, 0.2, 6.0), (10.6, 0.05, 9.0)]
    note = strike(f, length, partials, decay=decay * 1.1)
    t = _t(length)
    click = np.random.default_rng(int(f)).standard_normal(len(t)) * np.exp(-t * 400) * 0.25
    return level * (note + click)


def harp(f, length, level, decay):
    """
    A plucked string: whole-number overtones, shaped by plucking a third of the way along, the
    higher ones fading faster, with a brief pluck noise at the start.
    """
    t = _t(length)
    note = np.zeros_like(t)
    for k in range(1, 9):
        fk = f * k
        if fk > RATE / 2 - 500:
            break
        weight = abs(np.sin(np.pi * k / 3.3)) / k
        note += weight * np.exp(-t * decay * 0.8 * (1 + 0.5 * (k - 1))) * np.sin(2 * np.pi * fk * t)
    pluck = np.random.default_rng(int(f) + 1).standard_normal(len(t)) * np.exp(-t * 900) * 0.15
    return level * soft(note + pluck, 0.001)


def synth_pluck(f, length, level, decay):
    """A modern synth pluck: two slightly detuned saws, the brightness closing down as it fades."""
    t = _t(length)
    note = np.zeros_like(t)
    for detune in (-0.004, 0.004):
        for k in range(1, 40):
            fk = f * k * (1 + detune)
            if fk > RATE / 2 - 500:
                break
            note += (1 / k) * np.exp(-t * decay * (0.7 + 0.35 * (k - 1))) * np.sin(2 * np.pi * fk * t)
    return level * soft(note / 2, 0.003)


def semitones(n):
    return 2 ** (n / 12)


def tune(shift):
    """C4d's notes moved by [shift] semitones: (pickup, [the five])."""
    k = semitones(shift)
    return A6 * k, [D7 * k, A6 * k, G6 * k, A6 * k, E7 * k]


if __name__ == "__main__":
    finish("a_glass.wav", glass())
    finish("b_wind.wav", wind())
    finish("c_bell.wav", bell())
    finish("c2_layered_bell.wav", layered_bell(), compress=True)
    finish("c3_bell_chord.wav", bell_chord(), compress=True)
    finish("c4_soft_mallet.wav", soft_mallet(), compress=True)
    finish("c4a_arc.wav", five_note([E7, B6, A6, B6, E7]), compress=True)
    finish("c4b_pulse.wav", five_note([E7, B6, B6, B6, E7]), compress=True)
    finish("c4c_lift.wav", five_note([D7, A6, G6, A6, E7]), compress=True)
    finish("c4d_lift_pickup.wav", five_note([D7, A6, G6, A6, E7], pickup=A6), compress=True)

    # C4d a fifth lower (D6 · G6 · D6 C6 D6 · A6), on each instrument; and the mallet an octave down
    # (A5 · D6 · A5 G5 A5 · E6).
    pickup5, five5 = tune(-7)
    for name, instrument in [("1_mallet", mallet), ("2_marimba", marimba), ("3_vibraphone", vibraphone),
                             ("4_electric_piano", electric_piano), ("5_music_box", music_box),
                             ("6_kalimba", kalimba), ("7_harp", harp), ("8_synth_pluck", synth_pluck)]:
        finish(f"d{name}.wav", five_note(five5, pickup=pickup5, instrument=instrument), compress=True)
    pickup12, five12 = tune(-12)
    finish("d1_mallet_octave_down.wav", five_note(five12, pickup=pickup12), compress=True)
