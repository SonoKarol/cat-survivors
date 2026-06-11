package catsurvivors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tutta l'interfaccia disegnata sul canvas: menu, HUD, level up, pausa, fine partita. */
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

    private Ui() {}

    // ===== Input sulle schermate =====

    static void handleInput(Game game) {
        Input in = game.input;
        // il click viene valutato nel punto esatto della pressione, non dove il mouse è ora
        java.awt.Point c = in.consumeClick();
        switch (game.state) {
            case MENU -> {
                if (c != null) {
                    Rectangle[] cards = menuCards(game);
                    for (int i = 0; i < cards.length; i++) {
                        if (cards[i].contains(c)) {
                            game.startRun(Cats.ALL.get(i));
                            return;
                        }
                    }
                }
            }
            case LEVELUP -> {
                if (c != null && game.choices != null) {
                    Rectangle[] cards = levelUpCards(game);
                    for (int i = 0; i < cards.length && i < game.choices.size(); i++) {
                        if (cards[i].contains(c)) {
                            game.pickChoice(game.choices.get(i));
                            return;
                        }
                    }
                }
            }
            case OVER, WIN -> {
                if (c != null && endButton(game).contains(c)) {
                    game.state = Game.State.MENU;
                }
            }
            default -> { } // click ignorati negli altri stati (già consumati sopra)
        }
    }

    // ===== Layout =====

    static Rectangle[] menuCards(Game g) {
        int n = Cats.ALL.size();
        int cols = 4, cw = 215, ch = 252, gap = 14;
        int rows = (n + cols - 1) / cols;
        int gw = cols * cw + (cols - 1) * gap;
        int totalH = rows * ch + (rows - 1) * gap;
        int x0 = (g.viewW - gw) / 2;
        int y0 = Math.max(140, (g.viewH - totalH + 70) / 2);
        Rectangle[] out = new Rectangle[n];
        for (int i = 0; i < n; i++) {
            out[i] = new Rectangle(x0 + (i % cols) * (cw + gap), y0 + (i / cols) * (ch + gap), cw, ch);
        }
        return out;
    }

    static Rectangle[] levelUpCards(Game g) {
        int n = g.choices == null ? 0 : g.choices.size();
        int cw = 240, ch = 220, gap = 16;
        int gw = n * cw + Math.max(0, n - 1) * gap;
        int x0 = (g.viewW - gw) / 2, y0 = g.viewH / 2 - ch / 2;
        Rectangle[] out = new Rectangle[n];
        for (int i = 0; i < n; i++) out[i] = new Rectangle(x0 + i * (cw + gap), y0, cw, ch);
        return out;
    }

    static Rectangle endButton(Game g) {
        return new Rectangle(g.viewW / 2 - 140, g.viewH / 2 + 150, 280, 48);
    }

    // ===== Disegno =====

    static void draw(Game game, Graphics2D g, int w, int h) {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch (game.state) {
            case MENU -> drawMenu(game, g, w, h);
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
        center(g, "CAT SURVIVORS", F_TITLE, GOLD, w / 2, 64);
        center(g, "Il giardino è invaso da cetrioli, piccioni e aspirapolvere.", F_BOLD, GREEN_LT, w / 2, 96);
        center(g, "Scegli il tuo gatto e sopravvivi 10 minuti.", F_BOLD, GREEN_LT, w / 2, 116);

        Rectangle[] cards = menuCards(game);
        for (int i = 0; i < cards.length; i++) {
            Rectangle r = cards[i];
            CatDef cat = Cats.ALL.get(i);
            boolean hover = r.contains(game.input.mouseX, game.input.mouseY);
            panel(g, r, hover);
            BufferedImage img = Sprites.catBig(cat);
            g.drawImage(img, r.x + (r.width - 72) / 2, r.y + 6, 72, 72, null);
            center(g, cat.name, F_NAME, GOLD, r.x + r.width / 2, r.y + 96);
            center(g, cat.breed, F_SMALL, GREEN_LT, r.x + r.width / 2, r.y + 112);
            int ty = r.y + 130;
            g.setColor(TEXT);
            g.setFont(F_SMALL);
            for (String line : wrap(g, cat.personality, F_SMALL, r.width - 22)) {
                center(g, line, F_SMALL, TEXT, r.x + r.width / 2, ty);
                ty += 14;
            }
            center(g, cat.bonus, F_SMALL, BLUE_LT, r.x + r.width / 2, r.y + 196);
            WeaponDef wd = Weapons.MAP.get(cat.startWeapon);
            g.drawImage(Sprites.icon(wd.id), r.x + r.width / 2 - 64, r.y + 210, 22, 22, null);
            g.setFont(F_SMALL);
            g.setColor(PINK_LT);
            g.drawString("Arma: " + wd.name, r.x + r.width / 2 - 38, r.y + 225);
        }
        center(g, "WASD / frecce per muoverti  •  le armi attaccano da sole  •  P pausa  •  M audio on/off",
                F_SMALL, HINT, w / 2, h - 18);
    }

    private static void drawHud(Game game, Graphics2D g, int w, int h) {
        Player p = game.player;
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
        if (Sfx.muted) {
            right(g, "AUDIO OFF [M]", F_SMALL, w - 12, 74);
        }
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
            int bw = Math.min(540, (int) (w * 0.7));
            int bx = (w - bw) / 2, by = h - 38;
            center(g, boss.def.name, F_SMALL, new Color(0xf2b1b1), w / 2, by - 6);
            g.setColor(new Color(0x1a0e0e));
            g.fillRect(bx - 2, by - 2, bw + 4, 18 + 4);
            g.setColor(new Color(0xd64545));
            g.fillRect(bx, by, (int) (bw * Util.clamp(boss.hp / boss.maxHp, 0, 1)), 18);
        }
    }

    private static void slot(Graphics2D g, int x, int y, BufferedImage icon, int level) {
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
        center(g, "MIAO! Livello " + game.player.level + "!", F_H2, GOLD, w / 2, h / 2 - 160);
        center(g, "Scegli un potenziamento (clic o tasti 1-" + game.choices.size() + ")", F_TEXT, GREEN_LT, w / 2, h / 2 - 134);
        Rectangle[] cards = levelUpCards(game);
        for (int i = 0; i < cards.length && i < game.choices.size(); i++) {
            Rectangle r = cards[i];
            Choice c = game.choices.get(i);
            boolean hover = r.contains(game.input.mouseX, game.input.mouseY);
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
        Player p = game.player;
        center(g, p.cat.name + " — " + p.cat.breed, F_BOLD, GREEN_LT, w / 2, h / 2 - 84);
        // build attuale
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
        center(g, "P o Esc per riprendere  •  M audio on/off", F_TEXT, HINT, w / 2, h / 2 + 60);
    }

    private static void drawEnd(Game game, Graphics2D g, int w, int h, boolean win) {
        g.setColor(OVERLAY);
        g.fillRect(0, 0, w, h);
        if (win) {
            center(g, "VITTORIA!", F_TITLE, GOLD, w / 2, h / 2 - 150);
            center(g, "Dieci minuti, zero bagnetti. Il giardino è di nuovo tuo.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
        } else {
            center(g, "GAME OVER", F_TITLE, RED_LT, w / 2, h / 2 - 150);
            center(g, "Il giardino ha avuto la meglio... per stavolta.", F_BOLD, GREEN_LT, w / 2, h / 2 - 112);
        }
        Player p = game.player;
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
            g.drawString("Nemici sconfitti: " + game.kills, box.x + 104, box.y + 118);
            g.drawString("Armi: " + p.weapons.size() + "  •  Passivi: " + p.passives.size(), box.x + 104, box.y + 138);
        }
        Rectangle btn = endButton(game);
        boolean hover = btn.contains(game.input.mouseX, game.input.mouseY);
        g.setColor(hover ? new Color(0xffe49e) : GOLD);
        g.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
        center(g, "Torna al rifugio (R)", F_BOLD, new Color(0x20301c), btn.x + btn.width / 2, btn.y + 31);
    }

    // ===== Helper =====

    private static void panel(Graphics2D g, Rectangle r, boolean hover) {
        g.setColor(hover ? PANEL_HOVER : PANEL);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g.setColor(hover ? GOLD : BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
    }

    private static void center(Graphics2D g, String s, Font f, Color c, int cx, int y) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    private static void right(Graphics2D g, String s, Font f, int rx, int y) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, rx - fm.stringWidth(s), y);
    }

    private static List<String> wrap(Graphics2D g, String text, Font f, int maxW) {
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
