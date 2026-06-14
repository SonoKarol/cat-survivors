import { Projectile } from "./entities.ts";
import { rand, choice, dist2, angleTo, TAU } from "./util.ts";
import { playSfx } from "./sfx.ts";
import type { Player } from "./player.ts";
import type { Game } from "./game.ts";

/** Statistiche effettive di un'arma a un certo livello, già modificate da quelle del giocatore. */
export class WStats {
  damage = 0;
  cooldown = 0;
  area = 0;
  speed = 0;
  duration = 0;
  amount = 0;
  pierce = 0;
}

type StatsFn = (lv: number, p: Player) => WStats;
type FireFn = (g: Game, p: Player, st: WStats) => void;

/** Definizione di un'arma: statistiche per livello e comportamento di fuoco. */
export class WeaponDef {
  addDuration: boolean;
  constructor(
    readonly id: string,
    readonly name: string,
    readonly desc: string,
    readonly maxLevel: number,
    readonly upgrades: string[],
    private readonly statsFn: StatsFn,
    private readonly fireFn: FireFn,
    addDuration = false,
  ) {
    this.addDuration = addDuration;
  }

  stats(level: number, p: Player): WStats { return this.statsFn(level, p); }
  fire(g: Game, p: Player, st: WStats): void { this.fireFn(g, p, st); }
}

/** Applica i modificatori del giocatore alle statistiche base dell'arma. */
export function fin(
  p: Player, damage: number, cooldown: number, amount: number,
  area: number, speed: number, duration: number, pierce: number,
): WStats {
  const s = new WStats();
  s.damage = damage * p.stats.might;
  s.cooldown = Math.max(0.12, cooldown * p.stats.cooldown);
  s.amount = amount + p.stats.amount;
  s.area = area * p.stats.area;
  s.speed = speed;
  s.duration = duration;
  s.pierce = pierce;
  return s;
}

/** Le otto armi feline del gioco. */
export const WEAPONS = new Map<string, WeaponDef>();
function put(d: WeaponDef) { WEAPONS.set(d.id, d); }

put(new WeaponDef("graffio", "Feline Scratch",
  "Quick swipe in front. The weapon of the fearless.",
  6, ["Extra scratch from behind", "Damage +8", "Area +25%", "Damage +10", "Extra scratch"],
  (lv, p) => {
    const dmg = 15 + (lv >= 3 ? 8 : 0) + (lv >= 5 ? 10 : 0);
    const amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 6 ? 1 : 0);
    const area = lv >= 4 ? 1.25 : 1.0;
    return fin(p, dmg, 1.1, amount, area, 0, 0, 0);
  },
  (g, p, st) => {
    for (let i = 0; i < st.amount; i++) {
      const side = (i % 2 === 0) ? 1 : -1;
      const dx = p.faceX * side, dy = p.faceY * side;
      const pr = new Projectile("slash");
      pr.owner = p;
      pr.x = p.x + dx * 48;
      pr.y = p.y + dy * 48 - 6;
      pr.ang = Math.atan2(dy, dx);
      pr.r = 46 * st.area;
      pr.life = pr.maxLife = 0.18;
      pr.delay = i * 0.1;
      pr.damage = st.damage;
      pr.hit = new Set();
      g.projectiles.push(pr);
    }
  }));

put(new WeaponDef("gomitolo", "Bouncy Yarn Ball",
  "An indestructible ball of yarn that bounces all over the screen.",
  6, ["+1 yarn ball", "Damage +6", "Duration +1.5s", "+1 yarn ball", "Damage +10, faster"],
  (lv, p) => {
    const dmg = 10 + (lv >= 3 ? 6 : 0) + (lv >= 6 ? 10 : 0);
    const amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
    const speed = 250 + (lv >= 6 ? 50 : 0);
    const duration = 4 + (lv >= 4 ? 1.5 : 0);
    return fin(p, dmg, 3.0, amount, 1, speed, duration, 0);
  },
  (g, p, st) => {
    for (let i = 0; i < st.amount; i++) {
      const a = rand(0, TAU);
      const pr = new Projectile("ball");
      pr.owner = p;
      pr.x = p.x;
      pr.y = p.y;
      pr.vx = Math.cos(a) * st.speed;
      pr.vy = Math.sin(a) * st.speed;
      pr.r = 10 * st.area;
      pr.life = st.duration;
      pr.damage = st.damage;
      pr.hitCd = new Map();
      g.projectiles.push(pr);
    }
    playSfx("shoot");
  }));

