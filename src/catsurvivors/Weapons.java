package catsurvivors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Le otto armi feline del gioco. */
final class Weapons {
    static final Map<String, WeaponDef> MAP = new LinkedHashMap<>();

    private Weapons() {}

    static {
        MAP.put("graffio", new WeaponDef("graffio", "Graffio Felino",
                "Zampata rapida davanti a sé. L'arma di chi non ha paura di niente.",
                6, new String[]{"Un graffio extra alle spalle", "Danno +8", "Area +25%", "Danno +10", "Un graffio extra"}) {
            WStats stats(int lv, Player p) {
                double dmg = 15 + (lv >= 3 ? 8 : 0) + (lv >= 5 ? 10 : 0);
                int amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 6 ? 1 : 0);
                double area = lv >= 4 ? 1.25 : 1.0;
                return fin(p, dmg, 1.1, amount, area, 0, 0, 0);
            }
            void fire(Game g, Player p, WStats st) {
                for (int i = 0; i < st.amount; i++) {
                    int side = (i % 2 == 0) ? 1 : -1;
                    double dx = p.faceX * side, dy = p.faceY * side;
                    Projectile pr = new Projectile("slash");
                    pr.x = p.x + dx * 48;
                    pr.y = p.y + dy * 48 - 6;
                    pr.ang = Math.atan2(dy, dx);
                    pr.r = 46 * st.area;
                    pr.life = pr.maxLife = 0.18;
                    pr.delay = i * 0.1;
                    pr.damage = st.damage;
                    pr.hit = new HashSet<>();
                    g.projectiles.add(pr);
                }
            }
        });

        MAP.put("gomitolo", new WeaponDef("gomitolo", "Gomitolo Rimbalzino",
                "Una palla di lana indistruttibile che rimbalza per tutto lo schermo.",
                6, new String[]{"+1 gomitolo", "Danno +6", "Durata +1.5s", "+1 gomitolo", "Danno +10, più veloce"}) {
            WStats stats(int lv, Player p) {
                double dmg = 10 + (lv >= 3 ? 6 : 0) + (lv >= 6 ? 10 : 0);
                int amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
                double speed = 250 + (lv >= 6 ? 50 : 0);
                double duration = 4 + (lv >= 4 ? 1.5 : 0);
                return fin(p, dmg, 3.0, amount, 1, speed, duration, 0);
            }
            void fire(Game g, Player p, WStats st) {
                for (int i = 0; i < st.amount; i++) {
                    double a = Util.rand(0, Util.TAU);
                    Projectile pr = new Projectile("ball");
                    pr.x = p.x;
                    pr.y = p.y;
                    pr.vx = Math.cos(a) * st.speed;
                    pr.vy = Math.sin(a) * st.speed;
                    pr.r = 10 * st.area;
                    pr.life = st.duration;
                    pr.damage = st.damage;
                    pr.hitCd = new HashMap<>();
                    g.projectiles.add(pr);
                }
                Sfx.play("shoot");
            }
        });

        MAP.put("pallapelo", new WeaponDef("pallapelo", "Palla di Pelo",
                "Il classico felino: lanciata in aria con nonchalance, atterra con devastazione.",
                6, new String[]{"+1 palla", "Danno +12", "Perforazione +3, area +20%", "+1 palla", "Danno +15"}) {
            WStats stats(int lv, Player p) {
                double dmg = 25 + (lv >= 3 ? 12 : 0) + (lv >= 6 ? 15 : 0);
                int amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
                int pierce = 4 + (lv >= 4 ? 3 : 0);
                double area = lv >= 4 ? 1.2 : 1.0;
                return fin(p, dmg, 2.4, amount, area, 0, 0, pierce);
            }
            void fire(Game g, Player p, WStats st) {
                for (int i = 0; i < st.amount; i++) {
                    Projectile pr = new Projectile("lob");
                    pr.x = p.x;
                    pr.y = p.y - 10;
                    pr.vx = Util.rand(-90, 90) + p.faceX * 60;
                    pr.vy = -Util.rand(380, 470);
                    pr.grav = 780;
                    pr.r = 11 * st.area;
                    pr.rot = Util.rand(0, Util.TAU);
                    pr.vr = Util.rand(-6, 6);
                    pr.life = 2.5;
                    pr.damage = st.damage;
                    pr.pierce = st.pierce;
                    pr.hit = new HashSet<>();
                    g.projectiles.add(pr);
                }
            }
        });

