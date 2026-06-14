// Porting di Ui.java: menu, HUD, lobby, level up, pausa, fine partita.
// Adattamenti web: i tre pulsanti del menu usano il modello a CODICE STANZA
// (gioca da solo / crea stanza / entra in stanza) e il campo IP diventa un
// campo per il codice. Le FontMetrics di Java diventano g.stringWidth.

import { Color } from "./color.ts";
import { G } from "./g2d.ts";
import { clamp, fmtTime } from "./util.ts";
import { Rect } from "./rect.ts";
import { CATS } from "./cats.ts";
import { WEAPONS } from "./weapons.ts";
import { Sprites } from "./sprites.ts";
import { App, MAX_PLAYERS } from "./app.ts";
import { muted, playSfx } from "./sfx.ts";
import type { Game } from "./game.ts";
import type { Point } from "./input.ts";

export const GOLD = Color.rgb(0xffd166);
export const GREEN_LT = Color.rgb(0xb8d8a8);
export const BLUE_LT = Color.rgb(0x8fd3f4);
const PINK_LT = Color.rgb(0xf2b5d4);
export const RED_LT = Color.rgb(0xff6b6b);
const PANEL = Color.rgb(0x1c291c);
const PANEL_HOVER = Color.rgb(0x243524);
const BORDER = Color.rgb(0x3a553a);
export const OVERLAY = new Color(8, 14, 9, 216);
export const TEXT = Color.rgb(0xcfe3c2);
export const HINT = Color.rgb(0x8ba87e);
export const BTN_DARK = Color.rgb(0x20301c);
export const BTN_HOVER = Color.rgb(0xffe49e);

export const F_TITLE = "bold 46px sans-serif";
export const F_H2 = "bold 30px sans-serif";
export const F_BOLD = "bold 16px sans-serif";
const F_NAME = "bold 17px sans-serif";
export const F_TEXT = "12px sans-serif";
export const F_SMALL = "11px sans-serif";
export const F_TIMER = "bold 28px sans-serif";
export const F_HUD = "bold 15px sans-serif";
const F_SLOT = "bold 10px sans-serif";
const F_MONO = "bold 22px monospace";

const MENU_BUTTONS = ["PLAY SOLO", "HOST CO-OP", "JOIN A FRIEND"];

// ===== Helper di testo =====

export function center(g: G, s: string, f: string, c: Color, cx: number, y: number): void {
  g.setFont(f);
  g.setColor(c);
  g.drawString(s, cx - g.stringWidth(s) / 2, y);
}

export function right(g: G, s: string, f: string, rx: number, y: number): void {
  g.setFont(f);
  g.drawString(s, rx - g.stringWidth(s), y);
}

function wrap(g: G, text: string, f: string, maxW: number): string[] {
  g.setFont(f);
  const lines: string[] = [];
  let cur = "";
  for (const word of text.split(" ")) {
    const test = cur === "" ? word : cur + " " + word;
    if (g.stringWidth(test) > maxW && cur !== "") {
      lines.push(cur);
      cur = word;
    } else {
      cur = test;
    }
  }
  if (cur !== "") lines.push(cur);
  return lines;
}

export function panel(g: G, r: Rect, hover: boolean): void {
  g.setColor(hover ? PANEL_HOVER : PANEL);
  g.fillRoundRect(r.x, r.y, r.width, r.height, 14);
  g.setColor(hover ? GOLD : BORDER);
  g.setStroke(2);
  g.drawRoundRect(r.x, r.y, r.width, r.height, 14);
}

// ===== Adattamento a schermi piccoli =====
// Le schermate a tutto schermo (menu, lobby, level-up, pausa, fine) sono disegnate
// in uno spazio di design fisso e poi scalate/centrate per entrare nello schermo
// reale. Senza questo, su un telefono in orizzontale (molto basso, ~300px) i bottoni
// del menu finiscono disegnati SOTTO il bordo del canvas e spariscono. Disegno e
// hit-test usano la stessa trasformazione (uiFit), così i clic restano allineati.
export const DESIGN_W = 1280;
export const DESIGN_H = 720;

export function uiFit(w: number, h: number): { s: number; ox: number; oy: number } {
  const s = Math.min(w / DESIGN_W, h / DESIGN_H, 1);
  return { s, ox: (w - DESIGN_W * s) / 2, oy: (h - DESIGN_H * s) / 2 };
}

