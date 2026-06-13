// Porting di Sprites.java. Tutta la grafica è disegnata proceduralmente con il
// wrapper G e messa in cache come canvas off-screen: nessun asset esterno.
// RoundRectangle2D di Java usa arcwidth/archeight (diametro dell'arco): il
// raggio per roundRect del canvas è quindi arcwidth/2.

import { Color, cssF } from "./color.ts";
import { G } from "./g2d.ts";
import { TAU } from "./util.ts";
import type { CatDef, CatSprite } from "./types.ts";

const WHITE = Color.rgb(0xffffff);
const BLACK = Color.rgb(0x000000);
const GRAY = Color.rgb(0x808080);
const MAGENTA = Color.rgb(0xff00ff);

const CACHE = new Map<string, HTMLCanvasElement>();

type DrawFn = (g: G) => void;

function make(w: number, h: number, fn: DrawFn): HTMLCanvasElement {
  const cv = document.createElement("canvas");
  cv.width = w;
  cv.height = h;
  const ctx = cv.getContext("2d")!;
  fn(new G(ctx));
  return cv;
}

function cached(key: string, w: number, h: number, fn: DrawFn): HTMLCanvasElement {
  let img = CACHE.get(key);
  if (img === undefined) {
    img = make(w, h, fn);
    CACHE.set(key, img);
  }
  return img;
}

// ===== Helper geometrici =====

/** Ellisse piena centrata in (cx,cy) con semiassi rx,ry — come ell() del Java. */
function ell(g: G, cx: number, cy: number, rx: number, ry: number): void {
  g.fillEllipse(cx - rx, cy - ry, rx * 2, ry * 2);
}

function tri(g: G, x1: number, y1: number, x2: number, y2: number, x3: number, y3: number): void {
  const p = new Path2D();
  p.moveTo(x1, y1);
  p.lineTo(x2, y2);
  p.lineTo(x3, y3);
  p.closePath();
  g.fillPath(p);
}

// ===== Gatti =====

function drawCat(g: G, s: CatSprite): void {
  const f = s.fluffy ? 1.1 : 1.0;
  g.translate(24, 0);
  // coda
  g.setStroke(s.fluffy ? 7 : 4.5, "round", "round");
  g.setColor(s.mask ?? (s.stripes ?? s.body));
  g.drawQuad(10, 39, 22, 38, 21, 26);
  // corpo
  g.setColor(s.body);
  ell(g, 0, 36, 12 * f, 8.5 * f);
  if (s.belly) {
    g.setColor(s.belly);
    ell(g, 0, 38, 7 * f, 5.5 * f);
  }
  // zampe anteriori
  g.setColor(s.body);
  ell(g, -5, 43, 3, 2.5);
  ell(g, 5, 43, 3, 2.5);
  // orecchie
  const earH = s.sphynx ? 13 : 10;
  g.setColor(s.mask ?? s.body);
  tri(g, -11, 13, -8, 13 - earH, -2, 9);
  tri(g, 11, 13, 8, 13 - earH, 2, 9);
  g.setColor(Color.rgb(0xf2a0b5));
  tri(g, -9, 12, -7.5, 12 - earH * 0.55, -4, 10);
  tri(g, 9, 12, 7.5, 12 - earH * 0.55, 4, 10);
  // testa
  g.setColor(s.body);
  ell(g, 0, 19, 11.5 * f, 10.5 * f);
  // guance pelose (razze a pelo lungo)
  if (s.fluffy) {
    tri(g, -11 * f, 17, -14 * f, 22, -8, 23);
    tri(g, 11 * f, 17, 14 * f, 22, 8, 23);
  }
  // muso scuro (point siamese/ragdoll)
  if (s.mask) {
    g.setColor(s.mask.withAlpha(215));
    ell(g, 0, 23, 6.5, 5);
  }
  // strisce del tigrato
  if (s.stripes) {
    g.setColor(s.stripes);
    g.setStroke(1.6, "round", "round");
    g.drawLine(-5, 10, -6, 14);
    g.drawLine(0, 9, 0, 13);
    g.drawLine(5, 10, 6, 14);
  }
  // rughe dello sphynx
  if (s.sphynx) {
    g.setColor(new Color(0, 0, 0, 40));
    g.setStroke(1.2);
    g.drawArc(-6, 10, 12, 5, 0, 180);
    g.drawArc(-5, 13, 10, 4, 0, 180);
  }
  // occhi
  g.setColor(s.eyes);
  ell(g, -4.5, 18, 2.6, 3);
  ell(g, 4.5, 18, 2.6, 3);
  g.setColor(Color.rgb(0x1c1c22));
  ell(g, -4.5, 18.4, 1.1, 2.2);
  ell(g, 4.5, 18.4, 1.1, 2.2);
  g.setColor(new Color(255, 255, 255, 220));
  ell(g, -3.9, 17.2, 0.7, 0.7);
  ell(g, 5.1, 17.2, 0.7, 0.7);
  // naso e bocca
  g.setColor(Color.rgb(0xf7b9c4));
  tri(g, 0, 23.5, -1.8, 21.4, 1.8, 21.4);
  g.setColor(new Color(40, 30, 30, 180));
  g.setStroke(1, "round", "round");
  g.drawQuad(0, 23.5, -1.5, 25.5, -3, 24.5);
  g.drawQuad(0, 23.5, 1.5, 25.5, 3, 24.5);
  // baffi
  g.setColor(new Color(255, 255, 255, 200));
  g.drawLine(-7, 21.5, -14, 20.5);
  g.drawLine(-7, 23, -14, 23.5);
  g.drawLine(7, 21.5, 14, 20.5);
  g.drawLine(7, 23, 14, 23.5);
  g.translate(-24, 0);
}

