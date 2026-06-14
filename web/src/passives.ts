import type { Stats } from "./types.ts";

/** Modificatore applicato dalle statistiche di un passivo. */
export type StatMod = (s: Stats, lvl: number) => void;

/** Definizione di un oggetto passivo. */
export class PassiveDef {
  constructor(
    readonly id: string,
    readonly name: string,
    readonly desc: string,
    readonly maxLevel: number,
    readonly mod: StatMod,
  ) {}
}

/** I dieci oggetti passivi del gioco. */
export const PASSIVES = new Map<string, PassiveDef>();

function put(d: PassiveDef) { PASSIVES.set(d.id, d); }

put(new PassiveDef("latte", "Milk Bowl", "Max health +15%", 5, (s, l) => { s.maxHp *= 1 + 0.15 * l; }));
put(new PassiveDef("erbagatta", "Catnip", "Damage +8%", 5, (s, l) => { s.might *= 1 + 0.08 * l; }));
put(new PassiveDef("campanellino", "Little Bell", "Area +8%", 5, (s, l) => { s.area *= 1 + 0.08 * l; }));
put(new PassiveDef("pesciolino", "Dried Fish", "Regen +0.4 HP/s", 5, (s, l) => { s.regen += 0.4 * l; }));
put(new PassiveDef("cuscino", "Padded Cushion", "Armor +1", 5, (s, l) => { s.armor += l; }));
put(new PassiveDef("zampe", "Velvet Paws", "Speed +7%", 5, (s, l) => { s.speed *= 1 + 0.07 * l; }));
put(new PassiveDef("collare", "Tag Collar", "Weapon cooldown -6%", 5, (s, l) => { s.cooldown *= Math.pow(0.94, l); }));
put(new PassiveDef("scorta", "Spare Yarn Ball", "+1 projectile (does not affect auras)", 2, (s, l) => { s.amount += l; }));
put(new PassiveDef("calamita", "Kibble Magnet", "Pickup radius +30%", 5, (s, l) => { s.magnet *= 1 + 0.3 * l; }));
put(new PassiveDef("fortunato", "Lucky Kibble", "Luck +12% (drops and double gems)", 5, (s, l) => { s.luck *= 1 + 0.12 * l; }));
