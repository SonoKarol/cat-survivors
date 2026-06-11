package catsurvivors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un gatto in partita. L'input arriva dai campi inMove/inAim, riempiti
 * dall'Input locale (host/solo) o dai messaggi di rete (giocatori remoti).
 */
final class Player {
    /** Un'arma posseduta, con livello e timer di ricarica. */
    static final class WeaponInst {
        final WeaponDef def;
        int level = 1;
        double timer = 0.3;

        WeaponInst(WeaponDef def) { this.def = def; }
    }

    static final int MAX_WEAPONS = 6, MAX_PASSIVES = 6;

    final Game game;
    final CatDef cat;
    int pid = 0;
    boolean alive = true;
    double x, y;
    final double r = 13;
    // input del frame corrente (locale o di rete)
    double inMoveX, inMoveY, inAimX, inAimY;
    // direzione di mira normalizzata: segue il cursore, guida armi e sprite
    double faceX = 1, faceY = 0;
    int level = 1;
    double xp = 0, xpNext;
    int pendingLevels = 0;
    final List<WeaponInst> weapons = new ArrayList<>();
    final Map<String, Integer> passives = new LinkedHashMap<>();
    Stats stats;
    double hp, iframe = 0, auraR = 0, auraPulse = 0, walkT = 0;
    boolean moving;

    Player(CatDef cat, Game game) {
        this.game = game;
        this.cat = cat;
        xpNext = xpNeeded(1);
        recalcStats();
        hp = stats.maxHp;
        addWeapon(cat.startWeapon);
    }

    double xpNeeded(int level) {
        return Math.floor(9 + (level - 1) * 7 + Math.pow(level - 1, 1.5) * 2);
    }

    /** Ricalcola le statistiche effettive: base del gatto + tutti i passivi. */
    void recalcStats() {
        Stats b = cat.base;
        Stats s = new Stats(b.maxHp, 165 * b.speed, b.might, b.area, b.cooldown,
                b.amount, 70 * b.magnet, b.armor, b.regen, b.luck, b.xpGain);
        for (Map.Entry<String, Integer> en : passives.entrySet()) {
            Passives.MAP.get(en.getKey()).mod.apply(s, en.getValue());
        }
        double oldMax = stats != null ? stats.maxHp : -1;
        stats = s;
        if (oldMax > 0 && s.maxHp > oldMax) hp += s.maxHp - oldMax; // la vita extra viene anche curata
        if (oldMax > 0) hp = Math.min(hp, s.maxHp);
    }

    void addWeapon(String id) { weapons.add(new WeaponInst(Weapons.MAP.get(id))); }

    WeaponInst getWeapon(String id) {
        for (WeaponInst w : weapons) if (w.def.id.equals(id)) return w;
        return null;
    }

    void addPassive(String id) {
        passives.merge(id, 1, Integer::sum);
        recalcStats();
    }

    void update(double dt) {
        moving = inMoveX != 0 || inMoveY != 0;
        if (moving) walkT += dt * 9;
        x += inMoveX * stats.speed * dt;
        y += inMoveY * stats.speed * dt;
        // la mira segue il cursore (o l'ultima direzione valida)
        double alen = Math.hypot(inAimX, inAimY);
        if (alen > 0.01) {
            faceX = inAimX / alen;
            faceY = inAimY / alen;
        }
        if (!alive) return; // i fantasmi osservano, non combattono
        if (iframe > 0) iframe -= dt;
        if (stats.regen > 0) hp = Math.min(stats.maxHp, hp + stats.regen * dt);
        auraPulse = Math.max(0, auraPulse - dt * 3);
        for (WeaponInst w : weapons) {
            w.timer -= dt;
            if (w.timer <= 0) {
                WStats st = w.def.stats(w.level, this);
                w.def.fire(game, this, st);
                w.timer = st.cooldown + (w.def.addDuration ? st.duration : 0);
            }
        }
    }

    void gainXp(double v) {
        if (!alive) return;
        xp += v * stats.xpGain;
        while (xp >= xpNext) {
            xp -= xpNext;
            level++;
            xpNext = xpNeeded(level);
            pendingLevels++;
        }
    }

    void takeDamage(double dmg) {
        if (!alive || iframe > 0) return;
        double d = Math.max(1, Math.round(dmg - stats.armor));
        hp -= d;
        iframe = 0.7;
        Sfx.play("hurt");
        game.addFloat(x, y - 24, "-" + (int) d, new Color(0xff6b6b));
        game.shake = Math.min(8, game.shake + 4);
        if (hp <= 0) {
            hp = 0;
            game.onPlayerDown(this);
        }
    }
}
