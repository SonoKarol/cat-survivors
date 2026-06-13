// Porting di Input.java. Gli eventi AWT diventano eventi DOM; le strutture
// "thread-safe" del Java diventano semplici code/array perché in browser il
// loop di gioco e gli eventi girano sullo stesso thread (event loop singolo).

/** Codici tasto logici mappati su KeyboardEvent.code, per sostituire i KeyEvent.VK_* di Java. */
export const Key = {
  A: "KeyA", D: "KeyD", W: "KeyW", S: "KeyS",
  LEFT: "ArrowLeft", RIGHT: "ArrowRight", UP: "ArrowUp", DOWN: "ArrowDown",
  P: "KeyP", M: "KeyM", R: "KeyR", H: "KeyH", N: "KeyN",
  ESCAPE: "Escape", ENTER: "Enter", SPACE: "Space", BACKSPACE: "Backspace",
  DIGIT1: "Digit1", DIGIT2: "Digit2", DIGIT3: "Digit3", DIGIT4: "Digit4",
} as const;

// Tasti di cui blocchiamo il comportamento di default del browser (scroll pagina, ecc.)
const PREVENT = new Set<string>([
  Key.A, Key.D, Key.W, Key.S, Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN,
  Key.SPACE, Key.P, Key.M, Key.R, Key.ENTER,
  Key.DIGIT1, Key.DIGIT2, Key.DIGIT3, Key.DIGIT4,
]);

export interface Point { x: number; y: number; }

export class Input {
  private readonly keys = new Set<string>();
  private readonly presses: string[] = [];
  private readonly typed: string[] = [];
  private click: Point | null = null;
  mouseX = -1;
  mouseY = -1;
  focusLost = false;

  constructor(private readonly canvas: HTMLCanvasElement) {
    window.addEventListener("keydown", this.onKeyDown);
    window.addEventListener("keyup", this.onKeyUp);
    window.addEventListener("blur", this.onBlur);
    canvas.addEventListener("mousemove", this.onMouseMove);
    canvas.addEventListener("mousedown", this.onMouseDown);
    // su itch il gioco è in un iframe: il click iniziale dà il focus per la tastiera
    canvas.addEventListener("mousedown", () => canvas.focus());
    canvas.tabIndex = 0;
  }

  private onKeyDown = (e: KeyboardEvent) => {
    if (PREVENT.has(e.code)) e.preventDefault();
    if (!this.keys.has(e.code)) {
      this.keys.add(e.code);
      this.presses.push(e.code);
    }
    // carattere stampabile per i campi di testo (codice stanza)
    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      this.typed.push(e.key);
    }
  };

  private onKeyUp = (e: KeyboardEvent) => {
    this.keys.delete(e.code);
  };

  // Se la finestra/iframe perde il focus AWT non consegnava più i keyReleased:
  // svuotiamo i tasti per non lasciare il gatto a camminare da solo.
  private onBlur = () => {
    this.keys.clear();
    this.focusLost = true;
  };

  // Coordinate del puntatore in pixel logici (CSS), lo stesso sistema in cui
  // disegna il gioco: il devicePixelRatio è gestito dal transform del contesto
  // (vedi main.ts), quindi qui basta lo scarto dal bordo del canvas.
  private toCanvas(e: MouseEvent): Point {
    const rect = this.canvas.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  private onMouseMove = (e: MouseEvent) => {
    const p = this.toCanvas(e);
    this.mouseX = p.x;
    this.mouseY = p.y;
  };

  private onMouseDown = (e: MouseEvent) => {
    if (e.button !== 0) return; // solo tasto sinistro
    const p = this.toCanvas(e);
    this.mouseX = p.x;
    this.mouseY = p.y;
    this.click = p;
  };

  down(code: string): boolean {
    return this.keys.has(code);
  }

  /** Direzione di movimento normalizzata da WASD/frecce. */
  axis(): [number, number] {
    let x = 0, y = 0;
    if (this.down(Key.A) || this.down(Key.LEFT)) x -= 1;
    if (this.down(Key.D) || this.down(Key.RIGHT)) x += 1;
    if (this.down(Key.W) || this.down(Key.UP)) y -= 1;
    if (this.down(Key.S) || this.down(Key.DOWN)) y += 1;
    if (x !== 0 && y !== 0) { const k = Math.SQRT1_2; x *= k; y *= k; }
    return [x, y];
  }

  /** Prossimo tasto premuto (one-shot), o null. */
  nextPress(): string | null {
    return this.presses.shift() ?? null;
  }

  /** Prossimo carattere digitato (per i campi di testo), o null. */
  nextTyped(): string | null {
    return this.typed.shift() ?? null;
  }

  /** Consuma il click del mouse: restituisce il punto della pressione, o null. */
  consumeClick(): Point | null {
    const c = this.click;
    this.click = null;
    return c;
  }
}