        MAP.put("fusa", new WeaponDef("fusa", "Aura di Fusa",
                "Le fusa rilassano i gatti e disintegrano i nemici nelle vicinanze.",
                6, new String[]{"Area +15%", "Danno +3", "Area +15%, fusa più rapide", "Danno +4", "Area +20%, fusa più rapide"}) {
            WStats stats(int lv, Player p) {
                double dmg = 5 + (lv >= 3 ? 3 : 0) + (lv >= 5 ? 4 : 0);
                double tick = 0.5 - (lv >= 4 ? 0.06 : 0) - (lv >= 6 ? 0.08 : 0);
                double area = (lv >= 2 ? 1.15 : 1.0) * (lv >= 4 ? 1.15 : 1.0) * (lv >= 6 ? 1.2 : 1.0);
                return fin(p, dmg, tick, 1, area, 0, 0, 0);
            }
            void fire(Game g, Player p, WStats st) {
                double r = 75 * st.area;
                for (Enemy e : g.enemies) {
                    if (e.dead) continue;
                    double rr = r + e.r;
                    if (Util.dist2(p.x, p.y, e.x, e.y) <= rr * rr) {
                        g.damageEnemy(e, st.damage, 40, p.x, p.y);
                    }
                }
                p.auraR = r;
                p.auraPulse = 1;
            }
        });

        MAP.put("miagolio", new WeaponDef("miagolio", "Miagolio Sonico",
                "Un MIAO carico di rancore che attraversa i nemici come un'onda d'urto.",
                6, new String[]{"Danno +8", "+1 onda", "Area +30%", "Danno +10", "+1 onda"}) {
            WStats stats(int lv, Player p) {
                double dmg = 15 + (lv >= 2 ? 8 : 0) + (lv >= 5 ? 10 : 0);
                int amount = 1 + (lv >= 3 ? 1 : 0) + (lv >= 6 ? 1 : 0);
                double area = lv >= 4 ? 1.3 : 1.0;
                return fin(p, dmg, 2.6, amount, area, 230, 1.6, 0);
            }
            void fire(Game g, Player p, WStats st) {
                List<Enemy> targets = g.nearestEnemies(p.x, p.y, st.amount);
                for (int i = 0; i < st.amount; i++) {
                    double ang = i < targets.size()
                            ? Util.angleTo(p.x, p.y, targets.get(i).x, targets.get(i).y)
                            : Util.rand(0, Util.TAU);
                    Projectile pr = new Projectile("wave");
                    pr.x = p.x;
                    pr.y = p.y;
                    pr.vx = Math.cos(ang) * st.speed;
                    pr.vy = Math.sin(ang) * st.speed;
                    pr.ang = ang;
                    pr.r = 16;
                    pr.grow = 55 * st.area;
                    pr.life = pr.maxLife = st.duration;
                    pr.damage = st.damage;
                    pr.hit = new HashSet<>();
                    g.projectiles.add(pr);
                }
                Sfx.play("meow");
            }
        });