/** Disegna draw() nello spazio di design (DESIGN_W×DESIGN_H) scalato dentro (w,h) reali. */
export function withUiFit(g: G, w: number, h: number, draw: () => void): void {
  const { s, ox, oy } = uiFit(w, h);
  g.save();
  g.translate(ox, oy);
  g.scale(s, s);
  draw();
  g.restore();
}

/** Converte un punto dello schermo reale in coordinate di design (per l'hit-test). */
export function toUi(w: number, h: number, p: Point): Point {
  const { s, ox, oy } = uiFit(w, h);
  return { x: (p.x - ox) / s, y: (p.y - oy) / s };
}

// ===== Layout =====
// I layout lavorano sempre nello spazio di design (DESIGN_W×DESIGN_H): vengono poi
// scalati da withUiFit. Niente più dipendenza da g.viewW/g.viewH per questi.

function menuCards(): Rect[] {
  const n = CATS.length;
  const cols = 4, cw = 215, ch = 240, gap = 14;
  const rows = Math.ceil(n / cols);
  const gw = cols * cw + (cols - 1) * gap;
  const x0 = (DESIGN_W - gw) / 2;
  const y0 = Math.max(128, (DESIGN_H - (rows * ch + (rows - 1) * gap) - 60) / 2);
  const out: Rect[] = [];
  for (let i = 0; i < n; i++) {
    out.push(new Rect(x0 + (i % cols) * (cw + gap), y0 + Math.floor(i / cols) * (ch + gap), cw, ch));
  }
  return out;
}

function menuButtons(): Rect[] {
  const cards = menuCards();
  const last = cards[cards.length - 1];
  const y = last.y + last.height + 14;
  const bw = 230, bh = 44, gap = 14;
  const total = MENU_BUTTONS.length * bw + (MENU_BUTTONS.length - 1) * gap;
  const x0 = (DESIGN_W - total) / 2;
  const out: Rect[] = [];
  for (let i = 0; i < MENU_BUTTONS.length; i++) out.push(new Rect(x0 + i * (bw + gap), y, bw, bh));
  return out;
}

/** Carte del level up: layout condiviso tra host e client. */
export function choiceCards(n: number, viewW: number, viewH: number): Rect[] {
  const cw = 240, ch = 220, gap = 16;
  const gw = n * cw + Math.max(0, n - 1) * gap;
  const x0 = (viewW - gw) / 2, y0 = viewH / 2 - ch / 2;
  const out: Rect[] = [];
  for (let i = 0; i < n; i++) out.push(new Rect(x0 + i * (cw + gap), y0, cw, ch));
  return out;
}

export function endButton(viewW: number, viewH: number): Rect {
  return new Rect(viewW / 2 - 140, viewH / 2 + 150, 280, 48);
}

// Bottoni touch per le azioni che su mobile non hanno tastiera (start lobby,
// ripresa pausa, conferma/annulla codice stanza). Le hitbox stanno nello spazio
// di design, come le carte/menu, e sono testate in Ui.handleInput.
export function lobbyButtons(viewW: number, viewH: number): { start: Rect; cancel: Rect } {
  const bw = 200, bh = 48, gap = 24;
  const y = viewH / 2 + 132;
  const x0 = viewW / 2 - bw - gap / 2;
  return { start: new Rect(x0, y, bw, bh), cancel: new Rect(x0 + bw + gap, y, bw, bh) };
}

export function resumeButton(viewW: number, viewH: number): Rect {
  return new Rect(viewW / 2 - 110, viewH / 2 + 84, 220, 48);
}

export function joinButtons(viewW: number, viewH: number): { confirm: Rect; cancel: Rect } {
  const bw = 180, bh = 40, gap = 20;
  const y = viewH / 2 + 36;
  const x0 = viewW / 2 - bw - gap / 2;
  return { confirm: new Rect(x0, y, bw, bh), cancel: new Rect(x0 + bw + gap, y, bw, bh) };
}

/** Disegna un bottone rettangolare con stato hover, nello spazio di design. */
function drawButton(g: G, mp: Point, r: Rect, label: string): void {
  const hover = r.contains(mp.x, mp.y);
  g.setColor(hover ? BTN_HOVER : GOLD);
  g.fillRoundRect(r.x, r.y, r.width, r.height, 10);
  center(g, label, F_BOLD, BTN_DARK, r.x + r.width / 2, r.y + r.height / 2 + 5);
}

