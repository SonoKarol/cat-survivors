// Porting di Game.java: stato di gioco, simulazione e rendering del mondo.
// In co-op gira solo sull'host. Le primitive di disegno passano dal wrapper G.

import { Color, cssF } from "./color.ts";
import { G } from "./g2d.ts";
import { clamp, dist2, rand, random, shuffle, hash2, angleTo, TAU } from "./util.ts";
import { Key } from "./input.ts";
import type { Input } from "./input.ts";
import { CatDef } from "./types.ts";
import { Player, WeaponInst, MAX_WEAPONS, MAX_PASSIVES } from "./player.ts";
import { WEAPONS, WeaponDef } from "./weapons.ts";
import { PASSIVES } from "./passives.ts";
import {
  Enemy, ENEMY_TYPES, WAVES, BOSSES, RING_TIMES, DURATION,
} from "./enemies.ts";
import { Projectile, Gem, Pickup, Particle, FloatText, Choice } from "./entities.ts";
import { Sprites } from "./sprites.ts";
import { playSfx, toggleMute } from "./sfx.ts";
import { App } from "./app.ts";
import { Ui } from "./ui.ts";

export type State = "MENU" | "LOBBY" | "PLAYING" | "LEVELUP" | "PAUSED" | "OVER" | "WIN";

/** Server di rete (host): implementato da host.ts (Task #9). */
export interface GameServer {
  pump(): void;
  broadcast(): void;
  close(): void;
  sendLevelUp(p: Player, choices: Choice[]): void;
}

const GRASS_A = Color.rgb(0x7aa65a);
const GRASS_B = Color.rgb(0x74a055);
const GRASS_DARK = Color.rgb(0x5d8a45);
const DIRT = new Color(155, 139, 94, 90);
const FLOWER = Color.rgb(0xf2c94c);
const WHITE = Color.rgb(0xffffff);
const F_FLOAT = "bold 12px sans-serif";
const F_NAME = "bold 11px sans-serif";
// i rimbalzi del gomitolo usano un'area fissa intorno al proprietario,
// così host e client vedono la stessa fisica a prescindere dalla finestra
const VIRT_HALF_W = 640, VIRT_HALF_H = 360;

export class Game {
  readonly input: Input;
  state: State = "MENU";
  time = 0;
  kills = 0;
  readonly players: Player[] = [];
  server: GameServer | null = null;
  leveling: Player | null = null;
  private nextPid = 1;
  frame = 0;
  readonly enemies: Enemy[] = [];
  readonly projectiles: Projectile[] = [];
  readonly gems: Gem[] = [];
  readonly pickups: Pickup[] = [];
  readonly particles: Particle[] = [];
  readonly floats: FloatText[] = [];
  spawnAcc = 0;
  eliteTimer = 60;
  shake = 0;
  bossIdx = 0;
  ringIdx = 0;
  choices: Choice[] | null = null;
  viewW = 1280;
  viewH = 720;

  constructor(input: Input) { this.input = input; }

  localPlayer(): Player | null { return this.players.length === 0 ? null : this.players[0]; }

  isCoop(): boolean { return this.server !== null; }

  private resetWorld(): void {
    this.time = 0;
    this.kills = 0;
    this.enemies.length = 0;
    this.projectiles.length = 0;
    this.gems.length = 0;
    this.pickups.length = 0;
    this.particles.length = 0;
    this.floats.length = 0;
    this.spawnAcc = 0;
    this.eliteTimer = 60;
    this.shake = 0;
    this.bossIdx = 0;
    this.ringIdx = 0;
    this.choices = null;
    this.leveling = null;
    this.frame = 0;
  }

  reset(): void {
    this.resetWorld();
    this.players.length = 0;
    this.nextPid = 1;
  }

  /** Partita in solitaria. */
  startRun(cat: CatDef): void {
    this.reset();
    const p = new Player(cat, this);
    p.pid = 0;
    this.players.push(p);
    this.state = "PLAYING";
    playSfx("meow");
  }

  /** Apre la lobby co-op: l'host è il giocatore 0 (il server viene agganciato da host.ts). */
  startHosting(cat: CatDef): void {
    this.reset();
    const p = new Player(cat, this);
    p.pid = 0;
    this.players.push(p);
    this.state = "LOBBY";
    playSfx("meow");
  }

  /** Avvia la partita dalla lobby con tutti i giocatori collegati. */
  startRunMulti(): void {
    this.resetWorld();
    for (let i = 0; i < this.players.length; i++) {
      const p = this.players[i];
      const a = TAU * i / this.players.length;
      p.x = Math.cos(a) * 40;
      p.y = Math.sin(a) * 40;
    }
    this.state = "PLAYING";
    playSfx("meow");
  }

  /** Registra un amico appena connesso (chiamato dall'host). */
  addRemotePlayer(cat: CatDef): Player {
    const p = new Player(cat, this);
    p.pid = this.nextPid++;
    const a = rand(0, TAU);
    p.x = Math.cos(a) * 50;
    p.y = Math.sin(a) * 50;
    this.players.push(p);
    playSfx("meow");
    return p;
  }

