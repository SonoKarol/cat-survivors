package catsurvivors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tutta l'interfaccia disegnata sul canvas: menu, HUD, level up, lobby, pausa, fine partita. */
final class Ui {
    static final Color GOLD = new Color(0xffd166);
    static final Color GREEN_LT = new Color(0xb8d8a8);
    static final Color BLUE_LT = new Color(0x8fd3f4);
    static final Color PINK_LT = new Color(0xf2b5d4);
    static final Color RED_LT = new Color(0xff6b6b);
    static final Color PANEL = new Color(0x1c291c);
    static final Color PANEL_HOVER = new Color(0x243524);
    static final Color BORDER = new Color(0x3a553a);
    static final Color OVERLAY = new Color(8, 14, 9, 216);
    static final Color TEXT = new Color(0xcfe3c2);
    static final Color HINT = new Color(0x8ba87e);

    static final Font F_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 46);
    static final Font F_H2 = new Font(Font.SANS_SERIF, Font.BOLD, 30);
    static final Font F_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    static final Font F_NAME = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    static final Font F_TEXT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font F_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    static final Font F_TIMER = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    static final Font F_HUD = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    static final Font F_SLOT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    static final Font F_MONO = new Font(Font.MONOSPACED, Font.BOLD, 22);

    static final String[] MENU_BUTTONS = {"GIOCA DA SOLO", "OSPITA CO-OP", "UNISCITI A UN AMICO"};

    private Ui() {}

    // ===== Input sulle schermate =====

    static void handleInput(Game game) {
        Input in = game.input;
        if (App.ipActive()) App.ipType(); // caratteri per il campo IP
        // il click viene valutato nel punto esatto della pressione, non dove il mouse è ora
        java.awt.Point c = in.consumeClick();
        switch (game.state) {
            case MENU -> {
                if (c == null || App.ipActive() || App.connecting) return;
                Rectangle[] btns = menuButtons(game);
                for (int i = 0; i < btns.length; i++) {
                    if (btns[i].contains(c)) {
                        switch (i) {
                            case 0 -> App.solo();
                            case 1 -> App.host();
                            case 2 -> App.openIpInput();
                        }
                        return;
                    }
                }
                Rectangle[] cards = menuCards(game);
                for (int i = 0; i < cards.length; i++) {
                    if (cards[i].contains(c)) {
                        App.selectedCat = i;
                        Sfx.play("meow");
                        return;
                    }
                }
            }
            case LEVELUP -> {
                if (c != null && game.choices != null && game.leveling == game.localPlayer()) {
                    Rectangle[] cards = choiceCards(game.choices.size(), game.viewW, game.viewH);
                    for (int i = 0; i < cards.length && i < game.choices.size(); i++) {
                        if (cards[i].contains(c)) {
                            game.pickChoice(game.choices.get(i));
                            return;
                        }
                    }
                }
            }
            case OVER, WIN -> {
                if (c != null && endButton(game.viewW, game.viewH).contains(c)) game.toMenu();
            }
            default -> { } // click ignorati negli altri stati (già consumati sopra)
        }
    }

    // ===== Layout =====

    static Rectangle[] menuCards(Game g) {
        int n = Cats.ALL.size();
        int cols = 4, cw = 215, ch = 240, gap = 14;
        int rows = (n + cols - 1) / cols;
        int gw = cols * cw + (cols - 1) * gap;
        int x0 = (g.viewW - gw) / 2;
        int y0 = Math.max(128, (g.viewH - (rows * ch + (rows - 1) * gap) - 60) / 2);
        Rectangle[] out = new Rectangle[n];
        for (int i = 0; i < n; i++) {
            out[i] = new Rectangle(x0 + (i % cols) * (cw + gap), y0 + (i / cols) * (ch + gap), cw, ch);
        }
        return out;
    }

    static Rectangle[] menuButtons(Game g) {
        Rectangle[] cards = menuCards(g);
        Rectangle last = cards[cards.length - 1];
        int y = last.y + last.height + 14;
        int bw = 230, bh = 44, gap = 14;
        int total = MENU_BUTTONS.length * bw + (MENU_BUTTONS.length - 1) * gap;
        int x0 = (g.viewW - total) / 2;
        Rectangle[] out = new Rectangle[MENU_BUTTONS.length];
        for (int i = 0; i < out.length; i++) out[i] = new Rectangle(x0 + i * (bw + gap), y, bw, bh);
        return out;
    }

    /** Carte del level up: layout condiviso tra host e client. */
    static Rectangle[] choiceCards(int n, int viewW, int viewH) {
        int cw = 240, ch = 220, gap = 16;
        int gw = n * cw + Math.max(0, n - 1) * gap;
        int x0 = (viewW - gw) / 2, y0 = viewH / 2 - ch / 2;
        Rectangle[] out = new Rectangle[n];
        for (int i = 0; i < n; i++) out[i] = new Rectangle(x0 + i * (cw + gap), y0, cw, ch);
        return out;
    }

    static Rectangle endButton(int viewW, int viewH) {
        return new Rectangle(viewW / 2 - 140, viewH / 2 + 150, 280, 48);
    }

    // ===== Disegno =====

    static void draw(Game game, Graphics2D g, int w, int h) {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch (game.state) {
            case MENU -> drawMenu(game, g, w, h);
            case LOBBY -> drawLobby(game, g, w, h);
            case PLAYING -> drawHud(game, g, w, h);
            case LEVELUP -> { drawHud(game, g, w, h); drawLevelUp(game, g, w, h); }
            case PAUSED -> { drawHud(game, g, w, h); drawPause(game, g, w, h); }
            case OVER -> drawEnd(game, g, w, h, false);
            case WIN -> drawEnd(game, g, w, h, true);
        }
    }

    private static void drawMenu(Game game, Graphics2D g, int w, int h) {
        g.setColor(OVERLAY);
        g.fillRect(0, 0, w, h);
        center(g, "CAT SURVIVORS", F_TITLE, GOLD, w / 2, 58);
        center(g, "Il giardino è invaso. Scegli il tuo gatto, da solo o con gli amici (co-op fino a 4).",
                F_BOLD, GREEN_LT, w / 2, 90);

        Rectangle[] cards = menuCards(game);
        for (int i = 0; i < cards.length; i++) {
            Rectangle r = cards[i];
            CatDef cat = Cats.ALL.get(i);
            boolean hover = r.contains(game.input.mouseX, game.input.mouseY);
            boolean selected = App.selectedCat == i;
            g.setColor(hover || selected ? PANEL_HOVER : PANEL);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            g.setColor(selected ? GOLD : (hover ? GREEN_LT : BORDER));
            g.setStroke(new BasicStroke(selected ? 3f : 2f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);

            BufferedImage img = Sprites.catBig(cat);
            g.drawImage(img, r.x + (r.width - 64) / 2, r.y + 6, 64, 64, null);
            center(g, cat.name, F_NAME, GOLD, r.x + r.width / 2, r.y + 88);
            center(g, cat.breed, F_SMALL, GREEN_LT, r.x + r.width / 2, r.y + 102);
            int ty = r.y + 118;
            for (String line : wrap(g, cat.personality, F_SMALL, r.width - 22)) {
                center(g, line, F_SMALL, TEXT, r.x + r.width / 2, ty);
                ty += 13;
            }
            ty = r.y + 180;
            for (String line : wrap(g, cat.bonus, F_SMALL, r.width - 22)) {
                center(g, line, F_SMALL, BLUE_LT, r.x + r.width / 2, ty);
                ty += 13;
            }
            WeaponDef wd = Weapons.MAP.get(cat.startWeapon);
            g.drawImage(Sprites.icon(wd.id), r.x + r.width / 2 - 66, r.y + 212, 18, 18, null);
            g.setFont(F_SMALL);
            g.setColor(PINK_LT);
            g.drawString(wd.name, r.x + r.width / 2 - 44, r.y + 225);
        }

        Rectangle[] btns = menuButtons(game);
        for (int i = 0; i < btns.length; i++) {
            Rectangle b = btns[i];
            boolean hover = b.contains(game.input.mouseX, game.input.mouseY);
            g.setColor(hover ? new Color(0xffe49e) : GOLD);
            g.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
            center(g, MENU_BUTTONS[i], F_BOLD, new Color(0x20301c), b.x + b.width / 2, b.y + 28);
        }

        String st = App.status;
        if (App.connecting) st = App.status;
        if (st != null && !st.isEmpty()) {
            center(g, st, F_TEXT, st.startsWith("Connessione fallita") || st.startsWith("Impossibile")
                    ? RED_LT : GOLD, w / 2, btns[0].y + 66);
        }
        center(g, "WASD / frecce per muoverti  •  mira col mouse  •  P pausa  •  M audio on/off",
                F_SMALL, HINT, w / 2, h - 14);

        if (App.ipActive()) drawIpInput(game, g, w, h);
    }

    private static void drawIpInput(Game game, Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, w, h);
        Rectangle box = new Rectangle(w / 2 - 250, h / 2 - 90, 500, 180);
        panel(g, box, true);
        center(g, "Unisciti a un amico", F_BOLD, GOLD, w / 2, box.y + 36);
        center(g, "Scrivi l'indirizzo dell'host (es. 192.168.1.10 oppure ip:porta)", F_TEXT, TEXT, w / 2, box.y + 62);
        Rectangle field = new Rectangle(box.x + 40, box.y + 80, box.width - 80, 38);
        g.setColor(new Color(0x0d150d));
        g.fillRoundRect(field.x, field.y, field.width, field.height, 8, 8);
        g.setColor(BORDER);
        g.drawRoundRect(field.x, field.y, field.width, field.height, 8, 8);
        StringBuilder b = App.ip;
        String txt = b != null ? b.toString() : "";
        boolean cursor = (System.currentTimeMillis() / 400) % 2 == 0;
        g.setFont(F_MONO);
        g.setColor(Color.WHITE);
        g.drawString(txt + (cursor ? "_" : ""), field.x + 12, field.y + 27);
        center(g, "INVIO conferma  •  ESC annulla", F_SMALL, HINT, w / 2, box.y + 150);
    }

    private static void drawLobby(Game game, Graphics2D g, int w, int h) {
        g.setColor(new Color(8, 14, 9, 170));
        g.fillRect(0, 0, w, h);
        Rectangle box = new Rectangle(w / 2 - 290, h / 2 - 200, 580, 400);
        panel(g, box, false);
        center(g, "LOBBY CO-OP", F_H2, GOLD, w / 2, box.y + 42);

        // indirizzo LAN (amici sulla stessa rete)
        center(g, "Stessa rete (LAN):", F_SMALL, GREEN_LT, w / 2, box.y + 74);
        center(g, Server.lanIp() + ":" + Net.DEFAULT_PORT, F_MONO, BLUE_LT, w / 2, box.y + 98);

        // indirizzo internet + stato del port forwarding automatico
        center(g, "Da internet:", F_SMALL, GREEN_LT, w / 2, box.y + 128);
        String pub = App.coopPublic;
        if (pub != null && !pub.isEmpty()) {
            center(g, pub, F_MONO, BLUE_LT, w / 2, box.y + 152);
        } else {
            center(g, "...", F_MONO, HINT, w / 2, box.y + 152);
        }
        String up = App.coopUpnp;
        if (up != null && !up.isEmpty()) {
            boolean ok = up.startsWith("Internet:");
            center(g, up, F_SMALL, ok ? GREEN_LT : GOLD, w / 2, box.y + 172);
        }
        center(g, "(se UPnP non basta: port forwarding TCP " + Net.DEFAULT_PORT
                + " sul router, o VPN tipo Tailscale)", F_SMALL, HINT, w / 2, box.y + 190);

        int y = box.y + 226;
        center(g, "Gatti pronti (" + game.players.size() + "/" + Net.MAX_PLAYERS + "):", F_BOLD, GREEN_LT, w / 2, y);
        y += 18;
        int n = game.players.size();
        int x0 = w / 2 - n * 45 + 45 / 2;
        for (int i = 0; i < n; i++) {
            Player p = game.players.get(i);
            g.drawImage(Sprites.catBig(p.cat), x0 + i * 90 - 24, y, 48, 48, null);
            center(g, p.cat.name + (p.pid == 0 ? " (tu)" : ""), F_SMALL, TEXT, x0 + i * 90, y + 62);
        }
        center(g, "INVIO — si parte!     ESC — annulla", F_BOLD, GOLD, w / 2, box.y + 376);
    }

    private static void drawHud(Game game, Graphics2D g, int w, int h) {
        Player p = game.localPlayer();
        if (p == null) return;
        // barra esperienza
        g.setColor(new Color(0x0d150d));
        g.fillRect(0, 0, w, 16);
        double xr = Util.clamp(p.xp / p.xpNext, 0, 1);
        g.setColor(new Color(0x59c2e8));
        g.fillRect(0, 0, (int) (w * xr), 16);
        g.setColor(new Color(0x7de87d));
        g.fillRect(0, 12, (int) (w * xr), 4);
        g.setColor(Color.BLACK);
        g.fillRect(0, 16, w, 2);
        // riga superiore
        g.setFont(F_HUD);
        g.setColor(BLUE_LT);
        g.drawString("LV " + p.level, 12, 36);
        center(g, Util.fmtTime(game.time), F_TIMER, GOLD, w / 2, 46);
        g.setColor(TEXT);
        right(g, "KO " + game.kills, F_HUD, w - 12, 36);
        g.setColor(new Color(0xff8c8c));
        right(g, "PS " + (int) Math.ceil(p.hp) + "/" + (int) p.stats.maxHp, F_HUD, w - 12, 56);
        if (Sfx.muted) right(g, "AUDIO OFF [M]", F_SMALL, w - 12, 74);
        // slot armi e passivi
        int sx = 12, sy = 52;
        for (Player.WeaponInst wi : p.weapons) {
            slot(g, sx, sy, Sprites.icon(wi.def.id), wi.level);
            sx += 41;
        }
        sx = 12;
        sy += 41;
        for (Map.Entry<String, Integer> en : p.passives.entrySet()) {
            slot(g, sx, sy, Sprites.icon(en.getKey()), en.getValue());
            sx += 41;
        }
        // barra del boss
        Enemy boss = null;
        for (Enemy e : game.enemies) {
            if (e.boss && !e.dead) { boss = e; break; }
        }
        if (boss != null) {
            drawBossBar(g, w, h, boss.def.name, (float) Util.clamp(boss.hp / boss.maxHp, 0, 1));
        }
        // minimappa
        List<double[]> cats = new ArrayList<>();
        for (Player q : game.players) {
            cats.add(new double[]{q.x, q.y, q.cat.sprite.body.getRGB() & 0xffffff, q == p ? 1 : 0, q.alive ? 1 : 0});
        }
        List<double[]> foes = new ArrayList<>();
        for (Enemy e : game.enemies) {
            if (!e.dead) foes.add(new double[]{e.x, e.y, e.boss ? 2 : (e.elite ? 1 : 0)});
        }
        List<double[]> picks = new ArrayList<>();
        for (Pickup pk : game.pickups) picks.add(new double[]{pk.x, pk.y});
        drawMinimap(g, w, h, p.x, p.y, cats, foes, picks);
    }

    // ===== Minimappa =====

    /**
     * Radar in basso a destra. Punti: gatti {x, y, rgb, isMe, alive},
     * nemici {x, y, kind} (0=normale, 1=elite, 2=boss), pickup {x, y}.
     * Gli alleati fuori portata restano agganciati al bordo con una freccia:
     * in co-op non ci si perde più.
     */
    static void drawMinimap(Graphics2D g, int w, int h, double cx, double cy,
                            List<double[]> cats, List<double[]> foes, List<double[]> picks) {
        int size = 150, margin = 14;
        int mx = w - size - margin, my = h - size - margin;
        double half = size / 2.0;
        double scale = size / 1800.0; // la mappa copre ~1800 px di mondo

        Shape oldClip = g.getClip();
        g.setColor(new Color(10, 18, 10, 175));
        g.fillRoundRect(mx, my, size, size, 12, 12);
        g.setClip(new RoundRectangle2D.Double(mx, my, size, size, 12, 12));

        // croce di riferimento leggera
        g.setColor(new Color(255, 255, 255, 16));
        g.drawLine(mx, (int) (my + half), mx + size, (int) (my + half));
        g.drawLine((int) (mx + half), my, (int) (mx + half), my + size);

        // nemici (i boss restano visibili al bordo anche se lontani)
        for (double[] f : foes) {
            double dx = (f[0] - cx) * scale, dy = (f[1] - cy) * scale;
            int kind = (int) f[2];
            double px, py;
            if (kind == 2) {
                px = mx + half + Util.clamp(dx, -(half - 8), half - 8);
                py = my + half + Util.clamp(dy, -(half - 8), half - 8);
            } else {
                if (Math.abs(dx) > half + 4 || Math.abs(dy) > half + 4) continue;
                px = mx + half + dx;
                py = my + half + dy;
            }
            switch (kind) {
                case 2 -> {
                    g.setColor(new Color(0xff4040));
                    g.fill(new Ellipse2D.Double(px - 3.5, py - 3.5, 7, 7));
                    g.setColor(new Color(255, 64, 64, 110));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Double(px - 6, py - 6, 12, 12));
                }
                case 1 -> {
                    g.setColor(new Color(0xffd166));
                    g.fill(new Ellipse2D.Double(px - 2.5, py - 2.5, 5, 5));
                }
                default -> {
                    g.setColor(new Color(214, 69, 69, 200));
                    g.fill(new Ellipse2D.Double(px - 1.5, py - 1.5, 3, 3));
                }
            }
        }
        // pickup (croccantini e calamite)
        g.setColor(new Color(0x7de87d));
        for (double[] pk : picks) {
            double px = (pk[0] - cx) * scale + mx + half;
            double py = (pk[1] - cy) * scale + my + half;
            if (px < mx || px > mx + size || py < my || py > my + size) continue;
            g.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
        }

        // gatti: sempre visibili, con freccia quando l'alleato è fuori portata
        for (double[] c : cats) {
            double dx = (c[0] - cx) * scale, dy = (c[1] - cy) * scale;
            boolean isMe = c[3] > 0, alive = c[4] > 0;
            boolean outside = Math.abs(dx) > half - 9 || Math.abs(dy) > half - 9;
            double px = mx + half + Util.clamp(dx, -(half - 9), half - 9);
            double py = my + half + Util.clamp(dy, -(half - 9), half - 9);
            Color col = alive ? new Color((int) c[2] & 0xffffff) : new Color(120, 120, 120);
            if (outside && !isMe) {
                double ang = Math.atan2(dy, dx);
                g.setColor(col);
                Path2D tri = new Path2D.Double();
                tri.moveTo(px + Math.cos(ang) * 8, py + Math.sin(ang) * 8);
                tri.lineTo(px + Math.cos(ang + 2.4) * 5.5, py + Math.sin(ang + 2.4) * 5.5);
                tri.lineTo(px + Math.cos(ang - 2.4) * 5.5, py + Math.sin(ang - 2.4) * 5.5);
                tri.closePath();
                g.fill(tri);
            }
            g.setColor(Color.WHITE);
            double r = isMe ? 4.5 : 4;
            g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            g.setColor(col);
            double r2 = isMe ? 3 : 2.5;
            g.fill(new Ellipse2D.Double(px - r2, py - r2, r2 * 2, r2 * 2));
        }

        g.setClip(oldClip);
        g.setColor(new Color(0x44613f));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(mx, my, size, size, 12, 12);
    }

    static void drawBossBar(Graphics2D g, int w, int h, String name, float ratio) {
        int bw = Math.min(540, (int) (w * 0.7));
        int bx = (w - bw) / 2, by = h - 38;
        center(g, name, F_SMALL, new Color(0xf2b1b1), w / 2, by - 6);
        g.setColor(new Color(0x1a0e0e));
        g.fillRect(bx - 2, by - 2, bw + 4, 18 + 4);
        g.setColor(new Color(0xd64545));
        g.fillRect(bx, by, (int) (bw * ratio), 18);
    }

    static void slot(Graphics2D g, int x, int y, BufferedImage icon, int level) {
        g.setColor(new Color(10, 18, 10, 200));
        g.fillRoundRect(x, y, 36, 36, 8, 8);
        g.setColor(new Color(0x44613f));
        g.drawRoundRect(x, y, 36, 36, 8, 8);
        g.drawImage(icon, x + 5, y + 5, null);
        g.setFont(F_SLOT);
        g.setColor(GOLD);
        g.drawString(String.valueOf(level), x + 28, y + 33);
    }

    private static void drawLevelUp(Game game, Graphics2D g, int w, int h) {
        g.setColor(new Color(8, 14, 9, 190));
        g.fillRect(0, 0, w, h);
        Player me = game.localPlayer();
        if (game.leveling != me) {
            String who = game.leveling != null ? game.leveling.cat.name : "un amico";
            center(g, who + " sta scegliendo un potenziamento...", F_H2, GOLD, w / 2, h / 2 - 10);
            center(g, "(il mondo trattiene il fiato)", F_TEXT, HINT, w / 2, h / 2 + 22);
            return;
        }
        center(g, "MIAO! Livello " + me.level + "!", F_H2, GOLD, w / 2, h / 2 - 160);
        center(g, "Scegli un potenziamento (clic o tasti 1-" + game.choices.size() + ")", F_TEXT, GREEN_LT, w / 2, h / 2 - 134);
        drawChoiceCards(g, game.choices, game.input.mouseX, game.input.mouseY, w, h);
    }

    /** Carte di scelta: condivise tra host e client. */
    static void drawChoiceCards(Graphics2D g, List<Choice> choices, int mx, int my, int w, int h) {
        Rectangle[] cards = choiceCards(choices.size(), w, h);
        for (int i = 0; i < cards.length && i < choices.size(); i++) {
            Rectangle r = cards[i];
            Choice c = choices.get(i);
            boolean hover = r.contains(mx, my);
            panel(g, r, hover);
            g.drawImage(Sprites.icon(c.icon), r.x + r.width / 2 - 20, r.y + 14, 40, 40, null);
            center(g, c.name, F_BOLD, GOLD, r.x + r.width / 2, r.y + 76);
            center(g, c.lvlText, F_SMALL, BLUE_LT, r.x + r.width / 2, r.y + 94);
            int ty = r.y + 116;
            for (String line : wrap(g, c.desc, F_TEXT, r.width - 26)) {
                center(g, line, F_TEXT, TEXT, r.x + r.width / 2, ty);
                ty += 16;
            }
            g.setFont(F_SMALL);
            g.setColor(HINT);
            g.drawString(String.valueOf(i + 1), r.x + 10, r.y + r.height - 10);
        }
    }

    private static void drawPause(Game game, Graphics2D g, int w, int h) {
        g.setColor(new Color(8, 14, 9, 190));
        g.fillRect(0, 0, w, h);
        center(g, "PAUSA", F_H2, GOLD, w / 2, h / 2 - 120);
        Player p = game.localPlayer();
        if (p == null) return;
        center(g, p.cat.name + " — " + p.cat.breed, F_BOLD, GREEN_LT, w / 2, h / 2 - 84);
        int total = p.weapons.size() + p.passives.size();
        int bx = w / 2 - total * 23 + 3, by = h / 2 - 50;
        for (Player.WeaponInst wi : p.weapons) {
            slot(g, bx, by, Sprites.icon(wi.def.id), wi.level);
            bx += 46;
        }
        for (Map.Entry<String, Integer> en : p.passives.entrySet()) {
            slot(g, bx, by, Sprites.icon(en.getKey()), en.getValue());
            bx += 46;
        }
        center(g, "Tempo: " + Util.fmtTime(game.time) + "   •   Livello " + p.level + "   •   KO " + game.kills,
                F_TEXT, TEXT, w / 2, h / 2 + 30);
        String extra = game.isCoop() ? "   (la pausa ferma anche i tuoi amici!)" : "";
        center(g, "P o Esc per riprendere  •  M audio on/off" + extra, F_TEXT, HINT, w / 2, h / 2 + 60);
    }

    private static void drawEnd(Game game, Graphics2D g, int w, int h, boolean win) {
        g.setColor(OVERLAY);
        g.fillRect(0, 0, w, h);
        if (win) {
            center(g, "VITTORIA!", F_TITLE, GOLD, w / 2, h / 2 - 150);
            center(g, "Dieci minuti, zero bagnetti. Il giardino è di nuovo vostro.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
        } else {
            center(g, "GAME OVER", F_TITLE, RED_LT, w / 2, h / 2 - 150);
            center(g, "Il giardino ha avuto la meglio... per stavolta.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
        }
        Player p = game.localPlayer();
        Rectangle box = new Rectangle(w / 2 - 180, h / 2 - 80, 360, 180);
        panel(g, box, false);
        if (p != null) {
            g.drawImage(Sprites.catBig(p.cat), box.x + 16, box.y + 20, 72, 72, null);
            g.setFont(F_BOLD);
            g.setColor(GOLD);
            g.drawString(p.cat.name, box.x + 104, box.y + 38);
            g.setFont(F_TEXT);
            g.setColor(TEXT);
            g.drawString("Razza: " + p.cat.breed, box.x + 104, box.y + 58);
            g.drawString("Sopravvissuto: " + Util.fmtTime(game.time), box.x + 104, box.y + 78);
            g.drawString("Livello raggiunto: " + p.level, box.x + 104, box.y + 98);
            g.drawString("Nemici sconfitti (squadra): " + game.kills, box.x + 104, box.y + 118);
            g.drawString("Armi: " + p.weapons.size() + "  •  Passivi: " + p.passives.size(), box.x + 104, box.y + 138);
        }
        Rectangle btn = endButton(w, h);
        boolean hover = btn.contains(game.input.mouseX, game.input.mouseY);
        g.setColor(hover ? new Color(0xffe49e) : GOLD);
        g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
        center(g, "Torna al rifugio (R)", F_BOLD, new Color(0x20301c), btn.x + btn.width / 2, btn.y + 31);
    }

    // ===== Helper =====

    static void panel(Graphics2D g, Rectangle r, boolean hover) {
        g.setColor(hover ? PANEL_HOVER : PANEL);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g.setColor(hover ? GOLD : BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
    }

    static void center(Graphics2D g, String s, Font f, Color c, int cx, int y) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    static void right(Graphics2D g, String s, Font f, int rx, int y) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, rx - fm.stringWidth(s), y);
    }

    static List<String> wrap(Graphics2D g, String text, Font f, int maxW) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(test) > maxW && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }
}