/** Punto del cursore/tocco nello spazio di design (per l'hover dei bottoni). */
function uiMouse(game: Game): Point {
  return toUi(game.viewW, game.viewH, { x: game.input.mouseX, y: game.input.mouseY });
}

// ===== Disegno =====

function drawMenu(game: Game, g: G, w: number, h: number): void {
  g.setColor(OVERLAY);
  g.fillRect(0, 0, w, h);
  center(g, "CAT SURVIVORS", F_TITLE, GOLD, w / 2, 58);
  center(g, "The garden is overrun. Pick your cat and survive — solo or with friends (co-op up to 4).",
    F_BOLD, GREEN_LT, w / 2, 90);

  // il mouse è in coordinate reali: lo portiamo nello spazio di design come i rettangoli
  const mp = toUi(game.viewW, game.viewH, { x: game.input.mouseX, y: game.input.mouseY });
  const cards = menuCards();
  for (let i = 0; i < cards.length; i++) {
    const r = cards[i];
    const cat = CATS[i];
    const hover = r.contains(mp.x, mp.y);
    const selected = App.selectedCat === i;
    g.setColor(hover || selected ? PANEL_HOVER : PANEL);
    g.fillRoundRect(r.x, r.y, r.width, r.height, 14);
    g.setColor(selected ? GOLD : (hover ? GREEN_LT : BORDER));
    g.setStroke(selected ? 3 : 2);
    g.drawRoundRect(r.x, r.y, r.width, r.height, 14);

    g.ctx.drawImage(Sprites.catBig(cat), r.x + (r.width - 64) / 2, r.y + 6, 64, 64);
    center(g, cat.name, F_NAME, GOLD, r.x + r.width / 2, r.y + 88);
    center(g, cat.breed, F_SMALL, GREEN_LT, r.x + r.width / 2, r.y + 102);
    let ty = r.y + 118;
    for (const line of wrap(g, cat.personality, F_SMALL, r.width - 22)) {
      center(g, line, F_SMALL, TEXT, r.x + r.width / 2, ty);
      ty += 13;
    }
    ty = r.y + 180;
    for (const line of wrap(g, cat.bonus, F_SMALL, r.width - 22)) {
      center(g, line, F_SMALL, BLUE_LT, r.x + r.width / 2, ty);
      ty += 13;
    }
    const wd = WEAPONS.get(cat.startWeapon)!;
    g.ctx.drawImage(Sprites.icon(wd.id), r.x + r.width / 2 - 66, r.y + 212, 18, 18);
    g.setFont(F_SMALL);
    g.setColor(PINK_LT);
    g.drawString(wd.name, r.x + r.width / 2 - 44, r.y + 225);
  }

  const btns = menuButtons();
  for (let i = 0; i < btns.length; i++) {
    const b = btns[i];
    const hover = b.contains(mp.x, mp.y);
    g.setColor(hover ? BTN_HOVER : GOLD);
    g.fillRoundRect(b.x, b.y, b.width, b.height, 10);
    center(g, MENU_BUTTONS[i], F_BOLD, BTN_DARK, b.x + b.width / 2, b.y + 28);
  }

  const st = App.status;
  if (st !== "") {
    center(g, st, F_TEXT, st.startsWith("Connection failed") || st.startsWith("Unable") ? RED_LT : GOLD,
      w / 2, btns[0].y + 66);
  }
  center(g, "WASD / arrows to move  •  aim with mouse  •  P pause  •  M audio on/off",
    F_SMALL, HINT, w / 2, h - 14);

  if (App.roomActive()) drawJoinInput(game, g, w, h);
}

function drawJoinInput(game: Game, g: G, w: number, h: number): void {
  g.setColor(new Color(0, 0, 0, 160));
  g.fillRect(0, 0, w, h);
  const box = new Rect(w / 2 - 250, h / 2 - 90, 500, 180);
  panel(g, box, true);
  center(g, "Join a room", F_BOLD, GOLD, w / 2, box.y + 36);
  center(g, "Enter the room code your friend shared with you", F_TEXT, TEXT, w / 2, box.y + 62);
  const field = new Rect(box.x + 40, box.y + 80, box.width - 80, 38);
  g.setColor(Color.rgb(0x0d150d));
  g.fillRoundRect(field.x, field.y, field.width, field.height, 8);
  g.setColor(BORDER);
  g.drawRoundRect(field.x, field.y, field.width, field.height, 8);
  const txt = App.joinField ?? "";
  const cursor = Math.floor(performance.now() / 400) % 2 === 0;
  g.setFont(F_MONO);
  g.setColor(Color.rgb(0xffffff));
  g.drawString(txt + (cursor ? "_" : ""), field.x + 12, field.y + 27);
  const jb = joinButtons(w, h);
  const mp = uiMouse(game);
  drawButton(g, mp, jb.confirm, "CONFIRM");
  drawButton(g, mp, jb.cancel, "CANCEL");
}