  removePlayer(p: Player): void {
    const idx = this.players.indexOf(p);
    if (idx < 0) return;
    this.players.splice(idx, 1);
    for (let i = this.projectiles.length - 1; i >= 0; i--) {
      if (this.projectiles[i].owner === p) this.projectiles.splice(i, 1);
    }
    if (this.state !== "LOBBY") this.addFloat(p.x, p.y - 30, p.cat.name + " se n'è andato", Color.rgb(0xffd166));
    if (this.leveling === p) { // non bloccare la partita su una scelta orfana
      this.leveling = null;
      this.choices = null;
      if (this.state === "LEVELUP") this.state = "PLAYING";
      const nxt = this.nextLeveler();
      if (nxt !== null) this.openLevelUp(nxt);
    }
  }

  toMenu(): void {
    if (this.server !== null) {
      this.server.close();
      this.server = null;
      App.onHostStop();
    }
    this.reset();
    this.state = "MENU";
  }

  /** Un passo: input, messaggi di rete, simulazione, broadcast. */
  step(dt: number, w: number, h: number): void {
    this.viewW = w;
    this.viewH = h;
    this.frame++;
    if (this.input.focusLost) {
      this.input.focusLost = false;
      // in solitaria mettiti in pausa; in co-op non bloccare gli amici
      if (this.state === "PLAYING" && !this.isCoop()) this.state = "PAUSED";
    }
    let k: string | null;
    while ((k = this.input.nextPress()) !== null) {
      if (App.roomActive()) App.joinKey(k);
      else this.handleKey(k);
    }
    Ui.handleInput(this);
    if (this.server !== null) this.server.pump();
    if (this.state === "PLAYING") this.update(dt);
    if (this.server !== null && this.frame % 3 === 0) this.server.broadcast();
  }

  private handleKey(code: string): void {
    if (code === Key.P || code === Key.ESCAPE) {
      if (this.state === "PLAYING") this.state = "PAUSED";
      else if (this.state === "PAUSED") this.state = "PLAYING";
      else if (this.state === "LOBBY" && code === Key.ESCAPE) this.toMenu();
    } else if (code === Key.M) {
      toggleMute();
    } else if (code === Key.R) {
      if (this.state === "OVER" || this.state === "WIN") this.toMenu();
    } else if (code === Key.ENTER) {
      if (this.state === "LOBBY") this.startRunMulti();
    } else if (this.state === "LEVELUP" && this.choices !== null && this.leveling === this.localPlayer()) {
      const idx = code === Key.DIGIT1 ? 0 : code === Key.DIGIT2 ? 1
        : code === Key.DIGIT3 ? 2 : code === Key.DIGIT4 ? 3 : -1;
      if (idx >= 0 && idx < this.choices.length) this.pickChoice(this.choices[idx]);
    }
  }

  /** Movimento WASD + mira verso il cursore per il giocatore locale. */
  private applyLocalInput(): void {
    const p = this.localPlayer();
    if (p === null) return;
    const a = this.input.axis();
    p.inMoveX = a[0];
    p.inMoveY = a[1];
    if (this.input.mouseX >= 0) {
      // la camera è centrata sul gatto: il vettore centro->cursore è la mira
      const ax = this.input.mouseX - this.viewW / 2, ay = this.input.mouseY - this.viewH / 2;
      if (Math.hypot(ax, ay) > 12) {
        p.inAimX = ax;
        p.inAimY = ay;
      }
    }
  }

  private update(dt: number): void {
    this.time += dt;
    if (this.time >= DURATION) {
      this.win();
      return;
    }
    this.applyLocalInput();
    for (const p of this.players) p.update(dt);
    this.updateSpawns(dt);
    for (const e of this.enemies) {
      const t = this.nearestAlivePlayer(e.x, e.y);
      if (t !== null) e.update(dt, t.x, t.y);
    }
    this.separate();
    this.updateProjectiles(dt);
    this.updateGems(dt);
    this.updatePickups(dt);
    this.updateFx(dt);
    // danno da contatto
    for (const e of this.enemies) {
      if (e.dead) continue;
      for (const p of this.players) {
        if (!p.alive) continue;
        const rr = e.r + p.r - 4;
        if (dist2(e.x, e.y, p.x, p.y) < rr * rr) p.takeDamage(e.damage);
      }
    }
    // i nemici rimasti troppo indietro vengono riposizionati sul bordo (stile survivor)
    const far = Math.max(this.viewW, this.viewH) * 0.9 + 120;
    for (const e of this.enemies) {
      if (e.boss) continue;
      const near = this.nearestAlivePlayer(e.x, e.y);
      if (near !== null && dist2(e.x, e.y, near.x, near.y) > far * far) {
        const a = rand(0, TAU);
        e.x = near.x + Math.cos(a) * this.spawnRadius();
        e.y = near.y + Math.sin(a) * this.spawnRadius();
      }
    }
    this.removeDead(this.enemies);
    if (this.shake > 0) this.shake = Math.max(0, this.shake - 20 * dt);
    if (this.state === "PLAYING") {
      const lp = this.nextLeveler();
      if (lp !== null) this.openLevelUp(lp);
    }
  }

