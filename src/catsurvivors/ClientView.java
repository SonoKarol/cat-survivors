package catsurvivors;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lato client del co-op: invia l'input all'host e disegna il mondo
 * ricevuto negli snapshot, interpolando tra gli ultimi due per fluidità.
 */
final class ClientView {
    // ordinali di Game.State usati nel protocollo
    private static final int ST_LOBBY = 1, ST_PLAYING = 2, ST_LEVELUP = 3, ST_PAUSED = 4, ST_OVER = 5, ST_WIN = 6;

    private static float lastHpRatio = 1;
    private static int lastState = -1;
    private static boolean lastBoss = false;

    private ClientView() {}

    /** Un frame completo lato client: input, suoni, rendering. */
    static void frame(Client c, Input input, Graphics2D g, int w, int h) {
        handleKeys(c, input);

        Snapshot s = c.latest;
        if (c.error != null) {
            drawMessage(g, w, h, "Connessione persa", c.error, true);
            if (input.consumeClick() != null) App.backToMenu();
            return;
        }
        if (s == null || c.myPid < 0) {
            input.consumeClick();
            drawMessage(g, w, h, "Connessione...", "In attesa dell'host", false);
            return;
        }

        Snapshot.PlayerS me = findMe(s, c.myPid);

        // input verso l'host: movimento + mira (la camera è centrata su di noi)
        double[] a = input.axis();
        double aimX = 1, aimY = 0;
        if (input.mouseX >= 0) {
            double ax = input.mouseX - w / 2.0, ay = input.mouseY - h / 2.0;
            if (Math.hypot(ax, ay) > 12) { aimX = ax; aimY = ay; }
        }
        c.sendInput(a[0], a[1], aimX, aimY);

        playSounds(s, me);

        // interpolazione tra gli ultimi due snapshot
        Snapshot p = c.prev;
        double t = 1;
        if (p != null && s.arrivedAt > p.arrivedAt) {
            double interval = Math.min(0.2, (s.arrivedAt - p.arrivedAt) / 1e9);
            t = Util.clamp((System.nanoTime() - s.arrivedAt) / 1e9 / interval, 0, 1);
        }
        Map<Integer, float[]> prevEnemies = new HashMap<>();
        Map<Integer, float[]> prevPlayers = new HashMap<>();
        if (p != null) {
            for (Snapshot.EnemyS e : p.enemies) prevEnemies.put(e.id, new float[]{e.x, e.y});
            for (Snapshot.PlayerS pl : p.players) prevPlayers.put(pl.pid, new float[]{pl.x, pl.y});
        }

        double camX = -w / 2.0, camY = -h / 2.0;
        if (me != null) {
            float[] pm = prevPlayers.get(me.pid);
            double mx = pm != null ? Util.lerp(pm[0], me.x, t) : me.x;
            double my = pm != null ? Util.lerp(pm[1], me.y, t) : me.y;
            camX = mx - w / 2.0;
            camY = my - h / 2.0;
        }

        AffineTransform old = g.getTransform();
        g.translate(-camX, -camY);
        Game.drawBackground(g, camX, camY, w, h);
        drawWorld(g, s, c.myPid, t, prevEnemies, prevPlayers);
        g.setTransform(old);

        drawHud(g, s, me, w, h);

        switch (s.state) {
            case ST_LOBBY -> drawClientLobby(g, s, w, h);
            case ST_PAUSED -> Ui.center(g, "PAUSA (host)", Ui.F_H2, Ui.GOLD, w / 2, h / 2);
            case ST_LEVELUP -> {
                g.setColor(new Color(8, 14, 9, 190));
                g.fillRect(0, 0, w, h);
                List<Choice> cs = c.choices;
                if (cs != null) {
                    Ui.center(g, "MIAO! Scegli un potenziamento (clic o 1-" + cs.size() + ")",
                            Ui.F_H2, Ui.GOLD, w / 2, h / 2 - 140);
                    Ui.drawChoiceCards(g, cs, input.mouseX, input.mouseY, w, h);
                    java.awt.Point click = input.consumeClick();
                    if (click != null) {
                        Rectangle[] cards = Ui.choiceCards(cs.size(), w, h);
                        for (int i = 0; i < cards.length; i++) {
                            if (cards[i].contains(click)) { c.sendChoice(i); break; }
                        }
                    }
                } else {
                    String who = levelerName(s);
                    Ui.center(g, who + " sta scegliendo un potenziamento...", Ui.F_H2, Ui.GOLD, w / 2, h / 2 - 10);
                    Ui.center(g, "(il mondo trattiene il fiato)", Ui.F_TEXT, Ui.HINT, w / 2, h / 2 + 22);
                }
            }
            case ST_OVER -> drawClientEnd(g, s, me, w, h, false, input);
            case ST_WIN -> drawClientEnd(g, s, me, w, h, true, input);
            default -> { }
        }
        if (s.state != ST_LEVELUP && s.state != ST_OVER && s.state != ST_WIN) input.consumeClick();
    }

