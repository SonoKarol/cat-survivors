package catsurvivors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Lato host del co-op. Accetta connessioni su un thread dedicato; ogni connessione
 * ha un thread lettore e uno scrittore. La simulazione resta sul thread di gioco:
 * i messaggi vengono accodati e applicati da pump().
 */
final class Server {
    private final ServerSocket ss;
    private final Game game;
    private final List<Conn> conns = new CopyOnWriteArrayList<>();
    private volatile boolean closed = false;

    Server(Game game, int port) throws IOException {
        this.game = game;
        ss = new ServerSocket(port);
        Thread t = new Thread(this::acceptLoop, "coop-accept");
        t.setDaemon(true);
        t.start();
    }

    int port() { return ss.getLocalPort(); }

    int clientCount() { return conns.size(); }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                Conn c = new Conn(s);
                conns.add(c);
                c.start();
            } catch (IOException e) {
                if (closed) return;
            }
        }
    }

    /** Applica i messaggi dei client alla simulazione. Da chiamare sul thread di gioco. */
    void pump() {
        for (Conn c : conns) {
            Object[] m;
            while ((m = c.inbox.poll()) != null) {
                switch ((int) m[0]) {
                    case Net.HELLO -> {
                        if (game.state == Game.State.LOBBY && c.player == null
                                && game.players.size() < Net.MAX_PLAYERS) {
                            int ci = Math.floorMod((int) m[1], Cats.ALL.size());
                            c.player = game.addRemotePlayer(Cats.ALL.get(ci));
                            c.send(Net.welcome(c.player.pid));
                        } else {
                            c.dead = true; // partita già iniziata o piena
                        }
                    }
                    case Net.INPUT -> {
                        if (c.player != null) {
                            c.player.inMoveX = Util.clamp((float) m[1], -1, 1);
                            c.player.inMoveY = Util.clamp((float) m[2], -1, 1);
                            c.player.inAimX = (float) m[3];
                            c.player.inAimY = (float) m[4];
                        }
                    }
                    case Net.CHOICE -> {
                        if (c.player != null && game.state == Game.State.LEVELUP
                                && game.leveling == c.player && game.choices != null) {
                            int idx = (int) m[1];
                            if (idx >= 0 && idx < game.choices.size()) {
                                game.pickChoice(game.choices.get(idx));
                            }
                        }
                    }
                    default -> c.dead = true;
                }
            }
        }
        for (Conn c : conns) {
            if (c.dead) {
                conns.remove(c);
                c.closeQuiet();
                if (c.player != null) game.removePlayer(c.player);
            }
        }
    }

    /** Invia lo snapshot corrente a tutti i client registrati. */
    void broadcast() {
        if (conns.isEmpty()) return;
        byte[] snap = Net.snapshot(game);
        for (Conn c : conns) {
            if (c.player != null) c.send(snap);
        }
    }

    void sendLevelUp(Player p, List<Choice> cs) {
        for (Conn c : conns) {
            if (c.player == p) c.send(Net.levelUp(cs));
        }
    }

    void close() {
        closed = true;
        try { ss.close(); } catch (IOException ignored) { }
        for (Conn c : conns) c.closeQuiet();
        conns.clear();
    }

    /** IP locale da comunicare agli amici sulla stessa rete. */
    static String lanIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "?";
    }

    private final class Conn {
        final Socket sock;
        final Queue<Object[]> inbox = new ConcurrentLinkedQueue<>();
        final BlockingQueue<byte[]> outQ = new LinkedBlockingQueue<>(200);
        volatile Player player;
        volatile boolean dead = false;

        Conn(Socket sock) { this.sock = sock; }

        void start() {
            Thread r = new Thread(this::readLoop, "coop-read");
            r.setDaemon(true);
            r.start();
            Thread w = new Thread(this::writeLoop, "coop-write");
            w.setDaemon(true);
            w.start();
        }

        private void readLoop() {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()))) {
                while (!dead && !closed) {
                    byte t = in.readByte();
                    switch (t) {
                        case Net.HELLO -> inbox.add(new Object[]{(int) Net.HELLO, in.readInt()});
                        case Net.INPUT -> inbox.add(new Object[]{(int) Net.INPUT,
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()});
                        case Net.CHOICE -> inbox.add(new Object[]{(int) Net.CHOICE, in.readInt()});
                        default -> { dead = true; return; }
                    }
                }
            } catch (IOException e) {
                dead = true;
            }
        }

        private void writeLoop() {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()))) {
                while (!dead && !closed) {
                    byte[] b = outQ.take();
                    out.write(b);
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                dead = true;
            }
        }

        void send(byte[] b) {
            if (!outQ.offer(b)) dead = true; // client troppo lento: scollegalo
        }

        void closeQuiet() {
            dead = true;
            try { sock.close(); } catch (IOException ignored) { }
        }
    }
}