  private removeDead(list: { dead: boolean }[]): void {
    for (let i = list.length - 1; i >= 0; i--) if (list[i].dead) list.splice(i, 1);
  }

  nearestAlivePlayer(x: number, y: number): Player | null {
    let best: Player | null = null;
    let bestD2 = Infinity;
    for (const p of this.players) {
      if (!p.alive) continue;
      const d2 = dist2(x, y, p.x, p.y);
      if (d2 < bestD2) { bestD2 = d2; best = p; }
    }
    return best;
  }

  /** Un giocatore al tappeto diventa un fantasma; si perde solo se cadono tutti. */
  onPlayerDown(p: Player): void {
    p.alive = false;
    p.pendingLevels = 0;
    this.addFloat(p.x, p.y - 30, p.cat.name + " è KO!", Color.rgb(0xff6b6b));
    this.addParticles(p.x, p.y, Color.rgb(0xb9c4d8), 10);
    for (const q of this.players) if (q.alive) return;
    this.gameOver();
  }

  private spawnRadius(): number { return Math.max(this.viewW, this.viewH) / 2 + 80; }

  /** I nemici nascono intorno a un giocatore vivo a caso: azione per tutti. */
  private spawnAnchor(): Player | null {
    const alive = this.players.filter((p) => p.alive);
    return alive.length === 0 ? this.localPlayer() : alive[Math.floor(random() * alive.length)];
  }

  private updateSpawns(dt: number): void {
    let wave = WAVES[0];
    for (const w of WAVES) if (this.time >= w.t) wave = w;
    const mult = 1 + (this.players.length - 1) * 0.6; // più gatti, più cetrioli
    this.spawnAcc += wave.rate * mult * dt;
    const max = Math.trunc(wave.max * mult);
    while (this.spawnAcc >= 1) {
      this.spawnAcc -= 1;
      if (this.enemies.length < max) this.spawnEnemy(wave.types[Math.floor(random() * wave.types.length)], false);
    }
    this.eliteTimer -= dt;
    if (this.eliteTimer <= 0) {
      this.eliteTimer = 60;
      this.spawnEnemy(wave.types[Math.floor(random() * wave.types.length)], true);
    }
    if (this.bossIdx < BOSSES.length && this.time >= BOSSES[this.bossIdx].t) {
      const bs = BOSSES[this.bossIdx];
      this.bossIdx++;
      const b = this.spawnEnemy(bs.type, false);
      const lp = this.localPlayer();
      if (lp !== null) this.addFloat(lp.x, lp.y - 60, "ATTENZIONE: " + b.def.name + "!", Color.rgb(0xff5b5b));
      playSfx("boss");
      this.shake = 8;
    }
    if (this.ringIdx < RING_TIMES.length && this.time >= RING_TIMES[this.ringIdx]) {
      this.ringIdx++;
      const anchor = this.spawnAnchor();
      if (anchor !== null) {
        const n = 26;
        for (let i = 0; i < n; i++) {
          const a = TAU / n * i;
          this.spawnEnemyAt("cetriolo", anchor.x + Math.cos(a) * 380, anchor.y + Math.sin(a) * 380, false);
        }
        this.addFloat(anchor.x, anchor.y - 60, "Accerchiato dai cetrioli!", Color.rgb(0x9ee86a));
      }
    }
  }

  private hpScale(): number {
    return (1 + this.time / 60 * 0.16) * (1 + (this.players.length - 1) * 0.25);
  }

  spawnEnemy(type: string, elite: boolean): Enemy {
    const anchor = this.spawnAnchor();
    const ax = anchor !== null ? anchor.x : 0, ay = anchor !== null ? anchor.y : 0;
    const a = rand(0, TAU);
    return this.spawnEnemyAt(type, ax + Math.cos(a) * this.spawnRadius(), ay + Math.sin(a) * this.spawnRadius(), elite);
  }

  spawnEnemyAt(type: string, x: number, y: number, elite: boolean): Enemy {
    const e = new Enemy(type, x, y, this.hpScale(), elite);
    this.enemies.push(e);
    return e;
  }

