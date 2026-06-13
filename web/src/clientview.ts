// Lato client del co-op (porting di ClientView.java): invia l'input all'host e
// disegna il mondo ricevuto negli snapshot, interpolando tra gli ultimi due.

import { Color, cssF } from "./color.ts";
import { G } from "./g2d.ts";
import { clamp, lerp, fmtTime as fmt } from "./util.ts";
import { Rect } from "./rect.ts";
import { Key } from "./input.ts";
import type { Input } from "./input.ts";
import { CATS } from "./cats.ts";
import { ENEMY_TYPES } from "./enemies.ts";
import { Sprites } from "./sprites.ts";
import { drawBackground, blit, shadow, drawWave } from "./game.ts";
import * as Net from "./net.ts";
import { App } from "./app.ts";
import { playSfx, toggleMute, muted } from "./sfx.ts";
import type { Client } from "./client.ts";
import type { Snapshot, PlayerS } from "./snapshot.ts";
import * as Ui from "./ui.ts";

// ordinali di Game.State usati nel protocollo
const ST_LOBBY = 1, ST_PLAYING = 2, ST_LEVELUP = 3, ST_PAUSED = 4, ST_OVER = 5, ST_WIN = 6;

let lastHpRatio = 1;
let lastState = -1;
let lastBoss = false;

function findMe(s: Snapshot, pid: number): PlayerS | null {
  for (const p of s.players) if (p.pid === pid) return p;
  return null;
}

function levelerName(s: Snapshot): string {
  const p = findMe(s, s.levelingPid);
  return p !== null ? CATS[p.catIdx].name : "Un amico";
}

function handleKeys(c: Client, input: Input): void {
  let k: string | null;
  while ((k = input.nextPress()) !== null) {
    if (k === Key.M) {
      toggleMute();
    } else if (k === Key.R) {
      const s = c.latest;
      if (c.error !== null || (s !== null && (s.state === ST_OVER || s.state === ST_WIN))) {
        App.backToMenu();
        return;
      }
    } else {
      const cs = c.choices;
      if (cs !== null) {
        const idx = k === Key.DIGIT1 ? 0 : k === Key.DIGIT2 ? 1 : k === Key.DIGIT3 ? 2 : k === Key.DIGIT4 ? 3 : -1;
        if (idx >= 0 && idx < cs.length) c.sendChoice(idx);
      }
    }
  }
}

function playSounds(s: Snapshot, me: PlayerS | null): void {
  if (me !== null) {
    if (me.hpRatio < lastHpRatio - 0.01) playSfx("hurt");
    lastHpRatio = me.hpRatio;
  }
  if (s.hasBoss && !lastBoss) playSfx("boss");
  lastBoss = s.hasBoss;
  if (s.state !== lastState) {
    if (s.state === ST_WIN) playSfx("win");
    else if (s.state === ST_OVER) playSfx("gameover");
    lastState = s.state;
  }
}

