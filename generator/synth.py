"""Herramientas de sintesis de audio compartidas por sounds.py y sounds_species.py."""
import numpy as np

SR = 44100
rng = np.random.default_rng(1993)


# ---------------------------------------------------------------- bloques basicos
def curve(points, n):
    """points: [(fraccion_de_tiempo, valor), ...] -> array de n muestras."""
    xs, ys = zip(*points)
    return np.interp(np.linspace(0, 1, n), xs, ys)


def smooth_noise(n, rate_hz, amount):
    """Ruido lento (para jitter de tono)."""
    k = max(2, int(n * rate_hz / SR) + 2)
    return 1 + amount * np.interp(np.arange(n), np.linspace(0, n, k), rng.uniform(-1, 1, k))


def spectral(x, shape):
    """Filtra x con una curva de respuesta definida en frecuencia: shape(freqs) -> ganancia."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * shape(f), len(x))


def formants(peaks, floor=0.08):
    def shape(f):
        g = np.full_like(f, floor)
        for fc, gain, bw in peaks:
            g += gain * np.exp(-((f - fc) / bw) ** 2)
        return g
    return shape


def band(lo, hi):
    return lambda f: ((f > lo) & (f < hi)).astype(float) * 1.0


def noise(n):
    return rng.standard_normal(n)


def norm(x, peak=1.0):
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


def voice(dur, f0, env, harmonics=28, tilt=0.85, sub=0.35, rough=0.35, rough_hz=48,
          vib=(7.0, 0.035), jitter=0.02, peaks=((850, 1.0, 320), (2100, 0.75, 450), (3500, 0.45, 700)),
          breath=0.25, breath_band=(1800, 7500), drive=2.2):
    """Voz animal: armonicos con formantes + subarmonico + aspereza + aire."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    freq = curve(f0, n) * (1 + vib[1] * np.sin(2 * np.pi * vib[0] * t)) * smooth_noise(n, 25, jitter)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    src = np.zeros(n)
    for k in range(1, harmonics + 1):
        src += (k ** -tilt) * np.sin(k * phase) * (k * freq < 15000)
    src += sub * np.sin(0.5 * phase) * (1 + np.sin(phase))  # subarmonico: da el "gruñido"
    src *= 1 + rough * np.sin(2 * np.pi * rough_hz * t + 3 * smooth_noise(n, 8, 1))
    src = norm(spectral(src, formants(peaks)))
    air = norm(spectral(noise(n), band(*breath_band))) * breath
    y = (src + air) * curve(env, n)
    return np.tanh(drive * y) / np.tanh(drive)


def thump(dur=0.16, f=(75, 42), click=0.4):
    n = int(dur * SR)
    t = np.arange(n) / SR
    freq = curve([(0, f[0]), (1, f[1])], n)
    body = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t * 28)
    grit = spectral(noise(n), band(200, 2200)) * np.exp(-t * 90)
    return norm(body + click * norm(grit))


def click(dur=0.006, lo=1500, hi=9000):
    n = int(dur * SR)
    t = np.arange(n) / SR
    return norm(spectral(noise(n), band(lo, hi))) * np.exp(-t * 900)


def seq(parts, gaps):
    """Concatena partes con silencios (gaps en segundos, uno menos que partes)."""
    out = [parts[0]]
    for p, g in zip(parts[1:], gaps):
        out.append(np.zeros(int(g * SR)))
        out.append(p)
    return np.concatenate(out)


def place(dur, items):
    """items: [(tiempo_inicio, audio, ganancia)] -> mezcla de largo dur."""
    out = np.zeros(int(dur * SR))
    for start, audio, gain in items:
        i = int(start * SR)
        j = min(len(out), i + len(audio))
        out[i:j] += audio[: j - i] * gain
    return out


def room(x, wet=0.22):
    """Reverb corta (unos ecos tempranos) para que no suene 'seco'."""
    y = np.copy(x)
    for d, g in ((0.029, 1.0), (0.047, 0.7), (0.071, 0.5), (0.113, 0.3)):
        k = int(d * SR)
        y[k:] += x[:-k] * g * wet
    return y


def fade(x, ms=8):
    k = max(1, min(int(ms / 1000 * SR), len(x) // 4))
    x = np.copy(x)
    x[:k] *= np.linspace(0, 1, k)
    x[-k:] *= np.linspace(1, 0, k)
    return x