  /** Separazione morbida tra nemici vicini, su griglia per non esplodere in O(n²). */
  private separate(): void {
    const grid = new Map<string, Enemy[]>();
    for (const e of this.enemies) {
      const key = Math.floor(e.x / 64) + "," + Math.floor(e.y / 64);
      let cell = grid.get(key);
      if (cell === undefined) { cell = []; grid.set(key, cell); }
      cell.push(e);
    }
    for (const cell of grid.values()) {
      const n = Math.min(cell.length, 10);
      for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
          const a = cell[i], b = cell[j];
          const dx = b.x - a.x, dy = b.y - a.y;
          const d2 = dx * dx + dy * dy;
          const min = (a.r + b.r) * 0.8;
          if (d2 > 0.01 && d2 < min * min) {
            const d = Math.sqrt(d2);
            const push = (min - d) / d * 0.5;
            a.x -= dx * push;
            a.y -= dy * push;
            b.x += dx * push;
            b.y += dy * push;
          }
        }
      }
    }
  }

  private updateProjectiles(dt: number): void {
    const lp = this.localPlayer();
    for (const pr of this.projectiles) {
      if (pr.delay > 0) {
        pr.delay -= dt;
        continue;
      }
      const own = pr.owner !== null ? pr.owner : lp;
      if (own === null) continue;
      switch (pr.type) {
        case "slash": {
          pr.life -= dt;
          if (pr.life <= 0) { pr.dead = true; break; }
          for (const e of this.enemies) {
            if (e.dead || pr.hit!.has(e.id)) continue;
            const rr = pr.r + e.r;
            if (dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
              pr.hit!.add(e.id);
              this.damageEnemy(e, pr.damage, 140, own.x, own.y, own);
            }
          }
          break;
        }
        case "ball": {
          pr.life -= dt;
          if (pr.life <= 0) { pr.dead = true; break; }
          pr.x += pr.vx * dt;
          pr.y += pr.vy * dt;
          const left = own.x - VIRT_HALF_W + pr.r, right = own.x + VIRT_HALF_W - pr.r;
          const top = own.y - VIRT_HALF_H + pr.r, bot = own.y + VIRT_HALF_H - pr.r;
          if (pr.x < left) { pr.x = left; pr.vx = Math.abs(pr.vx); }
          if (pr.x > right) { pr.x = right; pr.vx = -Math.abs(pr.vx); }
          if (pr.y < top) { pr.y = top; pr.vy = Math.abs(pr.vy); }
          if (pr.y > bot) { pr.y = bot; pr.vy = -Math.abs(pr.vy); }
          this.hitTick(pr, 0.6, 100);
          break;
        }
        case "lob": {
          pr.life -= dt;
          pr.x += pr.vx * dt;
          pr.vy += pr.grav * dt;
          pr.y += pr.vy * dt;
          pr.rot += pr.vr * dt;
          if (pr.life <= 0 || pr.y > own.y + VIRT_HALF_H + 80) { pr.dead = true; break; }
          for (const e of this.enemies) {
            if (e.dead || pr.hit!.has(e.id)) continue;
            const rr = pr.r + e.r;
            if (dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
              pr.hit!.add(e.id);
              this.damageEnemy(e, pr.damage, 120, pr.x, pr.y - 20, own);
              if (--pr.pierce < 0) { pr.dead = true; break; }
            }
          }
          break;
        }
        case "wave": {
          pr.life -= dt;
          if (pr.life <= 0) { pr.dead = true; break; }
          pr.x += pr.vx * dt;
          pr.y += pr.vy * dt;
          pr.r += pr.grow * dt;
          for (const e of this.enemies) {
            if (e.dead || pr.hit!.has(e.id)) continue;
            const rr = pr.r + e.r;
            if (dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
              pr.hit!.add(e.id);
              this.damageEnemy(e, pr.damage, 60, pr.x, pr.y, own);
            }
          }
          break;
        }
        case "orbit": {
          pr.life -= dt;
          if (pr.life <= 0) { pr.dead = true; break; }
          pr.ang += pr.rotSpeed * dt;
          pr.x = own.x + Math.cos(pr.ang) * pr.radius;
          pr.y = own.y + Math.sin(pr.ang) * pr.radius;
          this.hitTick(pr, 0.5, 90);
          break;
        }
        case "knife": {
          pr.life -= dt;
          if (pr.life <= 0) { pr.dead = true; break; }
          pr.x += pr.vx * dt;
          pr.y += pr.vy * dt;
          for (const e of this.enemies) {
            if (e.dead || pr.hit!.has(e.id)) continue;
            const rr = pr.r + e.r;
            if (dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
              pr.hit!.add(e.id);
              this.damageEnemy(e, pr.damage, 60, pr.x - pr.vx * 0.01, pr.y - pr.vy * 0.01, own);
              if (--pr.pierce < 0) { pr.dead = true; break; }
            }
          }
          break;
        }
        case "fall": {
          pr.y += pr.vy * dt;
          if (pr.y >= pr.ty) {
            pr.dead = true;
            const boom = new Projectile("boom");
            boom.owner = own;
            boom.x = pr.x;
            boom.y = pr.ty;
            boom.r = pr.area;
            boom.life = boom.maxLife = 0.25;
            this.projectiles.push(boom);
            for (const e of this.enemies) {
              if (e.dead) continue;
              const rr = boom.r + e.r;
              if (dist2(boom.x, boom.y, e.x, e.y) < rr * rr) {
                this.damageEnemy(e, pr.damage, 160, boom.x, boom.y, own);
              }
            }
            this.addParticles(boom.x, boom.y, Color.rgb(0xf2b04a), 8);
            playSfx("boom");
            this.shake = Math.min(8, this.shake + 2);
          }
          break;
        }
        case "boom": { // solo effetto visivo
          pr.life -= dt;
          if (pr.life <= 0) pr.dead = true;
          break;
        }
      }
    }
    this.removeDead(this.projectiles);
  }

  /** Colpisce i nemici a contatto, ricolpibili dopo un intervallo (armi persistenti). */
  private hitTick(pr: Projectile, interval: number, kb: number): void {
    for (const e of this.enemies) {
      if (e.dead) continue;
      const rr = pr.r + e.r;
      if (dist2(pr.x, pr.y, e.x, e.y) >= rr * rr) continue;
      const next = pr.hitCd!.get(e.id);
      if (next !== undefined && this.time < next) continue;
      pr.hitCd!.set(e.id, this.time + interval);
      this.damageEnemy(e, pr.damage, kb, pr.x, pr.y, pr.owner);
    }
  }

  damageEnemy(e: Enemy, dmg: number, kb: number, fromX: number, fromY: number, src: Player | null): void {
    if (e.dead) return;
    e.hp -= dmg;
    e.flash = 0.12;
    if (kb > 0 && !e.boss) {
      const a = Math.atan2(e.y - fromY, e.x - fromX);
      e.kbx += Math.cos(a) * kb;
      e.kby += Math.sin(a) * kb;
    }
    if (this.floats.length < 70) this.addFloat(e.x, e.y - e.r - 6, String(Math.trunc(Math.max(1, dmg))), WHITE);
    playSfx("hit");
    if (e.hp <= 0) this.killEnemy(e, src);
  }

  private killEnemy(e: Enemy, src: Player | null): void {
    if (e.dead) return;
    e.dead = true;
    this.kills++;
    const luck = src !== null ? src.stats.luck : 1;
    // la fortuna raddoppia le gemme e aumenta i drop
    let gemValue = e.xp;
    if (random() < Math.min(0.5, (luck - 1) * 0.5)) gemValue *= 2;
    this.gems.push(new Gem(e.x + rand(-6, 6), e.y + rand(-6, 6), gemValue));
    if (e.boss) {
      this.pickups.push(new Pickup("croccantino", e.x, e.y));
      for (let i = 0; i < 8; i++) {
        this.gems.push(new Gem(e.x + rand(-40, 40), e.y + rand(-40, 40), 10));
      }
      this.shake = 10;
      playSfx("boom");
    } else if (e.elite) {
      this.pickups.push(new Pickup(random() < 0.5 ? "croccantino" : "magnete", e.x, e.y));
    } else {
      const roll = random();
      if (roll < 0.012 * luck) this.pickups.push(new Pickup("croccantino", e.x, e.y));
      else if (roll < 0.016 * luck) this.pickups.push(new Pickup("magnete", e.x, e.y));
    }
    this.addParticles(e.x, e.y, Color.rgb(0xcfd8cf), 6);
  }

  private updateGems(dt: number): void {
    for (const gm of this.gems) {
      const best = this.nearestAlivePlayer(gm.x, gm.y);
      if (best === null) continue;
      const d2 = dist2(gm.x, gm.y, best.x, best.y);
      const mr = best.stats.magnet;
      if (gm.vacuum || d2 < mr * mr) {
        gm.sp = Math.min(640, gm.sp + 900 * dt);
        const d = Math.sqrt(d2);
        if (d > 1) {
          gm.x += (best.x - gm.x) / d * gm.sp * dt;
          gm.y += (best.y - gm.y) / d * gm.sp * dt;
        }
      }
      if (d2 < 20 * 20) {
        gm.dead = true;
        best.gainXp(gm.value);
        playSfx("pickup");
      }
    }
    this.removeDead(this.gems);
    // troppe gemme in giro: fondi le più vecchie in una sola
    if (this.gems.length > 400) {
      const merge = 80;
      let total = 0, mx = 0, my = 0;
      for (let i = 0; i < merge; i++) {
        const gm = this.gems[i];
        total += gm.value;
        mx += gm.x;
        my += gm.y;
      }
      this.gems.splice(0, merge);
      this.gems.push(new Gem(mx / merge, my / merge, total));
    }
  }

  private updatePickups(dt: number): void {
    for (const pk of this.pickups) {
      pk.bob += dt * 4;
      const p = this.nearestAlivePlayer(pk.x, pk.y);
      if (p === null) continue;
      if (dist2(pk.x, pk.y, p.x, p.y) < 26 * 26) {
        pk.dead = true;
        if (pk.kind === "croccantino") {
          const heal = 30 * p.cat.foodBonus;
          p.hp = Math.min(p.stats.maxHp, p.hp + heal);
          this.addFloat(p.x, p.y - 28, "+" + Math.trunc(heal), Color.rgb(0x7de87d));
          playSfx("food");
        } else {
          for (const gm of this.gems) gm.vacuum = true;
          playSfx("pickup");
        }
      }
    }
    this.removeDead(this.pickups);
  }

  private updateFx(dt: number): void {
    const decay = Math.exp(-4 * dt);
    for (const pt of this.particles) {
      pt.x += pt.vx * dt;
      pt.y += pt.vy * dt;
      pt.vx *= decay;
      pt.vy *= decay;
      pt.life -= dt;
    }
    for (let i = this.particles.length - 1; i >= 0; i--) if (this.particles[i].life <= 0) this.particles.splice(i, 1);
    for (const f of this.floats) {
      f.y -= 30 * dt;
      f.life -= dt;
    }
    for (let i = this.floats.length - 1; i >= 0; i--) if (this.floats[i].life <= 0) this.floats.splice(i, 1);
  }

  addFloat(x: number, y: number, text: string, color: Color): void {
    if (this.floats.length > 80) this.floats.shift();
    this.floats.push(new FloatText(x, y, text, color));
  }

  addParticles(x: number, y: number, color: Color, n: number): void {
    if (this.particles.length > 220) return;
    for (let i = 0; i < n; i++) {
      const a = rand(0, TAU), sp = rand(30, 120);
      this.particles.push(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
        rand(0.3, 0.6), rand(2, 4.5), color));
    }
  }

  nearestEnemies(x: number, y: number, n: number): Enemy[] {
    const sorted = this.enemies.filter((e) => !e.dead);
    sorted.sort((a, b) => dist2(x, y, a.x, a.y) - dist2(x, y, b.x, b.y));
    return sorted.length > n ? sorted.slice(0, n) : sorted;
  }

  // ===== Level up =====

  private nextLeveler(): Player | null {
    for (const p of this.players) {
      if (p.alive && p.pendingLevels > 0) return p;
    }
    return null;
  }

  private openLevelUp(p: Player): void {
    this.state = "LEVELUP";
    this.leveling = p;
    this.choices = this.rollChoices(p);
    if (p.pid !== 0 && this.server !== null) this.server.sendLevelUp(p, this.choices);
    playSfx("levelup");
  }

  private rollChoices(p: Player): Choice[] {
    const pool: Choice[] = [];
    for (const w of p.weapons) {
      if (w.level < w.def.maxLevel) {
        pool.push(new Choice("weapon", w.def.id, w.def.name, w.def.upgrades[w.level - 1],
          "Liv. " + (w.level + 1), w.def.id));
      }
    }
    for (const [id, lvl] of p.passives) {
      const d = PASSIVES.get(id)!;
      if (lvl < d.maxLevel) {
        pool.push(new Choice("passive", d.id, d.name, d.desc, "Liv. " + (lvl + 1), d.id));
      }
    }
    if (p.weapons.length < MAX_WEAPONS) {
      for (const d of WEAPONS.values()) {
        if (p.getWeapon(d.id) === null) pool.push(new Choice("weapon", d.id, d.name, d.desc, "Nuova arma!", d.id));
      }
    }
    if (p.passives.size < MAX_PASSIVES) {
      for (const d of PASSIVES.values()) {
        if (!p.passives.has(d.id)) pool.push(new Choice("passive", d.id, d.name, d.desc, "Nuovo!", d.id));
      }
    }
    shuffle(pool);
    const n = p.stats.luck >= 1.25 ? 4 : 3;
    const picks = pool.slice(0, Math.min(n, pool.length));
    if (picks.length === 0) {
      picks.push(new Choice("heal", "", "Coccole", "Recupera 50 PS", "", "heal"));
      picks.push(new Choice("xp", "", "Croccantino d'Oro", "+30 esperienza", "", "xp"));
    }
    return picks;
  }

  pickChoice(c: Choice): void {
    const p = this.leveling !== null ? this.leveling : this.localPlayer();
    if (p === null) return;
    switch (c.kind) {
      case "weapon": {
        const w = p.getWeapon(c.id);
        if (w !== null) w.level++;
        else p.addWeapon(c.id);
        break;
      }
      case "passive": p.addPassive(c.id); break;
      case "heal": p.hp = Math.min(p.stats.maxHp, p.hp + 50); break;
      case "xp": p.gainXp(30); break;
    }
    playSfx("pickup");
    p.pendingLevels--;
    if (p.pendingLevels > 0) {
      this.choices = this.rollChoices(p);
      if (p.pid !== 0 && this.server !== null) this.server.sendLevelUp(p, this.choices);
    } else {
      this.leveling = null;
      this.choices = null;
      const nxt = this.nextLeveler();
      if (nxt !== null) this.openLevelUp(nxt);
      else this.state = "PLAYING";
    }
  }

  gameOver(): void {
    this.state = "OVER";
    playSfx("gameover");
  }

  win(): void {
    this.state = "WIN";
    playSfx("win");
  }

  // ===== Rendering del mondo =====

  render(g: G, w: number, h: number): void {
    const p = this.localPlayer();
    const camX = p !== null ? p.x - w / 2 : -w / 2;
    const camY = p !== null ? p.y - h / 2 : -h / 2;
    let sx = 0, sy = 0;
    if (this.shake > 0 && this.state === "PLAYING") {
      sx = rand(-this.shake, this.shake);
      sy = rand(-this.shake, this.shake);
    }
    g.save();
    g.translate(-camX + sx, -camY + sy);
    drawBackground(g, camX, camY, w, h);
    if (p !== null && this.state !== "MENU") this.drawWorld(g);
    g.restore();
  }

  private drawWorld(g: G): void {
    // aure di fusa
    for (const p of this.players) {
      if (p.alive && p.auraR > 0 && p.getWeapon("fusa") !== null) {
        const alpha = 0.10 + 0.08 * p.auraPulse;
        g.setColor(cssF(1, 0.75, 0.85, alpha));
        g.fillEllipse(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2);
        g.setColor(cssF(1, 0.75, 0.85, 0.30));
        g.setStroke(1.6);
        g.drawEllipse(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2);
      }
    }
    // gemme
    for (const gm of this.gems) blit(g, Sprites.gem(gm.value), gm.x, gm.y, false, 1, 0);
    // raccoglibili
    for (const pk of this.pickups) {
      shadow(g, pk.x, pk.y + 10, 9);
      blit(g, Sprites.pickup(pk.kind), pk.x, pk.y + Math.sin(pk.bob) * 3, false, 1, 0);
    }
    // esplosioni e bersagli dei croccantini (sotto i nemici)
    for (const pr of this.projectiles) {
      if (pr.type === "boom") {
        const a = clamp(pr.life / pr.maxLife, 0, 1);
        g.setColor(cssF(1, 0.69, 0.29, a * 0.32));
        g.fillEllipse(pr.x - pr.r, pr.y - pr.r, pr.r * 2, pr.r * 2);
        g.setColor(cssF(1, 0.82, 0.4, a * 0.8));
        g.setStroke(3);
        const rr = pr.r * (1.1 - 0.3 * a);
        g.drawEllipse(pr.x - rr, pr.y - rr, rr * 2, rr * 2);
      } else if (pr.type === "fall" && pr.delay <= 0) {
        g.setColor(new Color(20, 30, 15, 60));
        g.fillEllipse(pr.x - 12, pr.ty - 6, 24, 12);
      }
    }
    // nemici
    const lp = this.localPlayer();
    for (const e of this.enemies) {
      if (e.dead) continue;
      shadow(g, e.x, e.y + e.r * 0.85, e.r * 0.9);
      const img = Sprites.enemy(e.type);
      const scale = e.elite ? 1.5 : 1.0;
      const flip = lp !== null && lp.x < e.x;
      if (e.elite) {
        g.setColor(new Color(255, 209, 102, 120));
        g.setStroke(2.5);
        g.drawEllipse(e.x - e.r - 4, e.y - e.r - 4, (e.r + 4) * 2, (e.r + 4) * 2);
      }
      blit(g, img, e.x, e.y + Math.sin(e.wob) * 1.5, flip, scale, 0);
      if (e.flash > 0) {
        const a = clamp(e.flash / 0.12 * 0.7, 0, 1);
        g.setColor(cssF(1, 1, 1, a));
        g.fillEllipse(e.x - e.r, e.y - e.r, e.r * 2, e.r * 2);
      }
      if (e.boss) {
        const bw = 60, ratio = clamp(e.hp / e.maxHp, 0, 1);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(e.x - bw / 2, e.y - e.r - 16, bw, 6);
        g.setColor(Color.rgb(0xd64545));
        g.fillRect(e.x - bw / 2, e.y - e.r - 16, bw * ratio, 6);
      }
    }
    // gatti
    for (const p of this.players) {
      g.save();
      if (!p.alive) g.setAlpha(0.35);
      else shadow(g, p.x, p.y + 14, 13);
      const blink = p.alive && p.iframe > 0 && Math.trunc(p.iframe * 18) % 2 === 0;
      if (!blink) {
        const bob = p.moving ? Math.sin(p.walkT) * 2 : 0;
        blit(g, Sprites.cat(p.cat), p.x, p.y - 6 + bob, p.faceX < 0, 1.15, 0);
      }
      g.restore();
      if (p.alive) {
        const ratio = clamp(p.hp / p.stats.maxHp, 0, 1);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(p.x - 16, p.y + 18, 32, 5);
        g.setColor(ratio > 0.4 ? Color.rgb(0x7de87d) : Color.rgb(0xe85d5d));
        g.fillRect(p.x - 16, p.y + 18, 32 * ratio, 5);
      }
      if (p !== lp) { // il nome degli amici sopra la testa
        g.setFont(F_NAME);
        g.setColor(new Color(255, 255, 255, 220));
        const wname = g.stringWidth(p.cat.name);
        g.drawString(p.cat.name, p.x - wname / 2, p.y - 34);
      }
    }
    // proiettili
    for (const pr of this.projectiles) {
      if (pr.delay > 0) continue;
      switch (pr.type) {
        case "slash": {
          const a = clamp(pr.life / pr.maxLife, 0, 1);
          g.save();
          g.translate(pr.x, pr.y);
          g.rotate(pr.ang);
          g.setStroke(3, "round", "round");
          g.setColor(cssF(1, 1, 1, a * 0.9));
          const rr = pr.r;
          for (let k = -1; k <= 1; k++) {
            g.drawArc(-rr * 0.5 + k * 7, -rr, rr, rr * 2, -55, 110);
          }
          g.restore();
          break;
        }
        case "ball": blit(g, Sprites.proj("gomitolo"), pr.x, pr.y, false, pr.r / 9, this.time * 6); break;
        case "lob": blit(g, Sprites.proj("pallapelo"), pr.x, pr.y, false, pr.r / 10, pr.rot); break;
        case "wave": drawWave(g, pr.x, pr.y, pr.r, pr.ang, clamp(pr.life / pr.maxLife, 0, 1)); break;
        case "orbit": blit(g, Sprites.proj("artiglio"), pr.x, pr.y, false, 1.1, pr.ang + Math.PI / 2); break;
        case "knife": blit(g, Sprites.proj("sardina"), pr.x, pr.y, false, 1, pr.ang); break;
        case "fall": blit(g, Sprites.proj("crocc"), pr.x, pr.y, false, 1.1, this.time * 9); break;
      }
    }
    // particelle
    for (const pt of this.particles) {
      const a = clamp(pt.life / pt.maxLife, 0, 1);
      g.setColor(pt.color.withAlpha(Math.trunc(a * 200)));
      g.fillEllipse(pt.x - pt.size / 2, pt.y - pt.size / 2, pt.size, pt.size);
    }
    // testi fluttuanti
    g.setFont(F_FLOAT);
    for (const f of this.floats) {
      const a = clamp(f.life / 0.7, 0, 1);
      g.setColor(new Color(0, 0, 0, Math.trunc(a * 180)));
      g.drawString(f.text, f.x + 1, f.y + 1);
      g.setColor(f.color.withAlpha(Math.trunc(a * 255)));
      g.drawString(f.text, f.x, f.y);
    }
  }
}