function drawLobby(game: Game, g: G, w: number, h: number): void {
  g.setColor(new Color(8, 14, 9, 170));
  g.fillRect(0, 0, w, h);
  const box = new Rect(w / 2 - 290, h / 2 - 200, 580, 400);
  panel(g, box, false);
  center(g, "CO-OP LOBBY", F_H2, GOLD, w / 2, box.y + 42);

  // codice stanza da condividere con gli amici
  center(g, "Room code:", F_SMALL, GREEN_LT, w / 2, box.y + 96);
  center(g, App.roomCode !== "" ? App.roomCode : "...", F_MONO, BLUE_LT, w / 2, box.y + 130);
  center(g, "Share this code: your friends enter it under \"JOIN A FRIEND\".",
    F_SMALL, HINT, w / 2, box.y + 156);
  const up = App.status;
  if (up !== "") center(g, up, F_SMALL, GOLD, w / 2, box.y + 180);

  let y = box.y + 226;
  center(g, "Cats ready (" + game.players.length + "/" + MAX_PLAYERS + "):", F_BOLD, GREEN_LT, w / 2, y);
  y += 18;
  const n = game.players.length;
  const x0 = w / 2 - n * 45 + 45 / 2;
  for (let i = 0; i < n; i++) {
    const p = game.players[i];
    g.ctx.drawImage(Sprites.catBig(p.cat), x0 + i * 90 - 24, y, 48, 48);
    center(g, p.cat.name + (p.pid === 0 ? " (you)" : ""), F_SMALL, TEXT, x0 + i * 90, y + 62);
  }
  const lb = lobbyButtons(w, h);
  const mp = uiMouse(game);
  drawButton(g, mp, lb.start, "START");
  drawButton(g, mp, lb.cancel, "CANCEL");
}

function drawHud(game: Game, g: G, w: number, h: number): void {
  const p = game.localPlayer();
  if (p === null) return;
  // barra esperienza
  g.setColor(Color.rgb(0x0d150d));
  g.fillRect(0, 0, w, 16);
  const xr = clamp(p.xp / p.xpNext, 0, 1);
  g.setColor(Color.rgb(0x59c2e8));
  g.fillRect(0, 0, w * xr, 16);
  g.setColor(Color.rgb(0x7de87d));
  g.fillRect(0, 12, w * xr, 4);
  g.setColor(Color.rgb(0x000000));
  g.fillRect(0, 16, w, 2);
  // riga superiore
  g.setFont(F_HUD);
  g.setColor(BLUE_LT);
  g.drawString("Lv. " + p.level, 12, 36);
  center(g, fmtTime(game.time), F_TIMER, GOLD, w / 2, 46);
  g.setColor(TEXT);
  right(g, "KO " + game.kills, F_HUD, w - 12, 36);
  g.setColor(Color.rgb(0xff8c8c));
  right(g, "HP " + Math.ceil(p.hp) + "/" + Math.trunc(p.stats.maxHp), F_HUD, w - 12, 56);
  if (muted) right(g, "AUDIO OFF [M]", F_SMALL, w - 12, 74);
  // slot armi e passivi
  let sx = 12, sy = 52;
  for (const wi of p.weapons) {
    slot(g, sx, sy, Sprites.icon(wi.def.id), wi.level);
    sx += 41;
  }
  sx = 12;
  sy += 41;
  for (const [id, lvl] of p.passives) {
    slot(g, sx, sy, Sprites.icon(id), lvl);
    sx += 41;
  }
  // barra del boss
  let boss = null;
  for (const e of game.enemies) {
    if (e.boss && !e.dead) { boss = e; break; }
  }
  if (boss !== null) {
    drawBossBar(g, w, h, boss.def.name, clamp(boss.hp / boss.maxHp, 0, 1));
  }
  // minimappa
  const cats: number[][] = [];
  for (const q of game.players) {
    cats.push([q.x, q.y, q.cat.sprite.body.getRGB() & 0xffffff, q === p ? 1 : 0, q.alive ? 1 : 0]);
  }
  const foes: number[][] = [];
  for (const e of game.enemies) {
    if (!e.dead) foes.push([e.x, e.y, e.boss ? 2 : (e.elite ? 1 : 0)]);
  }
  const picks: number[][] = [];
  for (const pk of game.pickups) picks.push([pk.x, pk.y]);
  drawMinimap(g, w, h, p.x, p.y, cats, foes, picks);
}

