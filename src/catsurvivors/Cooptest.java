package catsurvivors;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Smoke test del co-op, headless e in un solo processo: avvia un host con lobby,
 * connette un client via TCP su localhost, simula 20 secondi di partita e
 * verifica snapshot, movimento remoto, uccisioni e level up di rete.
 * Uso: java -cp out catsurvivors.Main --cooptest
 */
final class Cooptest {
    private Cooptest() {}

    static void run() throws Exception {
        int port = 17777;
        Input hostInput = new Input();
        Game game = new Game(hostInput);
        App.init(game, hostInput);
        game.startHosting(Cats.ALL.get(0), port); // l'host è Romeo

        Client client = new Client();
        client.connect("127.0.0.1:" + port, 4); // l'amico è Felix

        double dt = 1 / 60.0;
        // lobby: lascia che HELLO/WELCOME facciano il giro
        for (int i = 0; i < 60 && game.players.size() < 2; i++) {
            game.step(dt, 1280, 720);
            Thread.sleep(2);
        }
        check(game.players.size() == 2, "il client non si è registrato nella lobby");
        check(game.state == Game.State.LOBBY, "stato lobby atteso, trovato " + game.state);

        game.startRunMulti();

        double remoteStartX = game.players.get(1).x;
        int levelupsServed = 0;
        for (int i = 0; i < 60 * 20; i++) {
            // host: gira in un piccolo cerchio e mira ruotando il mouse virtuale
            hostInput.keys.clear();
            int dir = (i / 60) % 4;
            hostInput.keys.add(switch (dir) {
                case 0 -> KeyEvent.VK_D;
                case 1 -> KeyEvent.VK_S;
                case 2 -> KeyEvent.VK_A;
                default -> KeyEvent.VK_W;
            });
            double aimAng = i * 0.05;
            hostInput.mouseX = (int) (640 + Math.cos(aimAng) * 220);
            hostInput.mouseY = (int) (360 + Math.sin(aimAng) * 220);
            // client: si muove verso destra e mira in alto a destra
            client.sendInput(1, 0, 0.7, -0.7);

            game.step(dt, 1280, 720);

            // a metà test, forza un level up del giocatore remoto per provare il giro completo
            if (i == 600) game.players.get(1).gainXp(40);

            // level up: l'host sceglie da solo, per il client simula il click remoto
            if (game.state == Game.State.LEVELUP && game.choices != null) {
                if (game.leveling == game.localPlayer()) {
                    game.pickChoice(game.choices.get(0));
                } else if (client.choices != null) {
                    client.sendChoice(0);
                    levelupsServed++;
                }
            }
            Thread.sleep(1);
        }

        check(client.error == null, "errore di connessione lato client: " + client.error);
        check(client.myPid == 1, "pid del client atteso 1, trovato " + client.myPid);
        Snapshot s = client.latest;
        check(s != null, "il client non ha mai ricevuto uno snapshot");
        check(s.players.length == 2, "snapshot con " + s.players.length + " giocatori invece di 2");
        check(game.players.get(1).x > remoteStartX + 200,
                "il giocatore remoto non si è mosso con l'input di rete (x=" + game.players.get(1).x + ")");
        check(game.kills > 0, "nessun nemico sconfitto in 20 secondi di co-op");
        check(s.enemies.length > 0, "nessun nemico nello snapshot");
        check(levelupsServed > 0, "il level up remoto non ha fatto il giro host->client->host");
        check(game.players.get(1).level >= 2, "la scelta remota non è stata applicata dall'host");

        // screenshot della vista del client
        BufferedImage shot = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shot.createGraphics();
        g.setColor(new java.awt.Color(0x131a12));
        g.fillRect(0, 0, 1280, 720);
        Input dummy = new Input();
        dummy.mouseX = 800;
        dummy.mouseY = 300;
        ClientView.frame(client, dummy, g, 1280, 720);
        g.dispose();
        ImageIO.write(shot, "png", new File("cooptest-client.png"));

        System.out.println("[cooptest] PASS — giocatori=" + game.players.size()
                + " ko=" + game.kills
                + " livelloHost=" + game.players.get(0).level
                + " livelloClient=" + game.players.get(1).level
                + " levelUpRemoti=" + levelupsServed
                + " nemiciSnapshot=" + s.enemies.length);
        System.out.println("[cooptest] screenshot client: " + new File("cooptest-client.png").getAbsolutePath());

        client.close();
        game.toMenu();
        System.exit(0);
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            System.out.println("[cooptest] FAIL: " + msg);
            System.exit(1);
        }
    }
}
