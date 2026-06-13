// Lato host del co-op (porting di Server.java adattato al relay WebSocket).
// L'host è un normale client del relay: riceve i dati dei giocatori incapsulati
// e li applica alla simulazione in pump(); invia snapshot in broadcast().

import { ByteReader } from "./bytes.ts";
import * as Net from "./net.ts";
import {
  envelope, R_ROLE, R_JOIN, R_FROM_PEER, R_LEAVE,
  P_TO_ONE, P_BROADCAST, ROLE_HOST,
} from "./relay-proto.ts";
import { CATS } from "./cats.ts";
import { clamp } from "./util.ts";
import type { Game, GameServer } from "./game.ts";
import type { Player } from "./player.ts";

interface Msg { op: number; a?: number; mx?: number; my?: number; ax?: number; ay?: number; }

class Conn {
  player: Player | null = null;
  readonly inbox: Msg[] = [];
  dead = false;
}

export class Host implements GameServer {
  private ws: WebSocket;
  private readonly conns = new Map<number, Conn>();
  ready = false;
  error: string | null = null;

  constructor(private readonly game: Game, url: string, onReady: () => void, onError: (e: string) => void) {
    this.ws = new WebSocket(url);
    this.ws.binaryType = "arraybuffer";
    this.ws.onmessage = (ev) => this.onMessage(new Uint8Array(ev.data as ArrayBuffer));
    this.ws.onopen = () => { /* il relay risponde con R_ROLE */ };
    this.ws.onerror = () => { if (!this.ready) onError("Impossibile contattare il relay"); };
    this.ws.onclose = () => { if (!this.ready) onError("Connessione al relay chiusa"); };
    this.onReady = onReady;
  }

  private onReady: () => void;

  private onMessage(data: Uint8Array): void {
    const op = data[0];
    const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
    switch (op) {
      case R_ROLE: {
        const role = data[1];
        if (role === ROLE_HOST) { this.ready = true; this.onReady(); }
        break;
      }
      case R_JOIN: {
        const cid = view.getInt32(1);
        this.conns.set(cid, new Conn());
        break;
      }
      case R_LEAVE: {
        const cid = view.getInt32(1);
        const c = this.conns.get(cid);
        if (c) c.dead = true;
        break;
      }
      case R_FROM_PEER: {
        const cid = view.getInt32(1);
        const c = this.conns.get(cid);
        if (!c) break;
        const r = new ByteReader(data.subarray(5));
        const t = r.byte();
        if (t === Net.HELLO) c.inbox.push({ op: Net.HELLO, a: r.int() });
        else if (t === Net.INPUT) c.inbox.push({ op: Net.INPUT, mx: r.float(), my: r.float(), ax: r.float(), ay: r.float() });
        else if (t === Net.CHOICE) c.inbox.push({ op: Net.CHOICE, a: r.int() });
        else c.dead = true;
        break;
      }
    }
  }

  private cidOf(player: Player): number | null {
    for (const [cid, c] of this.conns) if (c.player === player) return cid;
    return null;
  }

  /** Applica i messaggi dei client alla simulazione (sul thread di gioco). */
  pump(): void {
    for (const [cid, c] of this.conns) {
      let m: Msg | undefined;
      while ((m = c.inbox.shift()) !== undefined) {
        switch (m.op) {
          case Net.HELLO: {
            if (this.game.state === "LOBBY" && c.player === null
              && this.game.players.length < Net.MAX_PLAYERS) {
              const ci = ((m.a! % CATS.length) + CATS.length) % CATS.length;
              c.player = this.game.addRemotePlayer(CATS[ci]);
              this.send(envelope(P_TO_ONE, Net.welcome(c.player.pid), cid));
            } else {
              c.dead = true; // partita già iniziata o piena
            }
            break;
          }
          case Net.INPUT: {
            if (c.player !== null) {
              c.player.inMoveX = clamp(m.mx!, -1, 1);
              c.player.inMoveY = clamp(m.my!, -1, 1);
              c.player.inAimX = m.ax!;
              c.player.inAimY = m.ay!;
            }
            break;
          }
          case Net.CHOICE: {
            if (c.player !== null && this.game.state === "LEVELUP"
              && this.game.leveling === c.player && this.game.choices !== null) {
              const i = m.a!;
              if (i >= 0 && i < this.game.choices.length) this.game.pickChoice(this.game.choices[i]);
            }
            break;
          }
        }
      }
    }
    for (const [cid, c] of [...this.conns]) {
      if (c.dead) {
        this.conns.delete(cid);
        if (c.player !== null) this.game.removePlayer(c.player);
      }
    }
  }

  broadcast(): void {
    if (this.conns.size === 0) return;
    this.send(envelope(P_BROADCAST, Net.snapshot(this.game)));
  }

  sendLevelUp(p: Player, cs: import("./entities.ts").Choice[]): void {
    const cid = this.cidOf(p);
    if (cid !== null) this.send(envelope(P_TO_ONE, Net.levelUp(cs), cid));
  }

  private send(b: Uint8Array): void {
    if (this.ws.readyState === WebSocket.OPEN) this.ws.send(b);
  }

  close(): void {
    try { this.ws.close(); } catch { /* ignore */ }
  }
}