put(new WeaponDef("pallapelo", "Hairball",
  "Tossed nonchalantly into the air, lands with devastation.",
  6, ["+1 hairball", "Damage +12", "Pierce +3, area +20%", "+1 hairball", "Damage +15"],
  (lv, p) => {
    const dmg = 25 + (lv >= 3 ? 12 : 0) + (lv >= 6 ? 15 : 0);
    const amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
    const pierce = 4 + (lv >= 4 ? 3 : 0);
    const area = lv >= 4 ? 1.2 : 1.0;
    return fin(p, dmg, 2.4, amount, area, 0, 0, pierce);
  },
  (g, p, st) => {
    for (let i = 0; i < st.amount; i++) {
      const pr = new Projectile("lob");
      pr.owner = p;
      pr.x = p.x;
      pr.y = p.y - 10;
      pr.vx = rand(-90, 90) + p.faceX * 60;
      pr.vy = -rand(380, 470);
      pr.grav = 780;
      pr.r = 11 * st.area;
      pr.rot = rand(0, TAU);
      pr.vr = rand(-6, 6);
      pr.life = 2.5;
      pr.damage = st.damage;
      pr.pierce = st.pierce;
      pr.hit = new Set();
      g.projectiles.push(pr);
    }
  }));

put(new WeaponDef("fusa", "Purr Aura",
  "Purring relaxes cats and disintegrates nearby enemies.",
  6, ["Area +15%", "Damage +3", "Area +15%, faster purr", "Damage +4", "Area +20%, faster purr"],
  (lv, p) => {
    const dmg = 5 + (lv >= 3 ? 3 : 0) + (lv >= 5 ? 4 : 0);
    const tick = 0.5 - (lv >= 4 ? 0.06 : 0) - (lv >= 6 ? 0.08 : 0);
    const area = (lv >= 2 ? 1.15 : 1.0) * (lv >= 4 ? 1.15 : 1.0) * (lv >= 6 ? 1.2 : 1.0);
    return fin(p, dmg, tick, 1, area, 0, 0, 0);
  },
  (g, p, st) => {
    const r = 75 * st.area;
    for (const e of g.enemies) {
      if (e.dead) continue;
      const rr = r + e.r;
      if (dist2(p.x, p.y, e.x, e.y) <= rr * rr) {
        g.damageEnemy(e, st.damage, 40, p.x, p.y, p);
      }
    }
    p.auraR = r;
    p.auraPulse = 1;
  }));

put(new WeaponDef("miagolio", "Sonic Meow",
  "A MEOW packed with fury that tears through enemies like a shockwave.",
  6, ["Damage +8", "+1 wave", "Area +30%", "Damage +10", "+1 wave"],
  (lv, p) => {
    const dmg = 15 + (lv >= 2 ? 8 : 0) + (lv >= 5 ? 10 : 0);
    const amount = 1 + (lv >= 3 ? 1 : 0) + (lv >= 6 ? 1 : 0);
    const area = lv >= 4 ? 1.3 : 1.0;
    return fin(p, dmg, 2.6, amount, area, 230, 1.6, 0);
  },
  (g, p, st) => {
    const targets = g.nearestEnemies(p.x, p.y, st.amount);
    for (let i = 0; i < st.amount; i++) {
      const ang = i < targets.length
        ? angleTo(p.x, p.y, targets[i].x, targets[i].y)
        : rand(0, TAU);
      const pr = new Projectile("wave");
      pr.owner = p;
      pr.x = p.x;
      pr.y = p.y;
      pr.vx = Math.cos(ang) * st.speed;
      pr.vy = Math.sin(ang) * st.speed;
      pr.ang = ang;
      pr.r = 16;
      pr.grow = 55 * st.area;
      pr.life = pr.maxLife = st.duration;
      pr.damage = st.damage;
      pr.hit = new Set();
      g.projectiles.push(pr);
    }
    playSfx("meow");
  }));

