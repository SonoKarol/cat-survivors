// Lato ospite del co-op (porting di Client.java sul relay WebSocket): invia
// l'input all'host e riceve gli snapshot da renderizzare con interpolazione.

import { ByteReader } from "./bytes.ts";
import * as Net from "./net.ts";
import { envelope, R_ROLE, R_FROM_HOST, P_TO_HOST } from "./relay-proto.ts";
import { playSfx } from "./sfx.ts";
import { Snapshot } from "./snapshot.ts";
import { Choice } from "./entities.ts";

export class Client {
  private ws: WebSocket | null = null;
  latest: Snapshot | null = null;
  prev: Snapshot | null = null;
  choices: Choice[] | null = null;
  myPid = -1;
  error: string | null = null;
  private joined = false;

  /** Si collega alla stanza via relay e invia HELLO. Risolve quando il relay ci accetta. */
  connect(url: string, catIdx: number): Promise<void> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      this.ws = ws;
      ws.binaryType = "arraybuffer";
      let settled = false;
      const fail = (msg: string) => {
        if (settled) { if (this.error === null) this.error = msg; return; }
        settled = true;
        reject(new Error(msg));
      };
      ws.onopen = () => {
        // entra dall'host con il gatto scelto
        ws.send(envelope(P_TO_HOST, Net.hello(catIdx)));
      };
      ws.onmessage = (ev) => {
        const data = new Uint8Array(ev.data as ArrayBuffer);
        const op = data[0];
        if (op === R_ROLE) {
          this.joined = true;
          if (!settled) { settled = true; resolve(); }
        } else if (op === R_FROM_HOST) {
          this.handleHostMsg(new ByteReader(data.subarray(1)));
        }
      };
      ws.onerror = () => fail("Connessione fallita");
      ws.onclose = () => fail(this.joined ? "Connessione persa" : "Connessione fallita");
    });
  }

  private handleHostMsg(r: ByteReader): void {
    const t = r.byte();
    if (t === Net.WELCOME) {
      this.myPid = r.int();
    } else if (t === Net.SNAPSHOT) {
      const s = Net.readSnapshot(r);
      s.arrivedAt = performance.now();
      this.prev = this.latest;
      this.latest = s;
      // se l'host è andato avanti, le vecchie carte non valgono più
      if (s.levelingPid !== this.myPid) this.choices = null;
    } else if (t === Net.LEVELUP) {
      this.choices = Net.readChoices(r);
      playSfx("levelup");
    }
  }

  sendInput(moveX: number, moveY: number, aimX: number, aimY: number): void {
    this.send(envelope(P_TO_HOST, Net.input(moveX, moveY, aimX, aimY)));
  }

  sendChoice(i: number): void {
    this.send(envelope(P_TO_HOST, Net.choiceMsg(i)));
    this.choices = null; // se ci sono altri level up, l'host rimanda le carte
  }

  private send(b: Uint8Array): void {
    if (this.ws !== null && this.ws.readyState === WebSocket.OPEN) this.ws.send(b);
  }

  close(): void {
    try { this.ws?.close(); } catch { /* ignore */ }
  }
}