// ===== Nemici =====

function enemySize(t: string): [number, number] {
  switch (t) {
    case "cetriolo": return [28, 32];
    case "cetriolone": return [44, 50];
    case "piccione": return [30, 28];
    case "topo": return [28, 24];
    case "anatra": return [28, 30];
    case "spruzzino": return [26, 36];
    case "aspirapolvere": return [38, 46];
    case "roomba": return [64, 64];
    case "phon": return [56, 48];
    case "veterinario": return [52, 68];
    default: return [24, 24];
  }
}

function drawEnemy(g: G, type: string, w: number, h: number): void {
  switch (type) {
    case "cetriolo": drawCuke(g, w, h, Color.rgb(0x4f9e3c), Color.rgb(0x6cbf4e), Color.rgb(0x33701f)); break;
    case "cetriolone": drawCuke(g, w, h, Color.rgb(0x3f8a30), Color.rgb(0x5cab41), Color.rgb(0x265c1a)); break;
    case "piccione": drawPigeon(g); break;
    case "topo": drawRobotMouse(g); break;
    case "anatra": drawDuck(g); break;
    case "spruzzino": drawSprayer(g); break;
    case "aspirapolvere": drawVacuum(g); break;
    case "roomba": drawRoomba(g); break;
    case "phon": drawHairdryer(g); break;
    case "veterinario": drawVet(g); break;
    default: g.setColor(MAGENTA); ell(g, w / 2, h / 2, w / 2.5, h / 2.5);
  }
}

