package catsurvivors;

import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * Collante tra menu, partita locale/host e modalità client:
 * gatto selezionato, campo IP, connessioni in corso.
 */
final class App {
    enum Mode { LOCAL, CLIENT }

    static volatile Mode mode = Mode.LOCAL;
    static Game game;
    static Input input;
    static volatile Client client;
    static volatile int selectedCat = 0;
    static volatile StringBuilder ip = null; // campo IP attivo se != null
    static volatile String status = "";
    static volatile boolean connecting = false;

    private App() {}

    static void init(Game g, Input in) {
        game = g;
        input = in;
    }

    static boolean ipActive() { return ip != null; }

    static void openIpInput() {
        ip = new StringBuilder();
        status = "";
    }

    /** Tasti speciali del campo IP (i caratteri arrivano da Input.nextTyped). */
    static void ipKey(int code) {
        StringBuilder b = ip;
        if (b == null) return;
        switch (code) {
            case KeyEvent.VK_ESCAPE -> ip = null;
            case KeyEvent.VK_BACK_SPACE -> {
                if (b.length() > 0) b.deleteCharAt(b.length() - 1);
            }
            case KeyEvent.VK_ENTER -> {
                String addr = b.toString().trim();
                ip = null;
                if (!addr.isEmpty()) join(addr);
            }
            default -> { }
        }
    }

    /** Aggiunge i caratteri digitati al campo IP (chiamato mentre il campo è attivo). */
    static void ipType() {
        StringBuilder b = ip;
        Character ch;
        while ((ch = input.nextTyped()) != null) {
            if (b != null && b.length() < 45
                    && (Character.isLetterOrDigit(ch) || ch == '.' || ch == ':' || ch == '-')) {
                b.append(ch);
            }
        }
    }

    static void solo() {
        game.startRun(Cats.ALL.get(selectedCat));
    }

    static void host() {
        try {
            game.startHosting(Cats.ALL.get(selectedCat), Net.DEFAULT_PORT);
        } catch (IOException e) {
            status = "Impossibile aprire la porta " + Net.DEFAULT_PORT + ": " + e.getMessage();
        }
    }

    static void join(String addr) {
        if (connecting) return;
        connecting = true;
        status = "Connessione a " + addr + "...";
        Thread t = new Thread(() -> {
            Client c = new Client();
            try {
                c.connect(addr, selectedCat);
                client = c;
                mode = Mode.CLIENT;
                status = "";
            } catch (IOException e) {
                c.close();
                status = "Connessione fallita: " + e.getMessage();
            } finally {
                connecting = false;
            }
        }, "coop-connect");
        t.setDaemon(true);
        t.start();
    }

    static void backToMenu() {
        Client c = client;
        if (c != null) c.close();
        client = null;
        mode = Mode.LOCAL;
        status = "";
        game.toMenu();
    }
}
