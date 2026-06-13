// Porting di Snapshot.java: fotografia del mondo ricevuta dal client a ~20 Hz,
// renderizzata con interpolazione tra gli ultimi due snapshot.

export class PlayerS {
  pid = 0; catIdx = 0; level = 0;
  x = 0; y = 0; hpRatio = 0; xpRatio = 0; auraR = 0;
  alive = false; flipped = false; moving = false; hurt = false;
  weapons: number[][] = []; // [idx, livello]
  passives: number[][] = [];
}

export class EnemyS {
  id = 0; typeIdx = 0;
  x = 0; y = 0; hpRatio = 0;
  elite = false; boss = false; flash = false;
}

export class ProjS {
  typeIdx = 0;
  x = 0; y = 0; r = 0; ang = 0; lifeRatio = 0; extra = 0; // extra: rotazione, o ty per i croccantini in caduta
}

export class GemS { x = 0; y = 0; value = 0; }
export class PickS { kind = 0; x = 0; y = 0; }
export class FloatS { x = 0; y = 0; life = 0; rgb = 0; text = ""; }

export class Snapshot {
  time = 0;
  kills = 0;
  state = 0;
  levelingPid = 0;
  players: PlayerS[] = [];
  enemies: EnemyS[] = [];
  projs: ProjS[] = [];
  gems: GemS[] = [];
  picks: PickS[] = [];
  floats: FloatS[] = [];
  hasBoss = false;
  bossName = "";
  bossRatio = 0;
  arrivedAt = 0; // performance.now() alla ricezione, per interpolare
}
