// Wrapper sottile su CanvasRenderingContext2D che replica le primitive Java2D
// usate dal gioco (Graphics2D + java.awt.geom). Permette di portare Sprites,
// Game.render, Ui e ClientView quasi riga per riga.
//
// Convenzione angoli archi: java.awt.geom.Arc2D misura i gradi in senso
// antiorario con y verso l'alto, mentre il canvas usa radianti con y verso il
// basso. La mappatura è angolo_canvas = -gradi_java (vedi drawArc).

import { Color } from "./color.ts";
import { TAU } from "./util.ts";

export type CtxImage = HTMLCanvasElement | OffscreenCanvas | HTMLImageElement;

/** Costruisce una stringa CSS-font in stile `new Font(SANS_SERIF, BOLD, size)`. */
export function font(size: number, bold = true, family = "sans-serif"): string {
  return `${bold ? "bold " : ""}${size}px ${family}`;
}

export class G {
  readonly ctx: CanvasRenderingContext2D;

  constructor(ctx: CanvasRenderingContext2D) {
    this.ctx = ctx;
    ctx.textAlign = "left";
    ctx.textBaseline = "alphabetic";
  }

  // --- stato / trasformazioni (getTransform/setTransform e getComposite/setComposite di Java) ---
  save() { this.ctx.save(); }
  restore() { this.ctx.restore(); }
  translate(x: number, y: number) { this.ctx.translate(x, y); }
  rotate(a: number) { this.ctx.rotate(a); }
  scale(x: number, y: number) { this.ctx.scale(x, y); }
  setAlpha(a: number) { this.ctx.globalAlpha = a; }

  setColor(c: Color | string) {
    const s = typeof c === "string" ? c : c.toCss();
    this.ctx.fillStyle = s;
    this.ctx.strokeStyle = s;
  }

  /** Equivalente di new BasicStroke(width[, cap, join]). */
  setStroke(width: number, cap: CanvasLineCap = "butt", join: CanvasLineJoin = "miter") {
    this.ctx.lineWidth = width;
    this.ctx.lineCap = cap;
    this.ctx.lineJoin = join;
  }

  // --- riempimenti / contorni ---
  fillRect(x: number, y: number, w: number, h: number) { this.ctx.fillRect(x, y, w, h); }
  strokeRect(x: number, y: number, w: number, h: number) { this.ctx.strokeRect(x, y, w, h); }

  /** fill(new Ellipse2D.Double(x,y,w,h)) — x,y angolo in alto a sinistra del riquadro. */
  fillEllipse(x: number, y: number, w: number, h: number) {
    const c = this.ctx;
    c.beginPath();
    c.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, TAU);
    c.fill();
  }

  drawEllipse(x: number, y: number, w: number, h: number) {
    const c = this.ctx;
    c.beginPath();
    c.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, TAU);
    c.stroke();
  }

  fillCircle(cx: number, cy: number, r: number) {
    const c = this.ctx;
    c.beginPath();
    c.arc(cx, cy, r, 0, TAU);
    c.fill();
  }

  drawCircle(cx: number, cy: number, r: number) {
    const c = this.ctx;
    c.beginPath();
    c.arc(cx, cy, r, 0, TAU);
    c.stroke();
  }

  /** draw(new Arc2D.Double(x,y,w,h,start,extent,OPEN)) — arco non chiuso, da contornare. */
  drawArc(x: number, y: number, w: number, h: number, startDeg: number, extentDeg: number) {
    const c = this.ctx;
    const cx = x + w / 2, cy = y + h / 2, rx = w / 2, ry = h / 2;
    const s = -startDeg * Math.PI / 180;
    const e = -(startDeg + extentDeg) * Math.PI / 180;
    c.beginPath();
    c.ellipse(cx, cy, rx, ry, 0, s, e, extentDeg > 0);
    c.stroke();
  }

  /** fill(new Arc2D.Double(...,PIE)) — spicchio pieno (menu radiali). */
  fillArc(x: number, y: number, w: number, h: number, startDeg: number, extentDeg: number) {
    const c = this.ctx;
    const cx = x + w / 2, cy = y + h / 2, rx = w / 2, ry = h / 2;
    const s = -startDeg * Math.PI / 180;
    const e = -(startDeg + extentDeg) * Math.PI / 180;
    c.beginPath();
    c.moveTo(cx, cy);
    c.ellipse(cx, cy, rx, ry, 0, s, e, extentDeg > 0);
    c.closePath();
    c.fill();
  }

  /** draw(new QuadCurve2D.Double(x1,y1,cx,cy,x2,y2)). */
  drawQuad(x1: number, y1: number, cx: number, cy: number, x2: number, y2: number) {
    const c = this.ctx;
    c.beginPath();
    c.moveTo(x1, y1);
    c.quadraticCurveTo(cx, cy, x2, y2);
    c.stroke();
  }

  drawLine(x1: number, y1: number, x2: number, y2: number) {
    const c = this.ctx;
    c.beginPath();
    c.moveTo(x1, y1);
    c.lineTo(x2, y2);
    c.stroke();
  }

  fillRoundRect(x: number, y: number, w: number, h: number, r: number) {
    const c = this.ctx;
    c.beginPath();
    c.roundRect(x, y, w, h, r);
    c.fill();
  }

  drawRoundRect(x: number, y: number, w: number, h: number, r: number) {
    const c = this.ctx;
    c.beginPath();
    c.roundRect(x, y, w, h, r);
    c.stroke();
  }

  fillPath(p: Path2D) { this.ctx.fill(p); }
  drawPath(p: Path2D) { this.ctx.stroke(p); }

  /** drawImage(img, dx, dy) — l'immagine è già un canvas off-screen. */
  drawImage(img: CtxImage, dx: number, dy: number) {
    this.ctx.drawImage(img as CanvasImageSource, dx, dy);
  }

  // --- testo ---
  setFont(css: string) { this.ctx.font = css; }
  setTextAlign(a: CanvasTextAlign) { this.ctx.textAlign = a; }
  setTextBaseline(b: CanvasTextBaseline) { this.ctx.textBaseline = b; }
  drawString(text: string, x: number, y: number) { this.ctx.fillText(text, x, y); }
  stringWidth(text: string): number { return this.ctx.measureText(text).width; }
}
