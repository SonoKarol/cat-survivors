import { Color } from "./color.ts";

/** Statistiche di un personaggio: usate sia come base/moltiplicatori sia come valori effettivi calcolati. */
export class Stats {
  maxHp: number;
  speed: number;
  might: number;
  area: number;
  cooldown: number;
  amount: number;
  magnet: number;
  armor: number;
  regen: number;
  luck: number;
  xpGain: number;

  // Ordine identico al costruttore Java: maxHp, speed, might, area, cooldown, amount, magnet, armor, regen, luck, xpGain
  constructor(
    maxHp: number, speed: number, might: number, area: number, cooldown: number,
    amount: number, magnet: number, armor: number, regen: number, luck: number, xpGain: number,
  ) {
    this.maxHp = maxHp;
    this.speed = speed;
    this.might = might;
    this.area = area;
    this.cooldown = cooldown;
    this.amount = amount;
    this.magnet = magnet;
    this.armor = armor;
    this.regen = regen;
    this.luck = luck;
    this.xpGain = xpGain;
  }

  clone(): Stats {
    return new Stats(
      this.maxHp, this.speed, this.might, this.area, this.cooldown,
      this.amount, this.magnet, this.armor, this.regen, this.luck, this.xpGain,
    );
  }
}

/** Parametri estetici per disegnare proceduralmente un gatto.
 *  I colori opzionali (belly/stripes/mask) sono null quando non usati. */
export class CatSprite {
  constructor(
    readonly body: Color,
    readonly belly: Color | null,
    readonly stripes: Color | null,
    readonly mask: Color | null,
    readonly eyes: Color,
    readonly fluffy: boolean,
    readonly sphynx: boolean,
  ) {}
}

/** Definizione di un gatto giocabile: razza, carattere, statistiche e arma iniziale. */
export class CatDef {
  constructor(
    readonly id: string,
    readonly name: string,
    readonly breed: string,
    readonly personality: string,
    readonly bonus: string,
    readonly startWeapon: string,
    readonly base: Stats,
    readonly sprite: CatSprite,
    readonly foodBonus: number,
  ) {}
}