// ===== Minimappa =====

/**
 * Radar in basso a destra. Punti: gatti {x, y, rgb, isMe, alive},
 * nemici {x, y, kind} (0=normale, 1=elite, 2=boss), pickup {x, y}.
 * Gli alleati fuori portata restano agganciati al bordo con una freccia.
 */
export function drawMinimap(g: G, w: number, h: number, cx: number, cy: number,
  cats: number[][], foes: number[][], picks: number[][]): void {
  const size = 150, margin = 14;
  const mx = w - size - margin, my = h - size - margin;
  const half = size / 2;
  const scale = size / 1800; // la mappa copre ~1800 px di mondo

  g.setColor(new Color(10, 18, 10, 175));
  g.fillRoundRect(mx, my, size, size, 12);
  g.ctx.save();
  const clip = new Path2D();
  clip.roundRect(mx, my, size, size, 12);
  g.ctx.clip(clip);

  // croce di riferimento leggera
  g.setColor(new Color(255, 255, 255, 16));
  g.drawLine(mx, my + half, mx + size, my + half);
  g.drawLine(mx + half, my, mx + half, my + size);

  // nemici (i boss restano visibili al bordo anche se lontani)
  for (const f of foes) {
    const dx = (f[0] - cx) * scale, dy = (f[1] - cy) * scale;
    const kind = f[2];
    let px: number, py: number;
    if (kind === 2) {
      px = mx + half + clamp(dx, -(half - 8), half - 8);
      py = my + half + clamp(dy, -(half - 8), half - 8);
    } else {
      if (Math.abs(dx) > half + 4 || Math.abs(dy) > half + 4) continue;
      px = mx + half + dx;
      py = my + half + dy;
    }
    if (kind === 2) {
      g.setColor(Color.rgb(0xff4040));
      g.fillEllipse(px - 3.5, py - 3.5, 7, 7);
      g.setColor(new Color(255, 64, 64, 110));
      g.setStroke(1.5);
      g.drawEllipse(px - 6, py - 6, 12, 12);
    } else if (kind === 1) {
      g.setColor(Color.rgb(0xffd166));
      g.fillEllipse(px - 2.5, py - 2.5, 5, 5);
    } else {
      g.setColor(new Color(214, 69, 69, 200));
      g.fillEllipse(px - 1.5, py - 1.5, 3, 3);
    }
  }
  // pickup (croccantini e calamite)
  g.setColor(Color.rgb(0x7de87d));
  for (const pk of picks) {
    const px = (pk[0] - cx) * scale + mx + half;
    const py = (pk[1] - cy) * scale + my + half;
    if (px < mx || px > mx + size || py < my || py > my + size) continue;
    g.fillEllipse(px - 2, py - 2, 4, 4);
  }

  // gatti: sempre visibili, con freccia quando l'alleato è fuori portata
  for (const c of cats) {
    const dx = (c[0] - cx) * scale, dy = (c[1] - cy) * scale;
    const isMe = c[3] > 0, alive = c[4] > 0;
    const outside = Math.abs(dx) > half - 9 || Math.abs(dy) > half - 9;
    const px = mx + half + clamp(dx, -(half - 9), half - 9);
    const py = my + half + clamp(dy, -(half - 9), half - 9);
    const col = alive ? Color.rgb(c[2] & 0xffffff) : new Color(120, 120, 120);
    if (outside && !isMe) {
      const ang = Math.atan2(dy, dx);
      g.setColor(col);
      const tri = new Path2D();
      tri.moveTo(px + Math.cos(ang) * 8, py + Math.sin(ang) * 8);
      tri.lineTo(px + Math.cos(ang + 2.4) * 5.5, py + Math.sin(ang + 2.4) * 5.5);
      tri.lineTo(px + Math.cos(ang - 2.4) * 5.5, py + Math.sin(ang - 2.4) * 5.5);
      tri.closePath();
      g.fillPath(tri);
    }
    g.setColor(Color.rgb(0xffffff));
    const r = isMe ? 4.5 : 4;
    g.fillEllipse(px - r, py - r, r * 2, r * 2);
    g.setColor(col);
    const r2 = isMe ? 3 : 2.5;
    g.fillEllipse(px - r2, py - r2, r2 * 2, r2 * 2);
  }

  g.ctx.restore();
  g.setColor(Color.rgb(0x44613f));
  g.setStroke(2);
  g.drawRoundRect(mx, my, size, size, 12);
}

