package catsurvivors;

import java.awt.Color;

/** Gemma di esperienza lasciata dai nemici. */
final class Gem {
    double x, y, sp = 0;
    int value;
    boolean vacuum = false, dead = false;

    Gem(double x, double y, int value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }
}

/** Oggetto raccoglibile: croccantino (cura) o calamita (aspira tutte le gemme). */
final class Pickup {
    final String kind; // "croccantino" | "magnete"
    double x, y, bob = Util.rand(0, Util.TAU);
    boolean dead = false;

    Pickup(String kind, double x, double y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
    }
}

/** Particella decorativa (esplosioni, sbuffi). */
final class Particle {
    double x, y, vx, vy, life, maxLife, size;
    final Color color;

    Particle(double x, double y, double vx, double vy, double life, double size, Color color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = this.maxLife = life;
        this.size = size;
        this.color = color;
    }
}

/** Testo fluttuante (numeri di danno, avvisi). */
final class FloatText {
    double x, y, life = 0.7;
    final String text;
    final Color color;

    FloatText(double x, double y, String text, Color color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
    }
}
