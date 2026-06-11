package catsurvivors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tutta la grafica del gioco è disegnata proceduralmente con Java2D
 * e messa in cache come BufferedImage: nessun asset esterno.
 */
final class Sprites {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Sprites() {}

    static BufferedImage make(int w, int h, Consumer<Graphics2D> fn) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        fn.accept(g);
        g.dispose();
        return img;
    }

    // Nota: niente computeIfAbsent — le factory si richiamano a vicenda (icone che
    // compongono altri sprite) e l'annidamento sulla stessa HashMap lancerebbe CME.
    private static BufferedImage cached(String key, int w, int h, Consumer<Graphics2D> fn) {
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = make(w, h, fn);
            CACHE.put(key, img);
        }
        return img;
    }

    // ===== Gatti =====

    static BufferedImage cat(CatDef c) {
        return cached("cat:" + c.id, 48, 48, g -> drawCat(g, c.sprite));
    }

    /** Versione ingrandita per i menu. */
    static BufferedImage catBig(CatDef c) {
        return cached("catbig:" + c.id, 96, 96, g -> {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(cat(c), 0, 0, 96, 96, null);
        });
    }

    private static void drawCat(Graphics2D g, CatSprite s) {
        double f = s.fluffy ? 1.1 : 1.0;
        g.translate(24, 0);
        // coda
        g.setStroke(new BasicStroke(s.fluffy ? 7f : 4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(s.mask != null ? s.mask : (s.stripes != null ? s.stripes : s.body));
        g.draw(new QuadCurve2D.Double(10, 39, 22, 38, 21, 26));
        // corpo
        g.setColor(s.body);
        ell(g, 0, 36, 12 * f, 8.5 * f);
        if (s.belly != null) {
            g.setColor(s.belly);
            ell(g, 0, 38, 7 * f, 5.5 * f);
        }
        // zampe anteriori
        g.setColor(s.body);
        ell(g, -5, 43, 3, 2.5);
        ell(g, 5, 43, 3, 2.5);
        // orecchie
        double earH = s.sphynx ? 13 : 10;
        g.setColor(s.mask != null ? s.mask : s.body);
        tri(g, -11, 13, -8, 13 - earH, -2, 9);
        tri(g, 11, 13, 8, 13 - earH, 2, 9);
        g.setColor(new Color(0xf2a0b5));
        tri(g, -9, 12, -7.5, 12 - earH * 0.55, -4, 10);
        tri(g, 9, 12, 7.5, 12 - earH * 0.55, 4, 10);
        // testa
        g.setColor(s.body);
        ell(g, 0, 19, 11.5 * f, 10.5 * f);
        // guance pelose (razze a pelo lungo)
        if (s.fluffy) {
            tri(g, -11 * f, 17, -14 * f, 22, -8, 23);
            tri(g, 11 * f, 17, 14 * f, 22, 8, 23);
        }
        // muso scuro (point siamese/ragdoll)
        if (s.mask != null) {
            g.setColor(new Color(s.mask.getRed(), s.mask.getGreen(), s.mask.getBlue(), 215));
            ell(g, 0, 23, 6.5, 5);
        }
        // strisce del tigrato
        if (s.stripes != null) {
            g.setColor(s.stripes);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(-5, 10, -6, 14));
            g.draw(new Line2D.Double(0, 9, 0, 13));
            g.draw(new Line2D.Double(5, 10, 6, 14));
        }
        // rughe dello sphynx
        if (s.sphynx) {
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Arc2D.Double(-6, 10, 12, 5, 0, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(-5, 13, 10, 4, 0, 180, Arc2D.OPEN));
        }
        // occhi
        g.setColor(s.eyes);
        ell(g, -4.5, 18, 2.6, 3);
        ell(g, 4.5, 18, 2.6, 3);
        g.setColor(new Color(0x1c1c22));
        ell(g, -4.5, 18.4, 1.1, 2.2);
        ell(g, 4.5, 18.4, 1.1, 2.2);
        g.setColor(new Color(255, 255, 255, 220));
        ell(g, -3.9, 17.2, 0.7, 0.7);
        ell(g, 5.1, 17.2, 0.7, 0.7);
        // naso e bocca
        g.setColor(new Color(0xf7b9c4));
        tri(g, 0, 23.5, -1.8, 21.4, 1.8, 21.4);
        g.setColor(new Color(40, 30, 30, 180));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(0, 23.5, -1.5, 25.5, -3, 24.5));
        g.draw(new QuadCurve2D.Double(0, 23.5, 1.5, 25.5, 3, 24.5));
        // baffi
        g.setColor(new Color(255, 255, 255, 200));
        g.draw(new Line2D.Double(-7, 21.5, -14, 20.5));
        g.draw(new Line2D.Double(-7, 23, -14, 23.5));
        g.draw(new Line2D.Double(7, 21.5, 14, 20.5));
        g.draw(new Line2D.Double(7, 23, 14, 23.5));
        g.translate(-24, 0);
    }

    // ===== Nemici =====

    static BufferedImage enemy(String type) {
        String key = "en:" + type;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            int[] wh = enemySize(type);
            img = make(wh[0], wh[1], g -> drawEnemy(g, type, wh[0], wh[1]));
            CACHE.put(key, img);
        }
        return img;
    }

    private static int[] enemySize(String t) {
        return switch (t) {
            case "cetriolo" -> new int[]{28, 32};
            case "cetriolone" -> new int[]{44, 50};
            case "piccione" -> new int[]{30, 28};
            case "topo" -> new int[]{28, 24};
            case "anatra" -> new int[]{28, 30};
            case "spruzzino" -> new int[]{26, 36};
            case "aspirapolvere" -> new int[]{38, 46};
            case "roomba" -> new int[]{64, 64};
            case "phon" -> new int[]{56, 48};
            case "veterinario" -> new int[]{52, 68};
            default -> new int[]{24, 24};
        };
    }

    private static void drawEnemy(Graphics2D g, String type, int w, int h) {
        switch (type) {
            case "cetriolo" -> drawCuke(g, w, h, new Color(0x4f9e3c), new Color(0x6cbf4e), new Color(0x33701f));
            case "cetriolone" -> drawCuke(g, w, h, new Color(0x3f8a30), new Color(0x5cab41), new Color(0x265c1a));
            case "piccione" -> drawPigeon(g);
            case "topo" -> drawRobotMouse(g);
            case "anatra" -> drawDuck(g);
            case "spruzzino" -> drawSprayer(g);
            case "aspirapolvere" -> drawVacuum(g);
            case "roomba" -> drawRoomba(g);
            case "phon" -> drawHairdryer(g);
            case "veterinario" -> drawVet(g);
            default -> { g.setColor(Color.MAGENTA); ell(g, w / 2.0, h / 2.0, w / 2.5, h / 2.5); }
        }
    }

    /** Il terrore di ogni gatto: un cetriolo arrabbiato. */
    private static void drawCuke(Graphics2D g, int w, int h, Color body, Color light, Color dark) {
        double sx = w / 28.0, sy = h / 32.0;
        g.translate(w / 2.0, h / 2.0);
        g.rotate(-0.22);
        g.setColor(body);
        ell(g, 0, 0, 8.5 * sx, 13 * sy);
        g.setColor(light);
        ell(g, -2.5 * sx, -1 * sy, 3.6 * sx, 10.5 * sy);
        g.setColor(dark);
        double[][] bumps = {{-4, -9}, {5, -6}, {-5, 3}, {4, 7}, {0, 11}, {2, -12}};
        for (double[] b : bumps) ell(g, b[0] * sx, b[1] * sy, 1.3 * sx, 1.3 * sx);
        // occhi arrabbiati
        g.setColor(Color.WHITE);
        ell(g, -3.4 * sx, -3.5 * sy, 2.6 * sx, 2.8 * sy);
        ell(g, 3.4 * sx, -3.5 * sy, 2.6 * sx, 2.8 * sy);
        g.setColor(new Color(0x222222));
        ell(g, -2.8 * sx, -3 * sy, 1.1 * sx, 1.2 * sy);
        ell(g, 4 * sx, -3 * sy, 1.1 * sx, 1.2 * sy);
        g.setStroke(new BasicStroke((float) (1.6 * sx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x1e3d18));
        g.draw(new Line2D.Double(-6 * sx, -7.5 * sy, -1.5 * sx, -5.5 * sy));
        g.draw(new Line2D.Double(6 * sx, -7.5 * sy, 1.5 * sx, -5.5 * sy));
        // smorfia
        g.draw(new QuadCurve2D.Double(-3 * sx, 3.5 * sy, 0, 1.5 * sy, 3 * sx, 3.5 * sy));
    }

    private static void drawPigeon(Graphics2D g) {
        // corpo
        g.setColor(new Color(0x8d97a3));
        ell(g, 14, 16, 9.5, 7.5);
        // coda
        g.setColor(new Color(0x6d7681));
        tri(g, 5, 14, 0, 11, 2, 19);
        // ala
        g.setColor(new Color(0xaab3bd));
        ell(g, 12, 14.5, 5.5, 4);
        // testa
        g.setColor(new Color(0x7d8794));
        ell(g, 22.5, 9.5, 4.5, 4.5);
        // becco
        g.setColor(new Color(0xe8a13c));
        tri(g, 27, 9, 30, 10.5, 26.5, 11.5);
        // occhio
        g.setColor(new Color(0xf2622e));
        ell(g, 23.5, 8.5, 1.3, 1.3);
        g.setColor(Color.BLACK);
        ell(g, 23.7, 8.7, 0.6, 0.6);
        // zampe
        g.setColor(new Color(0xd77f4e));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(11, 23, 10, 27));
        g.draw(new Line2D.Double(16, 23, 16, 27));
    }

    private static void drawRobotMouse(Graphics2D g) {
        // coda a zig-zag
        g.setColor(new Color(0x6f7682));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D z = new Path2D.Double();
        z.moveTo(4, 14); z.lineTo(1, 11); z.lineTo(4, 9); z.lineTo(1, 6);
        g.draw(z);
        // orecchie
        g.setColor(new Color(0xb8bec9));
        ell(g, 9, 8, 3.5, 3.5);
        ell(g, 15, 7, 3.5, 3.5);
        g.setColor(new Color(0xf2a0b5));
        ell(g, 9, 8, 1.8, 1.8);
        ell(g, 15, 7, 1.8, 1.8);
        // corpo a goccia
        g.setColor(new Color(0x9aa1ad));
        ell(g, 13, 15, 9, 6.5);
        // muso
        g.setColor(new Color(0xb8bec9));
        tri(g, 19, 11, 24, 14, 19, 17);
        g.setColor(new Color(0xd3636f));
        ell(g, 23.5, 14, 1.8, 1.8);
        // occhio LED
        g.setColor(new Color(0xff4040));
        ell(g, 16.5, 12.5, 1.6, 1.6);
        g.setColor(new Color(255, 120, 120, 120));
        ell(g, 16.5, 12.5, 2.8, 2.8);
        // antenna
        g.setColor(new Color(0x6f7682));
        g.draw(new Line2D.Double(11, 10, 11, 4));
        g.setColor(new Color(0xffd166));
        ell(g, 11, 3.5, 1.5, 1.5);
        // rotelle
        g.setColor(new Color(0x4a4f58));
        ell(g, 9, 21, 2.2, 2.2);
        ell(g, 17, 21, 2.2, 2.2);
    }

    private static void drawDuck(Graphics2D g) {
        // corpo
        g.setColor(new Color(0xf4d03f));
        ell(g, 14, 19, 10, 7.5);
        // ala
        g.setColor(new Color(0xe2bb2d));
        ell(g, 11, 18.5, 5, 3.8);
        // testa
        g.setColor(new Color(0xf4d03f));
        ell(g, 18, 9, 6, 6);
        // becco
        g.setColor(new Color(0xef8e2e));
        ell(g, 25, 10.5, 3.4, 2);
        // occhio
        g.setColor(Color.BLACK);
        ell(g, 19.5, 7.5, 1.3, 1.3);
        g.setColor(Color.WHITE);
        ell(g, 19.9, 7.1, 0.5, 0.5);
        // riflesso di gomma
        g.setColor(new Color(255, 255, 255, 90));
        ell(g, 11, 15.5, 2.5, 1.5);
    }

    private static void drawSprayer(Graphics2D g) {
        // corpo della bottiglia
        g.setColor(new Color(0x5fa8d3));
        g.fill(new RoundRectangle2D.Double(7, 14, 12, 19, 4, 4));
        // livello dell'acqua
        g.setColor(new Color(0x8ec7e8));
        g.fill(new RoundRectangle2D.Double(8.5, 22, 9, 9.5, 3, 3));
        // testa con grilletto
        g.setColor(new Color(0x3c6e91));
        g.fill(new RoundRectangle2D.Double(8, 6, 10, 8, 2, 2));
        g.fill(new RoundRectangle2D.Double(17, 7, 5, 3, 1, 1)); // beccuccio
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(9, 14, 5, 15, 6, 19)); // grilletto
        // goccioline minacciose
        g.setColor(new Color(0xbfe3f5));
        ell(g, 23.5, 6.5, 1.2, 1.6);
        ell(g, 24.5, 10, 1, 1.3);
        // occhio cattivo stampato sul flacone
        g.setColor(Color.WHITE);
        ell(g, 13, 18, 2.6, 2.6);
        g.setColor(new Color(0x222222));
        ell(g, 13.7, 18.3, 1.1, 1.1);
    }

    private static void drawVacuum(Graphics2D g) {
        // corpo
        g.setColor(new Color(0x7a3b46));
        g.fill(new RoundRectangle2D.Double(8, 16, 22, 26, 6, 6));
        // sacco
        g.setColor(new Color(0xa05262));
        ell(g, 19, 27, 8, 10);
        // manico
        g.setColor(new Color(0x4a2a30));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(19, 16, 19, 5));
        ell(g, 19, 4, 3, 2);
        // occhio-faro
        g.setColor(new Color(255, 209, 102, 110));
        ell(g, 19, 21, 5.5, 5.5);
        g.setColor(new Color(0xffd166));
        ell(g, 19, 21, 3, 3);
        // ruote
        g.setColor(new Color(0x2e2e34));
        ell(g, 12, 43, 3, 3);
        ell(g, 26, 43, 3, 3);
    }

    private static void drawRoomba(Graphics2D g) {
        // disco
        g.setColor(new Color(0x2e3440));
        ell(g, 32, 32, 27, 27);
        g.setColor(new Color(0x4c566a));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(12, 12, 40, 40));
        // paraurti
        g.setColor(new Color(0x3b4252));
        g.fill(new Arc2D.Double(5, 5, 54, 54, 50, 80, Arc2D.PIE));
        g.setColor(new Color(0x2e3440));
        ell(g, 32, 32, 22, 22);
        // pulsante centrale
        g.setColor(new Color(0x88c0d0));
        ell(g, 32, 32, 6.5, 6.5);
        g.setColor(new Color(0x2e3440));
        ell(g, 32, 32, 3, 3);
        // LED arrabbiati
        g.setColor(new Color(255, 70, 70, 130));
        ell(g, 23, 23, 5, 5);
        ell(g, 41, 23, 5, 5);
        g.setColor(new Color(0xff4040));
        ell(g, 23, 23, 2.8, 2.8);
        ell(g, 41, 23, 2.8, 2.8);
        // spazzole
        g.setColor(new Color(0x6f7682));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 0.55 + i * 0.18;
            g.draw(new Line2D.Double(32 + Math.cos(a) * 26, 32 + Math.sin(a) * 26,
                    32 + Math.cos(a) * 32, 32 + Math.sin(a) * 32));
        }
    }

    private static void drawHairdryer(Graphics2D g) {
        // impugnatura
        g.setColor(new Color(0x9a4a72));
        g.fill(new RoundRectangle2D.Double(14, 26, 9, 18, 4, 4));
        // corpo
        g.setColor(new Color(0xc95d8e));
        ell(g, 22, 18, 14, 11);
        // bocchettone
        g.fill(new RoundRectangle2D.Double(33, 12, 14, 12, 3, 3));
        g.setColor(new Color(0x9a4a72));
        g.fill(new RoundRectangle2D.Double(45, 11, 3, 14, 1, 1));
        // aria
        g.setColor(new Color(0xbfe3f5));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(49, 13, 53, 15, 50, 18));
        g.draw(new QuadCurve2D.Double(50, 19, 54, 21, 51, 24));
        // interruttore
        g.setColor(new Color(0xffd166));
        g.fill(new RoundRectangle2D.Double(16, 30, 4, 7, 2, 2));
        // occhio cattivo
        g.setColor(Color.WHITE);
        ell(g, 19, 16, 3.2, 3.2);
        g.setColor(new Color(0x222222));
        ell(g, 20, 16.5, 1.4, 1.4);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x5e2c45));
        g.draw(new Line2D.Double(15.5, 12.5, 22, 14));
    }

    private static void drawVet(Graphics2D g) {
        // gambe
        g.setColor(new Color(0x3a4a5a));
        g.fill(new RoundRectangle2D.Double(18, 52, 7, 14, 2, 2));
        g.fill(new RoundRectangle2D.Double(28, 52, 7, 14, 2, 2));
        // camice
        g.setColor(new Color(0xf0f2f5));
        g.fill(new RoundRectangle2D.Double(13, 26, 27, 29, 8, 8));
        g.setColor(new Color(0xd5dae2));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Line2D.Double(26.5, 28, 26.5, 53));
        // braccio con siringa
        g.setColor(new Color(0xf0f2f5));
        g.fill(new RoundRectangle2D.Double(36, 30, 12, 6, 3, 3));
        g.setColor(new Color(0xcfe3f0));
        g.fill(new RoundRectangle2D.Double(44, 28, 7, 4, 1, 1));
        g.setColor(new Color(0x8aa3b8));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(51, 30, 56, 30)); // ago... che brividi
        // testa
        g.setColor(new Color(0xe8b88c));
        ell(g, 26, 15, 10, 10);
        // capelli
        g.setColor(new Color(0x5a4632));
        g.fill(new Arc2D.Double(16, 4, 20, 14, 0, 180, Arc2D.PIE));
        // occhiali
        g.setColor(new Color(0x2b2b30));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(18, 13, 6.5, 6));
        g.draw(new Ellipse2D.Double(27.5, 13, 6.5, 6));
        g.draw(new Line2D.Double(24.5, 15.5, 27.5, 15.5));
        // sopracciglia severe
        g.draw(new Line2D.Double(18, 11.5, 24, 13));
        g.draw(new Line2D.Double(34, 11.5, 28, 13));
        // bocca seria
        g.draw(new Line2D.Double(23, 22.5, 29, 22.5));
        // stetoscopio
        g.setColor(new Color(0x4a6a8a));
        g.draw(new QuadCurve2D.Double(20, 28, 26, 36, 32, 28));
        ell(g, 26, 37, 2.4, 2.4);
    }

    // ===== Gemme e raccoglibili =====

    static BufferedImage gem(int value) {
        String tier = value >= 50 ? "gold" : value >= 20 ? "red" : value >= 5 ? "green" : "blue";
        return cached("gem:" + tier, 16, 20, g -> {
            Color c = switch (tier) {
                case "gold" -> new Color(0xffd166);
                case "red" -> new Color(0xf56a93);
                case "green" -> new Color(0x7ddb66);
                default -> new Color(0x5ec8f0);
            };
            Path2D d = new Path2D.Double();
            d.moveTo(8, 1); d.lineTo(14.5, 8); d.lineTo(8, 19); d.lineTo(1.5, 8); d.closePath();
            g.setColor(c);
            g.fill(d);
            g.setColor(new Color(255, 255, 255, 130));
            Path2D top = new Path2D.Double();
            top.moveTo(8, 1); top.lineTo(11, 8); top.lineTo(5, 8); top.closePath();
            g.fill(top);
            g.setColor(c.darker());
            g.setStroke(new BasicStroke(1f));
            g.draw(d);
        });
    }

    static BufferedImage pickup(String kind) {
        if (kind.equals("croccantino")) {
            return cached("pk:croc", 22, 16, g -> {
                // croccantino a forma di pesce
                g.setColor(new Color(0xe8a13c));
                ell(g, 9, 8, 7, 5);
                tri(g, 15, 8, 20, 4, 20, 12);
                g.setColor(new Color(0xc8842a));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Ellipse2D.Double(2, 3, 14, 10));
                g.setColor(new Color(0x6b4a1c));
                ell(g, 6, 7, 1.1, 1.1);
            });
        }
        return cached("pk:mag", 22, 22, g -> {
            // calamita a ferro di cavallo
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xd64545));
            g.draw(new Arc2D.Double(4, 4, 14, 14, 0, 180, Arc2D.OPEN));
            g.draw(new Line2D.Double(6.5, 11, 6.5, 17));
            g.draw(new Line2D.Double(15.5, 11, 15.5, 17));
            g.setColor(new Color(0xd5dae2));
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(6.5, 17, 6.5, 20));
            g.draw(new Line2D.Double(15.5, 17, 15.5, 20));
        });
    }

    // ===== Proiettili =====

    static BufferedImage proj(String name) {
        return switch (name) {
            case "gomitolo" -> cached("pr:gomitolo", 20, 20, g -> {
                g.setColor(new Color(0xe07a9a));
                ell(g, 10, 10, 8.5, 8.5);
                g.setColor(new Color(0xf3b1c6));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new Arc2D.Double(2.5, 4, 15, 13, 30, 120, Arc2D.OPEN));
                g.draw(new Arc2D.Double(3.5, 8, 13, 9, 200, 120, Arc2D.OPEN));
                g.draw(new Arc2D.Double(6, 2.5, 9, 15, 100, 130, Arc2D.OPEN));
            });
            case "pallapelo" -> cached("pr:pallapelo", 22, 22, g -> {
                g.setColor(new Color(0x9a8f85));
                ell(g, 11, 11, 8, 8);
                g.setColor(new Color(0x7a7066));
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 10; i++) {
                    double a = Util.TAU / 10 * i;
                    g.draw(new Line2D.Double(11 + Math.cos(a) * 7, 11 + Math.sin(a) * 7,
                            11 + Math.cos(a + 0.3) * 10.5, 11 + Math.sin(a + 0.3) * 10.5));
                }
                g.setColor(new Color(0xb5aba0));
                ell(g, 8.5, 8.5, 3, 3);
            });
            case "sardina" -> cached("pr:sardina", 18, 10, g -> {
                g.setColor(new Color(0x7ab3d4));
                ell(g, 8, 5, 6.5, 3.4);
                tri(g, 13, 5, 17.5, 1.5, 17.5, 8.5);
                g.setColor(new Color(0xa9d2e8));
                ell(g, 6.5, 4, 3, 1.4);
                g.setColor(Color.BLACK);
                ell(g, 3.5, 4.5, 0.9, 0.9);
            });
            case "artiglio" -> cached("pr:artiglio", 20, 20, g -> {
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 255, 255, 110));
                g.draw(new Arc2D.Double(2, 2, 16, 16, 300, 140, Arc2D.OPEN));
                g.setColor(new Color(0xf5f7fa));
                g.draw(new Arc2D.Double(4, 1, 13, 17, 300, 130, Arc2D.OPEN));
                g.draw(new Arc2D.Double(7, 3, 10, 14, 300, 120, Arc2D.OPEN));
            });
            case "crocc" -> cached("pr:crocc", 12, 12, g -> {
                g.setColor(new Color(0xd98c3f));
                g.fill(new RoundRectangle2D.Double(1.5, 1.5, 9, 9, 4, 4));
                g.setColor(new Color(0xb56f2c));
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(1.5, 1.5, 9, 9, 4, 4));
                g.setColor(new Color(0xeec27e));
                ell(g, 4.5, 4.5, 1.5, 1.5);
            });
            default -> cached("pr:?", 8, 8, g -> { g.setColor(Color.WHITE); ell(g, 4, 4, 3, 3); });
        };
    }

    // ===== Icone per HUD e level up =====

    static BufferedImage icon(String id) {
        return cached("ic:" + id, 26, 26, g -> drawIcon(g, id));
    }

    private static void drawIcon(Graphics2D g, String id) {
        switch (id) {
            case "graffio" -> {
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0xf5f7fa));
                for (int i = -1; i <= 1; i++) {
                    g.draw(new QuadCurve2D.Double(6 + i * 6, 4, 10 + i * 6, 13, 6 + i * 6, 22));
                }
            }
            case "gomitolo" -> g.drawImage(proj("gomitolo"), 3, 3, null);
            case "pallapelo" -> g.drawImage(proj("pallapelo"), 2, 2, null);
            case "fusa" -> {
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0xf2a0b5));
                g.draw(new Arc2D.Double(8, 8, 10, 10, 0, 360, Arc2D.OPEN));
                g.setColor(new Color(242, 160, 181, 140));
                g.draw(new Arc2D.Double(4, 4, 18, 18, 20, 140, Arc2D.OPEN));
                g.setColor(new Color(242, 160, 181, 80));
                g.draw(new Arc2D.Double(1, 1, 24, 24, 20, 140, Arc2D.OPEN));
            }
            case "miagolio" -> {
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0x8fd3f4));
                g.draw(new Arc2D.Double(2, 5, 10, 16, -50, 100, Arc2D.OPEN));
                g.draw(new Arc2D.Double(6, 2, 14, 22, -50, 100, Arc2D.OPEN));
                g.setColor(new Color(143, 211, 244, 120));
                g.draw(new Arc2D.Double(10, -1, 18, 28, -50, 100, Arc2D.OPEN));
            }
            case "artigli" -> g.drawImage(proj("artiglio"), 3, 3, null);
            case "sardine" -> g.drawImage(proj("sardina"), 4, 8, null);
            case "croccantini" -> {
                g.drawImage(proj("crocc"), 2, 10, null);
                g.drawImage(proj("crocc"), 12, 4, null);
                g.setColor(new Color(0xffd166));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(8, 2, 8, 6));
                g.draw(new Line2D.Double(20, 16, 20, 20));
            }
            case "latte" -> {
                g.setColor(new Color(0xd5dae2));
                g.fill(new Arc2D.Double(3, 10, 20, 14, 180, 180, Arc2D.PIE));
                g.setColor(new Color(0xf7f9fc));
                ell(g, 13, 11, 9, 2.6);
                g.setColor(new Color(0x9aa6b5));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Arc2D.Double(3, 10, 20, 14, 180, 180, Arc2D.OPEN));
            }
            case "erbagatta" -> {
                g.setColor(new Color(0x6cbf4e));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(13, 23, 13, 9));
                Ellipse2D leaf = new Ellipse2D.Double(0, 0, 9, 4.5);
                for (int i = 0; i < 3; i++) {
                    double y = 8 + i * 5;
                    g.fill(rotated(leaf, 13, y, -0.6, -9, -2));
                    g.fill(rotated(leaf, 13, y, 0.6 + Math.PI, 0, -2));
                }
            }
            case "campanellino" -> {
                g.setColor(new Color(0xffd166));
                g.fill(new Arc2D.Double(5, 4, 16, 18, 0, 180, Arc2D.PIE));
                g.fill(new RoundRectangle2D.Double(4, 12, 18, 4, 2, 2));
                ell(g, 13, 19, 2.5, 2.5);
                g.setColor(new Color(0xc8842a));
                ell(g, 13, 4.5, 2, 2);
            }
            case "pesciolino" -> {
                g.setColor(new Color(0x9fb8c8));
                ell(g, 10, 13, 7.5, 4.2);
                tri(g, 16, 13, 22, 8.5, 22, 17.5);
                g.setColor(Color.BLACK);
                ell(g, 5.5, 12, 1, 1);
                g.setColor(new Color(0x7a96a8));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Line2D.Double(8, 10, 8, 16));
                g.draw(new Line2D.Double(11, 9.7, 11, 16.3));
            }
            case "cuscino" -> {
                g.setColor(new Color(0xb06a8c));
                g.fill(new RoundRectangle2D.Double(3, 6, 20, 14, 7, 7));
                g.setColor(new Color(0xd490b2));
                g.fill(new RoundRectangle2D.Double(5, 8, 16, 10, 5, 5));
                g.setColor(new Color(0x8c5070));
                ell(g, 13, 13, 1.4, 1.4);
            }
            case "zampe" -> {
                g.setColor(new Color(0xf2a0b5));
                ell(g, 13, 16, 6, 5);
                ell(g, 6, 9, 2.4, 3);
                ell(g, 11, 6.5, 2.4, 3);
                ell(g, 16, 6.5, 2.4, 3);
                ell(g, 21, 9, 2.4, 3);
            }
            case "collare" -> {
                g.setStroke(new BasicStroke(3f));
                g.setColor(new Color(0xd64545));
                g.draw(new Ellipse2D.Double(4, 4, 18, 14));
                g.setColor(new Color(0xffd166));
                ell(g, 13, 20, 3.5, 3.5);
                g.setColor(new Color(0xc8842a));
                ell(g, 13, 20, 1.2, 1.2);
            }
            case "scorta" -> {
                g.drawImage(proj("gomitolo"), 0, 6, null);
                g.drawImage(proj("gomitolo"), 8, 2, null);
            }
            case "calamita" -> g.drawImage(pickup("magnete"), 2, 2, null);
            case "fortunato" -> {
                g.setColor(new Color(0x6cbf4e));
                ell(g, 9, 9, 4.5, 4.5);
                ell(g, 17, 9, 4.5, 4.5);
                ell(g, 9, 17, 4.5, 4.5);
                ell(g, 17, 17, 4.5, 4.5);
                g.setColor(new Color(0x4f9e3c));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Double(13, 14, 15, 20, 19, 24));
            }
            case "heal" -> {
                g.setColor(new Color(0xf56a93));
                ell(g, 9, 9, 5.5, 5.5);
                ell(g, 17, 9, 5.5, 5.5);
                tri(g, 4, 11.5, 22, 11.5, 13, 23);
            }
            case "xp" -> {
                g.setColor(new Color(0xffd166));
                Path2D star = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double rr = (i % 2 == 0) ? 11 : 4.6;
                    double a = -Math.PI / 2 + Util.TAU / 10 * i;
                    double px = 13 + Math.cos(a) * rr, py = 13 + Math.sin(a) * rr;
                    if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.closePath();
                g.fill(star);
            }
            default -> { g.setColor(Color.GRAY); ell(g, 13, 13, 9, 9); }
        }
    }

    // ===== Helper geometrici =====

    private static void ell(Graphics2D g, double cx, double cy, double rx, double ry) {
        g.fill(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
    }

    private static void tri(Graphics2D g, double x1, double y1, double x2, double y2, double x3, double y3) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.closePath();
        g.fill(p);
    }

    /** Ruota una forma intorno a un punto e la trasla (per foglie e dettagli). */
    private static java.awt.Shape rotated(java.awt.Shape s, double cx, double cy, double angle, double ox, double oy) {
        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform();
        t.translate(cx, cy);
        t.rotate(angle);
        t.translate(ox, oy);
        return t.createTransformedShape(s);
    }
}