function drawWorld(g: G, s: Snapshot, myPid: number, t: number,
  prevEnemies: Map<number, [number, number]>, prevPlayers: Map<number, [number, number]>): void {
  const now = s.time;
  // aure
  for (const p of s.players) {
    if (p.alive && p.auraR > 0) {
      g.setColor(cssF(1, 0.75, 0.85, 0.12));
      g.fillEllipse(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2);
      g.setColor(cssF(1, 0.75, 0.85, 0.30));
      g.setStroke(1.6);
      g.drawEllipse(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2);
    }
  }
  // gemme e raccoglibili
  for (const gm of s.gems) blit(g, Sprites.gem(gm.value), gm.x, gm.y, false, 1, 0);
  for (const pk of s.picks) {
    shadow(g, pk.x, pk.y + 10, 9);
    blit(g, Sprites.pickup(pk.kind === 0 ? "croccantino" : "magnete"), pk.x, pk.y, false, 1, 0);
  }
  // esplosioni e bersagli (sotto i nemici)
  for (const pr of s.projs) {
    if (pr.lifeRatio < 0) continue;
    if (pr.typeIdx === 7) { // boom
      const a = pr.lifeRatio;
      g.setColor(cssF(1, 0.69, 0.29, a * 0.32));
      g.fillEllipse(pr.x - pr.r, pr.y - pr.r, pr.r * 2, pr.r * 2);
      g.setColor(cssF(1, 0.82, 0.4, a * 0.8));
      g.setStroke(3);
      const rr = pr.r * (1.1 - 0.3 * a);
      g.drawEllipse(pr.x - rr, pr.y - rr, rr * 2, rr * 2);
    } else if (pr.typeIdx === 6) { // croccantino in caduta: ombra sul bersaglio
      g.setColor(new Color(20, 30, 15, 60));
      g.fillEllipse(pr.x - 12, pr.extra - 6, 24, 12);
    }
  }
  // nemici
  for (const e of s.enemies) {
    const pe = prevEnemies.get(e.id);
    const ex = pe !== undefined ? lerp(pe[0], e.x, t) : e.x;
    const ey = pe !== undefined ? lerp(pe[1], e.y, t) : e.y;
    const type = Net.ENEMY_IDS[e.typeIdx];
    const r = ENEMY_TYPES.get(type)!.r * (e.elite ? 1.5 : 1);
    shadow(g, ex, ey + r * 0.85, r * 0.9);
    if (e.elite) {
      g.setColor(new Color(255, 209, 102, 120));
      g.setStroke(2.5);
      g.drawEllipse(ex - r - 4, ey - r - 4, (r + 4) * 2, (r + 4) * 2);
    }
    blit(g, Sprites.enemy(type), ex, ey + Math.sin(now * 6 + e.id) * 1.5, false, e.elite ? 1.5 : 1, 0);
    if (e.flash) {
      g.setColor(cssF(1, 1, 1, 0.5));
      g.fillEllipse(ex - r, ey - r, r * 2, r * 2);
    }
    if (e.boss) {
      const bw = 60;
      g.setColor(new Color(0, 0, 0, 150));
      g.fillRect(ex - bw / 2, ey - r - 16, bw, 6);
      g.setColor(Color.rgb(0xd64545));
      g.fillRect(ex - bw / 2, ey - r - 16, bw * e.hpRatio, 6);
    }
  }
  // gatti
  for (const p of s.players) {
    const pp = prevPlayers.get(p.pid);
    const px = pp !== undefined ? lerp(pp[0], p.x, t) : p.x;
    const py = pp !== undefined ? lerp(pp[1], p.y, t) : p.y;
    const cat = CATS[p.catIdx];
    g.save();
    if (!p.alive) g.setAlpha(0.35);
    else shadow(g, px, py + 14, 13);
    const blink = p.alive && p.hurt && Math.floor(performance.now() / 60) % 2 === 0;
    if (!blink) {
      const bob = p.moving ? Math.sin(now * 9) * 2 : 0;
      blit(g, Sprites.cat(cat), px, py - 6 + bob, p.flipped, 1.15, 0);
    }
    g.restore();
    if (p.alive) {
      g.setColor(new Color(0, 0, 0, 150));
      g.fillRect(px - 16, py + 18, 32, 5);
      g.setColor(p.hpRatio > 0.4 ? Color.rgb(0x7de87d) : Color.rgb(0xe85d5d));
      g.fillRect(px - 16, py + 18, 32 * p.hpRatio, 5);
    }
    if (p.pid !== myPid) {
      g.setFont(Ui.F_SMALL);
      g.setColor(new Color(255, 255, 255, 220));
      g.drawString(cat.name, px - g.stringWidth(cat.name) / 2, py - 34);
    }
  }
  // proiettili
  for (const pr of s.projs) {
    if (pr.lifeRatio < 0) continue;
    switch (pr.typeIdx) {
      case 0: { // graffio
        const a = pr.lifeRatio;
        g.save();
        g.translate(pr.x, pr.y);
        g.rotate(pr.ang);
        g.setStroke(3, "round", "round");
        g.setColor(cssF(1, 1, 1, a * 0.9));
        for (let k = -1; k <= 1; k++) {
          g.drawArc(-pr.r * 0.5 + k * 7, -pr.r, pr.r, pr.r * 2, -55, 110);
        }
        g.restore();
        break;
      }
      case 1: blit(g, Sprites.proj("gomitolo"), pr.x, pr.y, false, pr.r / 9, now * 6); break;
      case 2: blit(g, Sprites.proj("pallapelo"), pr.x, pr.y, false, pr.r / 10, pr.extra); break;
      case 3: drawWave(g, pr.x, pr.y, pr.r, pr.ang, pr.lifeRatio); break;
      case 4: blit(g, Sprites.proj("artiglio"), pr.x, pr.y, false, 1.1, pr.ang + Math.PI / 2); break;
      case 5: blit(g, Sprites.proj("sardina"), pr.x, pr.y, false, 1, pr.ang); break;
      case 6: blit(g, Sprites.proj("crocc"), pr.x, pr.y, false, 1.1, now * 9); break;
    }
  }
  // testi fluttuanti
  g.setFont("bold 12px sans-serif");
  for (const f of s.floats) {
    const a = clamp(f.life / 0.7, 0, 1);
    const r = (f.rgb >> 16) & 0xff, gg = (f.rgb >> 8) & 0xff, b = f.rgb & 0xff;
    g.setColor(new Color(0, 0, 0, Math.trunc(a * 180)));
    g.drawString(f.text, f.x + 1, f.y + 1);
    g.setColor(new Color(r, gg, b, Math.trunc(a * 255)));
    g.drawString(f.text, f.x, f.y);
  }
}