/** Il terrore di ogni gatto: un cetriolo arrabbiato. */
function drawCuke(g: G, w: number, h: number, body: Color, light: Color, dark: Color): void {
  const sx = w / 28, sy = h / 32;
  g.translate(w / 2, h / 2);
  g.rotate(-0.22);
  g.setColor(body);
  ell(g, 0, 0, 8.5 * sx, 13 * sy);
  g.setColor(light);
  ell(g, -2.5 * sx, -1 * sy, 3.6 * sx, 10.5 * sy);
  g.setColor(dark);
  const bumps = [[-4, -9], [5, -6], [-5, 3], [4, 7], [0, 11], [2, -12]];
  for (const b of bumps) ell(g, b[0] * sx, b[1] * sy, 1.3 * sx, 1.3 * sx);
  // occhi arrabbiati
  g.setColor(WHITE);
  ell(g, -3.4 * sx, -3.5 * sy, 2.6 * sx, 2.8 * sy);
  ell(g, 3.4 * sx, -3.5 * sy, 2.6 * sx, 2.8 * sy);
  g.setColor(Color.rgb(0x222222));
  ell(g, -2.8 * sx, -3 * sy, 1.1 * sx, 1.2 * sy);
  ell(g, 4 * sx, -3 * sy, 1.1 * sx, 1.2 * sy);
  g.setStroke(1.6 * sx, "round", "round");
  g.setColor(Color.rgb(0x1e3d18));
  g.drawLine(-6 * sx, -7.5 * sy, -1.5 * sx, -5.5 * sy);
  g.drawLine(6 * sx, -7.5 * sy, 1.5 * sx, -5.5 * sy);
  // smorfia
  g.drawQuad(-3 * sx, 3.5 * sy, 0, 1.5 * sy, 3 * sx, 3.5 * sy);
}

function drawPigeon(g: G): void {
  g.setColor(Color.rgb(0x8d97a3));
  ell(g, 14, 16, 9.5, 7.5);
  g.setColor(Color.rgb(0x6d7681));
  tri(g, 5, 14, 0, 11, 2, 19);
  g.setColor(Color.rgb(0xaab3bd));
  ell(g, 12, 14.5, 5.5, 4);
  g.setColor(Color.rgb(0x7d8794));
  ell(g, 22.5, 9.5, 4.5, 4.5);
  g.setColor(Color.rgb(0xe8a13c));
  tri(g, 27, 9, 30, 10.5, 26.5, 11.5);
  g.setColor(Color.rgb(0xf2622e));
  ell(g, 23.5, 8.5, 1.3, 1.3);
  g.setColor(BLACK);
  ell(g, 23.7, 8.7, 0.6, 0.6);
  g.setColor(Color.rgb(0xd77f4e));
  g.setStroke(1.5, "round", "round");
  g.drawLine(11, 23, 10, 27);
  g.drawLine(16, 23, 16, 27);
}

function drawRobotMouse(g: G): void {
  g.setColor(Color.rgb(0x6f7682));
  g.setStroke(1.4, "round", "round");
  const z = new Path2D();
  z.moveTo(4, 14); z.lineTo(1, 11); z.lineTo(4, 9); z.lineTo(1, 6);
  g.drawPath(z);
  g.setColor(Color.rgb(0xb8bec9));
  ell(g, 9, 8, 3.5, 3.5);
  ell(g, 15, 7, 3.5, 3.5);
  g.setColor(Color.rgb(0xf2a0b5));
  ell(g, 9, 8, 1.8, 1.8);
  ell(g, 15, 7, 1.8, 1.8);
  g.setColor(Color.rgb(0x9aa1ad));
  ell(g, 13, 15, 9, 6.5);
  g.setColor(Color.rgb(0xb8bec9));
  tri(g, 19, 11, 24, 14, 19, 17);
  g.setColor(Color.rgb(0xd3636f));
  ell(g, 23.5, 14, 1.8, 1.8);
  g.setColor(Color.rgb(0xff4040));
  ell(g, 16.5, 12.5, 1.6, 1.6);
  g.setColor(new Color(255, 120, 120, 120));
  ell(g, 16.5, 12.5, 2.8, 2.8);
  g.setColor(Color.rgb(0x6f7682));
  g.drawLine(11, 10, 11, 4);
  g.setColor(Color.rgb(0xffd166));
  ell(g, 11, 3.5, 1.5, 1.5);
  g.setColor(Color.rgb(0x4a4f58));
  ell(g, 9, 21, 2.2, 2.2);
  ell(g, 17, 21, 2.2, 2.2);
}

