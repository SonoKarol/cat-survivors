// Porting di Net.java: protocollo binario del co-op (host autoritativo).
// Le stringhe diventano indici stabili; i payload viaggiano poi nel relay
// WebSocket (vedi host.ts/client.ts/relay/server.js).

import { ByteReader, ByteWriter } from "./bytes.ts";
import { clamp } from "./util.ts";
import { CATS } from "./cats.ts";
import { WEAPONS } from "./weapons.ts";
import { PASSIVES } from "./passives.ts";
import { ENEMY_TYPES } from "./enemies.ts";
import { Choice } from "./entities.ts";
import { Snapshot, PlayerS, EnemyS, ProjS, GemS, PickS, FloatS } from "./snapshot.ts";
import type { Game, State } from "./game.ts";

export const MAX_PLAYERS = 4;

export const HELLO = 1, INPUT = 2, CHOICE = 3;
export const WELCOME = 10, SNAPSHOT = 11, LEVELUP = 12;

// ordine = ordinal() di Game.State in Java
export const STATE_ORDER: State[] = ["MENU", "LOBBY", "PLAYING", "LEVELUP", "PAUSED", "OVER", "WIN"];

// indici stabili per non spedire stringhe a ogni frame (Map preserva l'ordine d'inserimento)
export const ENEMY_IDS = [...ENEMY_TYPES.keys()];
export const WEAPON_IDS = [...WEAPONS.keys()];
export const PASSIVE_IDS = [...PASSIVES.keys()];
export const PROJ_TYPES = ["slash", "ball", "lob", "wave", "orbit", "knife", "fall", "boom"];

function idx(list: string[], v: string): number {
  const i = list.indexOf(v);
  return i < 0 ? 0 : i;
}

// ===== Client -> Host =====

export function hello(catIdx: number): Uint8Array {
  return new ByteWriter().byte(HELLO).int(catIdx).toBytes();
}

export function input(mx: number, my: number, ax: number, ay: number): Uint8Array {
  return new ByteWriter().byte(INPUT).float(mx).float(my).float(ax).float(ay).toBytes();
}

export function choiceMsg(i: number): Uint8Array {
  return new ByteWriter().byte(CHOICE).int(i).toBytes();
}

// ===== Host -> Client =====

export function welcome(pid: number): Uint8Array {
  return new ByteWriter().byte(WELCOME).int(pid).toBytes();
}

export function levelUp(cs: Choice[]): Uint8Array {
  const out = new ByteWriter().byte(LEVELUP).byte(cs.length);
  for (const c of cs) {
    out.utf(c.kind).utf(c.id).utf(c.name).utf(c.desc).utf(c.lvlText).utf(c.icon);
  }
  return out.toBytes();
}

export function readChoices(r: ByteReader): Choice[] {
  const n = r.ubyte();
  const cs: Choice[] = [];
  for (let i = 0; i < n; i++) {
    cs.push(new Choice(r.utf(), r.utf(), r.utf(), r.utf(), r.utf(), r.utf()));
  }
  return cs;
}