// ===== Helper di rendering condivisi (anche con ClientView, Task #9) =====

export function drawBackground(g: G, camX: number, camY: number, w: number, h: number): void {
  const ts = 64;
  const x0 = Math.floor(camX / ts), x1 = Math.floor((camX + w) / ts);
  const y0 = Math.floor(camY / ts), y1 = Math.floor((camY + h) / ts);
  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) {
      g.setColor(((tx + ty) & 1) === 0 ? GRASS_A : GRASS_B);
      g.fillRect(tx * ts, ty * ts, ts, ts);
      const hsh = hash2(tx, ty);
      const ox = tx * ts + 8 + hash2(tx * 3 + 1, ty * 5 + 2) * (ts - 16);
      const oy = ty * ts + 8 + hash2(tx * 7 + 3, ty * 11 + 5) * (ts - 16);
      if (hsh < 0.05) { // fiorellino
        g.setColor(WHITE);
        for (let i = 0; i < 4; i++) {
          const a = TAU / 4 * i + 0.6;
          g.fillEllipse(ox + Math.cos(a) * 3 - 2, oy + Math.sin(a) * 3 - 2, 4, 4);
        }
        g.setColor(FLOWER);
        g.fillEllipse(ox - 2, oy - 2, 4, 4);
      } else if (hsh < 0.12) { // ciuffo d'erba
        g.setColor(GRASS_DARK);
        g.setStroke(1.6, "round", "round");
        g.drawQuad(ox, oy + 5, ox - 2, oy, ox - 4, oy - 4);
        g.drawQuad(ox, oy + 5, ox, oy - 1, ox + 1, oy - 5);
        g.drawQuad(ox, oy + 5, ox + 3, oy, ox + 5, oy - 3);
      } else if (hsh < 0.15) { // zolla di terra
        g.setColor(DIRT);
        g.fillEllipse(ox - 7, oy - 4, 14, 8);
      }
    }
  }
}