function drawHud(g: G, s: Snapshot, me: PlayerS | null, w: number, h: number): void {
  if (me === null) return;
  g.setColor(Color.rgb(0x0d150d));
  g.fillRect(0, 0, w, 16);
  g.setColor(Color.rgb(0x59c2e8));
  g.fillRect(0, 0, w * clamp(me.xpRatio, 0, 1), 16);
  g.setColor(Color.rgb(0x7de87d));
  g.fillRect(0, 12, w * clamp(me.xpRatio, 0, 1), 4);
  g.setColor(Color.rgb(0x000000));
  g.fillRect(0, 16, w, 2);
  g.setFont(Ui.F_HUD);
  g.setColor(Ui.BLUE_LT);
  g.drawString("LV " + me.level, 12, 36);
  Ui.center(g, fmt(s.time), Ui.F_TIMER, Ui.GOLD, w / 2, 46);
  g.setColor(Ui.TEXT);
  Ui.right(g, "KO " + s.kills, Ui.F_HUD, w - 12, 36);
  g.setColor(Color.rgb(0xff8c8c));
  Ui.right(g, "PS " + Math.round(me.hpRatio * 100) + "%", Ui.F_HUD, w - 12, 56);
  if (muted) Ui.right(g, "AUDIO OFF [M]", Ui.F_SMALL, w - 12, 74);
  let sx = 12, sy = 52;
  for (const wi of me.weapons) {
    Ui.slot(g, sx, sy, Sprites.icon(Net.WEAPON_IDS[wi[0]]), wi[1]);
    sx += 41;
  }
  sx = 12;
  sy += 41;
  for (const pi of me.passives) {
    Ui.slot(g, sx, sy, Sprites.icon(Net.PASSIVE_IDS[pi[0]]), pi[1]);
    sx += 41;
  }
  if (s.hasBoss) Ui.drawBossBar(g, w, h, s.bossName, s.bossRatio);
  // minimappa
  const cats: number[][] = [];
  for (const pl of s.players) {
    cats.push([pl.x, pl.y, CATS[pl.catIdx].sprite.body.getRGB() & 0xffffff, pl.pid === me.pid ? 1 : 0, pl.alive ? 1 : 0]);
  }
  const foes: number[][] = [];
  for (const e of s.enemies) foes.push([e.x, e.y, e.boss ? 2 : (e.elite ? 1 : 0)]);
  const picks: number[][] = [];
  for (const pk of s.picks) picks.push([pk.x, pk.y]);
  Ui.drawMinimap(g, w, h, me.x, me.y, cats, foes, picks);
}

