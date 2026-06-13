import { Color } from "./color.ts";
import { Stats, CatDef } from "./types.ts";
import { WEAPONS, WeaponDef } from "./weapons.ts";
import { PASSIVES } from "./passives.ts";
import { playSfx } from "./sfx.ts";
import type { Game } from "./game.ts";

/** Un'arma posseduta, con livello e timer di ricarica. */
export class WeaponInst {
  level = 1;
  timer = 0.3;
  constructor(readonly def: WeaponDef) {}
}

export const MAX_WEAPONS = 6;
export const MAX_PASSIVES = 6;

/**
 * Un gatto in partita. L'input arriva dai campi inMove/inAim, riempiti
 * dall'Input locale (host/solo) o dai messaggi di rete (giocatori remoti).
 */
export class Player {
  pid = 0;
  alive = true;
  x = 0;
  y = 0;
  readonly r = 13;
  // input del frame corrente (locale o di rete)
  inMoveX = 0; inMoveY = 0; inAimX = 0; inAimY = 0;
  // direzione di mira normalizzata: segue il cursore, guida armi e sprite
  faceX = 1; faceY = 0;
  level = 1;
  xp = 0;
  xpNext: number;
  pendingLevels = 0;
  readonly weapons: WeaponInst[] = [];
  readonly passives = new Map<string, number>();
  stats!: Stats;
  hp = 0;
  iframe = 0;
  auraR = 0;
  auraPulse = 0;
  walkT = 0;
  moving = false;

  constructor(readonly cat: CatDef, readonly game: Game) {
    this.xpNext = this.xpNeeded(1);
    this.recalcStats();
    this.hp = this.stats.maxHp;
    this.addWeapon(cat.startWeapon);
  }

  xpNeeded(level: number): number {
    return Math.floor(9 + (level - 1) * 7 + Math.pow(level - 1, 1.5) * 2);
  }

  /** Ricalcola le statistiche effettive: base del gatto + tutti i passivi. */
  recalcStats(): void {
    const b = this.cat.base;
    const s = new Stats(b.maxHp, 165 * b.speed, b.might, b.area, b.cooldown,
      b.amount, 70 * b.magnet, b.armor, b.regen, b.luck, b.xpGain);
    for (const [id, lvl] of this.passives) {
      PASSIVES.get(id)!.mod(s, lvl);
    }
    const oldMax = this.stats != null ? this.stats.maxHp : -1;
    this.stats = s;
    if (oldMax > 0 && s.maxHp > oldMax) this.hp += s.maxHp - oldMax; // la vita extra viene anche curata
    if (oldMax > 0) this.hp = Math.min(this.hp, s.maxHp);
  }

  addWeapon(id: string): void { this.weapons.push(new WeaponInst(WEAPONS.get(id)!)); }

  getWeapon(id: string): WeaponInst | null {
    for (const w of this.weapons) if (w.def.id === id) return w;
    return null;
  }

  addPassive(id: string): void {
    this.passives.set(id, (this.passives.get(id) ?? 0) + 1);
    this.recalcStats();
  }

  update(dt: number): void {
    this.moving = this.inMoveX !== 0 || this.inMoveY !== 0;
    if (this.moving) this.walkT += dt * 9;
    this.x += this.inMoveX * this.stats.speed * dt;
    this.y += this.inMoveY * this.stats.speed * dt;
    // la mira segue il cursore (o l'ultima direzione valida)
    const alen = Math.hypot(this.inAimX, this.inAimY);
    if (alen > 0.01) {
      this.faceX = this.inAimX / alen;
      this.faceY = this.inAimY / alen;
    }
    if (!this.alive) return; // i fantasmi osservano, non combattono
    if (this.iframe > 0) this.iframe -= dt;
    if (this.stats.regen > 0) this.hp = Math.min(this.stats.maxHp, this.hp + this.stats.regen * dt);
    this.auraPulse = Math.max(0, this.auraPulse - dt * 3);
    for (const w of this.weapons) {
      w.timer -= dt;
      if (w.timer <= 0) {
        const st = w.def.stats(w.level, this);
        w.def.fire(this.game, this, st);
        w.timer = st.cooldown + (w.def.addDuration ? st.duration : 0);
      }
    }
  }

  gainXp(v: number): void {
    if (!this.alive) return;
    this.xp += v * this.stats.xpGain;
    while (this.xp >= this.xpNext) {
      this.xp -= this.xpNext;
      this.level++;
      this.xpNext = this.xpNeeded(this.level);
      this.pendingLevels++;
    }
  }

  takeDamage(dmg: number): void {
    if (!this.alive || this.iframe > 0) return;
    const d = Math.max(1, Math.round(dmg - this.stats.armor));
    this.hp -= d;
    this.iframe = 0.7;
    playSfx("hurt");
    this.game.addFloat(this.x, this.y - 24, "-" + Math.trunc(d), Color.rgb(0xff6b6b));
    this.game.shake = Math.min(8, this.game.shake + 4);
    if (this.hp <= 0) {
      this.hp = 0;
      this.game.onPlayerDown(this);
    }
  }
}