/** Onda sonora del miagolio: condivisa con il rendering del client. */
export function drawWave(g: G, x: number, y: number, r: number, ang: number, a: number): void {
  g.setStroke(3, "round", "round");
  const deg = -ang * 180 / Math.PI;
  g.setColor(cssF(0.56, 0.83, 0.96, a * 0.9));
  g.drawArc(x - r, y - r, r * 2, r * 2, deg - 40, 80);
  g.setColor(cssF(0.56, 0.83, 0.96, a * 0.55));
  const r2 = r * 0.72;
  g.drawArc(x - r2, y - r2, r2 * 2, r2 * 2, deg - 35, 70);
  const r3 = r * 0.45;
  g.setColor(cssF(0.56, 0.83, 0.96, a * 0.3));
  g.drawArc(x - r3, y - r3, r3 * 2, r3 * 2, deg - 30, 60);
}

export function shadow(g: G, x: number, y: number, r: number): void {
  g.setColor(new Color(0, 0, 0, 45));
  g.fillEllipse(x - r, y - r * 0.45, r * 2, r * 0.9);
}

export function blit(g: G, img: HTMLCanvasElement, x: number, y: number, flip: boolean, scale: number, rot: number): void {
  g.save();
  g.translate(x, y);
  if (rot !== 0) g.rotate(rot);
  g.scale(flip ? -scale : scale, scale);
  g.drawImage(img, -img.width / 2, -img.height / 2);
  g.restore();
}