/** Fotografia del mondo per i client. */
export function snapshot(g: Game): Uint8Array {
  const out = new ByteWriter();
  out.byte(SNAPSHOT);
  out.double(g.time);
  out.int(g.kills);
  out.byte(STATE_ORDER.indexOf(g.state));
  out.byte(g.leveling !== null ? g.leveling.pid : -1);

  out.byte(g.players.length);
  for (const p of g.players) {
    out.byte(p.pid);
    out.byte(CATS.indexOf(p.cat));
    out.float(p.x);
    out.float(p.y);
    const flags = (p.alive ? 1 : 0) | (p.faceX < 0 ? 2 : 0) | (p.moving ? 4 : 0) | (p.iframe > 0 ? 8 : 0);
    out.byte(flags);
    out.float(p.hp / p.stats.maxHp);
    out.short(p.level);
    out.float(clamp(p.xp / p.xpNext, 0, 1));
    out.float(p.getWeapon("fusa") !== null ? p.auraR : 0);
    out.byte(p.weapons.length);
    for (const w of p.weapons) {
      out.byte(idx(WEAPON_IDS, w.def.id));
      out.byte(w.level);
    }
    out.byte(p.passives.size);
    for (const [id, lvl] of p.passives) {
      out.byte(idx(PASSIVE_IDS, id));
      out.byte(lvl);
    }
  }

  const ne = Math.min(g.enemies.length, 220);
  out.short(ne);
  for (let i = 0; i < ne; i++) {
    const e = g.enemies[i];
    out.short(e.id & 0x7fff);
    out.byte(idx(ENEMY_IDS, e.type));
    out.float(e.x);
    out.float(e.y);
    out.byte((e.elite ? 1 : 0) | (e.boss ? 2 : 0) | (e.flash > 0 ? 4 : 0));
    out.float(clamp(e.hp / e.maxHp, 0, 1));
  }

  const np = Math.min(g.projectiles.length, 250);
  out.short(np);
  for (let i = 0; i < np; i++) {
    const pr = g.projectiles[i];
    out.byte(idx(PROJ_TYPES, pr.type));
    out.float(pr.x);
    out.float(pr.y);
    out.float(pr.r);
    out.float(pr.ang);
    let lifeRatio = pr.maxLife > 0 ? clamp(pr.life / pr.maxLife, 0, 1) : 1;
    if (pr.delay > 0) lifeRatio = -1; // non ancora attivo
    out.float(lifeRatio);
    out.float(pr.type === "fall" ? pr.ty : pr.rot);
  }

  const ng = Math.min(g.gems.length, 220);
  out.short(ng);
  for (let i = 0; i < ng; i++) {
    const gm = g.gems[i];
    out.float(gm.x);
    out.float(gm.y);
    out.int(gm.value);
  }

  const npk = Math.min(g.pickups.length, 120);
  out.byte(npk);
  for (let i = 0; i < npk; i++) {
    const pk = g.pickups[i];
    out.byte(pk.kind === "croccantino" ? 0 : 1);
    out.float(pk.x);
    out.float(pk.y);
  }

  const nf = Math.min(g.floats.length, 24);
  out.byte(nf);
  for (let i = g.floats.length - nf; i < g.floats.length; i++) {
    const f = g.floats[i];
    out.float(f.x);
    out.float(f.y);
    out.int(f.color.getRGB());
    out.float(f.life);
    out.utf(f.text);
  }

  let boss = null;
  for (const e of g.enemies) {
    if (e.boss && !e.dead) { boss = e; break; }
  }
  out.bool(boss !== null);
  if (boss !== null) {
    out.utf(boss.def.name);
    out.float(clamp(boss.hp / boss.maxHp, 0, 1));
  }
  return out.toBytes();
}

export function readSnapshot(r: ByteReader): Snapshot {
  const s = new Snapshot();
  s.time = r.double();
  s.kills = r.int();
  s.state = r.ubyte();
  s.levelingPid = r.byte();

  const npl = r.ubyte();
  for (let i = 0; i < npl; i++) {
    const p = new PlayerS();
    p.pid = r.ubyte();
    p.catIdx = r.ubyte();
    p.x = r.float();
    p.y = r.float();
    const flags = r.ubyte();
    p.alive = (flags & 1) !== 0;
    p.flipped = (flags & 2) !== 0;
    p.moving = (flags & 4) !== 0;
    p.hurt = (flags & 8) !== 0;
    p.hpRatio = r.float();
    p.level = r.short();
    p.xpRatio = r.float();
    p.auraR = r.float();
    const nw = r.ubyte();
    for (let j = 0; j < nw; j++) p.weapons.push([r.ubyte(), r.ubyte()]);
    const np2 = r.ubyte();
    for (let j = 0; j < np2; j++) p.passives.push([r.ubyte(), r.ubyte()]);
    s.players.push(p);
  }

  const ne = r.short();
  for (let i = 0; i < ne; i++) {
    const e = new EnemyS();
    e.id = r.short();
    e.typeIdx = r.ubyte();
    e.x = r.float();
    e.y = r.float();
    const flags = r.ubyte();
    e.elite = (flags & 1) !== 0;
    e.boss = (flags & 2) !== 0;
    e.flash = (flags & 4) !== 0;
    e.hpRatio = r.float();
    s.enemies.push(e);
  }

  const np = r.short();
  for (let i = 0; i < np; i++) {
    const pr = new ProjS();
    pr.typeIdx = r.ubyte();
    pr.x = r.float();
    pr.y = r.float();
    pr.r = r.float();
    pr.ang = r.float();
    pr.lifeRatio = r.float();
    pr.extra = r.float();
    s.projs.push(pr);
  }

  const ng = r.short();
  for (let i = 0; i < ng; i++) {
    const gm = new GemS();
    gm.x = r.float();
    gm.y = r.float();
    gm.value = r.int();
    s.gems.push(gm);
  }

  const npk = r.ubyte();
  for (let i = 0; i < npk; i++) {
    const pk = new PickS();
    pk.kind = r.ubyte();
    pk.x = r.float();
    pk.y = r.float();
    s.picks.push(pk);
  }

  const nf = r.ubyte();
  for (let i = 0; i < nf; i++) {
    const f = new FloatS();
    f.x = r.float();
    f.y = r.float();
    f.rgb = r.int();
    f.life = r.float();
    f.text = r.utf();
    s.floats.push(f);
  }

  s.hasBoss = r.bool();
  if (s.hasBoss) {
    s.bossName = r.utf();
    s.bossRatio = r.float();
  }
  return s;
}
