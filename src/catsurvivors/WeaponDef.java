package catsurvivors;

/** Statistiche effettive di un'arma a un certo livello, già modificate da quelle del giocatore. */
final class WStats {
    double damage, cooldown, area, speed, duration;
    int amount, pierce;
}

/** Definizione astratta di un'arma: statistiche per livello e comportamento di fuoco. */
abstract class WeaponDef {
    final String id, name, desc;
    final int maxLevel;
    final String[] upgrades; // descrizione dei livelli 2..maxLevel
    boolean addDuration = false; // se true il cooldown parte dopo la durata (armi orbitanti)

    WeaponDef(String id, String name, String desc, int maxLevel, String[] upgrades) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.maxLevel = maxLevel;
        this.upgrades = upgrades;
    }

    abstract WStats stats(int level, Player p);

    abstract void fire(Game g, Player p, WStats st);

    /** Applica i modificatori del giocatore alle statistiche base dell'arma. */
    WStats fin(Player p, double damage, double cooldown, int amount, double area,
               double speed, double duration, int pierce) {
        WStats s = new WStats();
        s.damage = damage * p.stats.might;
        s.cooldown = Math.max(0.12, cooldown * p.stats.cooldown);
        s.amount = amount + p.stats.amount;
        s.area = area * p.stats.area;
        s.speed = speed;
        s.duration = duration;
        s.pierce = pierce;
        return s;
    }
}
