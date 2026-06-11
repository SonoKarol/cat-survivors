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
    // stato del port forwarding automatico (UPnP), mostrato in lobby
    static volatile String coopUpnp = "";
    static volatile String coopPublic = "";
    static volatile Upnp.Result upnp;

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
            return;
        }
        // chiedi al router di inoltrare la porta verso questo PC (per gli amici fuori dalla LAN)
        coopUpnp = "Port forwarding automatico (UPnP) in corso...";
        coopPublic = "";
        Thread t = new Thread(() -> {
            Upnp.Result r = Upnp.openPort(Net.DEFAULT_PORT, Server.lanIp());
            upnp = r;
            if (r.mapped && r.externalIp != null) {
                coopUpnp = "Internet: porta aperta sul router (UPnP)";
                coopPublic = r.externalIp + ":" + Net.DEFAULT_PORT;
            } else if (r.externalIp != null) {
                coopUpnp = "UPnP non disponibile — apri tu la porta TCP " + Net.DEFAULT_PORT;
                coopPublic = r.externalIp + ":" + Net.DEFAULT_PORT + " (dopo il forwarding)";
            } else {
                coopUpnp = r.message;
            }
        }, "coop-upnp");
        t.setDaemon(true);
        t.start();
    }

    /** Chiamato quando l'host torna al menu: rimuove la mappatura UPnP. */
    static void onHostStop() {
        Upnp.Result r = upnp;
        upnp = null;
        coopUpnp = "";
        coopPublic = "";
        if (r != null && r.mapped) {
            Thread t = new Thread(() -> Upnp.closePort(r), "coop-upnp-close");
            t.setDaemon(true);
            t.start();
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