    private static void handleKeys(Client c, Input input) {
        Integer k;
        while ((k = input.nextPress()) != null) {
            switch (k) {
                case KeyEvent.VK_M -> Sfx.toggle();
                case KeyEvent.VK_R -> {
                    Snapshot s = c.latest;
                    if (c.error != null || (s != null && (s.state == ST_OVER || s.state == ST_WIN))) {
                        App.backToMenu();
                        return;
                    }
                }
                default -> {
                    List<Choice> cs = c.choices;
                    if (cs != null) {
                        int idx = switch (k) {
                            case KeyEvent.VK_1 -> 0;
                            case KeyEvent.VK_2 -> 1;
                            case KeyEvent.VK_3 -> 2;
                            case KeyEvent.VK_4 -> 3;
                            default -> -1;
                        };
                        if (idx >= 0 && idx < cs.size()) c.sendChoice(idx);
                    }
                }
            }
        }
    }

    private static Snapshot.PlayerS findMe(Snapshot s, int pid) {
        for (Snapshot.PlayerS p : s.players) if (p.pid == pid) return p;
        return null;
    }

    private static String levelerName(Snapshot s) {
        Snapshot.PlayerS p = findMe(s, s.levelingPid);
        return p != null ? Cats.ALL.get(p.catIdx).name : "Un amico";
    }

    private static void playSounds(Snapshot s, Snapshot.PlayerS me) {
        if (me != null) {
            if (me.hpRatio < lastHpRatio - 0.01f) Sfx.play("hurt");
            lastHpRatio = me.hpRatio;
        }
        if (s.hasBoss && !lastBoss) Sfx.play("boss");
        lastBoss = s.hasBoss;
        if (s.state != lastState) {
            if (s.state == ST_WIN) Sfx.play("win");
            else if (s.state == ST_OVER) Sfx.play("gameover");
            lastState = s.state;
        }
    }