export function drawBossBar(g: G, w: number, h: number, name: string, ratio: number): void {
  const bw = Math.min(540, w * 0.7);
  const bx = (w - bw) / 2, by = h - 38;
  center(g, name, F_SMALL, Color.rgb(0xf2b1b1), w / 2, by - 6);
  g.setColor(Color.rgb(0x1a0e0e));
  g.fillRect(bx - 2, by - 2, bw + 4, 18 + 4);
  g.setColor(Color.rgb(0xd64545));
  g.fillRect(bx, by, bw * ratio, 18);
}

export function slot(g: G, x: number, y: number, icon: HTMLCanvasElement, level: number): void {
  g.setColor(new Color(10, 18, 10, 200));
  g.fillRoundRect(x, y, 36, 36, 8);
  g.setColor(Color.rgb(0x44613f));
  g.drawRoundRect(x, y, 36, 36, 8);
  g.drawImage(icon, x + 5, y + 5);
  g.setFont(F_SLOT);
  g.setColor(GOLD);
  g.drawString(String(level), x + 28, y + 33);
}

function drawLevelUp(game: Game, g: G, w: number, h: number): void {
  g.setColor(new Color(8, 14, 9, 190));
  g.fillRect(0, 0, w, h);
  const me = game.localPlayer();
  if (game.leveling !== me) {
    const who = game.leveling !== null ? game.leveling.cat.name : "a friend";
    center(g, who + " is choosing an upgrade...", F_H2, GOLD, w / 2, h / 2 - 10);
    center(g, "(the world holds its breath)", F_TEXT, HINT, w / 2, h / 2 + 22);
    return;
  }
  center(g, "MEOW! Level " + me!.level + "!", F_H2, GOLD, w / 2, h / 2 - 160);
  center(g, "Choose an upgrade (click or keys 1-" + game.choices!.length + ")", F_TEXT, GREEN_LT, w / 2, h / 2 - 134);
  const mp = toUi(game.viewW, game.viewH, { x: game.input.mouseX, y: game.input.mouseY });
  drawChoiceCards(g, game.choices!, mp.x, mp.y, w, h);
}

/** Carte di scelta: condivise tra host e client. */
export function drawChoiceCards(g: G, choices: { name: string; lvlText: string; desc: string; icon: string }[],
  mx: number, my: number, w: number, h: number): void {
  const cards = choiceCards(choices.length, w, h);
  for (let i = 0; i < cards.length && i < choices.length; i++) {
    const r = cards[i];
    const c = choices[i];
    const hover = r.contains(mx, my);
    panel(g, r, hover);
    g.ctx.drawImage(Sprites.icon(c.icon), r.x + r.width / 2 - 20, r.y + 14, 40, 40);
    center(g, c.name, F_BOLD, GOLD, r.x + r.width / 2, r.y + 76);
    center(g, c.lvlText, F_SMALL, BLUE_LT, r.x + r.width / 2, r.y + 94);
    let ty = r.y + 116;
    for (const line of wrap(g, c.desc, F_TEXT, r.width - 26)) {
      center(g, line, F_TEXT, TEXT, r.x + r.width / 2, ty);
      ty += 16;
    }
    g.setFont(F_SMALL);
    g.setColor(HINT);
    g.drawString(String(i + 1), r.x + 10, r.y + r.height - 10);
  }
}

function drawPause(game: Game, g: G, w: number, h: number): void {
  g.setColor(new Color(8, 14, 9, 190));
  g.fillRect(0, 0, w, h);
  center(g, "PAUSE", F_H2, GOLD, w / 2, h / 2 - 120);
  const p = game.localPlayer();
  if (p === null) return;
  center(g, p.cat.name + " — " + p.cat.breed, F_BOLD, GREEN_LT, w / 2, h / 2 - 84);
  const total = p.weapons.length + p.passives.size;
  let bx = w / 2 - total * 23 + 3;
  const by = h / 2 - 50;
  for (const wi of p.weapons) {
    slot(g, bx, by, Sprites.icon(wi.def.id), wi.level);
    bx += 46;
  }
  for (const [id, lvl] of p.passives) {
    slot(g, bx, by, Sprites.icon(id), lvl);
    bx += 46;
  }
  center(g, "Time: " + fmtTime(game.time) + "   •   Level " + p.level + "   •   KO " + game.kills,
    F_TEXT, TEXT, w / 2, h / 2 + 30);
  const extra = game.isCoop() ? "   (pausing also pauses your friends!)" : "";
  center(g, "P or Esc to resume  •  M audio on/off" + extra, F_TEXT, HINT, w / 2, h / 2 + 60);
  drawButton(g, uiMouse(game), resumeButton(w, h), "RESUME");
}