function drawDuck(g: G): void {
  g.setColor(Color.rgb(0xf4d03f));
  ell(g, 14, 19, 10, 7.5);
  g.setColor(Color.rgb(0xe2bb2d));
  ell(g, 11, 18.5, 5, 3.8);
  g.setColor(Color.rgb(0xf4d03f));
  ell(g, 18, 9, 6, 6);
  g.setColor(Color.rgb(0xef8e2e));
  ell(g, 25, 10.5, 3.4, 2);
  g.setColor(BLACK);
  ell(g, 19.5, 7.5, 1.3, 1.3);
  g.setColor(WHITE);
  ell(g, 19.9, 7.1, 0.5, 0.5);
  g.setColor(new Color(255, 255, 255, 90));
  ell(g, 11, 15.5, 2.5, 1.5);
}

function drawSprayer(g: G): void {
  g.setColor(Color.rgb(0x5fa8d3));
  g.fillRoundRect(7, 14, 12, 19, 2);
  g.setColor(Color.rgb(0x8ec7e8));
  g.fillRoundRect(8.5, 22, 9, 9.5, 1.5);
  g.setColor(Color.rgb(0x3c6e91));
  g.fillRoundRect(8, 6, 10, 8, 1);
  g.fillRoundRect(17, 7, 5, 3, 0.5);
  g.setStroke(2, "round", "round");
  g.drawQuad(9, 14, 5, 15, 6, 19);
  g.setColor(Color.rgb(0xbfe3f5));
  ell(g, 23.5, 6.5, 1.2, 1.6);
  ell(g, 24.5, 10, 1, 1.3);
  g.setColor(WHITE);
  ell(g, 13, 18, 2.6, 2.6);
  g.setColor(Color.rgb(0x222222));
  ell(g, 13.7, 18.3, 1.1, 1.1);
}

function drawVacuum(g: G): void {
  g.setColor(Color.rgb(0x7a3b46));
  g.fillRoundRect(8, 16, 22, 26, 3);
  g.setColor(Color.rgb(0xa05262));
  ell(g, 19, 27, 8, 10);
  g.setColor(Color.rgb(0x4a2a30));
  g.setStroke(3, "round", "round");
  g.drawLine(19, 16, 19, 5);
  ell(g, 19, 4, 3, 2);
  g.setColor(new Color(255, 209, 102, 110));
  ell(g, 19, 21, 5.5, 5.5);
  g.setColor(Color.rgb(0xffd166));
  ell(g, 19, 21, 3, 3);
  g.setColor(Color.rgb(0x2e2e34));
  ell(g, 12, 43, 3, 3);
  ell(g, 26, 43, 3, 3);
}

function drawRoomba(g: G): void {
  g.setColor(Color.rgb(0x2e3440));
  ell(g, 32, 32, 27, 27);
  g.setColor(Color.rgb(0x4c566a));
  g.setStroke(4);
  g.drawEllipse(12, 12, 40, 40);
  g.setColor(Color.rgb(0x3b4252));
  g.fillArc(5, 5, 54, 54, 50, 80);
  g.setColor(Color.rgb(0x2e3440));
  ell(g, 32, 32, 22, 22);
  g.setColor(Color.rgb(0x88c0d0));
  ell(g, 32, 32, 6.5, 6.5);
  g.setColor(Color.rgb(0x2e3440));
  ell(g, 32, 32, 3, 3);
  g.setColor(new Color(255, 70, 70, 130));
  ell(g, 23, 23, 5, 5);
  ell(g, 41, 23, 5, 5);
  g.setColor(Color.rgb(0xff4040));
  ell(g, 23, 23, 2.8, 2.8);
  ell(g, 41, 23, 2.8, 2.8);
  g.setColor(Color.rgb(0x6f7682));
  g.setStroke(1.6, "round", "round");
  for (let i = 0; i < 5; i++) {
    const a = Math.PI * 0.55 + i * 0.18;
    g.drawLine(32 + Math.cos(a) * 26, 32 + Math.sin(a) * 26,
      32 + Math.cos(a) * 32, 32 + Math.sin(a) * 32);
  }
}