function drawClientLobby(g: G, s: Snapshot, w: number, h: number): void {
  g.setColor(new Color(8, 14, 9, 170));
  g.fillRect(0, 0, w, h);
  const box = new Rect(w / 2 - 270, h / 2 - 130, 540, 260);
  Ui.panel(g, box, false);
  Ui.center(g, "LOBBY CO-OP", Ui.F_H2, Ui.GOLD, w / 2, box.y + 44);
  Ui.center(g, "Connesso! In attesa che l'host avvii la partita...", Ui.F_TEXT, Ui.TEXT, w / 2, box.y + 76);
  const n = s.players.length;
  Ui.center(g, "Gatti pronti (" + n + "/" + Net.MAX_PLAYERS + "):", Ui.F_BOLD, Ui.GREEN_LT, w / 2, box.y + 110);
  const x0 = w / 2 - n * 45 + 22;
  for (let i = 0; i < n; i++) {
    const cat = CATS[s.players[i].catIdx];
    g.ctx.drawImage(Sprites.catBig(cat), x0 + i * 90 - 24, box.y + 124, 48, 48);
    Ui.center(g, cat.name, Ui.F_SMALL, Ui.TEXT, x0 + i * 90, box.y + 186);
  }
  Ui.center(g, "Prepara gli artigli!", Ui.F_SMALL, Ui.HINT, w / 2, box.y + 226);
}

function drawClientEnd(g: G, s: Snapshot, me: PlayerS | null, w: number, h: number, win: boolean, input: Input): void {
  g.setColor(Ui.OVERLAY);
  g.fillRect(0, 0, w, h);
  if (win) {
    Ui.center(g, "VITTORIA!", Ui.F_TITLE, Ui.GOLD, w / 2, h / 2 - 130);
    Ui.center(g, "Dieci minuti, zero bagnetti. Il giardino è di nuovo vostro.", Ui.F_BOLD, Ui.GREEN_LT, w / 2, h / 2 - 92);
  } else {
    Ui.center(g, "GAME OVER", Ui.F_TITLE, Ui.RED_LT, w / 2, h / 2 - 130);
    Ui.center(g, "Il giardino ha avuto la meglio... per stavolta.", Ui.F_BOLD, Ui.GREEN_LT, w / 2, h / 2 - 92);
  }
  if (me !== null) {
    const cat = CATS[me.catIdx];
    Ui.center(g, cat.name + "  •  Livello " + me.level + "  •  KO di squadra: " + s.kills
      + "  •  Tempo: " + fmt(s.time), Ui.F_TEXT, Ui.TEXT, w / 2, h / 2 - 40);
  }
  const btn = Ui.endButton(w, h);
  const hover = btn.contains(input.mouseX, input.mouseY);
  g.setColor(hover ? Ui.BTN_HOVER : Ui.GOLD);
  g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10);
  Ui.center(g, "Torna al rifugio (R)", Ui.F_BOLD, Ui.BTN_DARK, btn.x + btn.width / 2, btn.y + 31);
  const c = input.consumeClick();
  if (c !== null && btn.contains(c.x, c.y)) App.backToMenu();
}

function drawMessage(g: G, w: number, h: number, title: string, sub: string, error: boolean): void {
  Ui.center(g, title, Ui.F_H2, error ? Ui.RED_LT : Ui.GOLD, w / 2, h / 2 - 20);
  Ui.center(g, sub, Ui.F_TEXT, Ui.TEXT, w / 2, h / 2 + 12);
  Ui.center(g, error ? "R o clic per tornare al menu" : "Aspetta che l'host avvii la partita",
    Ui.F_SMALL, Ui.HINT, w / 2, h / 2 + 44);
}

