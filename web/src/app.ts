// Porting/adattamento di App.java per il browser. Invece dell'IP dell'host si usa
// un CODICE STANZA: l'host ne genera uno, gli amici lo digitano. La connessione
// passa per un relay WebSocket (vedi net.ts/host.ts/client.ts, Task #9). Niente
// UPnP: il relay è già pubblico.

import { CATS } from "./cats.ts";
import { playSfx } from "./sfx.ts";
import { Host } from "./host.ts";
import { Client } from "./client.ts";
import type { Game } from "./game.ts";
import type { Input } from "./input.ts";

export type Mode = "LOCAL" | "CLIENT";

export const MAX_PLAYERS = 4;

/** URL del relay WebSocket. Override con VITE_RELAY_URL; altrimenti in dev punta al
 *  relay locale (npm run relay), in produzione al relay pubblico su Railway. */
export const RELAY_URL = import.meta.env.VITE_RELAY_URL
  ?? (import.meta.env.DEV ? "ws://localhost:8080" : "wss://catsurvivors-production.up.railway.app");

const ROOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // niente caratteri ambigui (0/O, 1/I)

class AppState {
  mode: Mode = "LOCAL";
  game!: Game;
  input!: Input;
  client: Client | null = null;
  selectedCat = 0;
  status = "";
  connecting = false;

  // campo di digitazione del codice stanza (null = chiuso), come il vecchio campo IP
  joinField: string | null = null;
  // codice della stanza attiva (mostrato in lobby per condividerlo)
  roomCode = "";
  // input HTML nascosto: su mobile serve un vero elemento focalizzato per far
  // comparire la tastiera software (il campo visibile è disegnato sul canvas)
  private roomInput: HTMLInputElement | null = null;

  init(game: Game, input: Input): void {
    this.game = game;
    this.input = input;
    const el = document.getElementById("roomInput") as HTMLInputElement | null;
    this.roomInput = el;
    if (el !== null) {
      // su touch i caratteri arrivano da qui (la tastiera software spesso non
      // genera keydown affidabili): il valore dell'input è la fonte autorevole.
      el.addEventListener("input", () => {
        if (this.joinField === null) return;
        const v = el.value.replace(/[^a-zA-Z0-9]/g, "").toUpperCase().slice(0, 8);
        if (el.value !== v) el.value = v;
        this.joinField = v;
      });
      el.addEventListener("keydown", (e) => {
        if (this.joinField === null) return;
        if (e.key === "Enter") { e.preventDefault(); this.joinKey("Enter"); }
        else if (e.key === "Escape") { e.preventDefault(); this.joinKey("Escape"); }
      });
    }
  }

  roomActive(): boolean { return this.joinField !== null; }

  openJoinInput(): void {
    this.joinField = "";
    this.status = "";
    const el = this.roomInput;
    if (el !== null) {
      el.value = "";
      // il focus fa comparire la tastiera software su mobile (gesto utente: il
      // tocco sul bottone JOIN). Su desktop non serve: si continua via nextTyped.
      if (this.input.isTouch) el.focus();
    }
  }

  /** Dà il focus all'input nascosto per far comparire la tastiera software.
   *  Va chiamato dentro un gesto di tocco (vedi Input.onTap). */
  focusRoomInput(): void {
    if (this.roomInput !== null) this.roomInput.focus();
  }

  /** Chiude l'input nascosto (svuota e toglie il focus/tastiera). */
  private closeRoomInput(): void {
    const el = this.roomInput;
    if (el !== null) { el.value = ""; el.blur(); }
  }

  /** Tasti speciali del campo codice (i caratteri arrivano da Input.nextTyped). */
  joinKey(code: string): void {
    if (this.joinField === null) return;
    if (code === "Escape") {
      this.joinField = null;
      this.closeRoomInput();
    } else if (code === "Backspace") {
      if (this.joinField.length > 0) this.joinField = this.joinField.slice(0, -1);
    } else if (code === "Enter") {
      const room = this.joinField.trim();
      this.joinField = null;
      this.closeRoomInput();
      if (room.length > 0) this.connectRoom(room);
    }
  }

  /** Aggiunge i caratteri digitati al codice (lettere/cifre, maiuscole, max 8).
   *  Saltato quando l'input nascosto ha il focus: lì la fonte è il suo valore. */
  joinType(): void {
    if (this.roomInput !== null && document.activeElement === this.roomInput) return;
    let ch: string | null;
    while ((ch = this.input.nextTyped()) !== null) {
      if (this.joinField !== null && this.joinField.length < 8 && /[a-zA-Z0-9]/.test(ch)) {
        this.joinField += ch.toUpperCase();
      }
    }
  }

  solo(): void {
    this.game.startRun(CATS[this.selectedCat]);
  }

  /** Genera un codice stanza casuale (l'RNG di gioco non va usato qui: serve casualità vera). */
  generateRoomCode(): string {
    let s = "";
    for (let i = 0; i < 5; i++) {
      s += ROOM_CHARS[Math.floor(Math.random() * ROOM_CHARS.length)];
    }
    return s;
  }

  // --- Co-op via relay WebSocket ---

  /** Crea una stanza: apre la lobby locale e si registra come host sul relay. */
  host(): void {
    const code = this.generateRoomCode();
    this.roomCode = code;
    this.status = "Opening room...";
    this.game.startHosting(CATS[this.selectedCat]);
    const url = RELAY_URL + "?room=" + code + "&role=host";
    const server = new Host(
      this.game,
      url,
      () => { this.status = "Room ready! Share the code " + code; },
      (err) => {
        this.status = "Relay unreachable: " + err;
        this.roomCode = "";
        if (this.game.state === "LOBBY") this.game.toMenu();
      },
    );
    this.game.server = server;
  }

  /** Si unisce a una stanza come client (rendering via snapshot). */
  connectRoom(room: string): void {
    if (this.connecting) return;
    this.connecting = true;
    this.status = "Connecting to " + room + "...";
    const client = new Client();
    const url = RELAY_URL + "?room=" + room.toUpperCase() + "&role=client";
    client.connect(url, this.selectedCat).then(() => {
      this.client = client;
      this.mode = "CLIENT";
      this.status = "";
      this.connecting = false;
    }).catch((e: Error) => {
      client.close();
      this.status = "Connection failed: " + e.message;
      this.connecting = false;
    });
  }

  /** Chiamato quando l'host torna al menu: il relay viene chiuso da server.close(). */
  onHostStop(): void {
    this.roomCode = "";
    this.status = "";
  }

  backToMenu(): void {
    if (this.client !== null) this.client.close();
    this.client = null;
    this.mode = "LOCAL";
    this.status = "";
    this.game.toMenu();
    playSfx("meow");
  }
}

export const App = new AppState();