function drawHairdryer(g: G): void {
  g.setColor(Color.rgb(0x9a4a72));
  g.fillRoundRect(14, 26, 9, 18, 2);
  g.setColor(Color.rgb(0xc95d8e));
  ell(g, 22, 18, 14, 11);
  g.fillRoundRect(33, 12, 14, 12, 1.5);
  g.setColor(Color.rgb(0x9a4a72));
  g.fillRoundRect(45, 11, 3, 14, 0.5);
  g.setColor(Color.rgb(0xbfe3f5));
  g.setStroke(2, "round", "round");
  g.drawQuad(49, 13, 53, 15, 50, 18);
  g.drawQuad(50, 19, 54, 21, 51, 24);
  g.setColor(Color.rgb(0xffd166));
  g.fillRoundRect(16, 30, 4, 7, 1);
  g.setColor(WHITE);
  ell(g, 19, 16, 3.2, 3.2);
  g.setColor(Color.rgb(0x222222));
  ell(g, 20, 16.5, 1.4, 1.4);
  g.setStroke(1.8, "round", "round");
  g.setColor(Color.rgb(0x5e2c45));
  g.drawLine(15.5, 12.5, 22, 14);
}

function drawVet(g: G): void {
  g.setColor(Color.rgb(0x3a4a5a));
  g.fillRoundRect(18, 52, 7, 14, 1);
  g.fillRoundRect(28, 52, 7, 14, 1);
  g.setColor(Color.rgb(0xf0f2f5));
  g.fillRoundRect(13, 26, 27, 29, 4);
  g.setColor(Color.rgb(0xd5dae2));
  g.setStroke(1.4);
  g.drawLine(26.5, 28, 26.5, 53);
  g.setColor(Color.rgb(0xf0f2f5));
  g.fillRoundRect(36, 30, 12, 6, 1.5);
  g.setColor(Color.rgb(0xcfe3f0));
  g.fillRoundRect(44, 28, 7, 4, 0.5);
  g.setColor(Color.rgb(0x8aa3b8));
  g.setStroke(1.5, "round", "round");
  g.drawLine(51, 30, 56, 30);
  g.setColor(Color.rgb(0xe8b88c));
  ell(g, 26, 15, 10, 10);
  g.setColor(Color.rgb(0x5a4632));
  g.fillArc(16, 4, 20, 14, 0, 180);
  g.setColor(Color.rgb(0x2b2b30));
  g.setStroke(1.6);
  g.drawEllipse(18, 13, 6.5, 6);
  g.drawEllipse(27.5, 13, 6.5, 6);
  g.drawLine(24.5, 15.5, 27.5, 15.5);
  g.drawLine(18, 11.5, 24, 13);
  g.drawLine(34, 11.5, 28, 13);
  g.drawLine(23, 22.5, 29, 22.5);
  g.setColor(Color.rgb(0x4a6a8a));
  g.drawQuad(20, 28, 26, 36, 32, 28);
  ell(g, 26, 37, 2.4, 2.4);
}

// ===== Proiettili =====

