// Porting di java.awt.Color: il gioco costruisce colori da interi 0xRRGGBB o da
// componenti r,g,b(,a) e usa brighter()/darker()/getRed()... per le sfumature.
// La stringa CSS viene memorizzata per non ricostruirla a ogni draw call.

const FACTOR = 0.7;

/** Stringa CSS rgba() da componenti float 0..1, per i `new Color(1f, 0.75f, ...)` di Java2D. */
export function cssF(r: number, g: number, b: number, a: number): string {
  return `rgba(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)},${a})`;
}

export class Color {
  readonly r: number;
  readonly g: number;
  readonly b: number;
  readonly a: number; // 0-255
  private css: string | null = null;

  constructor(r: number, g: number, b: number, a = 255) {
    this.r = r | 0;
    this.g = g | 0;
    this.b = b | 0;
    this.a = a | 0;
  }

  /** Da intero 0xRRGGBB (alpha pieno), come `new Color(int)` in Java. */
  static rgb(hex: number): Color {
    return new Color((hex >> 16) & 0xff, (hex >> 8) & 0xff, hex & 0xff, 255);
  }

  getRed() { return this.r; }
  getGreen() { return this.g; }
  getBlue() { return this.b; }
  getAlpha() { return this.a; }

  /** Intero 0xAARRGGBB come Java Color.getRGB(). */
  getRGB(): number {
    return ((this.a << 24) | (this.r << 16) | (this.g << 8) | this.b) | 0;
  }

  /** Stessa identica logica di java.awt.Color.darker(). */
  darker(): Color {
    return new Color(
      Math.max((this.r * FACTOR) | 0, 0),
      Math.max((this.g * FACTOR) | 0, 0),
      Math.max((this.b * FACTOR) | 0, 0),
      this.a,
    );
  }

  /** Stessa identica logica di java.awt.Color.brighter(). */
  brighter(): Color {
    let r = this.r, g = this.g, b = this.b;
    const i = Math.trunc(1.0 / (1.0 - FACTOR)); // = 3
    if (r === 0 && g === 0 && b === 0) return new Color(i, i, i, this.a);
    if (r > 0 && r < i) r = i;
    if (g > 0 && g < i) g = i;
    if (b > 0 && b < i) b = i;
    return new Color(
      Math.min((r / FACTOR) | 0, 255),
      Math.min((g / FACTOR) | 0, 255),
      Math.min((b / FACTOR) | 0, 255),
      this.a,
    );
  }

  /** Nuovo colore con alpha diverso (0-255). Comodo per i `new Color(r,g,b,a)` derivati. */
  withAlpha(a: number): Color {
    return new Color(this.r, this.g, this.b, a);
  }

  /** Stringa CSS rgba() pronta per fillStyle/strokeStyle, calcolata una volta sola. */
  toCss(): string {
    if (this.css === null) {
      this.css = this.a >= 255
        ? `rgb(${this.r},${this.g},${this.b})`
        : `rgba(${this.r},${this.g},${this.b},${(this.a / 255).toFixed(3)})`;
    }
    return this.css;
  }
}