    private static void drawWorld(Graphics2D g, Snapshot s, int myPid, double t,
                                  Map<Integer, float[]> prevEnemies, Map<Integer, float[]> prevPlayers) {
        double now = s.time;
        // aure
        for (Snapshot.PlayerS p : s.players) {
            if (p.alive && p.auraR > 0) {
                g.setColor(new Color(1f, 0.75f, 0.85f, 0.12f));
                g.fill(new Ellipse2D.Double(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2));
                g.setColor(new Color(1f, 0.75f, 0.85f, 0.30f));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Ellipse2D.Double(p.x - p.auraR, p.y - p.auraR, p.auraR * 2, p.auraR * 2));
            }
        }
        // gemme e raccoglibili
        for (Snapshot.GemS gm : s.gems) Game.blit(g, Sprites.gem(gm.value), gm.x, gm.y, false, 1, 0);
        for (Snapshot.PickS pk : s.picks) {
            Game.shadow(g, pk.x, pk.y + 10, 9);
            Game.blit(g, Sprites.pickup(pk.kind == 0 ? "croccantino" : "magnete"), pk.x, pk.y, false, 1, 0);
        }
        // esplosioni e bersagli (sotto i nemici)
        for (Snapshot.ProjS pr : s.projs) {
            if (pr.lifeRatio < 0) continue;
            int type = pr.typeIdx;
            if (type == 7) { // boom
                float a = pr.lifeRatio;
                g.setColor(new Color(1f, 0.69f, 0.29f, a * 0.32f));
                g.fill(new Ellipse2D.Double(pr.x - pr.r, pr.y - pr.r, pr.r * 2, pr.r * 2));
                g.setColor(new Color(1f, 0.82f, 0.4f, a * 0.8f));
                g.setStroke(new BasicStroke(3f));
                double rr = pr.r * (1.1 - 0.3 * a);
                g.draw(new Ellipse2D.Double(pr.x - rr, pr.y - rr, rr * 2, rr * 2));
            } else if (type == 6) { // croccantino in caduta: ombra sul bersaglio
                g.setColor(new Color(20, 30, 15, 60));
                g.fill(new Ellipse2D.Double(pr.x - 12, pr.extra - 6, 24, 12));
            }
        }
        // nemici
        for (Snapshot.EnemyS e : s.enemies) {
            float[] pe = prevEnemies.get(e.id);
            double ex = pe != null ? Util.lerp(pe[0], e.x, t) : e.x;
            double ey = pe != null ? Util.lerp(pe[1], e.y, t) : e.y;
            String type = Net.ENEMY_IDS.get(e.typeIdx);
            double r = Enemies.TYPES.get(type).r * (e.elite ? 1.5 : 1);
            Game.shadow(g, ex, ey + r * 0.85, r * 0.9);
            if (e.elite) {
                g.setColor(new Color(255, 209, 102, 120));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Ellipse2D.Double(ex - r - 4, ey - r - 4, (r + 4) * 2, (r + 4) * 2));
            }
            Game.blit(g, Sprites.enemy(type), ex, ey + Math.sin(now * 6 + e.id) * 1.5, false, e.elite ? 1.5 : 1, 0);
            if (e.flash) {
                g.setColor(new Color(1f, 1f, 1f, 0.5f));
                g.fill(new Ellipse2D.Double(ex - r, ey - r, r * 2, r * 2));
            }
            if (e.boss) {
                double bw = 60;
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect((int) (ex - bw / 2), (int) (ey - r - 16), (int) bw, 6);
                g.setColor(new Color(0xd64545));
                g.fillRect((int) (ex - bw / 2), (int) (ey - r - 16), (int) (bw * e.hpRatio), 6);
            }
        }
        // gatti
        for (Snapshot.PlayerS p : s.players) {
            float[] pp = prevPlayers.get(p.pid);
            double px = pp != null ? Util.lerp(pp[0], p.x, t) : p.x;
            double py = pp != null ? Util.lerp(pp[1], p.y, t) : p.y;
            CatDef cat = Cats.ALL.get(p.catIdx);
            Composite oldComp = g.getComposite();
            if (!p.alive) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            else Game.shadow(g, px, py + 14, 13);
            boolean blink = p.alive && p.hurt && (System.nanoTime() / 60_000_000) % 2 == 0;
            if (!blink) {
                double bob = p.moving ? Math.sin(now * 9) * 2 : 0;
                Game.blit(g, Sprites.cat(cat), px, py - 6 + bob, p.flipped, 1.15, 0);
            }
            g.setComposite(oldComp);
            if (p.alive) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect((int) px - 16, (int) py + 18, 32, 5);
                g.setColor(p.hpRatio > 0.4f ? new Color(0x7de87d) : new Color(0xe85d5d));
                g.fillRect((int) px - 16, (int) py + 18, (int) (32 * p.hpRatio), 5);
            }
            if (p.pid != myPid) {
                g.setFont(Ui.F_SMALL);
                g.setColor(new Color(255, 255, 255, 220));
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.drawString(cat.name, (float) (px - fm.stringWidth(cat.name) / 2.0), (float) (py - 34));
            }
        }
        // proiettili
        for (Snapshot.ProjS pr : s.projs) {
            if (pr.lifeRatio < 0) continue;
            switch (pr.typeIdx) {
                case 0 -> { // graffio
                    float a = pr.lifeRatio;
                    AffineTransform tr = g.getTransform();
                    g.translate(pr.x, pr.y);
                    g.rotate(pr.ang);
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(1f, 1f, 1f, a * 0.9f));
                    for (int k = -1; k <= 1; k++) {
                        g.draw(new Arc2D.Double(-pr.r * 0.5 + k * 7, -pr.r, pr.r, pr.r * 2, -55, 110, Arc2D.OPEN));
                    }
                    g.setTransform(tr);
                }
                case 1 -> Game.blit(g, Sprites.proj("gomitolo"), pr.x, pr.y, false, pr.r / 9.0, now * 6);
                case 2 -> Game.blit(g, Sprites.proj("pallapelo"), pr.x, pr.y, false, pr.r / 10.0, pr.extra);
                case 3 -> Game.drawWave(g, pr.x, pr.y, pr.r, pr.ang, pr.lifeRatio);
                case 4 -> Game.blit(g, Sprites.proj("artiglio"), pr.x, pr.y, false, 1.1, pr.ang + Math.PI / 2);
                case 5 -> Game.blit(g, Sprites.proj("sardina"), pr.x, pr.y, false, 1, pr.ang);
                case 6 -> Game.blit(g, Sprites.proj("crocc"), pr.x, pr.y, false, 1.1, now * 9);
                default -> { }
            }
        }
        // testi fluttuanti
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 12));
        for (Snapshot.FloatS f : s.floats) {
            float a = (float) Util.clamp(f.life / 0.7, 0, 1);
            Color base = new Color(f.rgb, true);
            g.setColor(new Color(0, 0, 0, (int) (a * 180)));
            g.drawString(f.text, f.x + 1, f.y + 1);
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) (a * 255)));
            g.drawString(f.text, f.x, f.y);
        }
    }

    private static void drawHud(Graphics2D g, Snapshot s, Snapshot.PlayerS me, int w, int h) {
        if (me == null) return;
        g.setColor(new Color(0x0d150d));
        g.fillRect(0, 0, w, 16);
        g.setColor(new Color(0x59c2e8));
        g.fillRect(0, 0, (int) (w * Util.clamp(me.xpRatio, 0, 1)), 16);
        g.setColor(new Color(0x7de87d));
        g.fillRect(0, 12, (int) (w * Util.clamp(me.xpRatio, 0, 1)), 4);
        g.setColor(Color.BLACK);
        g.fillRect(0, 16, w, 2);
        g.setFont(Ui.F_HUD);
        g.setColor(Ui.BLUE_LT);
        g.drawString("LV " + me.level, 12, 36);
        Ui.center(g, Util.fmtTime(s.time), Ui.F_TIMER, Ui.GOLD, w / 2, 46);
        g.setColor(Ui.TEXT);
        Ui.right(g, "KO " + s.kills, Ui.F_HUD, w - 12, 36);
        g.setColor(new Color(0xff8c8c));
        Ui.right(g, "PS " + Math.round(me.hpRatio * 100) + "%", Ui.F_HUD, w - 12, 56);
        if (Sfx.muted) Ui.right(g, "AUDIO OFF [M]", Ui.F_SMALL, w - 12, 74);
        int sx = 12, sy = 52;
        for (int[] wi : me.weapons) {
            Ui.slot(g, sx, sy, Sprites.icon(Net.WEAPON_IDS.get(wi[0])), wi[1]);
            sx += 41;
        }
        sx = 12;
        sy += 41;
        for (int[] pi : me.passives) {
            Ui.slot(g, sx, sy, Sprites.icon(Net.PASSIVE_IDS.get(pi[0])), pi[1]);
            sx += 41;
        }
        if (s.hasBoss) Ui.drawBossBar(g, w, h, s.bossName, s.bossRatio);
    }

    private static void drawClientLobby(Graphics2D g, Snapshot s, int w, int h) {
        g.setColor(new Color(8, 14, 9, 170));
        g.fillRect(0, 0, w, h);
        Rectangle box = new Rectangle(w / 2 - 270, h / 2 - 130, 540, 260);
        Ui.panel(g, box, false);
        Ui.center(g, "LOBBY CO-OP", Ui.F_H2, Ui.GOLD, w / 2, box.y + 44);
        Ui.center(g, "Connesso! In attesa che l'host avvii la partita...", Ui.F_TEXT, Ui.TEXT, w / 2, box.y + 76);
        int n = s.players.length;
        Ui.center(g, "Gatti pronti (" + n + "/" + Net.MAX_PLAYERS + "):", Ui.F_BOLD, Ui.GREEN_LT, w / 2, box.y + 110);
        int x0 = w / 2 - n * 45 + 22;
        for (int i = 0; i < n; i++) {
            CatDef cat = Cats.ALL.get(s.players[i].catIdx);
            g.drawImage(Sprites.catBig(cat), x0 + i * 90 - 24, box.y + 124, 48, 48, null);
            Ui.center(g, cat.name, Ui.F_SMALL, Ui.TEXT, x0 + i * 90, box.y + 186);
        }
        Ui.center(g, "Prepara gli artigli!", Ui.F_SMALL, Ui.HINT, w / 2, box.y + 226);
    }

    private static void drawClientEnd(Graphics2D g, Snapshot s, Snapshot.PlayerS me, int w, int h,
                                      boolean win, Input input) {
        g.setColor(Ui.OVERLAY);
        g.fillRect(0, 0, w, h);
        if (win) {
            Ui.center(g, "VITTORIA!", Ui.F_TITLE, Ui.GOLD, w / 2, h / 2 - 130);
            Ui.center(g, "Dieci minuti, zero bagnetti. Il giardino è di nuovo vostro.", Ui.F_BOLD, Ui.GREEN_LT, w / 2, h / 2 - 92);
        } else {
            Ui.center(g, "GAME OVER", Ui.F_TITLE, Ui.RED_LT, w / 2, h / 2 - 130);
            Ui.center(g, "Il giardino ha avuto la meglio... per stavolta.", Ui.F_BOLD, Ui.GREEN_LT, w / 2, h / 2 - 92);
        }
        if (me != null) {
            CatDef cat = Cats.ALL.get(me.catIdx);
            Ui.center(g, cat.name + "  •  Livello " + me.level + "  •  KO di squadra: " + s.kills
                    + "  •  Tempo: " + Util.fmtTime(s.time), Ui.F_TEXT, Ui.TEXT, w / 2, h / 2 - 40);
        }
        Rectangle btn = Ui.endButton(w, h);
        boolean hover = btn.contains(input.mouseX, input.mouseY);
        g.setColor(hover ? new Color(0xffe49e) : Ui.GOLD);
        g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
        Ui.center(g, "Torna al rifugio (R)", Ui.F_BOLD, new Color(0x20301c), btn.x + btn.width / 2, btn.y + 31);
        java.awt.Point c = input.consumeClick();
        if (c != null && btn.contains(c)) App.backToMenu();
    }

    private static void drawMessage(Graphics2D g, int w, int h, String title, String sub, boolean error) {
        Ui.center(g, title, Ui.F_H2, error ? Ui.RED_LT : Ui.GOLD, w / 2, h / 2 - 20);
        Ui.center(g, sub, Ui.F_TEXT, Ui.TEXT, w / 2, h / 2 + 12);
        Ui.center(g, error ? "R o clic per tornare al menu" : "ESC per annullare... scherzo, aspetta e basta",
                Ui.F_SMALL, Ui.HINT, w / 2, h / 2 + 44);
    }
}
