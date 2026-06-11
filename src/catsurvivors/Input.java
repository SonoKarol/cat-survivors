package catsurvivors;

import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stato di tastiera e mouse. Gli eventi arrivano sull'EDT di AWT,
 * le letture avvengono sul thread di gioco: tutte le strutture sono thread-safe.
 */
final class Input {
    final Set<Integer> keys = ConcurrentHashMap.newKeySet();
    private final Queue<Integer> presses = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Point> click = new AtomicReference<>();
    volatile int mouseX = -1, mouseY = -1;
    volatile boolean focusLost = false;

    final KeyAdapter keyListener = new KeyAdapter() {
        @Override public void keyPressed(KeyEvent e) {
            if (keys.add(e.getKeyCode())) presses.add(e.getKeyCode());
        }
        @Override public void keyReleased(KeyEvent e) {
            keys.remove(e.getKeyCode());
        }
    };

    final MouseAdapter mouseListener = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); click.set(e.getPoint()); }
        @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
        @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    };

    // Se la finestra perde il focus, AWT non consegna più i keyReleased:
    // svuota i tasti premuti per non lasciare il gatto a camminare da solo.
    final FocusAdapter focusListener = new FocusAdapter() {
        @Override public void focusLost(FocusEvent e) {
            keys.clear();
            focusLost = true;
        }
    };

    boolean down(int code) { return keys.contains(code); }

    /** Direzione di movimento normalizzata da WASD/frecce. */
    double[] axis() {
        double x = 0, y = 0;
        if (down(KeyEvent.VK_A) || down(KeyEvent.VK_LEFT)) x -= 1;
        if (down(KeyEvent.VK_D) || down(KeyEvent.VK_RIGHT)) x += 1;
        if (down(KeyEvent.VK_W) || down(KeyEvent.VK_UP)) y -= 1;
        if (down(KeyEvent.VK_S) || down(KeyEvent.VK_DOWN)) y += 1;
        if (x != 0 && y != 0) { x *= Math.sqrt(0.5); y *= Math.sqrt(0.5); }
        return new double[]{x, y};
    }

    /** Prossimo tasto premuto (one-shot), o null. */
    Integer nextPress() { return presses.poll(); }

    /** Consuma il click del mouse: restituisce il punto esatto della pressione, o null. */
    Point consumeClick() { return click.getAndSet(null); }
}