        MAP.put("artigli", new WeaponDef("artigli", "Artigli Spettrali",
                "Artigli luminosi orbitano intorno al gatto, affilati come la sua lingua.",
                6, new String[]{"+1 artiglio", "Danno +5, rotazione più rapida", "Durata +1.2s", "+1 artiglio", "Danno +8, area +20%"}) {
            { addDuration = true; }
            WStats stats(int lv, Player p) {
                double dmg = 8 + (lv >= 3 ? 5 : 0) + (lv >= 6 ? 8 : 0);
                int amount = 2 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 1 : 0);
                double rot = 3.2 + (lv >= 3 ? 0.8 : 0);
                double dur = 3 + (lv >= 4 ? 1.2 : 0);
                double area = lv >= 6 ? 1.2 : 1.0;
                return fin(p, dmg, 2.0, amount, area, rot, dur, 0);
            }
            void fire(Game g, Player p, WStats st) {
                for (int i = 0; i < st.amount; i++) {
                    Projectile pr = new Projectile("orbit");
                    pr.ang = Util.TAU / st.amount * i;
                    pr.radius = 80 * st.area;
                    pr.rotSpeed = st.speed;
                    pr.life = st.duration;
                    pr.r = 12 * st.area; // anche l'artiglio cresce, non solo l'orbita
                    pr.damage = st.damage;
                    pr.hitCd = new HashMap<>();
                    pr.x = p.x;
                    pr.y = p.y;
                    g.projectiles.add(pr);
                }
            }
        });

        MAP.put("sardine", new WeaponDef("sardine", "Lancio di Sardine",
                "Sardine balistiche di precisione. Un po' puzzano, ma funzionano.",
                6, new String[]{"+1 sardina", "+1 sardina", "Danno +6", "+1 sardina", "Danno +8, perforazione +1"}) {
            WStats stats(int lv, Player p) {
                double dmg = 9 + (lv >= 4 ? 6 : 0) + (lv >= 6 ? 8 : 0);
                int amount = 1 + (lv >= 2 ? 1 : 0) + (lv >= 3 ? 1 : 0) + (lv >= 5 ? 1 : 0);
                int pierce = 1 + (lv >= 6 ? 1 : 0);
                return fin(p, dmg, 1.0, amount, 1, 430, 0, pierce);
            }
            void fire(Game g, Player p, WStats st) {
                double base = Math.atan2(p.faceY, p.faceX);
                for (int i = 0; i < st.amount; i++) {
                    double off = i - (st.amount - 1) / 2.0;
                    double ang = base + off * 0.08;
                    Projectile pr = new Projectile("knife");
                    pr.x = p.x - Math.sin(base) * off * 10;
                    pr.y = p.y + Math.cos(base) * off * 10;
                    pr.vx = Math.cos(ang) * st.speed;
                    pr.vy = Math.sin(ang) * st.speed;
                    pr.ang = ang;
                    pr.r = 8 * st.area;
                    pr.life = 1.4;
                    pr.damage = st.damage;
                    pr.pierce = st.pierce;
                    pr.hit = new HashSet<>();
                    g.projectiles.add(pr);
                }
                Sfx.play("shoot");
            }
        });

        MAP.put("croccantini", new WeaponDef("croccantini", "Pioggia di Croccantini",
                "Croccantini esplosivi piovono dal cielo. La pappa è servita.",
                6, new String[]{"+1 croccantino", "Danno +10", "Area +25%", "+2 croccantini", "Danno +15"}) {
            WStats stats(int lv, Player p) {
                double dmg = 20 + (lv >= 3 ? 10 : 0) + (lv >= 6 ? 15 : 0);
                int amount = 2 + (lv >= 2 ? 1 : 0) + (lv >= 5 ? 2 : 0);
                double area = lv >= 4 ? 1.25 : 1.0;
                return fin(p, dmg, 3.2, amount, area, 0, 0, 0);
            }
            void fire(Game g, Player p, WStats st) {
                for (int i = 0; i < st.amount; i++) {
                    double tx, ty;
                    Enemy e = g.enemies.isEmpty() ? null : Util.choice(g.enemies);
                    if (e != null && !e.dead && Util.dist2(p.x, p.y, e.x, e.y) < 500 * 500) {
                        tx = e.x + Util.rand(-30, 30);
                        ty = e.y + Util.rand(-30, 30);
                    } else {
                        tx = p.x + Util.rand(-260, 260);
                        ty = p.y + Util.rand(-200, 200);
                    }
                    Projectile pr = new Projectile("fall");
                    pr.x = tx;
                    pr.y = ty - 320;
                    pr.ty = ty;
                    pr.vy = 560;
                    pr.r = 7;
                    pr.damage = st.damage;
                    pr.area = 60 * st.area;
                    pr.life = 2;
                    pr.delay = i * 0.12;
                    g.projectiles.add(pr);
                }
            }
        });
    }
}