function projImg(name: string): HTMLCanvasElement {
  switch (name) {
    case "gomitolo": return cached("pr:gomitolo", 20, 20, (g) => {
      g.setColor(Color.rgb(0xe07a9a));
      ell(g, 10, 10, 8.5, 8.5);
      g.setColor(Color.rgb(0xf3b1c6));
      g.setStroke(1.4);
      g.drawArc(2.5, 4, 15, 13, 30, 120);
      g.drawArc(3.5, 8, 13, 9, 200, 120);
      g.drawArc(6, 2.5, 9, 15, 100, 130);
    });
    case "pallapelo": return cached("pr:pallapelo", 22, 22, (g) => {
      g.setColor(Color.rgb(0x9a8f85));
      ell(g, 11, 11, 8, 8);
      g.setColor(Color.rgb(0x7a7066));
      g.setStroke(1.3, "round", "round");
      for (let i = 0; i < 10; i++) {
        const a = TAU / 10 * i;
        g.drawLine(11 + Math.cos(a) * 7, 11 + Math.sin(a) * 7,
          11 + Math.cos(a + 0.3) * 10.5, 11 + Math.sin(a + 0.3) * 10.5);
      }
      g.setColor(Color.rgb(0xb5aba0));
      ell(g, 8.5, 8.5, 3, 3);
    });
    case "sardina": return cached("pr:sardina", 18, 10, (g) => {
      g.setColor(Color.rgb(0x7ab3d4));
      ell(g, 8, 5, 6.5, 3.4);
      tri(g, 13, 5, 17.5, 1.5, 17.5, 8.5);
      g.setColor(Color.rgb(0xa9d2e8));
      ell(g, 6.5, 4, 3, 1.4);
      g.setColor(BLACK);
      ell(g, 3.5, 4.5, 0.9, 0.9);
    });
    case "artiglio": return cached("pr:artiglio", 20, 20, (g) => {
      g.setStroke(2.6, "round", "round");
      g.setColor(new Color(255, 255, 255, 110));
      g.drawArc(2, 2, 16, 16, 300, 140);
      g.setColor(Color.rgb(0xf5f7fa));
      g.drawArc(4, 1, 13, 17, 300, 130);
      g.drawArc(7, 3, 10, 14, 300, 120);
    });
    case "crocc": return cached("pr:crocc", 12, 12, (g) => {
      g.setColor(Color.rgb(0xd98c3f));
      g.fillRoundRect(1.5, 1.5, 9, 9, 2);
      g.setColor(Color.rgb(0xb56f2c));
      g.setStroke(1);
      g.drawRoundRect(1.5, 1.5, 9, 9, 2);
      g.setColor(Color.rgb(0xeec27e));
      ell(g, 4.5, 4.5, 1.5, 1.5);
    });
    default: return cached("pr:?", 8, 8, (g) => { g.setColor(WHITE); ell(g, 4, 4, 3, 3); });
  }
}

// ===== Gemme e raccoglibili =====

function gemImg(value: number): HTMLCanvasElement {
  const tier = value >= 50 ? "gold" : value >= 20 ? "red" : value >= 5 ? "green" : "blue";
  return cached("gem:" + tier, 16, 20, (g) => {
    const c = tier === "gold" ? Color.rgb(0xffd166)
      : tier === "red" ? Color.rgb(0xf56a93)
      : tier === "green" ? Color.rgb(0x7ddb66)
      : Color.rgb(0x5ec8f0);
    const d = new Path2D();
    d.moveTo(8, 1); d.lineTo(14.5, 8); d.lineTo(8, 19); d.lineTo(1.5, 8); d.closePath();
    g.setColor(c);
    g.fillPath(d);
    g.setColor(new Color(255, 255, 255, 130));
    const top = new Path2D();
    top.moveTo(8, 1); top.lineTo(11, 8); top.lineTo(5, 8); top.closePath();
    g.fillPath(top);
    g.setColor(c.darker());
    g.setStroke(1);
    g.drawPath(d);
  });
}

function pickupImg(kind: string): HTMLCanvasElement {
  if (kind === "croccantino") {
    return cached("pk:croc", 22, 16, (g) => {
      g.setColor(Color.rgb(0xe8a13c));
      ell(g, 9, 8, 7, 5);
      tri(g, 15, 8, 20, 4, 20, 12);
      g.setColor(Color.rgb(0xc8842a));
      g.setStroke(1.2);
      g.drawEllipse(2, 3, 14, 10);
      g.setColor(Color.rgb(0x6b4a1c));
      ell(g, 6, 7, 1.1, 1.1);
    });
  }
  return cached("pk:mag", 22, 22, (g) => {
    g.setStroke(5, "butt", "round");
    g.setColor(Color.rgb(0xd64545));
    g.drawArc(4, 4, 14, 14, 0, 180);
    g.drawLine(6.5, 11, 6.5, 17);
    g.drawLine(15.5, 11, 15.5, 17);
    g.setColor(Color.rgb(0xd5dae2));
    g.setStroke(5, "butt", "miter");
    g.drawLine(6.5, 17, 6.5, 20);
    g.drawLine(15.5, 17, 15.5, 20);
  });
}

// ===== Icone per HUD e level up =====

