package catsurvivors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.util.HashMap;
import java.util.Map;

/**
 * Effetti sonori chiptune sintetizzati a runtime con javax.sound.sampled.
 * Nessun file audio esterno. Se l'audio non è disponibile il gioco continua muto.
 */
final class Sfx {
    private static final int SR = 44100;
    private static final Map<String, Clip> CLIPS = new HashMap<>();
    static volatile boolean muted = false;
    private static long lastHit = 0;

    private Sfx() {}

    // Ogni segmento: { frequenza, durata, forma d'onda (0=quadra 1=dente di sega 2=triangolare), volume, glissando, ritardo }
    static void init() {
        try {
            put("hit",      new double[][]{{210, 0.06, 0, 0.18, 0, 0}});
            put("shoot",    new double[][]{{520, 0.05, 0, 0.10, -120, 0}});
            put("hurt",     new double[][]{{140, 0.22, 1, 0.40, -60, 0}});
            put("pickup",   new double[][]{{740, 0.07, 0, 0.22, 250, 0}});
            put("food",     new double[][]{{420, 0.10, 2, 0.35, 120, 0}});
            put("levelup",  new double[][]{{523, 0.12, 0, 0.30, 0, 0}, {659, 0.12, 0, 0.30, 0, 0.07},
                                           {784, 0.12, 0, 0.30, 0, 0.14}, {1047, 0.14, 0, 0.30, 0, 0.21}});
            put("meow",     new double[][]{{620, 0.18, 2, 0.45, -180, 0}, {930, 0.12, 2, 0.25, -250, 0.04}});
            put("boom",     new double[][]{{90, 0.25, 1, 0.40, -50, 0}});
            put("boss",     new double[][]{{80, 0.5, 1, 0.45, 40, 0}, {60, 0.5, 0, 0.35, 20, 0.1}});
            put("win",      new double[][]{{523, 0.2, 2, 0.40, 0, 0}, {659, 0.2, 2, 0.40, 0, 0.12},
                                           {784, 0.2, 2, 0.40, 0, 0.24}, {1047, 0.2, 2, 0.40, 0, 0.36},
                                           {1319, 0.3, 2, 0.40, 0, 0.48}});
            put("gameover", new double[][]{{400, 0.25, 1, 0.35, -30, 0}, {320, 0.25, 1, 0.35, -30, 0.18},
                                           {240, 0.25, 1, 0.35, -30, 0.36}, {160, 0.35, 1, 0.35, -30, 0.54}});
        } catch (Throwable t) {
            CLIPS.clear(); // audio non disponibile: si gioca in silenzio
        }
    }

    private static void put(String name, double[][] segs) throws Exception {
        byte[] pcm = synth(segs);
        Clip clip = AudioSystem.getClip();
        clip.open(new AudioFormat(SR, 16, 1, true, false), pcm, 0, pcm.length);
        CLIPS.put(name, clip);
    }

    private static byte[] synth(double[][] segs) {
        double total = 0;
        for (double[] s : segs) total = Math.max(total, s[5] + s[1] + 0.05);
        int n = (int) (total * SR);
        double[] buf = new double[n];
        for (double[] s : segs) {
            double freq = s[0], dur = s[1], vol = s[3], slide = s[4], delay = s[5];
            int type = (int) s[2];
            int start = (int) (delay * SR), len = (int) (dur * SR);
            double phase = 0;
            for (int i = 0; i < len && start + i < n; i++) {
                double tt = i / (double) len;
                double f = Math.max(30, freq + slide * tt);
                phase += f / SR;
                double p = phase - Math.floor(phase);
                double w = switch (type) {
                    case 1 -> 2 * p - 1;                       // dente di sega
                    case 2 -> p < 0.5 ? 4 * p - 1 : 3 - 4 * p; // triangolare
                    default -> p < 0.5 ? 1 : -1;               // quadra
                };
                double env = Math.pow(1 - tt, 1.8);
                buf[start + i] += w * vol * env;
            }
        }
        byte[] out = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int v = (int) (Util.clamp(buf[i], -1, 1) * 32767 * 0.55);
            out[i * 2] = (byte) v;
            out[i * 2 + 1] = (byte) (v >> 8);
        }
        return out;
    }

    static void play(String name) {
        if (muted || CLIPS.isEmpty()) return;
        if (name.equals("hit")) {
            long now = System.currentTimeMillis();
            if (now - lastHit < 70) return;
            lastHit = now;
        }
        Clip c = CLIPS.get(name);
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.start();
    }

    static boolean toggle() {
        muted = !muted;
        return muted;
    }
}
