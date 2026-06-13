// Envelope del relay WebSocket: incapsula i payload del protocollo di gioco
// (net.ts) aggiungendo l'instradamento per stanza/peer. Il relay (relay/server.js)
// usa gli stessi opcode. cid = identificativo a 4 byte del client, assegnato dal relay.

// relay -> peer
export const R_ROLE = 1;      // [1][role:1][cid:4]  role 0=host 1=client
export const R_JOIN = 2;      // [2][cid:4]          un client è entrato (solo all'host)
export const R_FROM_PEER = 3; // [3][cid:4][payload] dati da un client (solo all'host)
export const R_FROM_HOST = 4; // [4][payload]        dati dall'host (solo ai client)
export const R_LEAVE = 6;     // [6][cid:4]          un client è uscito (solo all'host)

// peer -> relay
export const P_TO_ONE = 16;   // [16][cid:4][payload] host -> un client
export const P_BROADCAST = 17;// [17][payload]        host -> tutti i client
export const P_TO_HOST = 18;  // [18][payload]        client -> host

export const ROLE_HOST = 0, ROLE_CLIENT = 1;

/** Antepone un opcode (e opzionalmente un cid) a un payload. */
export function envelope(op: number, payload: Uint8Array | null, cid?: number): Uint8Array {
  const hasCid = cid !== undefined;
  const head = 1 + (hasCid ? 4 : 0);
  const out = new Uint8Array(head + (payload ? payload.length : 0));
  const view = new DataView(out.buffer);
  out[0] = op;
  let off = 1;
  if (hasCid) { view.setInt32(1, cid!); off = 5; }
  if (payload) out.set(payload, off);
  return out;
}
