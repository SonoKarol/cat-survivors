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

  // true sui dispositivi prevalentemente touch (telefoni/tablet): pointer grossolano
  readonly isTouch = !!window.matchMedia && window.matchMedia("(pointer: coarse)").matches;
  // touch: dito sinistro = joystick movimento, dito destro = aim
  isTouchActive = false;
  // invocato in modo sincrono dentro il gesto di tocco (touchstart): permette di
  // dare il focus a un input HTML — e quindi far comparire la tastiera software —
  // cosa che fuori dal gesto utente i browser mobile rifiutano.
  onTap: ((p: Point) => void) | null = null;
  private leftId: number | null = null;
  private leftOriginX = 0;
  private leftOriginY = 0;
  private leftDX = 0;
  private leftDY = 0;
  private rightId: number | null = null;
  private fullscreenTried = false;

  // raggio entro cui il joystick si considera "a riposo"
  private static readonly JOYSTICK_RADIUS = 70;

  constructor(private readonly canvas: HTMLCanvasElement) {
    window.addEventListener("keydown", this.onKeyDown);
    window.addEventListener("keyup", this.onKeyUp);
    window.addEventListener("blur", this.onBlur);
    canvas.addEventListener("mousemove", this.onMouseMove);
    canvas.addEventListener("mousedown", this.onMouseDown);
    // su itch il gioco è in un iframe: il click iniziale dà il focus per la tastiera
    canvas.addEventListener("mousedown", () => canvas.focus());
    canvas.addEventListener("touchstart", this.onTouchStart, { passive: false });
    canvas.addEventListener("touchmove", this.onTouchMove, { passive: false });
    canvas.addEventListener("touchend", this.onTouchEnd, { passive: false });
    canvas.addEventListener("touchcancel", this.onTouchEnd, { passive: false });
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

  private touchToCanvas(t: Touch): Point {
    const rect = this.canvas.getBoundingClientRect();
    return { x: t.clientX - rect.left, y: t.clientY - rect.top };
  }

  // Su mobile il gioco rende bene solo a tutto schermo: itch lo incorpora in un
  // riquadro fisso (es. 1280×720) che su un telefono viene ritagliato. Al primo
  // tocco — gesto utente richiesto dal browser — proviamo a entrare in fullscreen.
  // iOS Safari non supporta requestFullscreen su elementi non-video: in quel caso
  // l'eccezione viene ignorata e restano attivi l'hint di rotazione e il "Mobile
  // friendly" di itch.
  private tryEnterFullscreen(): void {
    if (this.fullscreenTried) return;
    this.fullscreenTried = true;
    if (!this.isTouch) return;
    if (document.fullscreenElement) return;
    const el = document.documentElement as HTMLElement & {
      webkitRequestFullscreen?: () => Promise<void> | void;
    };
    try {
      const r = el.requestFullscreen ? el.requestFullscreen() : el.webkitRequestFullscreen?.();
      if (r && typeof (r as Promise<void>).then === "function") (r as Promise<void>).catch(() => {});
    } catch {
      /* fullscreen non disponibile (es. iOS): si prosegue senza */
    }
  }

  private onTouchStart = (e: TouchEvent) => {
    e.preventDefault();
    this.isTouchActive = true;
    this.tryEnterFullscreen();
    const w = this.canvas.clientWidth;
    for (let i = 0; i < e.changedTouches.length; i++) {
      const t = e.changedTouches[i];
      const p = this.touchToCanvas(t);
      // ogni tocco vale come click nel punto premuto: così i menu (anche i pulsanti
      // sulla metà sinistra) sono sempre toccabili. Durante il gioco i click non sono
      // usati, quindi è innocuo. La mira (mouseX/Y) la muove solo il dito destro.
      this.click = p;
      // dentro al gesto: chi ascolta può aprire la tastiera software (codice stanza)
      if (this.onTap !== null) this.onTap(p);
      if (p.x < w / 2) {
        // metà sinistra → joystick movimento
        if (this.leftId === null) {
          this.leftId = t.identifier;
          this.leftOriginX = p.x;
          this.leftOriginY = p.y;
          this.leftDX = 0;
          this.leftDY = 0;
        }
      } else {
        // metà destra → aim
        if (this.rightId === null) {
          this.rightId = t.identifier;
          this.mouseX = p.x;
          this.mouseY = p.y;
        }
      }
    }
  };

  private onTouchMove = (e: TouchEvent) => {
    e.preventDefault();
    for (let i = 0; i < e.changedTouches.length; i++) {
      const t = e.changedTouches[i];
      const p = this.touchToCanvas(t);
      if (t.identifier === this.leftId) {
        const dx = p.x - this.leftOriginX;
        const dy = p.y - this.leftOriginY;
        const len = Math.hypot(dx, dy);
        if (len > 0) {
          const clamped = Math.min(len, Input.JOYSTICK_RADIUS);
          this.leftDX = (dx / len) * (clamped / Input.JOYSTICK_RADIUS);
          this.leftDY = (dy / len) * (clamped / Input.JOYSTICK_RADIUS);
        } else {
          this.leftDX = 0;
          this.leftDY = 0;
        }
      } else if (t.identifier === this.rightId) {
        this.mouseX = p.x;
        this.mouseY = p.y;
      }
    }
  };

  private onTouchEnd = (e: TouchEvent) => {
    e.preventDefault();
    for (let i = 0; i < e.changedTouches.length; i++) {
      const t = e.changedTouches[i];
      if (t.identifier === this.leftId) {
        this.leftId = null;
        this.leftDX = 0;
        this.leftDY = 0;
      } else if (t.identifier === this.rightId) {
        this.rightId = null;
      }
    }
  };

  /** Posizione attuale della manopola del joystick sinistro (per il rendering). */
  touchJoystick(): { ox: number; oy: number; dx: number; dy: number; active: boolean } {
    return {
      ox: this.leftOriginX,
      oy: this.leftOriginY,
      dx: this.leftDX,
      dy: this.leftDY,
      active: this.leftId !== null,
    };
  }

  down(code: string): boolean {
    return this.keys.has(code);
  }

  /** Direzione di movimento normalizzata da WASD/frecce o joystick touch. */
  axis(): [number, number] {
    let x = 0, y = 0;
    if (this.down(Key.A) || this.down(Key.LEFT)) x -= 1;
    if (this.down(Key.D) || this.down(Key.RIGHT)) x += 1;
    if (this.down(Key.W) || this.down(Key.UP)) y -= 1;
    if (this.down(Key.S) || this.down(Key.DOWN)) y += 1;
    if (x !== 0 || y !== 0) {
      if (x !== 0 && y !== 0) { const k = Math.SQRT1_2; x *= k; y *= k; }
      return [x, y];
    }
    // fallback touch joystick
    return [this.leftDX, this.leftDY];
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
