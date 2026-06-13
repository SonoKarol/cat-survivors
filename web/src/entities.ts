import { Color } from "./color.ts";
import { rand, TAU } from "./util.ts";
import type { Player } from "./player.ts";

/**
 * Proiettile generico: i campi usati dipendono dal tipo
 * (slash, ball, lob, wave, orbit, knife, fall, boom).
 */
export class Projectile {
  owner: Player | null = null; // chi l'ha sparato
  x = 0; y = 0; vx = 0; vy = 0; r = 0;
  life = 0; maxLife = 0; delay = 0; damage = 0;
  ang = 0; grow = 0; radius = 0; rotSpeed = 0; grav = 0;
  rot = 0; vr = 0; ty = 0; area = 0;
  pierce = 0;
  dead = false;
  hit: Set<number> | null = null;            // nemici già colpiti (armi a colpo singolo)
  hitCd: Map<number, number> | null = null;  // prossimo istante in cui ogni nemico è ricolpibile

  constructor(readonly type: string) {}
}

/** Gemma di esperienza lasciata dai nemici. */
export class Gem {
  sp = 0;
  vacuum = false;
  dead = false;
  constructor(public x: number, public y: number, public value: number) {}
}

/** Oggetto raccoglibile: croccantino (cura) o calamita (aspira tutte le gemme). */
export class Pickup {
  bob = rand(0, TAU);
  dead = false;
  constructor(readonly kind: string, public x: number, public y: number) {}
}

/** Particella decorativa (esplosioni, sbuffi). */
export class Particle {
  maxLife: number;
  constructor(
    public x: number, public y: number,
    public vx: number, public vy: number,
    public life: number, public size: number,
    readonly color: Color,
  ) {
    this.maxLife = life;
  }
}

/** Testo fluttuante (numeri di danno, avvisi). */
export class FloatText {
  life = 0.7;
  constructor(public x: number, public y: number, readonly text: string, readonly color: Color) {}
}

/** Una scelta proposta al level up. kind: "weapon" | "passive" | "heal" | "xp". */
export class Choice {
  constructor(
    readonly kind: string,
    readonly id: string,
    readonly name: string,
    readonly desc: string,
    readonly lvlText: string,
    readonly icon: string,
  ) {}
}
