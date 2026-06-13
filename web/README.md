# Cat Survivors — versione browser

Porting in TypeScript + Canvas 2D del gioco Java `catsurvivors`, giocabile dal browser
(niente download) e pubblicabile su **itch.io** come gioco HTML5. Il co-op online fino a 4
giocatori passa per un piccolo **relay WebSocket** (al posto del TCP/UPnP della versione Java):
chi ospita genera un **codice stanza**, gli amici lo digitano.

## Sviluppo

```bash
npm install
npm run dev        # http://localhost:5173  (gioco)
npm run relay      # ws://localhost:8080     (relay per il co-op in locale)
```

Per provare il co-op in locale: apri due schede, in una clicca **CREA STANZA** (appare il codice),
nell'altra **ENTRA IN STANZA** e digita quel codice.

## Build & pacchetto itch

```bash
npm run build      # genera dist/ (index.html + bundle JS, percorsi relativi)
npm run pack       # build + crea cat-survivors-itch.zip pronto per itch.io
npm run preview    # anteprima della build
```

L'URL del relay è già il default per le build di produzione
(`wss://catsurvivors-production.up.railway.app`, in `src/app.ts`); in `npm run dev`
si usa invece `ws://localhost:8080`. Per puntare a un altro relay:

```bash
# Windows PowerShell
$env:VITE_RELAY_URL = "wss://altro-relay.example"; npm run pack
```

Il gioco in solitaria funziona comunque anche senza relay.

## Deploy del relay (gratis)

Il relay è in `relay/` (Node.js + `ws`). Deploy su Railway / Render / Fly.io (tier gratuito):

```bash
cd relay
# Railway: railway init && railway up   (oppure collega la cartella dal sito)
```

Il servizio ascolta su `process.env.PORT`. Una volta online avrai un URL `wss://...`:
mettilo in `VITE_RELAY_URL` e ri-builda.

## Pubblicazione su itch.io

1. `npm run pack` → crea `cat-survivors-itch.zip` (relay già configurato).
2. Su itch.io: nuovo progetto → *Kind of project: HTML* → carica lo zip →
   spunta *This file will be played in the browser*.
3. Imposta la dimensione viewport (es. 1280×720) e abilita *Fullscreen button*.

## Verifica automatica

```bash
node smoketest.mjs        # gioco solo (richiede npm run dev attivo)
node coop-smoketest.mjs   # co-op host+client (richiede dev + relay attivi)
```

Salvano screenshot in `screenshots/`.

## Struttura

`src/` rispecchia i file Java omonimi: `game.ts`, `sprites.ts`, `weapons.ts`, `enemies.ts`,
`ui.ts`, `sfx.ts`, `net.ts`, `host.ts` (era `Server.java`), `client.ts`, `clientview.ts`.
`g2d.ts` è un wrapper su Canvas 2D che replica le primitive Java2D usate.