function drawIcon(g: G, id: string): void {
  switch (id) {
    case "graffio": {
      g.setStroke(2.4, "round", "round");
      g.setColor(Color.rgb(0xf5f7fa));
      for (let i = -1; i <= 1; i++) {
        g.drawQuad(6 + i * 6, 4, 10 + i * 6, 13, 6 + i * 6, 22);
      }
      break;
    }
    case "gomitolo": g.drawImage(projImg("gomitolo"), 3, 3); break;
    case "pallapelo": g.drawImage(projImg("pallapelo"), 2, 2); break;
    case "fusa": {
      g.setStroke(2.2, "round", "round");
      g.setColor(Color.rgb(0xf2a0b5));
      g.drawArc(8, 8, 10, 10, 0, 360);
      g.setColor(new Color(242, 160, 181, 140));
      g.drawArc(4, 4, 18, 18, 20, 140);
      g.setColor(new Color(242, 160, 181, 80));
      g.drawArc(1, 1, 24, 24, 20, 140);
      break;
    }
    case "miagolio": {
      g.setStroke(2.2, "round", "round");
      g.setColor(Color.rgb(0x8fd3f4));
      g.drawArc(2, 5, 10, 16, -50, 100);
      g.drawArc(6, 2, 14, 22, -50, 100);
      g.setColor(new Color(143, 211, 244, 120));
      g.drawArc(10, -1, 18, 28, -50, 100);
      break;
    }
    case "artigli": g.drawImage(projImg("artiglio"), 3, 3); break;
    case "sardine": g.drawImage(projImg("sardina"), 4, 8); break;
    case "croccantini": {
      g.drawImage(projImg("crocc"), 2, 10);
      g.drawImage(projImg("crocc"), 12, 4);
      g.setColor(Color.rgb(0xffd166));
      g.setStroke(1.4, "round", "round");
      g.drawLine(8, 2, 8, 6);
      g.drawLine(20, 16, 20, 20);
      break;
    }
    case "latte": {
      g.setColor(Color.rgb(0xd5dae2));
      g.fillArc(3, 10, 20, 14, 180, 180);
      g.setColor(Color.rgb(0xf7f9fc));
      ell(g, 13, 11, 9, 2.6);
      g.setColor(Color.rgb(0x9aa6b5));
      g.setStroke(1.2);
      g.drawArc(3, 10, 20, 14, 180, 180);
      break;
    }
    case "erbagatta": {
      g.setColor(Color.rgb(0x6cbf4e));
      g.setStroke(2, "round", "round");
      g.drawLine(13, 23, 13, 9);
      for (let i = 0; i < 3; i++) {
        const y = 8 + i * 5;
        rotLeaf(g, 13, y, -0.6, -9, -2);
        rotLeaf(g, 13, y, 0.6 + Math.PI, 0, -2);
      }
      break;
    }
    case "campanellino": {
      g.setColor(Color.rgb(0xffd166));
      g.fillArc(5, 4, 16, 18, 0, 180);
      g.fillRoundRect(4, 12, 18, 4, 1);
      ell(g, 13, 19, 2.5, 2.5);
      g.setColor(Color.rgb(0xc8842a));
      ell(g, 13, 4.5, 2, 2);
      break;
    }
    case "pesciolino": {
      g.setColor(Color.rgb(0x9fb8c8));
      ell(g, 10, 13, 7.5, 4.2);
      tri(g, 16, 13, 22, 8.5, 22, 17.5);
      g.setColor(BLACK);
      ell(g, 5.5, 12, 1, 1);
      g.setColor(Color.rgb(0x7a96a8));
      g.setStroke(1);
      g.drawLine(8, 10, 8, 16);
      g.drawLine(11, 9.7, 11, 16.3);
      break;
    }
    case "cuscino": {
      g.setColor(Color.rgb(0xb06a8c));
      g.fillRoundRect(3, 6, 20, 14, 3.5);
      g.setColor(Color.rgb(0xd490b2));
      g.fillRoundRect(5, 8, 16, 10, 2.5);
      g.setColor(Color.rgb(0x8c5070));
      ell(g, 13, 13, 1.4, 1.4);
      break;
    }
    case "zampe": {
      g.setColor(Color.rgb(0xf2a0b5));
      ell(g, 13, 16, 6, 5);
      ell(g, 6, 9, 2.4, 3);
      ell(g, 11, 6.5, 2.4, 3);
      ell(g, 16, 6.5, 2.4, 3);
      ell(g, 21, 9, 2.4, 3);
      break;
    }
    case "collare": {
      g.setStroke(3);
      g.setColor(Color.rgb(0xd64545));
      g.drawEllipse(4, 4, 18, 14);
      g.setColor(Color.rgb(0xffd166));
      ell(g, 13, 20, 3.5, 3.5);
      g.setColor(Color.rgb(0xc8842a));
      ell(g, 13, 20, 1.2, 1.2);
      break;
    }
    case "scorta": {
      g.drawImage(projImg("gomitolo"), 0, 6);
      g.drawImage(projImg("gomitolo"), 8, 2);
      break;
    }
    case "calamita": g.drawImage(pickupImg("magnete"), 2, 2); break;
    case "fortunato": {
      g.setColor(Color.rgb(0x6cbf4e));
      ell(g, 9, 9, 4.5, 4.5);
      ell(g, 17, 9, 4.5, 4.5);
      ell(g, 9, 17, 4.5, 4.5);
      ell(g, 17, 17, 4.5, 4.5);
      g.setColor(Color.rgb(0x4f9e3c));
      g.setStroke(1.6, "round", "round");
      g.drawQuad(13, 14, 15, 20, 19, 24);
      break;
    }
    case "heal": {
      g.setColor(Color.rgb(0xf56a93));
      ell(g, 9, 9, 5.5, 5.5);
      ell(g, 17, 9, 5.5, 5.5);
      tri(g, 4, 11.5, 22, 11.5, 13, 23);
      break;
    }
    case "xp": {
      g.setColor(Color.rgb(0xffd166));
      const star = new Path2D();
      for (let i = 0; i < 10; i++) {
        const rr = (i % 2 === 0) ? 11 : 4.6;
        const a = -Math.PI / 2 + TAU / 10 * i;
        const px = 13 + Math.cos(a) * rr, py = 13 + Math.sin(a) * rr;
        if (i === 0) star.moveTo(px, py); else star.lineTo(px, py);
      }
      star.closePath();
      g.fillPath(star);
      break;
    }
    default: g.setColor(GRAY); ell(g, 13, 13, 9, 9);
  }
}