function drawEnd(game: Game, g: G, w: number, h: number, win: boolean): void {
  g.setColor(OVERLAY);
  g.fillRect(0, 0, w, h);
  if (win) {
    center(g, "VICTORY!", F_TITLE, GOLD, w / 2, h / 2 - 150);
    center(g, "Ten minutes, zero baths. The garden is yours again.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
  } else {
    center(g, "GAME OVER", F_TITLE, RED_LT, w / 2, h / 2 - 150);
    center(g, "The garden won this time... but you'll be back.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
  }
  const p = game.localPlayer();
  const box = new Rect(w / 2 - 180, h / 2 - 80, 360, 180);
  panel(g, box, false);
  if (p !== null) {
    g.ctx.drawImage(Sprites.catBig(p.cat), box.x + 16, box.y + 20, 72, 72);
    g.setFont(F_BOLD);
    g.setColor(GOLD);
    g.drawString(p.cat.name, box.x + 104, box.y + 38);
    g.setFont(F_TEXT);
    g.setColor(TEXT);
    g.drawString("Breed: " + p.cat.breed, box.x + 104, box.y + 58);
    g.drawString("Survived: " + fmtTime(game.time), box.x + 104, box.y + 78);
    g.drawString("Level reached: " + p.level, box.x + 104, box.y + 98);
    g.drawString("Enemies defeated (team): " + game.kills, box.x + 104, box.y + 118);
    g.drawString("Weapons: " + p.weapons.length + "  •  Passives: " + p.passives.size, box.x + 104, box.y + 138);
  }
  const btn = endButton(w, h);
  const mp = toUi(game.viewW, game.viewH, { x: game.input.mouseX, y: game.input.mouseY });
  const hover = btn.contains(mp.x, mp.y);
  g.setColor(hover ? BTN_HOVER : GOLD);
  g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10);
  center(g, "Back to shelter (R)", F_BOLD, BTN_DARK, btn.x + btn.width / 2, btn.y + 31);
}

// ===== API pubblica =====

export const Ui = {
  handleInput(game: Game): void {
    const inp = game.input;
    if (App.roomActive()) App.joinType(); // caratteri per il campo codice
    // il click viene valutato nel punto esatto della pressione, non dove il mouse è ora;
    // lo convertiamo nello spazio di design degli overlay (vedi toUi/withUiFit)
    const raw = inp.consumeClick();
    const c: Point | null = raw === null ? null : toUi(game.viewW, game.viewH, raw);
    switch (game.state) {
      case "MENU": {
        if (c === null || App.connecting) return;
        if (App.roomActive()) {
          // pannello "Join a friend": bottoni CONFIRM / CANCEL al posto di INVIO/ESC
          const jb = joinButtons(DESIGN_W, DESIGN_H);
          if (jb.confirm.contains(c.x, c.y)) App.joinKey("Enter");
          else if (jb.cancel.contains(c.x, c.y)) App.joinKey("Escape");
          return;
        }
        const btns = menuButtons();
        for (let i = 0; i < btns.length; i++) {
          if (btns[i].contains(c.x, c.y)) {
            if (i === 0) App.solo();
            else if (i === 1) App.host();
            else App.openJoinInput();
            return;
          }
        }
        const cards = menuCards();
        for (let i = 0; i < cards.length; i++) {
          if (cards[i].contains(c.x, c.y)) {
            App.selectedCat = i;
            playSfx("meow");
            return;
          }
        }
        break;
      }
      case "LEVELUP": {
        if (c !== null && game.choices !== null && game.leveling === game.localPlayer()) {
          const cards = choiceCards(game.choices.length, DESIGN_W, DESIGN_H);
          for (let i = 0; i < cards.length && i < game.choices.length; i++) {
            if (cards[i].contains(c.x, c.y)) {
              game.pickChoice(game.choices[i]);
              return;
            }
          }
        }
        break;
      }
      case "LOBBY": {
        if (c === null) return;
        const lb = lobbyButtons(DESIGN_W, DESIGN_H);
        if (lb.start.contains(c.x, c.y)) game.startRunMulti();
        else if (lb.cancel.contains(c.x, c.y)) game.toMenu();
        break;
      }
      case "PAUSED": {
        if (c !== null && resumeButton(DESIGN_W, DESIGN_H).contains(c.x, c.y)) game.state = "PLAYING";
        break;
      }
      case "OVER":
      case "WIN": {
        if (c !== null && endButton(DESIGN_W, DESIGN_H).contains(c.x, c.y)) game.toMenu();
        break;
      }
    }
  },

  draw(game: Game, g: G, w: number, h: number): void {
    // gli overlay sono disegnati nello spazio di design scalato; l'HUD di gioco resta a
    // dimensioni reali (riempie lo schermo, ancorato ai bordi)
    switch (game.state) {
      case "MENU": withUiFit(g, w, h, () => drawMenu(game, g, DESIGN_W, DESIGN_H)); break;
      case "LOBBY": withUiFit(g, w, h, () => drawLobby(game, g, DESIGN_W, DESIGN_H)); break;
      case "PLAYING": drawHud(game, g, w, h); break;
      case "LEVELUP": drawHud(game, g, w, h); withUiFit(g, w, h, () => drawLevelUp(game, g, DESIGN_W, DESIGN_H)); break;
      case "PAUSED": drawHud(game, g, w, h); withUiFit(g, w, h, () => drawPause(game, g, DESIGN_W, DESIGN_H)); break;
      case "OVER": withUiFit(g, w, h, () => drawEnd(game, g, DESIGN_W, DESIGN_H, false)); break;
      case "WIN": withUiFit(g, w, h, () => drawEnd(game, g, DESIGN_W, DESIGN_H, true)); break;
    }
  },
};

const JOYSTICK_RADIUS = 70;
const KNOB_RADIUS = 26;

/** Disegna il joystick virtuale touch (solo se il dito sinistro è attivo). */
export function drawTouchControls(
  g: G,
  _w: number,
  _h: number,
  joystick: { ox: number; oy: number; dx: number; dy: number; active: boolean },
): void {
  if (!joystick.active) return;
  const { ox, oy, dx, dy } = joystick;
  const knobX = ox + dx * JOYSTICK_RADIUS;
  const knobY = oy + dy * JOYSTICK_RADIUS;

  // cerchio base
  g.ctx.save();
  g.ctx.globalAlpha = 0.35;
  g.ctx.beginPath();
  g.ctx.arc(ox, oy, JOYSTICK_RADIUS, 0, Math.PI * 2);
  g.ctx.fillStyle = "#ffffff";
  g.ctx.fill();
  g.ctx.strokeStyle = "#ffffff";
  g.ctx.lineWidth = 2;
  g.ctx.stroke();

  // manopola
  g.ctx.globalAlpha = 0.65;
  g.ctx.beginPath();
  g.ctx.arc(knobX, knobY, KNOB_RADIUS, 0, Math.PI * 2);
  g.ctx.fillStyle = "#ffffff";
  g.ctx.fill();
  g.ctx.restore();
}

/**
 * Overlay mostrato in verticale sui dispositivi touch: il gioco è landscape, quindi
 * invita a ruotare il telefono. Copre lo schermo per nascondere il layout compresso.
 */
export function drawRotateHint(g: G, w: number, h: number): void {
  const ctx = g.ctx;
  ctx.save();
  ctx.fillStyle = "#0a0d09";
  ctx.fillRect(0, 0, w, h);
  // telefono stilizzato in orizzontale (la posizione "giusta")
  const cx = w / 2, cy = h / 2 - 20;
  ctx.translate(cx, cy);
  ctx.strokeStyle = "#ffd166";
  ctx.lineWidth = 4;
  ctx.strokeRect(-65, -38, 130, 76);
  ctx.fillStyle = "rgba(255,209,102,0.16)";
  ctx.fillRect(-55, -28, 100, 56);
  ctx.restore();
  center(g, "ROTATE YOUR PHONE", F_H2, GOLD, w / 2, cy + 95);
  center(g, "Play in landscape to see everything", F_TEXT, TEXT, w / 2, cy + 125);
}
