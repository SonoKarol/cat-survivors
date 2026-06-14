// Entry point del gioco web. Sostituisce Main.java: canvas a tutto schermo,
// game loop con requestAnimationFrame (al posto di Thread.sleep) e disegno in
// pixel logici (CSS), con il backing store scalato per devicePixelRatio.

import { Input } from "./input.ts";
import { Game } from "./game.ts";
import { G } from "./g2d.ts";
import { Ui, drawTouchControls, drawRotateHint } from "./ui.ts";
import { App } from "./app.ts";
import { initSfx } from "./sfx.ts";
import { clientFrame } from "./clientview.ts";

const canvas = document.getElementById("game") as HTMLCanvasElement;
const ctx = canvas.getContext("2d")!;
const g = new G(ctx);

let logicalW = 0, logicalH = 0, dpr = 1;

function resize(): void {
  dpr = Math.min(window.devicePixelRatio || 1, 2); // cap a 2x: oltre è spreco
  logicalW = Math.max(1, Math.floor(canvas.clientWidth));
  logicalH = Math.max(1, Math.floor(canvas.clientHeight));
  canvas.width = Math.floor(logicalW * dpr);
  canvas.height = Math.floor(logicalH * dpr);
}

window.addEventListener("resize", resize);

const input = new Input(canvas);
const game = new Game(input);
App.init(game, input);
// tocco dentro il gesto: se il pannello "Join a friend" è aperto, diamo il focus
// all'input nascosto per far comparire la tastiera software (codice stanza).
input.onTap = () => {
  if (game.state === "MENU" && App.roomActive()) App.focusRoomInput();
};
initSfx();
resize();

// handle di debug/test (utile anche da console del browser)
(window as unknown as { cs: unknown }).cs = { game, input, App };

let last = performance.now();

function frame(now: number): void {
  const dt = Math.min((now - last) / 1000, 0.05);
  last = now;

  const client = App.client;
  const clientMode = App.mode === "CLIENT" && client !== null;
  if (!clientMode) game.step(dt, logicalW, logicalH);

  // sistema di coordinate in pixel logici; il dpr crispa senza cambiare la fisica
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.setColor("#131a12");
  g.fillRect(0, 0, logicalW, logicalH);
  if (clientMode) {
    clientFrame(client, input, g, logicalW, logicalH);
  } else {
    game.render(g, logicalW, logicalH);
    Ui.draw(game, g, logicalW, logicalH);
  }
  if (input.isTouchActive) {
    drawTouchControls(g, logicalW, logicalH, input.touchJoystick());
  }
  // gioco landscape: in verticale su mobile invitiamo a ruotare (sopra a tutto)
  if (input.isTouch && logicalH > logicalW) {
    drawRotateHint(g, logicalW, logicalH);
  }

  requestAnimationFrame(frame);
}

requestAnimationFrame(frame);