/** Foglia ellittica (0,0,9,4.5) ruotata e traslata, come rotated() del Java. */
function rotLeaf(g: G, cx: number, cy: number, angle: number, ox: number, oy: number): void {
  g.save();
  g.translate(cx, cy);
  g.rotate(angle);
  g.translate(ox, oy);
  g.fillEllipse(0, 0, 9, 4.5);
  g.restore();
}

// ===== API pubblica (stessi nomi del Sprites.java) =====

export const Sprites = {
  cat(c: CatDef): HTMLCanvasElement {
    return cached("cat:" + c.id, 48, 48, (g) => drawCat(g, c.sprite));
  },
  /** Versione ingrandita per i menu (scaling nearest-neighbor). */
  catBig(c: CatDef): HTMLCanvasElement {
    return cached("catbig:" + c.id, 96, 96, (g) => {
      g.ctx.imageSmoothingEnabled = false;
      g.ctx.drawImage(Sprites.cat(c), 0, 0, 96, 96);
    });
  },
  enemy(type: string): HTMLCanvasElement {
    const key = "en:" + type;
    let img = CACHE.get(key);
    if (img === undefined) {
      const [w, h] = enemySize(type);
      img = make(w, h, (g) => drawEnemy(g, type, w, h));
      CACHE.set(key, img);
    }
    return img;
  },
  gem: gemImg,
  pickup: pickupImg,
  proj: projImg,
  icon(id: string): HTMLCanvasElement {
    return cached("ic:" + id, 26, 26, (g) => drawIcon(g, id));
  },
};
