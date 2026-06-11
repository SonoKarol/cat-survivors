package catsurvivors;

/** Un nemico del giardino: insegue il giocatore, subisce contraccolpi, brilla quando colpito. */
final class Enemy {
    private static int nextId = 1;

    final int id = nextId++;
    final String type;
    final EnemyDef def;
    double x, y, r, hp, maxHp, speed, damage;
    final int xp;
    final boolean boss, elite;
    double kbx = 0, kby = 0, flash = 0, wob;
    boolean dead = false;

    Enemy(String type, double x, double y, double hpMul, boolean elite) {
        EnemyDef d = Enemies.TYPES.get(type);
        this.type = type;
        this.def = d;
        this.x = x;
        this.y = y;
        this.elite = elite;
        this.r = d.r * (elite ? 1.5 : 1);
        this.maxHp = this.hp = d.hp * hpMul * (elite ? 6 : 1);
        this.speed = d.speed * Util.rand(0.9, 1.1);
        this.damage = d.damage * (elite ? 1.5 : 1);
        this.xp = (int) (d.xp * (elite ? 8 : 1));
        this.boss = d.boss;
        this.wob = Util.rand(0, Util.TAU);
    }

    void update(double dt, double targetX, double targetY) {
        double dx = targetX - x, dy = targetY - y;
        double len = Math.hypot(dx, dy);
        if (len < 1) len = 1;
        x += dx / len * speed * dt + kbx * dt;
        y += dy / len * speed * dt + kby * dt;
        double decay = Math.exp(-7 * dt);
        kbx *= decay;
        kby *= decay;
        if (flash > 0) flash -= dt;
        wob += dt * 6;
    }
}
