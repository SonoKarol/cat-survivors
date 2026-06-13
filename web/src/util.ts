// Porting di Util.java. La riproducibilità bit-a-bit con java.util.Random non
// serve nel browser (il gioco è host-authoritative: l'host tira i dadi, i client
// renderizzano gli snapshot), quindi usiamo un PRNG mulberry32 semplice e seedabile.

export const TAU = Math.PI * 2;

let _state = (Math.random() * 0xffffffff) >>> 0;

function next(): number {
  // mulberry32
  _state = (_state + 0x6d2b79f5) | 0;
  let t = Math.imul(_state ^ (_state >>> 15), 1 | _state);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

/** Fissa il seme del PRNG (per test riproducibili). */
export function seed(s: number): void {
  _state = s >>> 0;
}

export function rand(a: number, b: number): number {
  return a + next() * (b - a);
}

/** Intero in [a, b] inclusi, come `a + RNG.nextInt(b-a+1)` in Java. */
export function randInt(a: number, b: number): number {
  return a + Math.floor(next() * (b - a + 1));
}

export function choice<T>(arr: readonly T[]): T {
  return arr[Math.floor(next() * arr.length)];
}

export function chance(p: number): boolean {
  return next() < p;
}

/** Numero casuale in [0,1), come Random.nextDouble(). */
export function random(): number {
  return next();
}

/** Mescola in place (Fisher-Yates), come Collections.shuffle(list, RNG). */
export function shuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(next() * (i + 1));
    const t = arr[i]; arr[i] = arr[j]; arr[j] = t;
  }
  return arr;
}

export function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v;
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

export function dist(ax: number, ay: number, bx: number, by: number): number {
  return Math.hypot(bx - ax, by - ay);
}

export function dist2(ax: number, ay: number, bx: number, by: number): number {
  const dx = bx - ax, dy = by - ay;
  return dx * dx + dy * dy;
}

export function angleTo(ax: number, ay: number, bx: number, by: number): number {
  return Math.atan2(by - ay, bx - ax);
}

/** Hash deterministico 2D -> [0,1), per decorare il terreno senza memorizzarlo.
 *  Replica l'overflow a 32 bit di Java con Math.imul e gli shift. */
export function hash2(x: number, y: number): number {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) & 0x7fffffff) / 2147483648.0;
}

export function fmtTime(s: number): string {
  const t = Math.max(0, Math.floor(s));
  return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, "0")}`;
}
