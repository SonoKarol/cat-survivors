package catsurvivors;

/** Fotografia del mondo ricevuta dal client a ~20 Hz, da renderizzare con interpolazione. */
final class Snapshot {
    double time;
    int kills, state, levelingPid;
    PlayerS[] players;
    EnemyS[] enemies;
    ProjS[] projs;
    GemS[] gems;
    PickS[] picks;
    FloatS[] floats;
    boolean hasBoss;
    String bossName;
    float bossRatio;
    long arrivedAt; // System.nanoTime() alla ricezione, per interpolare

    static final class PlayerS {
        int pid, catIdx, level;
        float x, y, hpRatio, xpRatio, auraR;
        boolean alive, flipped, moving, hurt;
        int[][] weapons, passives; // [idx, livello]
    }

    static final class EnemyS {
        int id, typeIdx;
        float x, y, hpRatio;
        boolean elite, boss, flash;
    }

    static final class ProjS {
        int typeIdx;
        float x, y, r, ang, lifeRatio, extra; // extra: rotazione, o ty per i croccantini in caduta
    }

    static final class GemS { float x, y; int value; }

    static final class PickS { int kind; float x, y; }

    static final class FloatS { float x, y, life; int rgb; String text; }
}
