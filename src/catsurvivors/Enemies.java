package catsurvivors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Definizione di un tipo di nemico. */
final class EnemyDef {
    final String id, name;
    final double hp, speed, damage, r;
    final int xp;
    final boolean boss;

    EnemyDef(String id, String name, double hp, double speed, double damage, double r, int xp, boolean boss) {
        this.id = id;
        this.name = name;
        this.hp = hp;
        this.speed = speed;
        this.damage = damage;
        this.r = r;
        this.xp = xp;
        this.boss = boss;
    }
}

/** Un'ondata: da quale secondo vale, cosa spawna, a che ritmo, fino a che affollamento. */
final class Wave {
    final double t, rate;
    final String[] types;
    final int max;

    Wave(double t, String[] types, double rate, int max) {
        this.t = t;
        this.types = types;
        this.rate = rate;
        this.max = max;
    }
}

/** Apparizione programmata di un boss. */
final class BossSpawn {
    final double t;
    final String type;

    BossSpawn(double t, String type) {
        this.t = t;
        this.type = type;
    }
}

/** Bestiario, ondate e calendario dei boss. Tutto ciò che un gatto teme. */
final class Enemies {
    static final double DURATION = 600; // 10 minuti per vincere

    static final Map<String, EnemyDef> TYPES = new LinkedHashMap<>();

    private Enemies() {}

    private static void put(EnemyDef d) { TYPES.put(d.id, d); }

    static {
        put(new EnemyDef("cetriolo", "Cetriolo", 12, 55, 6, 12, 1, false));
        put(new EnemyDef("piccione", "Piccione", 8, 100, 5, 11, 1, false));
        put(new EnemyDef("topo", "Topo Robot", 25, 72, 8, 11, 2, false));
        put(new EnemyDef("anatra", "Anatra di Gomma", 45, 60, 10, 13, 3, false));
        put(new EnemyDef("cetriolone", "Cetriolone", 95, 45, 12, 19, 5, false));
        put(new EnemyDef("spruzzino", "Spruzzino d'Acqua", 70, 88, 11, 14, 4, false));
        put(new EnemyDef("aspirapolvere", "Aspirapolvere", 230, 38, 16, 20, 8, false));
        put(new EnemyDef("roomba", "Roomba 9000", 1300, 62, 20, 32, 50, true));
        put(new EnemyDef("phon", "Phon Turbo", 3600, 74, 25, 28, 80, true));
        put(new EnemyDef("veterinario", "Dott. Forbici, il Veterinario", 9500, 130, 30, 34, 150, true)); // veloce: l'ultimo minuto deve far paura
    }

    static final List<Wave> WAVES = List.of(
        new Wave(0,   new String[]{"cetriolo"}, 0.9, 25),
        new Wave(45,  new String[]{"cetriolo", "piccione"}, 1.3, 40),
        new Wave(90,  new String[]{"piccione", "topo"}, 1.6, 55),
        new Wave(150, new String[]{"cetriolo", "topo", "anatra"}, 1.9, 70),
        new Wave(210, new String[]{"anatra", "cetriolone", "piccione"}, 2.1, 85),
        new Wave(270, new String[]{"cetriolone", "spruzzino"}, 2.3, 95),
        new Wave(330, new String[]{"spruzzino", "anatra", "topo"}, 2.6, 110),
        new Wave(390, new String[]{"aspirapolvere", "cetriolone"}, 2.3, 120),
        new Wave(450, new String[]{"aspirapolvere", "spruzzino", "cetriolone", "piccione"}, 2.9, 140),
        new Wave(510, new String[]{"aspirapolvere", "spruzzino", "cetriolone", "anatra", "topo"}, 3.3, 160)
    );

    static final List<BossSpawn> BOSSES = List.of(
        new BossSpawn(180, "roomba"),
        new BossSpawn(360, "phon"),
        new BossSpawn(540, "veterinario")
    );

    /** Momenti in cui un anello di cetrioli accerchia il giocatore. */
    static final double[] RING_TIMES = {120, 300, 480};
}
