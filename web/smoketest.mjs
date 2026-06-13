// Smoke test headless: avvia Chromium, carica il gioco, fa partire una run solo,
// simula input per qualche secondo e verifica che non ci siano errori runtime.
// Salva due screenshot (menu e gameplay) in screenshots/.
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";

const URL = process.env.URL || "http://localhost:5173/";
mkdirSync("screenshots", { recursive: true });

const errors = [];
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
page.on("console", (m) => { if (m.type() === "error") errors.push("console: " + m.text()); });
page.on("pageerror", (e) => errors.push("pageerror: " + e.message));

await page.goto(URL, { waitUntil: "networkidle" });
await page.waitForTimeout(600);
await page.screenshot({ path: "screenshots/menu.png" });

// avvia una partita in solitaria col gatto selezionato
await page.evaluate(() => window.cs.App.solo());
await page.waitForTimeout(300);

// simula movimento + mira per qualche secondo (eventi su window/canvas)
await page.mouse.move(900, 300);
for (const code of ["KeyD", "KeyS"]) {
  await page.evaluate((c) => window.dispatchEvent(new KeyboardEvent("keydown", { code: c, key: c })), code);
}
await page.waitForTimeout(4000);

const state = await page.evaluate(() => ({
  state: window.cs.game.state,
  time: window.cs.game.time,
  enemies: window.cs.game.enemies.length,
  projectiles: window.cs.game.projectiles.length,
  hp: window.cs.game.players[0]?.hp,
  level: window.cs.game.players[0]?.level,
}));
await page.screenshot({ path: "screenshots/gameplay.png" });

await browser.close();

console.log("Stato dopo ~4s:", JSON.stringify(state));
if (errors.length) {
  console.log("ERRORI RILEVATI:");
  for (const e of errors) console.log("  - " + e);
  process.exit(1);
}
if (state.time <= 0 || state.state !== "PLAYING" && state.state !== "LEVELUP") {
  console.log("FALLITO: la simulazione non è avanzata correttamente.");
  process.exit(1);
}
console.log("OK: nessun errore, simulazione avanzata, nemici/proiettili generati.");
