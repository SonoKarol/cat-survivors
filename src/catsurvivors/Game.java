package catsurvivors;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stato di gioco, simulazione e rendering del mondo. In co-op gira solo sull'host. */
final class Game {
    enum State { MENU, LOBBY, PLAYING, LEVELUP, PAUSED, OVER, WIN }

    private static final Color GRASS_A = new Color(0x7aa65a);
    private static final Color GRASS_B = new Color(0x74a055);
    private static final Color GRASS_DARK = new Color(0x5d8a45);
    private static final Color DIRT = new Color(155, 139, 94, 90);
    private static final Font F_FLOAT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font F_NAME = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    // i rimbalzi del gomitolo usano un'area fissa intorno al proprietario,
    // così host e client vedono la stessa fisica a prescindere dalla finestra
    private static final double VIRT_HALF_W = 640, VIRT_HALF_H = 360;

    final Input input;
    volatile State state = State.MENU;
    double time = 0;
    int kills = 0;
    final List<Player> players = new ArrayList<>();
    Server server; // non null quando si ospita una partita co-op
    Player leveling; // chi sta scegliendo il potenziamento
    private int nextPid = 1;
    long frame = 0;
    final List<Enemy> enemies = new ArrayList<>();
    final List<Projectile> projectiles = new ArrayList<>();
    final List<Gem> gems = new ArrayList<>();
    final List<Pickup> pickups = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();
    final List<FloatText> floats = new ArrayList<>();
    double spawnAcc = 0, eliteTimer = 60, shake = 0;
    int bossIdx = 0, ringIdx = 0;
    List<Choice> choices = null;
    int viewW = 1280, viewH = 720;

    Game(Input input) { this.input = input; }

    Player localPlayer() { return players.isEmpty() ? null : players.get(0); }

    boolean isCoop() { return server != null; }

    private void resetWorld() {
        time = 0;
        kills = 0;
        enemies.clear();
        projectiles.clear();
        gems.clear();
        pickups.clear();
        particles.clear();
        floats.clear();
        spawnAcc = 0;
        eliteTimer = 60;
        shake = 0;
        bossIdx = 0;
        ringIdx = 0;
        choices = null;
        leveling = null;
        frame = 0;
    }

    void reset() {
        resetWorld();
        players.clear();
        nextPid = 1;
    }

    /** Partita in solitaria. */
    void startRun(CatDef cat) {
        reset();
        Player p = new Player(cat, this);
        p.pid = 0;
        players.add(p);
        state = State.PLAYING;
        Sfx.play("meow");
    }

    /** Apre la lobby co-op: l'host è il giocatore 0, gli amici si collegano via TCP. */
    void startHosting(CatDef cat, int port) throws IOException {
        reset();
        Player p = new Player(cat, this);
        p.pid = 0;
        players.add(p);
        server = new Server(this, port);
        state = State.LOBBY;
        Sfx.play("meow");
    }

