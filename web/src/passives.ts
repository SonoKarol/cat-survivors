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

put(new PassiveDef("latte", "Ciotola di Latte", "Salute massima +15%", 5, (s, l) => { s.maxHp *= 1 + 0.15 * l; }));
put(new PassiveDef("erbagatta", "Erba Gatta", "Danno +8%", 5, (s, l) => { s.might *= 1 + 0.08 * l; }));
put(new PassiveDef("campanellino", "Campanellino", "Area +8%", 5, (s, l) => { s.area *= 1 + 0.08 * l; }));
put(new PassiveDef("pesciolino", "Pesciolino Secco", "Rigenerazione +0.4 PS/s", 5, (s, l) => { s.regen += 0.4 * l; }));
put(new PassiveDef("cuscino", "Cuscino Imbottito", "Armatura +1", 5, (s, l) => { s.armor += l; }));
put(new PassiveDef("zampe", "Zampe Felpate", "Velocità +7%", 5, (s, l) => { s.speed *= 1 + 0.07 * l; }));
put(new PassiveDef("collare", "Collare con Medaglietta", "Ricarica armi -6%", 5, (s, l) => { s.cooldown *= Math.pow(0.94, l); }));
put(new PassiveDef("scorta", "Gomitolo di Scorta", "+1 proiettile (non influisce sulle aure)", 2, (s, l) => { s.amount += l; }));
put(new PassiveDef("calamita", "Calamita Croccante", "Raggio di raccolta +30%", 5, (s, l) => { s.magnet *= 1 + 0.3 * l; }));
put(new PassiveDef("fortunato", "Croccantino Fortunato", "Fortuna +12% (drop e gemme doppie)", 5, (s, l) => { s.luck *= 1 + 0.12 * l; }));
