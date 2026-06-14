import { rand, TAU } from "./util.ts";

/** Definizione di un tipo di nemico. */
export class EnemyDef {
  constructor(
    readonly id: string,
    readonly name: string,
    readonly hp: number,
    readonly speed: number,
    readonly damage: number,
    readonly r: number,
    readonly xp: number,
    readonly boss: boolean,
  ) {}
}

/** Un'ondata: da quale secondo vale, cosa spawna, a che ritmo, fino a che affollamento. */
export class Wave {
  constructor(
    readonly t: number,
    readonly types: string[],
    readonly rate: number,
    readonly max: number,
  ) {}
}

/** Apparizione programmata di un boss. */
export class BossSpawn {
  constructor(readonly t: number, readonly type: string) {}
}

export const DURATION = 600; // 10 minuti per vincere

/** Bestiario. Tutto ciò che un gatto teme. */
export const ENEMY_TYPES = new Map<string, EnemyDef>();
function put(d: EnemyDef) { ENEMY_TYPES.set(d.id, d); }

put(new EnemyDef("cetriolo", "Cucumber", 12, 55, 6, 12, 1, false));
put(new EnemyDef("piccione", "Pigeon", 8, 100, 5, 11, 1, false));
put(new EnemyDef("topo", "Robot Mouse", 25, 72, 8, 11, 2, false));
put(new EnemyDef("anatra", "Rubber Duck", 45, 60, 10, 13, 3, false));
put(new EnemyDef("cetriolone", "Giant Cucumber", 95, 45, 12, 19, 5, false));
put(new EnemyDef("spruzzino", "Water Squirter", 70, 88, 11, 14, 4, false));
put(new EnemyDef("aspirapolvere", "Vacuum", 230, 38, 16, 20, 8, false));
put(new EnemyDef("roomba", "Roomba 9000", 1300, 62, 20, 32, 50, true));
put(new EnemyDef("phon", "Turbo Hair Dryer", 3600, 74, 25, 28, 80, true));
put(new EnemyDef("veterinario", "Dr. Scissors, the Vet", 9500, 130, 30, 34, 150, true));

export const WAVES: readonly Wave[] = [
  new Wave(0,   ["cetriolo"], 0.9, 25),
  new Wave(45,  ["cetriolo", "piccione"], 1.3, 40),
  new Wave(90,  ["piccione", "topo"], 1.6, 55),
  new Wave(150, ["cetriolo", "topo", "anatra"], 1.9, 70),
  new Wave(210, ["anatra", "cetriolone", "piccione"], 2.1, 85),
  new Wave(270, ["cetriolone", "spruzzino"], 2.3, 95),
  new Wave(330, ["spruzzino", "anatra", "topo"], 2.6, 110),
  new Wave(390, ["aspirapolvere", "cetriolone"], 2.3, 120),
  new Wave(450, ["aspirapolvere", "spruzzino", "cetriolone", "piccione"], 2.9, 140),
  new Wave(510, ["aspirapolvere", "spruzzino", "cetriolone", "anatra", "topo"], 3.3, 160),
];

export const BOSSES: readonly BossSpawn[] = [
  new BossSpawn(180, "roomba"),
  new BossSpawn(360, "phon"),
  new BossSpawn(540, "veterinario"),
];

/** Momenti in cui un anello di cetrioli accerchia il giocatore. */
export const RING_TIMES = [120, 300, 480];

/** Un nemico del giardino: insegue il giocatore, subisce contraccolpi, brilla quando colpito. */
export class Enemy {
  private static nextId = 1;

  readonly id = Enemy.nextId++;
  readonly def: EnemyDef;
  r: number;
  hp: number;
  maxHp: number;
  speed: number;
  damage: number;
  readonly xp: number;
  readonly boss: boolean;
  kbx = 0;
  kby = 0;
  flash = 0;
  wob: number;
  dead = false;

  constructor(
    readonly type: string,
    public x: number,
    public y: number,
    hpMul: number,
    readonly elite: boolean,
  ) {
    const d = ENEMY_TYPES.get(type)!;
    this.def = d;
    this.r = d.r * (elite ? 1.5 : 1);
    this.maxHp = this.hp = d.hp * hpMul * (elite ? 6 : 1);
    this.speed = d.speed * rand(0.9, 1.1);
    this.damage = d.damage * (elite ? 1.5 : 1);
    this.xp = Math.trunc(d.xp * (elite ? 8 : 1));
    this.boss = d.boss;
    this.wob = rand(0, TAU);
  }

  update(dt: number, targetX: number, targetY: number): void {
    const dx = targetX - this.x, dy = targetY - this.y;
    let len = Math.hypot(dx, dy);
    if (len < 1) len = 1;
    this.x += dx / len * this.speed * dt + this.kbx * dt;
    this.y += dy / len * this.speed * dt + this.kby * dt;
    const decay = Math.exp(-7 * dt);
    this.kbx *= decay;
    this.kby *= decay;
    if (this.flash > 0) this.flash -= dt;
    this.wob += dt * 6;
  }
}