/** Un frame completo lato client: input, suoni, rendering. */
export function clientFrame(c: Client, input: Input, g: G, w: number, h: number): void {
  handleKeys(c, input);

  const s = c.latest;
  if (c.error !== null) {
    drawMessage(g, w, h, "Connessione persa", c.error, true);
    if (input.consumeClick() !== null) App.backToMenu();
    return;
  }
  if (s === null || c.myPid < 0) {
    input.consumeClick();
    drawMessage(g, w, h, "Connessione...", "In attesa dell'host", false);
    return;
  }

  const me = findMe(s, c.myPid);

  // input verso l'host: movimento + mira (la camera è centrata su di noi)
  const a = input.axis();
  let aimX = 1, aimY = 0;
  if (input.mouseX >= 0) {
    const ax = input.mouseX - w / 2, ay = input.mouseY - h / 2;
    if (Math.hypot(ax, ay) > 12) { aimX = ax; aimY = ay; }
  }
  c.sendInput(a[0], a[1], aimX, aimY);

  playSounds(s, me);

  // interpolazione tra gli ultimi due snapshot
  const p = c.prev;
  let t = 1;
  if (p !== null && s.arrivedAt > p.arrivedAt) {
    const interval = Math.min(0.2, (s.arrivedAt - p.arrivedAt) / 1000);
    t = clamp((performance.now() - s.arrivedAt) / 1000 / interval, 0, 1);
  }
  const prevEnemies = new Map<number, [number, number]>();
  const prevPlayers = new Map<number, [number, number]>();
  if (p !== null) {
    for (const e of p.enemies) prevEnemies.set(e.id, [e.x, e.y]);
    for (const pl of p.players) prevPlayers.set(pl.pid, [pl.x, pl.y]);
  }

  let camX = -w / 2, camY = -h / 2;
  if (me !== null) {
    const pm = prevPlayers.get(me.pid);
    const mx = pm !== undefined ? lerp(pm[0], me.x, t) : me.x;
    const my = pm !== undefined ? lerp(pm[1], me.y, t) : me.y;
    camX = mx - w / 2;
    camY = my - h / 2;
  }

  g.save();
  g.translate(-camX, -camY);
  drawBackground(g, camX, camY, w, h);
  drawWorld(g, s, c.myPid, t, prevEnemies, prevPlayers);
  g.restore();

  drawHud(g, s, me, w, h);

  switch (s.state) {
    case ST_LOBBY: drawClientLobby(g, s, w, h); break;
    case ST_PAUSED: Ui.center(g, "PAUSA (host)", Ui.F_H2, Ui.GOLD, w / 2, h / 2); break;
    case ST_LEVELUP: {
      g.setColor(new Color(8, 14, 9, 190));
      g.fillRect(0, 0, w, h);
      const cs = c.choices;
      if (cs !== null) {
        Ui.center(g, "MIAO! Scegli un potenziamento (clic o 1-" + cs.length + ")", Ui.F_H2, Ui.GOLD, w / 2, h / 2 - 140);
        Ui.drawChoiceCards(g, cs, input.mouseX, input.mouseY, w, h);
        const click = input.consumeClick();
        if (click !== null) {
          const cards = Ui.choiceCards(cs.length, w, h);
          for (let i = 0; i < cards.length; i++) {
            if (cards[i].contains(click.x, click.y)) { c.sendChoice(i); break; }
          }
        }
      } else {
        const who = levelerName(s);
        Ui.center(g, who + " sta scegliendo un potenziamento...", Ui.F_H2, Ui.GOLD, w / 2, h / 2 - 10);
        Ui.center(g, "(il mondo trattiene il fiato)", Ui.F_TEXT, Ui.HINT, w / 2, h / 2 + 22);
      }
      break;
    }
    case ST_OVER: drawClientEnd(g, s, me, w, h, false, input); break;
    case ST_WIN: drawClientEnd(g, s, me, w, h, true, input); break;
  }
  if (s.state !== ST_LEVELUP && s.state !== ST_OVER && s.state !== ST_WIN) input.consumeClick();
}
