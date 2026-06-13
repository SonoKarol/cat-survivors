// Smoke test co-op: due browser (host + client) via relay locale. Verifica che
// l'host veda 2 gatti e che il client riceva snapshot, senza errori runtime.
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";

const URL = process.env.URL || "http://localhost:5173/";
mkdirSync("screenshots", { recursive: true });

const errors = [];
const browser = await chromium.launch();
const ctx1 = await browser.newContext({ viewport: { width: 1280, height: 720 } });
const ctx2 = await browser.newContext({ viewport: { width: 1280, height: 720 } });
const host = await ctx1.newPage();
const client = await ctx2.newPage();
for (const [name, pg] of [["host", host], ["client", client]]) {
  pg.on("pageerror", (e) => errors.push(name + " pageerror: " + e.message));
  pg.on("console", (m) => { if (m.type() === "error") errors.push(name + " console: " + m.text()); });
}

await host.goto(URL, { waitUntil: "networkidle" });
await client.goto(URL, { waitUntil: "networkidle" });
await host.waitForTimeout(400);

await host.evaluate(() => window.cs.App.host());
await host.waitForTimeout(800);
const room = await host.evaluate(() => window.cs.App.roomCode);
console.log("Codice stanza:", room);

await client.evaluate(() => { window.cs.App.selectedCat = 3; }); // Luna
await client.evaluate((r) => window.cs.App.connectRoom(r), room);
await client.waitForTimeout(1200);

await host.evaluate(() => window.cs.game.startRunMulti());
await client.mouse.move(900, 300);
await client.evaluate(() => window.dispatchEvent(new KeyboardEvent("keydown", { code: "KeyD", key: "d" })));
await host.waitForTimeout(3000);

const hostState = await host.evaluate(() => ({
  players: window.cs.game.players.length,
  state: window.cs.game.state,
}));
const clientState = await client.evaluate(() => ({
  mode: window.cs.App.mode,
  hasSnap: !!window.cs.App.client?.latest,
  myPid: window.cs.App.client?.myPid,
  players: window.cs.App.client?.latest?.players.length,
  state: window.cs.App.client?.latest?.state,
  error: window.cs.App.client?.error,
}));
await host.screenshot({ path: "screenshots/coop-host.png" });
await client.screenshot({ path: "screenshots/coop-client.png" });
await browser.close();

console.log("HOST  ", JSON.stringify(hostState));
console.log("CLIENT", JSON.stringify(clientState));

let ok = true;
if (errors.length) { ok = false; console.log("ERRORI:"); errors.forEach((e) => console.log("  - " + e)); }
if (hostState.players !== 2) { ok = false; console.log("FALLITO: l'host non vede 2 giocatori"); }
if (clientState.mode !== "CLIENT" || !clientState.hasSnap || clientState.players !== 2) {
  ok = false; console.log("FALLITO: il client non riceve snapshot a 2 giocatori");
}
if (!ok) process.exit(1);
console.log("OK co-op: host vede 2 gatti, client riceve snapshot, nessun errore.");
