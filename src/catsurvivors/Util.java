package catsurvivors;

import java.util.List;
import java.util.Random;

/** Utilità matematiche e helper condivisi. */
final class Util {
    static Random RNG = new Random();
    static final double TAU = Math.PI * 2;

    private Util() {}

    /** Fissa il seme dell'RNG: usato dai test per risultati riproducibili. */
    static void seed(long s) { RNG = new Random(s); }

    static double rand(double a, double b) { return a + RNG.nextDouble() * (b - a); }

    static int randInt(int a, int b) { return a + RNG.nextInt(b - a + 1); }

    static <T> T choice(List<T> list) { return list.get(RNG.nextInt(list.size())); }

    static <T> T choice(T[] arr) { return arr[RNG.nextInt(arr.length)]; }

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double dist(double ax, double ay, double bx, double by) { return Math.hypot(bx - ax, by - ay); }

    static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        return dx * dx + dy * dy;
    }

    static double angleTo(double ax, double ay, double bx, double by) { return Math.atan2(by - ay, bx - ax); }

    /** Hash deterministico 2D -> [0,1), per decorare il terreno senza memorizzarlo. */
    static double hash2(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7fffffff) / 2147483648.0;
    }

    static String fmtTime(double s) {
        int t = (int) Math.max(0, Math.floor(s));
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }
}
