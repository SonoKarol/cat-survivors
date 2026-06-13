

import { WebSocketServer } from "ws";

const PORT = process.env.PORT || 8080;
const MAX_CLIENTS = 3; // host + 3 = 4 giocatori

// opcode envelope (devono combaciare con src/relay-proto.ts)
const R_ROLE = 1, R_JOIN = 2, R_FROM_PEER = 3, R_FROM_HOST = 4, R_LEAVE = 6;
const P_TO_ONE = 16, P_BROADCAST = 17, P_TO_HOST = 18;
const ROLE_HOST = 0, ROLE_CLIENT = 1;

/** room -> { host: ws|null, clients: Map<cid, ws>, nextCid } */
const rooms = new Map();

function bufRole(role, cid) {
  const b = Buffer.alloc(6);
  b[0] = R_ROLE; b[1] = role; b.writeInt32BE(cid, 2);
  return b;
}
function bufCid(op, cid) {
  const b = Buffer.alloc(5);
  b[0] = op; b.writeInt32BE(cid, 1);
  return b;
}
function bufFromHost(payload) {
  return Buffer.concat([Buffer.from([R_FROM_HOST]), payload]);
}
function bufFromPeer(cid, payload) {
  const head = Buffer.alloc(5);
  head[0] = R_FROM_PEER; head.writeInt32BE(cid, 1);
  return Buffer.concat([head, payload]);
}

const wss = new WebSocketServer({ port: PORT });
console.log("Relay Cat Survivors in ascolto sulla porta " + PORT);

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, "http://x");
  const room = (url.searchParams.get("room") || "").toUpperCase();
  const role = url.searchParams.get("role");

  if (!room) { ws.close(); return; }

  if (role === "host") {
    const existing = rooms.get(room);
    if (existing && existing.host) { ws.close(); return; } // stanza già ospitata
    const r = existing || { host: null, clients: new Map(), nextCid: 1 };
    r.host = ws;
    rooms.set(room, r);
    ws._room = room; ws._role = "host";
    ws.send(bufRole(ROLE_HOST, 0));
  } else if (role === "client") {
    const r = rooms.get(room);
    if (!r || !r.host || r.clients.size >= MAX_CLIENTS) { ws.close(); return; }
    const cid = r.nextCid++;
    r.clients.set(cid, ws);
    ws._room = room; ws._role = "client"; ws._cid = cid;
    ws.send(bufRole(ROLE_CLIENT, cid));
    if (r.host.readyState === ws.OPEN) r.host.send(bufCid(R_JOIN, cid));
  } else {
    ws.close();
    return;
  }

  ws.on("message", (data, isBinary) => {
    if (!isBinary) return;
    const r = rooms.get(ws._room);
    if (!r) return;
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
    const op = buf[0];
    if (ws._role === "host") {
      if (op === P_TO_ONE) {
        const cid = buf.readInt32BE(1);
        const target = r.clients.get(cid);
        if (target && target.readyState === ws.OPEN) target.send(bufFromHost(buf.subarray(5)));
      } else if (op === P_BROADCAST) {
        const payload = buf.subarray(1);
        for (const target of r.clients.values()) {
          if (target.readyState === ws.OPEN) target.send(bufFromHost(payload));
        }
      }
    } else if (ws._role === "client") {
      if (op === P_TO_HOST && r.host && r.host.readyState === ws.OPEN) {
        r.host.send(bufFromPeer(ws._cid, buf.subarray(1)));
      }
    }
  });

  ws.on("close", () => {
    const r = rooms.get(ws._room);
    if (!r) return;
    if (ws._role === "host") {
      for (const target of r.clients.values()) { try { target.close(); } catch { /* */ } }
      rooms.delete(ws._room);
    } else if (ws._role === "client") {
      r.clients.delete(ws._cid);
      if (r.host && r.host.readyState === ws.OPEN) r.host.send(bufCid(R_LEAVE, ws._cid));
    }
  });
});
