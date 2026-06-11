package catsurvivors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Lato ospite del co-op: invia input all'host e riceve snapshot da renderizzare.
 * Un thread lettore aggiorna `latest`/`prev`; il thread di gioco li legge.
 */
final class Client {
    private Socket sock;
    private DataOutputStream out;
    volatile Snapshot latest, prev;
    volatile List<Choice> choices; // scelte di level up in attesa (solo quando tocca a noi)
    volatile int myPid = -1;
    volatile String error;

    void connect(String addr, int catIdx) throws IOException {
        String host = addr.trim();
        int port = Net.DEFAULT_PORT;
        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) { }
        }
        if (host.isEmpty()) host = "localhost";

        sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), 5000);
        sock.setTcpNoDelay(true);
        out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));

        synchronized (this) {
            out.writeByte(Net.HELLO);
            out.writeInt(catIdx);
            out.flush();
        }

        Thread t = new Thread(() -> readLoop(in), "coop-client-read");
        t.setDaemon(true);
        t.start();
    }

    private void readLoop(DataInputStream in) {
        try {
            while (true) {
                byte t = in.readByte();
                switch (t) {
                    case Net.WELCOME -> myPid = in.readInt();
                    case Net.SNAPSHOT -> {
                        Snapshot s = Net.readSnapshot(in);
                        s.arrivedAt = System.nanoTime();
                        prev = latest;
                        latest = s;
                        // se l'host è andato avanti, le vecchie carte non valgono più
                        if (s.levelingPid != myPid) choices = null;
                    }
                    case Net.LEVELUP -> {
                        choices = Net.readChoices(in);
                        Sfx.play("levelup");
                    }
                    default -> throw new IOException("messaggio sconosciuto: " + t);
                }
            }
        } catch (IOException e) {
            if (error == null) error = "Connessione persa";
        }
    }

    void sendInput(double moveX, double moveY, double aimX, double aimY) {
        try {
            synchronized (this) {
                out.writeByte(Net.INPUT);
                out.writeFloat((float) moveX);
                out.writeFloat((float) moveY);
                out.writeFloat((float) aimX);
                out.writeFloat((float) aimY);
                out.flush();
            }
        } catch (IOException e) {
            if (error == null) error = "Connessione persa";
        }
    }

    void sendChoice(int idx) {
        try {
            synchronized (this) {
                out.writeByte(Net.CHOICE);
                out.writeInt(idx);
                out.flush();
            }
        } catch (IOException e) {
            if (error == null) error = "Connessione persa";
        }
        choices = null; // se ci sono altri level up, l'host rimanda le carte
    }

    void close() {
        try { if (sock != null) sock.close(); } catch (IOException ignored) { }
    }
}
