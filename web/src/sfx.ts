// Porting di Sfx.java. La sintesi chiptune è identica: stessa matematica di
// fase/onda/inviluppo. Cambia solo il delivery: javax.sound.sampled.Clip
// diventa Web Audio API (AudioBuffer pre-sintetizzati, riprodotti on demand).
// I browser bloccano l'audio finché non c'è un gesto utente: lo sblocchiamo
// al primo click/tasto.

import { clamp } from "./util.ts";

const SR = 44100;

// Ogni segmento: [frequenza, durata, forma d'onda (0=quadra 1=dente di sega 2=triangolare), volume, glissando, ritardo]
const DEFS: Record<string, number[][]> = {
  hit:      [[210, 0.06, 0, 0.18, 0, 0]],
  shoot:    [[520, 0.05, 0, 0.10, -120, 0]],
  hurt:     [[140, 0.22, 1, 0.40, -60, 0]],
  pickup:   [[740, 0.07, 0, 0.22, 250, 0]],
  food:     [[420, 0.10, 2, 0.35, 120, 0]],
  levelup:  [[523, 0.12, 0, 0.30, 0, 0], [659, 0.12, 0, 0.30, 0, 0.07],
             [784, 0.12, 0, 0.30, 0, 0.14], [1047, 0.14, 0, 0.30, 0, 0.21]],
  meow:     [[620, 0.18, 2, 0.45, -180, 0], [930, 0.12, 2, 0.25, -250, 0.04]],
  boom:     [[90, 0.25, 1, 0.40, -50, 0]],
  boss:     [[80, 0.5, 1, 0.45, 40, 0], [60, 0.5, 0, 0.35, 20, 0.1]],
  win:      [[523, 0.2, 2, 0.40, 0, 0], [659, 0.2, 2, 0.40, 0, 0.12],
             [784, 0.2, 2, 0.40, 0, 0.24], [1047, 0.2, 2, 0.40, 0, 0.36],
             [1319, 0.3, 2, 0.40, 0, 0.48]],
  gameover: [[400, 0.25, 1, 0.35, -30, 0], [320, 0.25, 1, 0.35, -30, 0.18],
             [240, 0.25, 1, 0.35, -30, 0.36], [160, 0.35, 1, 0.35, -30, 0.54]],
};

let ctx: AudioContext | null = null;
const buffers = new Map<string, AudioBuffer>();
export let muted = false;
let lastHit = 0;

/** Riempie un Float32 mono con la sintesi del set di segmenti (identica a Sfx.synth). */
function synth(c: AudioContext, segs: number[][]): AudioBuffer {
  let total = 0;
  for (const s of segs) total = Math.max(total, s[5] + s[1] + 0.05);
  const n = Math.max(1, Math.floor(total * SR));
  const buf = new Float32Array(n);
  for (const s of segs) {
    const freq = s[0], dur = s[1], type = Math.trunc(s[2]), vol = s[3], slide = s[4], delay = s[5];
    const start = Math.floor(delay * SR), len = Math.floor(dur * SR);
    let phase = 0;
    for (let i = 0; i < len && start + i < n; i++) {
      const tt = i / len;
      const f = Math.max(30, freq + slide * tt);
      phase += f / SR;
      const p = phase - Math.floor(phase);
      let w: number;
      switch (type) {
        case 1: w = 2 * p - 1; break;                       // dente di sega
        case 2: w = p < 0.5 ? 4 * p - 1 : 3 - 4 * p; break; // triangolare
        default: w = p < 0.5 ? 1 : -1;                      // quadra
      }
      const env = Math.pow(1 - tt, 1.8);
      buf[start + i] += w * vol * env;
    }
  }
  // stesso fattore di scala del Java (0.55), senza la quantizzazione a 16 bit
  for (let i = 0; i < n; i++) buf[i] = clamp(buf[i], -1, 1) * 0.55;
  const audioBuf = c.createBuffer(1, n, SR);
  audioBuf.copyToChannel(buf, 0);
  return audioBuf;
}

/** Crea il contesto audio e pre-sintetizza tutti i clip. Se l'audio non è
 *  disponibile, il gioco continua muto (come il fallback Java). */
export function initSfx(): void {
  try {
    const AC = window.AudioContext || (window as any).webkitAudioContext;
    ctx = new AC();
    for (const [name, segs] of Object.entries(DEFS)) {
      buffers.set(name, synth(ctx, segs));
    }
    // sblocco al primo gesto utente (policy autoplay dei browser)
    const unlock = () => {
      ctx?.resume();
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    };
    window.addEventListener("pointerdown", unlock);
    window.addEventListener("keydown", unlock);
  } catch {
    ctx = null;
    buffers.clear();
  }
}

export function playSfx(name: string): void {
  if (muted || ctx === null || buffers.size === 0) return;
  if (name === "hit") {
    const now = performance.now();
    if (now - lastHit < 70) return;
    lastHit = now;
  }
  const buf = buffers.get(name);
  if (!buf) return;
  const src = ctx.createBufferSource();
  src.buffer = buf;
  src.connect(ctx.destination);
  src.start();
}

export function toggleMute(): boolean {
  muted = !muted;
  return muted;
}