    /** Avvia la partita dalla lobby con tutti i giocatori collegati. */
    void startRunMulti() {
        resetWorld();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            double a = Util.TAU * i / players.size();
            p.x = Math.cos(a) * 40;
            p.y = Math.sin(a) * 40;
        }
        state = State.PLAYING;
        Sfx.play("meow");
    }

    /** Registra un amico appena connesso (chiamato dal Server sul thread di gioco). */
    Player addRemotePlayer(CatDef cat) {
        Player p = new Player(cat, this);
        p.pid = nextPid++;
        double a = Util.rand(0, Util.TAU);
        p.x = Math.cos(a) * 50;
        p.y = Math.sin(a) * 50;
        players.add(p);
        Sfx.play("meow");
        return p;
    }

    void removePlayer(Player p) {
        if (!players.remove(p)) return;
        projectiles.removeIf(pr -> pr.owner == p);
        if (state != State.LOBBY) addFloat(p.x, p.y - 30, p.cat.name + " se n'è andato", new Color(0xffd166));
        if (leveling == p) { // non bloccare la partita su una scelta orfana
            leveling = null;
            choices = null;
            if (state == State.LEVELUP) state = State.PLAYING;
            Player nxt = nextLeveler();
            if (nxt != null) openLevelUp(nxt);
        }
    }

    void toMenu() {
        if (server != null) {
            server.close();
            server = null;
        }
        reset();
        state = State.MENU;
    }

    /** Un passo: input, messaggi di rete, simulazione, broadcast. */
    void step(double dt, int w, int h) {
        viewW = w;
        viewH = h;
        frame++;
        if (input.focusLost) {
            input.focusLost = false;
            // in solitaria mettiti in pausa; in co-op non bloccare gli amici
            if (state == State.PLAYING && !isCoop()) state = State.PAUSED;
        }
        Integer k;
        while ((k = input.nextPress()) != null) {
            if (App.ipActive()) App.ipKey(k);
            else handleKey(k);
        }
        Ui.handleInput(this);
        if (server != null) server.pump();
        if (state == State.PLAYING) update(dt);
        if (server != null && frame % 3 == 0) server.broadcast();
    }

    private void handleKey(int code) {
        switch (code) {
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> {
                if (state == State.PLAYING) state = State.PAUSED;
                else if (state == State.PAUSED) state = State.PLAYING;
                else if (state == State.LOBBY && code == KeyEvent.VK_ESCAPE) toMenu();
            }
            case KeyEvent.VK_M -> Sfx.toggle();
            case KeyEvent.VK_R -> {
                if (state == State.OVER || state == State.WIN) toMenu();
            }
            case KeyEvent.VK_ENTER -> {
                if (state == State.LOBBY) startRunMulti();
            }
            default -> {
                if (state == State.LEVELUP && choices != null && leveling == localPlayer()) {
                    int idx = switch (code) {
                        case KeyEvent.VK_1 -> 0;
                        case KeyEvent.VK_2 -> 1;
                        case KeyEvent.VK_3 -> 2;
                        case KeyEvent.VK_4 -> 3;
                        default -> -1;
                    };
                    if (idx >= 0 && idx < choices.size()) pickChoice(choices.get(idx));
                }
            }
        }
    }

    /** Movimento WASD + mira verso il cursore per il giocatore locale. */
    private void applyLocalInput() {
        Player p = localPlayer();
        if (p == null) return;
        double[] a = input.axis();
        p.inMoveX = a[0];
        p.inMoveY = a[1];
        if (input.mouseX >= 0) {
            // la camera è centrata sul gatto: il vettore centro->cursore è la mira
            double ax = input.mouseX - viewW / 2.0, ay = input.mouseY - viewH / 2.0;
            if (Math.hypot(ax, ay) > 12) {
                p.inAimX = ax;
                p.inAimY = ay;
            }
        }
    }

    private void update(double dt) {
        time += dt;
        if (time >= Enemies.DURATION) {
            win();
            return;
        }
        applyLocalInput();
        for (Player p : players) p.update(dt);
        updateSpawns(dt);
        for (Enemy e : enemies) {
            Player t = nearestAlivePlayer(e.x, e.y);
            if (t != null) e.update(dt, t.x, t.y);
        }
        separate();
        updateProjectiles(dt);
        updateGems(dt);
        updatePickups(dt);
        updateFx(dt);
        // danno da contatto
        for (Enemy e : enemies) {
            if (e.dead) continue;
            for (Player p : players) {
                if (!p.alive) continue;
                double rr = e.r + p.r - 4;
                if (Util.dist2(e.x, e.y, p.x, p.y) < rr * rr) p.takeDamage(e.damage);
            }
        }
        // i nemici rimasti troppo indietro vengono riposizionati sul bordo (stile survivor)
        double far = Math.max(viewW, viewH) * 0.9 + 120;
        for (Enemy e : enemies) {
            if (e.boss) continue;
            Player near = nearestAlivePlayer(e.x, e.y);
            if (near != null && Util.dist2(e.x, e.y, near.x, near.y) > far * far) {
                double a = Util.rand(0, Util.TAU);
                e.x = near.x + Math.cos(a) * spawnRadius();
                e.y = near.y + Math.sin(a) * spawnRadius();
            }
        }
        enemies.removeIf(e -> e.dead);
        if (shake > 0) shake = Math.max(0, shake - 20 * dt);
        if (state == State.PLAYING) {
            Player lp = nextLeveler();
            if (lp != null) openLevelUp(lp);
        }
    }

    Player nearestAlivePlayer(double x, double y) {
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Player p : players) {
            if (!p.alive) continue;
            double d2 = Util.dist2(x, y, p.x, p.y);
            if (d2 < bestD2) { bestD2 = d2; best = p; }
        }
        return best;
    }

    /** Un giocatore al tappeto diventa un fantasma; si perde solo se cadono tutti. */
    void onPlayerDown(Player p) {
        p.alive = false;
        p.pendingLevels = 0;
        addFloat(p.x, p.y - 30, p.cat.name + " è KO!", new Color(0xff6b6b));
        addParticles(p.x, p.y, new Color(0xb9c4d8), 10);
        for (Player q : players) if (q.alive) return;
        gameOver();
    }

    private double spawnRadius() { return Math.max(viewW, viewH) / 2.0 + 80; }

    /** I nemici nascono intorno a un giocatore vivo a caso: azione per tutti. */
    private Player spawnAnchor() {
        List<Player> alive = new ArrayList<>();
        for (Player p : players) if (p.alive) alive.add(p);
        return alive.isEmpty() ? localPlayer() : Util.choice(alive);
    }

    private void updateSpawns(double dt) {
        Wave wave = Enemies.WAVES.get(0);
        for (Wave w : Enemies.WAVES) if (time >= w.t) wave = w;
        double mult = 1 + (players.size() - 1) * 0.6; // più gatti, più cetrioli
        spawnAcc += wave.rate * mult * dt;
        int max = (int) (wave.max * mult);
        while (spawnAcc >= 1) {
            spawnAcc -= 1;
            if (enemies.size() < max) spawnEnemy(Util.choice(wave.types), false);
        }
        eliteTimer -= dt;
        if (eliteTimer <= 0) {
            eliteTimer = 60;
            spawnEnemy(Util.choice(wave.types), true);
        }
        if (bossIdx < Enemies.BOSSES.size() && time >= Enemies.BOSSES.get(bossIdx).t) {
            BossSpawn bs = Enemies.BOSSES.get(bossIdx);
            bossIdx++;
            Enemy b = spawnEnemy(bs.type, false);
            Player lp = localPlayer();
            if (lp != null) addFloat(lp.x, lp.y - 60, "ATTENZIONE: " + b.def.name + "!", new Color(0xff5b5b));
            Sfx.play("boss");
            shake = 8;
        }
        if (ringIdx < Enemies.RING_TIMES.length && time >= Enemies.RING_TIMES[ringIdx]) {
            ringIdx++;
            Player anchor = spawnAnchor();
            if (anchor != null) {
                int n = 26;
                for (int i = 0; i < n; i++) {
                    double a = Util.TAU / n * i;
                    spawnEnemyAt("cetriolo", anchor.x + Math.cos(a) * 380, anchor.y + Math.sin(a) * 380, false);
                }
                addFloat(anchor.x, anchor.y - 60, "Accerchiato dai cetrioli!", new Color(0x9ee86a));
            }
        }
    }

    private double hpScale() {
        return (1 + time / 60.0 * 0.16) * (1 + (players.size() - 1) * 0.25);
    }

    Enemy spawnEnemy(String type, boolean elite) {
        Player anchor = spawnAnchor();
        double ax = anchor != null ? anchor.x : 0, ay = anchor != null ? anchor.y : 0;
        double a = Util.rand(0, Util.TAU);
        return spawnEnemyAt(type, ax + Math.cos(a) * spawnRadius(), ay + Math.sin(a) * spawnRadius(), elite);
    }

    Enemy spawnEnemyAt(String type, double x, double y, boolean elite) {
        Enemy e = new Enemy(type, x, y, hpScale(), elite);
        enemies.add(e);
        return e;
    }

    /** Separazione morbida tra nemici vicini, su griglia per non esplodere in O(n²). */
    private void separate() {
        Map<Long, List<Enemy>> grid = new HashMap<>();
        for (Enemy e : enemies) {
            long key = (((long) Math.floor(e.x / 64)) << 32) ^ (((long) Math.floor(e.y / 64)) & 0xffffffffL);
            grid.computeIfAbsent(key, q -> new ArrayList<>()).add(e);
        }
        for (List<Enemy> cell : grid.values()) {
            int n = Math.min(cell.size(), 10);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Enemy a = cell.get(i), b = cell.get(j);
                    double dx = b.x - a.x, dy = b.y - a.y;
                    double d2 = dx * dx + dy * dy;
                    double min = (a.r + b.r) * 0.8;
                    if (d2 > 0.01 && d2 < min * min) {
                        double d = Math.sqrt(d2);
                        double push = (min - d) / d * 0.5;
                        a.x -= dx * push;
                        a.y -= dy * push;
                        b.x += dx * push;
                        b.y += dy * push;
                    }
                }
            }
        }
    }

    private void updateProjectiles(double dt) {
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile pr = projectiles.get(i);
            if (pr.delay > 0) {
                pr.delay -= dt;
                continue;
            }
            Player own = pr.owner != null ? pr.owner : localPlayer();
            switch (pr.type) {
                case "slash" -> {
                    pr.life -= dt;
                    if (pr.life <= 0) { pr.dead = true; break; }
                    for (Enemy e : enemies) {
                        if (e.dead || pr.hit.contains(e.id)) continue;
                        double rr = pr.r + e.r;
                        if (Util.dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
                            pr.hit.add(e.id);
                            damageEnemy(e, pr.damage, 140, own.x, own.y, own);
                        }
                    }
                }
                case "ball" -> {
                    pr.life -= dt;
                    if (pr.life <= 0) { pr.dead = true; break; }
                    pr.x += pr.vx * dt;
                    pr.y += pr.vy * dt;
                    double left = own.x - VIRT_HALF_W + pr.r, right = own.x + VIRT_HALF_W - pr.r;
                    double top = own.y - VIRT_HALF_H + pr.r, bot = own.y + VIRT_HALF_H - pr.r;
                    if (pr.x < left) { pr.x = left; pr.vx = Math.abs(pr.vx); }
                    if (pr.x > right) { pr.x = right; pr.vx = -Math.abs(pr.vx); }
                    if (pr.y < top) { pr.y = top; pr.vy = Math.abs(pr.vy); }
                    if (pr.y > bot) { pr.y = bot; pr.vy = -Math.abs(pr.vy); }
                    hitTick(pr, 0.6, 100);
                }
                case "lob" -> {
                    pr.life -= dt;
                    pr.x += pr.vx * dt;
                    pr.vy += pr.grav * dt;
                    pr.y += pr.vy * dt;
                    pr.rot += pr.vr * dt;
                    if (pr.life <= 0 || pr.y > own.y + VIRT_HALF_H + 80) { pr.dead = true; break; }
                    for (Enemy e : enemies) {
                        if (e.dead || pr.hit.contains(e.id)) continue;
                        double rr = pr.r + e.r;
                        if (Util.dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
                            pr.hit.add(e.id);
                            damageEnemy(e, pr.damage, 120, pr.x, pr.y - 20, own);
                            if (--pr.pierce < 0) { pr.dead = true; break; }
                        }
                    }
                }
                case "wave" -> {
                    pr.life -= dt;
                    if (pr.life <= 0) { pr.dead = true; break; }
                    pr.x += pr.vx * dt;
                    pr.y += pr.vy * dt;
                    pr.r += pr.grow * dt;
                    for (Enemy e : enemies) {
                        if (e.dead || pr.hit.contains(e.id)) continue;
                        double rr = pr.r + e.r;
                        if (Util.dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
                            pr.hit.add(e.id);
                            damageEnemy(e, pr.damage, 60, pr.x, pr.y, own);
                        }
                    }
                }
                case "orbit" -> {
                    pr.life -= dt;
                    if (pr.life <= 0) { pr.dead = true; break; }
                    pr.ang += pr.rotSpeed * dt;
                    pr.x = own.x + Math.cos(pr.ang) * pr.radius;
                    pr.y = own.y + Math.sin(pr.ang) * pr.radius;
                    hitTick(pr, 0.5, 90);
                }
                case "knife" -> {
                    pr.life -= dt;
                    if (pr.life <= 0) { pr.dead = true; break; }
                    pr.x += pr.vx * dt;
                    pr.y += pr.vy * dt;
                    for (Enemy e : enemies) {
                        if (e.dead || pr.hit.contains(e.id)) continue;
                        double rr = pr.r + e.r;
                        if (Util.dist2(pr.x, pr.y, e.x, e.y) < rr * rr) {
                            pr.hit.add(e.id);
                            damageEnemy(e, pr.damage, 60, pr.x - pr.vx * 0.01, pr.y - pr.vy * 0.01, own);
                            if (--pr.pierce < 0) { pr.dead = true; break; }
                        }
                    }
                }
                case "fall" -> {
                    pr.y += pr.vy * dt;
                    if (pr.y >= pr.ty) {
                        pr.dead = true;
                        Projectile boom = new Projectile("boom");
                        boom.owner = own;
                        boom.x = pr.x;
                        boom.y = pr.ty;
                        boom.r = pr.area;
                        boom.life = boom.maxLife = 0.25;
                        projectiles.add(boom);
                        for (Enemy e : enemies) {
                            if (e.dead) continue;
                            double rr = boom.r + e.r;
                            if (Util.dist2(boom.x, boom.y, e.x, e.y) < rr * rr) {
                                damageEnemy(e, pr.damage, 160, boom.x, boom.y, own);
                            }
                        }
                        addParticles(boom.x, boom.y, new Color(0xf2b04a), 8);
                        Sfx.play("boom");
                        shake = Math.min(8, shake + 2);
                    }
                }
                case "boom" -> { // solo effetto visivo
                    pr.life -= dt;
                    if (pr.life <= 0) pr.dead = true;
                }
            }
        }
        projectiles.removeIf(q -> q.dead);
    }

    /** Colpisce i nemici a contatto, ricolpibili dopo un intervallo (armi persistenti). */
    private void hitTick(Projectile pr, double interval, double kb) {
        for (Enemy e : enemies) {
            if (e.dead) continue;
            double rr = pr.r + e.r;
            if (Util.dist2(pr.x, pr.y, e.x, e.y) >= rr * rr) continue;
            Double next = pr.hitCd.get(e.id);
            if (next != null && time < next) continue;
            pr.hitCd.put(e.id, time + interval);
            damageEnemy(e, pr.damage, kb, pr.x, pr.y, pr.owner);
        }
    }

    void damageEnemy(Enemy e, double dmg, double kb, double fromX, double fromY, Player src) {
        if (e.dead) return;
        e.hp -= dmg;
        e.flash = 0.12;
        if (kb > 0 && !e.boss) {
            double a = Math.atan2(e.y - fromY, e.x - fromX);
            e.kbx += Math.cos(a) * kb;
            e.kby += Math.sin(a) * kb;
        }
        if (floats.size() < 70) addFloat(e.x, e.y - e.r - 6, String.valueOf((int) Math.max(1, dmg)), Color.WHITE);
        Sfx.play("hit");
        if (e.hp <= 0) killEnemy(e, src);
    }

    private void killEnemy(Enemy e, Player src) {
        if (e.dead) return;
        e.dead = true;
        kills++;
        double luck = src != null ? src.stats.luck : 1;
        // la fortuna raddoppia le gemme e aumenta i drop
        int gemValue = e.xp;
        if (Util.RNG.nextDouble() < Math.min(0.5, (luck - 1) * 0.5)) gemValue *= 2;
        gems.add(new Gem(e.x + Util.rand(-6, 6), e.y + Util.rand(-6, 6), gemValue));
        if (e.boss) {
            pickups.add(new Pickup("croccantino", e.x, e.y));
            for (int i = 0; i < 8; i++) {
                gems.add(new Gem(e.x + Util.rand(-40, 40), e.y + Util.rand(-40, 40), 10));
            }
            shake = 10;
            Sfx.play("boom");
        } else if (e.elite) {
            pickups.add(new Pickup(Util.RNG.nextDouble() < 0.5 ? "croccantino" : "magnete", e.x, e.y));
        } else {
            double roll = Util.RNG.nextDouble();
            if (roll < 0.012 * luck) pickups.add(new Pickup("croccantino", e.x, e.y));
            else if (roll < 0.016 * luck) pickups.add(new Pickup("magnete", e.x, e.y));
        }
        addParticles(e.x, e.y, new Color(0xcfd8cf), 6);
    }

    private void updateGems(double dt) {
        for (Gem g : gems) {
            Player best = nearestAlivePlayer(g.x, g.y);
            if (best == null) continue;
            double d2 = Util.dist2(g.x, g.y, best.x, best.y);
            double mr = best.stats.magnet;
            if (g.vacuum || d2 < mr * mr) {
                g.sp = Math.min(640, g.sp + 900 * dt);
                double d = Math.sqrt(d2);
                if (d > 1) {
                    g.x += (best.x - g.x) / d * g.sp * dt;
                    g.y += (best.y - g.y) / d * g.sp * dt;
                }
            }
            if (d2 < 20 * 20) {
                g.dead = true;
                best.gainXp(g.value);
                Sfx.play("pickup");
            }
        }
        gems.removeIf(g -> g.dead);
        // troppe gemme in giro: fondi le più vecchie in una sola
        if (gems.size() > 400) {
            int merge = 80, total = 0;
            double mx = 0, my = 0;
            for (int i = 0; i < merge; i++) {
                Gem g = gems.get(i);
                total += g.value;
                mx += g.x;
                my += g.y;
            }
            gems.subList(0, merge).clear();
            gems.add(new Gem(mx / merge, my / merge, total));
        }
    }

    private void updatePickups(double dt) {
        for (Pickup pk : pickups) {
            pk.bob += dt * 4;
            Player p = nearestAlivePlayer(pk.x, pk.y);
            if (p == null) continue;
            if (Util.dist2(pk.x, pk.y, p.x, p.y) < 26 * 26) {
                pk.dead = true;
                if (pk.kind.equals("croccantino")) {
                    double heal = 30 * p.cat.foodBonus;
                    p.hp = Math.min(p.stats.maxHp, p.hp + heal);
                    addFloat(p.x, p.y - 28, "+" + (int) heal, new Color(0x7de87d));
                    Sfx.play("food");
                } else {
                    for (Gem g : gems) g.vacuum = true;
                    Sfx.play("pickup");
                }
            }
        }
        pickups.removeIf(pk -> pk.dead);
    }

    private void updateFx(double dt) {
        double decay = Math.exp(-4 * dt);
        for (Particle pt : particles) {
            pt.x += pt.vx * dt;
            pt.y += pt.vy * dt;
            pt.vx *= decay;
            pt.vy *= decay;
            pt.life -= dt;
        }
        particles.removeIf(pt -> pt.life <= 0);
        for (FloatText f : floats) {
            f.y -= 30 * dt;
            f.life -= dt;
        }
        floats.removeIf(f -> f.life <= 0);
    }

    void addFloat(double x, double y, String text, Color color) {
        if (floats.size() > 80) floats.remove(0);
        floats.add(new FloatText(x, y, text, color));
    }

    void addParticles(double x, double y, Color color, int n) {
        if (particles.size() > 220) return;
        for (int i = 0; i < n; i++) {
            double a = Util.rand(0, Util.TAU), sp = Util.rand(30, 120);
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    Util.rand(0.3, 0.6), Util.rand(2, 4.5), color));
        }
    }

    List<Enemy> nearestEnemies(double x, double y, int n) {
        List<Enemy> sorted = new ArrayList<>(enemies);
        sorted.removeIf(e -> e.dead);
        sorted.sort((a, b) -> Double.compare(Util.dist2(x, y, a.x, a.y), Util.dist2(x, y, b.x, b.y)));
        return sorted.size() > n ? sorted.subList(0, n) : sorted;
    }

    // ===== Level up =====

    private Player nextLeveler() {
        for (Player p : players) {
            if (p.alive && p.pendingLevels > 0) return p;
        }
        return null;
    }

    private void openLevelUp(Player p) {
        state = State.LEVELUP;
        leveling = p;
        choices = rollChoices(p);
        if (p.pid != 0 && server != null) server.sendLevelUp(p, choices);
        Sfx.play("levelup");
    }

    private List<Choice> rollChoices(Player p) {
        List<Choice> pool = new ArrayList<>();
        for (Player.WeaponInst w : p.weapons) {
            if (w.level < w.def.maxLevel) {
                pool.add(new Choice("weapon", w.def.id, w.def.name, w.def.upgrades[w.level - 1],
                        "Liv. " + (w.level + 1), w.def.id));
            }
        }
        for (Map.Entry<String, Integer> en : p.passives.entrySet()) {
            PassiveDef d = Passives.MAP.get(en.getKey());
            if (en.getValue() < d.maxLevel) {
                pool.add(new Choice("passive", d.id, d.name, d.desc, "Liv. " + (en.getValue() + 1), d.id));
            }
        }
        if (p.weapons.size() < Player.MAX_WEAPONS) {
            for (WeaponDef d : Weapons.MAP.values()) {
                if (p.getWeapon(d.id) == null) pool.add(new Choice("weapon", d.id, d.name, d.desc, "Nuova arma!", d.id));
            }
        }
        if (p.passives.size() < Player.MAX_PASSIVES) {
            for (PassiveDef d : Passives.MAP.values()) {
                if (!p.passives.containsKey(d.id)) pool.add(new Choice("passive", d.id, d.name, d.desc, "Nuovo!", d.id));
            }
        }
        Collections.shuffle(pool, Util.RNG);
        int n = p.stats.luck >= 1.25 ? 4 : 3;
        List<Choice> picks = new ArrayList<>(pool.subList(0, Math.min(n, pool.size())));
        if (picks.isEmpty()) {
            picks.add(new Choice("heal", "", "Coccole", "Recupera 50 PS", "", "heal"));
            picks.add(new Choice("xp", "", "Croccantino d'Oro", "+30 esperienza", "", "xp"));
        }
        return picks;
    }

    void pickChoice(Choice c) {
        Player p = leveling != null ? leveling : localPlayer();
        if (p == null) return;
        switch (c.kind) {
            case "weapon" -> {
                Player.WeaponInst w = p.getWeapon(c.id);
                if (w != null) w.level++;
                else p.addWeapon(c.id);
            }
            case "passive" -> p.addPassive(c.id);
            case "heal" -> p.hp = Math.min(p.stats.maxHp, p.hp + 50);
            case "xp" -> p.gainXp(30);
        }
        Sfx.play("pickup");
        p.pendingLevels--;
        if (p.pendingLevels > 0) {
            choices = rollChoices(p);
            if (p.pid != 0 && server != null) server.sendLevelUp(p, choices);
        } else {
            leveling = null;
            choices = null;
            Player nxt = nextLeveler();
            if (nxt != null) openLevelUp(nxt);
            else state = State.PLAYING;
        }
    }

    void gameOver() {
        state = State.OVER;
        Sfx.play("gameover");
    }

    void win() {
        state = State.WIN;
        Sfx.play("win");
    }

    // ===== Rendering del mondo =====

    void render(Graphics2D g, int w, int h) {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        Player p = localPlayer();
        double camX = p != null ? p.x - w / 2.0 : -w / 2.0;
        double camY = p != null ? p.y - h / 2.0 : -h / 2.0;
        double sx = 0, sy = 0;
        if (shake > 0 && state == State.PLAYING) {
            sx = Util.rand(-shake, shake);
            sy = Util.rand(-shake, shake);
        }
        AffineTransform old = g.getTransform();
        g.translate(-camX + sx, -camY + sy);
        drawBackground(g, camX, camY, w, h);
        if (p != null && state != State.MENU) drawWorld(g);
        g.setTransform(old);
    }

    static void drawBackground(Graphics2D g, double camX, double camY, int w, int h) {
        int ts = 64;
        int x0 = (int) Math.floor(camX / ts), x1 = (int) Math.floor((camX + w) / ts);
        int y0 = (int) Math.floor(camY / ts), y1 = (int) Math.floor((camY + h) / ts);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                g.setColor(((tx + ty) & 1) == 0 ? GRASS_A : GRASS_B);
                g.fillRect(tx * ts, ty * ts, ts, ts);
                double hsh = Util.hash2(tx, ty);
                double ox = tx * ts + 8 + Util.hash2(tx * 3 + 1, ty * 5 + 2) * (ts - 16);
                double oy = ty * ts + 8 + Util.hash2(tx * 7 + 3, ty * 11 + 5) * (ts - 16);
                if (hsh < 0.05) { // fiorellino
                    g.setColor(Color.WHITE);
                    for (int i = 0; i < 4; i++) {
                        double a = Util.TAU / 4 * i + 0.6;
                        g.fill(new Ellipse2D.Double(ox + Math.cos(a) * 3 - 2, oy + Math.sin(a) * 3 - 2, 4, 4));
                    }
                    g.setColor(new Color(0xf2c94c));
                    g.fill(new Ellipse2D.Double(ox - 2, oy - 2, 4, 4));
                } else if (hsh < 0.12) { // ciuffo d'erba
                    g.setColor(GRASS_DARK);
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new QuadCurve2D.Double(ox, oy + 5, ox - 2, oy, ox - 4, oy - 4));
                    g.draw(new QuadCurve2D.Double(ox, oy + 5, ox, oy - 1, ox + 1, oy - 5));
                    g.draw(new QuadCurve2D.Double(ox, oy + 5, ox + 3, oy, ox + 5, oy - 3));
                } else if (hsh < 0.15) { // zolla di terra
                    g.setColor(DIRT);
                    g.fill(new Ellipse2D.Double(ox - 7, oy - 4, 14, 8));
                }
            }
        }
    }

    private void drawWorld(Graphics2D g) {
        // aure di fusa
        for (Player p : players) {
            if (p.alive && p.auraR > 0 && p.getWeapon("fusa") != null) {
                float alpha = (float) (0.10 + 0.08 * p.auraPulse);
                g.setColor(new Color(1f, 0.75f, 0.85f, alpha));
                g.fill(new Ellipse2D.Double(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2));
                g.setColor(new Color(1f, 0.75f, 0.85f, 0.30f));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Ellipse2D.Double(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2));
            }
        }
        // gemme
        for (Gem gm : gems) blit(g, Sprites.gem(gm.value), gm.x, gm.y, false, 1, 0);
        // raccoglibili
        for (Pickup pk : pickups) {
            shadow(g, pk.x, pk.y + 10, 9);
            blit(g, Sprites.pickup(pk.kind), pk.x, pk.y + Math.sin(pk.bob) * 3, false, 1, 0);
        }
        // esplosioni e bersagli dei croccantini (sotto i nemici)
        for (Projectile pr : projectiles) {
            if (pr.type.equals("boom")) {
                float a = (float) Util.clamp(pr.life / pr.maxLife, 0, 1);
                g.setColor(new Color(1f, 0.69f, 0.29f, a * 0.32f));
                g.fill(new Ellipse2D.Double(pr.x - pr.r, pr.y - pr.r, pr.r * 2, pr.r * 2));
                g.setColor(new Color(1f, 0.82f, 0.4f, a * 0.8f));
                g.setStroke(new BasicStroke(3f));
                double rr = pr.r * (1.1 - 0.3 * a);
                g.draw(new Ellipse2D.Double(pr.x - rr, pr.y - rr, rr * 2, rr * 2));
            } else if (pr.type.equals("fall") && pr.delay <= 0) {
                g.setColor(new Color(20, 30, 15, 60));
                g.fill(new Ellipse2D.Double(pr.x - 12, pr.ty - 6, 24, 12));
            }
        }
        // nemici
        Player lp = localPlayer();
        for (Enemy e : enemies) {
            if (e.dead) continue;
            shadow(g, e.x, e.y + e.r * 0.85, e.r * 0.9);
            BufferedImage img = Sprites.enemy(e.type);
            double scale = e.elite ? 1.5 : 1.0;
            boolean flip = lp != null && lp.x < e.x;
            if (e.elite) {
                g.setColor(new Color(255, 209, 102, 120));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Ellipse2D.Double(e.x - e.r - 4, e.y - e.r - 4, (e.r + 4) * 2, (e.r + 4) * 2));
            }
            blit(g, img, e.x, e.y + Math.sin(e.wob) * 1.5, flip, scale, 0);
            if (e.flash > 0) {
                float a = (float) Util.clamp(e.flash / 0.12 * 0.7, 0, 1);
                g.setColor(new Color(1f, 1f, 1f, a));
                g.fill(new Ellipse2D.Double(e.x - e.r, e.y - e.r, e.r * 2, e.r * 2));
            }
            if (e.boss) {
                double bw = 60, ratio = Util.clamp(e.hp / e.maxHp, 0, 1);
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect((int) (e.x - bw / 2), (int) (e.y - e.r - 16), (int) bw, 6);
                g.setColor(new Color(0xd64545));
                g.fillRect((int) (e.x - bw / 2), (int) (e.y - e.r - 16), (int) (bw * ratio), 6);
            }
        }
        // gatti
        for (Player p : players) {
            Composite oldComp = g.getComposite();
            if (!p.alive) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            else shadow(g, p.x, p.y + 14, 13);
            boolean blink = p.alive && p.iframe > 0 && ((int) (p.iframe * 18)) % 2 == 0;
            if (!blink) {
                double bob = p.moving ? Math.sin(p.walkT) * 2 : 0;
                blit(g, Sprites.cat(p.cat), p.x, p.y - 6 + bob, p.faceX < 0, 1.15, 0);
            }
            g.setComposite(oldComp);
            if (p.alive) {
                double ratio = Util.clamp(p.hp / p.stats.maxHp, 0, 1);
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect((int) p.x - 16, (int) p.y + 18, 32, 5);
                g.setColor(ratio > 0.4 ? new Color(0x7de87d) : new Color(0xe85d5d));
                g.fillRect((int) p.x - 16, (int) p.y + 18, (int) (32 * ratio), 5);
            }
            if (p != lp) { // il nome degli amici sopra la testa
                g.setFont(F_NAME);
                g.setColor(new Color(255, 255, 255, 220));
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.drawString(p.cat.name, (float) (p.x - fm.stringWidth(p.cat.name) / 2.0), (float) (p.y - 34));
            }
        }
        // proiettili
        for (Projectile pr : projectiles) {
            if (pr.delay > 0) continue;
            switch (pr.type) {
                case "slash" -> {
                    float a = (float) Util.clamp(pr.life / pr.maxLife, 0, 1);
                    AffineTransform t = g.getTransform();
                    g.translate(pr.x, pr.y);
                    g.rotate(pr.ang);
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(1f, 1f, 1f, a * 0.9f));
                    double rr = pr.r;
                    for (int k = -1; k <= 1; k++) {
                        g.draw(new Arc2D.Double(-rr * 0.5 + k * 7, -rr, rr, rr * 2, -55, 110, Arc2D.OPEN));
                    }
                    g.setTransform(t);
                }
                case "ball" -> blit(g, Sprites.proj("gomitolo"), pr.x, pr.y, false, pr.r / 9.0, time * 6);
                case "lob" -> blit(g, Sprites.proj("pallapelo"), pr.x, pr.y, false, pr.r / 10.0, pr.rot);
                case "wave" -> drawWave(g, pr.x, pr.y, pr.r, pr.ang, (float) Util.clamp(pr.life / pr.maxLife, 0, 1));
                case "orbit" -> blit(g, Sprites.proj("artiglio"), pr.x, pr.y, false, 1.1, pr.ang + Math.PI / 2);
                case "knife" -> blit(g, Sprites.proj("sardina"), pr.x, pr.y, false, 1, pr.ang);
                case "fall" -> blit(g, Sprites.proj("crocc"), pr.x, pr.y, false, 1.1, time * 9);
            }
        }
        // particelle
        for (Particle pt : particles) {
            float a = (float) Util.clamp(pt.life / pt.maxLife, 0, 1);
            g.setColor(new Color(pt.color.getRed(), pt.color.getGreen(), pt.color.getBlue(), (int) (a * 200)));
            g.fill(new Ellipse2D.Double(pt.x - pt.size / 2, pt.y - pt.size / 2, pt.size, pt.size));
        }
        // testi fluttuanti
        g.setFont(F_FLOAT);
        for (FloatText f : floats) {
            float a = (float) Util.clamp(f.life / 0.7, 0, 1);
            g.setColor(new Color(0, 0, 0, (int) (a * 180)));
            g.drawString(f.text, (float) f.x + 1, (float) f.y + 1);
            g.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), (int) (a * 255)));
            g.drawString(f.text, (float) f.x, (float) f.y);
        }
    }

    /** Onda sonora del miagolio: condivisa con il rendering del client. */
    static void drawWave(Graphics2D g, double x, double y, double r, double ang, float a) {
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double deg = -Math.toDegrees(ang);
        g.setColor(new Color(0.56f, 0.83f, 0.96f, a * 0.9f));
        g.draw(new Arc2D.Double(x - r, y - r, r * 2, r * 2, deg - 40, 80, Arc2D.OPEN));
        g.setColor(new Color(0.56f, 0.83f, 0.96f, a * 0.55f));
        double r2 = r * 0.72;
        g.draw(new Arc2D.Double(x - r2, y - r2, r2 * 2, r2 * 2, deg - 35, 70, Arc2D.OPEN));
        double r3 = r * 0.45;
        g.setColor(new Color(0.56f, 0.83f, 0.96f, a * 0.3f));
        g.draw(new Arc2D.Double(x - r3, y - r3, r3 * 2, r3 * 2, deg - 30, 60, Arc2D.OPEN));
    }

    static void shadow(Graphics2D g, double x, double y, double r) {
        g.setColor(new Color(0, 0, 0, 45));
        g.fill(new Ellipse2D.Double(x - r, y - r * 0.45, r * 2, r * 0.9));
    }

    static void blit(Graphics2D g, BufferedImage img, double x, double y, boolean flip, double scale, double rot) {
        AffineTransform t = g.getTransform();
        g.translate(x, y);
        if (rot != 0) g.rotate(rot);
        g.scale(flip ? -scale : scale, scale);
        g.drawImage(img, -img.getWidth() / 2, -img.getHeight() / 2, null);
        g.setTransform(t);
    }
}
