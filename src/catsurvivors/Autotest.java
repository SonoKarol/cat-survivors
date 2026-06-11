package catsurvivors;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Smoke test headless: simula 30 secondi di partita con movimento sintetico,
 * sceglie automaticamente i potenziamenti e salva uno screenshot (autotest.png).
 * Uso: java -cp out catsurvivors.Main --autotest
 */
final class Autotest {
    private Autotest() {}

    static void run() throws Exception {
        Input input = new Input();
        Game game = new Game(input);
        App.init(game, input);
        game.startRun(Cats.ALL.get(0));

        int w = 1280, h = 720;
        double dt = 1 / 60.0;
        int frames = 60 * 30;
        for (int i = 0; i < frames; i++) {
            // gira in un piccolo cerchio: i nemici convergono e finiscono sotto i colpi
            input.keys.clear();
            int dir = (i / 60) % 4;
            input.keys.add(switch (dir) {
                case 0 -> KeyEvent.VK_D;
                case 1 -> KeyEvent.VK_S;
                case 2 -> KeyEvent.VK_A;
                default -> KeyEvent.VK_W;
            });
            // mira rotante col mouse virtuale, come farebbe un giocatore vero
            double aimAng = i * 0.04;
            input.mouseX = (int) (640 + Math.cos(aimAng) * 240);
            input.mouseY = (int) (360 + Math.sin(aimAng) * 240);
            game.step(dt, w, h);
            if (game.state == Game.State.LEVELUP && game.choices != null) {
                game.pickChoice(game.choices.get(0));
            }
        }

        BufferedImage shot = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shot.createGraphics();
        g.setColor(new java.awt.Color(0x131a12));
        g.fillRect(0, 0, w, h);
        game.render(g, w, h);
        Ui.draw(game, g, w, h);
        g.dispose();
        File out = new File("autotest.png");
        ImageIO.write(shot, "png", out);

        // screenshot anche del menu di selezione
        game.state = Game.State.MENU;
        BufferedImage menuShot = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D mg = menuShot.createGraphics();
        mg.setColor(new java.awt.Color(0x131a12));
        mg.fillRect(0, 0, w, h);
        game.render(mg, w, h);
        Ui.draw(game, mg, w, h);
        mg.dispose();
        ImageIO.write(menuShot, "png", new File("autotest-menu.png"));
        game.state = Game.State.PLAYING;

        Player p = game.localPlayer();
        System.out.println("[autotest] stato=" + game.state
                + " tempo=" + String.format("%.1f", game.time) + "s"
                + " nemici=" + game.enemies.size()
                + " ko=" + game.kills
                + " livello=" + p.level
                + " ps=" + (int) p.hp
                + " armi=" + p.weapons.size()
                + " passivi=" + p.passives.size());
        System.out.println("[autotest] screenshot: " + out.getAbsolutePath());
        if (game.kills == 0) {
            System.out.println("[autotest] ERRORE: nessun nemico sconfitto in 30s, qualcosa non va.");
            System.exit(1);
        }
    }
}
