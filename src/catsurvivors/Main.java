package catsurvivors;

import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;

/** Entry point: finestra, canvas con BufferStrategy e game loop a ~60 FPS. */
public class Main {
    public static void main(String[] args) throws Exception {
        for (String a : args) {
            if (a.equals("--autotest")) {
                System.setProperty("java.awt.headless", "true");
                Autotest.run();
                return;
            }
            if (a.equals("--cooptest")) {
                System.setProperty("java.awt.headless", "true");
                Cooptest.run();
                return;
            }
        }

        Input input = new Input();
        Game game = new Game(input);
        App.init(game, input);

        final Canvas[] canvasRef = new Canvas[1];
        EventQueue.invokeAndWait(() -> {
            JFrame frame = new JFrame("Cat Survivors — miagola e sopravvivi");
            Canvas canvas = new Canvas();
            canvas.setPreferredSize(new Dimension(1280, 720));
            canvas.setBackground(Color.BLACK);
            canvas.setIgnoreRepaint(true); // rendering attivo: niente repaint dall'EDT
            frame.add(canvas);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            canvas.addKeyListener(input.keyListener);
            canvas.addMouseListener(input.mouseListener);
            canvas.addMouseMotionListener(input.mouseListener);
            canvas.addFocusListener(input.focusListener);
            canvas.setFocusable(true);
            canvas.requestFocusInWindow();
            canvasRef[0] = canvas;
        });
        Canvas canvas = canvasRef[0];

        Sfx.init();

        canvas.createBufferStrategy(2);
        BufferStrategy bs = canvas.getBufferStrategy();

        long last = System.nanoTime();
        while (true) {
            long now = System.nanoTime();
            double dt = Math.min((now - last) / 1e9, 0.05);
            last = now;
            int w = Math.max(1, canvas.getWidth());
            int h = Math.max(1, canvas.getHeight());

            Client client = App.client;
            boolean clientMode = App.mode == App.Mode.CLIENT && client != null;
            if (!clientMode) game.step(dt, w, h);

            do {
                do {
                    Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                    g.setColor(new Color(0x131a12));
                    g.fillRect(0, 0, w, h);
                    if (clientMode) {
                        ClientView.frame(client, input, g, w, h);
                    } else {
                        game.render(g, w, h);
                        Ui.draw(game, g, w, h);
                    }
                    g.dispose();
                } while (bs.contentsRestored());
                bs.show();
            } while (bs.contentsLost());
            Toolkit.getDefaultToolkit().sync();

            long elapsed = System.nanoTime() - now;
            long sleepMs = (16_666_667 - elapsed) / 1_000_000;
            if (sleepMs > 0) Thread.sleep(sleepMs);
        }
    }
}