put(new WeaponDef("artigli", "Spectral Claws",
  "Glowing claws orbit the cat, sharp as its tongue.",
  6, ["+1 claw", "Damage +5, faster spin", "Duration +1.2s", "+1 claw", "Damage +8, area +20%"],
  (lv, p) => {
    const dmg = 8 + (lv >= 3 ? 5 : 0) + (lv >= 6 ? 8 : 0);
    const amount = 2 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
    const rot = 3.2 + (lv >= 3 ? 0.8 : 0);
    const dur = 3 + (lv >= 4 ? 1.2 : 0);
    const area = lv >= 6 ? 1.2 : 1.0;
    return fin(p, dmg, 2.0, amount, area, rot, dur, 0);
  },
  (g, p, st) => {
    for (let i = 0; i < st.amount; i++) {
      const pr = new Projectile("orbit");
      pr.owner = p;
      pr.ang = TAU / st.amount * i;
      pr.radius = 80 * st.area;
      pr.rotSpeed = st.speed;
      pr.life = st.duration;
      pr.r = 12 * st.area;
      pr.damage = st.damage;
      pr.hitCd = new Map();
      pr.x = p.x;
      pr.y = p.y;
      g.projectiles.push(pr);
    }
  },
  /* addDuration */ true));

put(new WeaponDef("sardine", "Sardine Toss",
  "Precision ballistic sardines. A bit smelly, but they work.",
  6, ["+1 sardine", "+1 sardine", "Damage +6", "+1 sardine", "Damage +8, pierce +1"],
  (lv, p) => {
    const dmg = 9 + (lv >= 4 ? 6 : 0) + (lv >= 6 ? 8 : 0);
    const amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 3 ? 1 : 0) + (lv >= 5 ? 1 : 0);
    const pierce = 1 + (lv >= 6 ? 1 : 0);
    return fin(p, dmg, 1.0, amount, 1, 430, 0, pierce);
  },
  (g, p, st) => {
    const base = Math.atan2(p.faceY, p.faceX);
    for (let i = 0; i < st.amount; i++) {
      const off = i - (st.amount - 1) / 2.0;
      const ang = base + off * 0.08;
      const pr = new Projectile("knife");
      pr.owner = p;
      pr.x = p.x - Math.sin(base) * off * 10;
      pr.y = p.y + Math.cos(base) * off * 10;
      pr.vx = Math.cos(ang) * st.speed;
      pr.vy = Math.sin(ang) * st.speed;
      pr.ang = ang;
      pr.r = 8 * st.area;
      pr.life = 1.4;
      pr.damage = st.damage;
      pr.pierce = st.pierce;
      pr.hit = new Set();
      g.projectiles.push(pr);
    }
    playSfx("shoot");
  }));

put(new WeaponDef("croccantini", "Kibble Rain",
  "Explosive kibble rains from the sky. Dinner is served.",
  6, ["+1 kibble", "Damage +10", "Area +25%", "+2 kibble", "Damage +15"],
  (lv, p) => {
    const dmg = 20 + (lv >= 3 ? 10 : 0) + (lv >= 6 ? 15 : 0);
    const amount = 2 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 2 : 0);
    const area = lv >= 4 ? 1.25 : 1.0;
    return fin(p, dmg, 3.2, amount, area, 0, 0, 0);
  },
  (g, p, st) => {
    for (let i = 0; i < st.amount; i++) {
      let tx: number, ty: number;
      const e = g.enemies.length === 0 ? null : choice(g.enemies);
      if (e !== null && !e.dead && dist2(p.x, p.y, e.x, e.y) < 500 * 500) {
        tx = e.x + rand(-30, 30);
        ty = e.y + rand(-30, 30);
      } else {
        tx = p.x + rand(-260, 260);
        ty = p.y + rand(-200, 200);
      }
      const pr = new Projectile("fall");
      pr.owner = p;
      pr.x = tx;
      pr.y = ty - 320;
      pr.ty = ty;
      pr.vy = 560;
      pr.r = 7;
      pr.damage = st.damage;
      pr.area = 60 * st.area;
      pr.life = 2;
      pr.delay = i * 0.12;
      g.projectiles.push(pr);
    }
  }));
