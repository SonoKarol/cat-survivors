package catsurvivors;

import java.util.Map;
import java.util.Set;

/**
 * Proiettile generico: i campi usati dipendono dal tipo
 * (slash, ball, lob, wave, orbit, knife, fall, boom).
 */
final class Projectile {
    final String type;
    Player owner; // chi l'ha sparato (in co-op: orbite, rimbalzi e contraccolpi sono relativi a lui)
    double x, y, vx, vy, r, life, maxLife, delay, damage, ang, grow, radius, rotSpeed, grav, rot, vr, ty, area;
    int pierce;
    boolean dead;
    Set<Integer> hit;             // nemici già colpiti (armi a colpo singolo)
    Map<Integer, Double> hitCd;   // prossimo istante in cui ogni nemico è ricolpibile (armi persistenti)

    Projectile(String type) { this.type = type; }
}
