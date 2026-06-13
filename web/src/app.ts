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

  init(game: Game, input: Input): void {
    this.game = game;
    this.input = input;
  }

  roomActive(): boolean { return this.joinField !== null; }

  openJoinInput(): void {
    this.joinField = "";
    this.status = "";
  }

  /** Tasti speciali del campo codice (i caratteri arrivano da Input.nextTyped). */
  joinKey(code: string): void {
    if (this.joinField === null) return;
    if (code === "Escape") {
      this.joinField = null;
    } else if (code === "Backspace") {
      if (this.joinField.length > 0) this.joinField = this.joinField.slice(0, -1);
    } else if (code === "Enter") {
      const room = this.joinField.trim();
      this.joinField = null;
      if (room.length > 0) this.connectRoom(room);
    }
  }

  /** Aggiunge i caratteri digitati al codice (lettere/cifre, maiuscole, max 8). */
  joinType(): void {
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
    this.status = "Apertura stanza...";
    this.game.startHosting(CATS[this.selectedCat]);
    const url = RELAY_URL + "?room=" + code + "&role=host";
    const server = new Host(
      this.game,
      url,
      () => { this.status = "Stanza pronta! Condividi il codice " + code; },
      (err) => {
        this.status = "Relay non raggiungibile: " + err;
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
    this.status = "Connessione a " + room + "...";
    const client = new Client();
    const url = RELAY_URL + "?room=" + room.toUpperCase() + "&role=client";
    client.connect(url, this.selectedCat).then(() => {
      this.client = client;
      this.mode = "CLIENT";
      this.status = "";
      this.connecting = false;
    }).catch((e: Error) => {
      client.close();
      this.status = "Connessione fallita: " + e.message;
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
