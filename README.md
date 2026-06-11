# 🐱 Cat Survivors

Un clone di **Vampire Survivors** scritto in **Java puro (Java2D/AWT)**, dove i protagonisti
sono gatti di razze e caratteri diversi. Il giardino di casa è stato invaso da cetrioli,
piccioni, aspirapolvere e altri incubi felini: scegli il tuo gatto e **sopravvivi 10 minuti**.

Zero dipendenze esterne, zero asset: tutta la grafica è disegnata proceduralmente con Java2D
e tutti gli effetti sonori sono sintetizzati a runtime. Serve solo un JDK.

![Selezione del gatto](docs/screenshot-menu.png)
![In gioco](docs/screenshot-game.png)

## Requisiti

- **JDK 17 o superiore** (`javac` e `java` nel PATH) — nessuna libreria esterna, nessun build tool.

## Come si gioca

```bat
run.bat
```

Oppure manualmente:

```bat
javac -encoding UTF-8 --release 17 -d out src\catsurvivors\*.java
java -cp out catsurvivors.Main
```

Con `build.bat` si crea anche il jar eseguibile `dist\CatSurvivors.jar`
(avviabile con `java -jar dist\CatSurvivors.jar`).

### Controlli

| Tasto | Azione |
|---|---|
| **WASD / Frecce** | Movimento (le armi attaccano da sole) |
| **Mouse / 1-4** | Scelta dei potenziamenti al level up |
| **P / Esc** | Pausa |
| **M** | Audio on/off |
| **R** | Torna al menu (a fine partita) |

## I gatti giocabili

| Gatto | Razza | Carattere | Bonus | Arma iniziale |
|---|---|---|---|---|
| **Romeo** | Soriano Europeo | Il randagio col cuore d'oro | +10% esperienza | Graffio Felino |
| **Duchessa** | Persiano | Aristocratica e pigrissima | +20% area, -10% velocità | Aura di Fusa |
| **Diesel** | Maine Coon | Un gigante buono | +50 salute, +20% danno, +1 armatura, -15% velocità | Palla di Pelo |
| **Luna** | Siamese | Iperattiva e chiacchierona | +25% velocità, ricarica -8%, -20 salute | Miagolio Sonico |
| **Felix** | Gatto Nero | Porta sfortuna ai nemici | +40% fortuna (più scelte, drop e gemme doppie) | Gomitolo Rimbalzino |
| **Cleo** | Sphynx | Mistica, senza pelo e senza paura | Ricarica -15%, -10 salute | Artigli Spettrali |
| **Pallino** | Rosso Europeo | Un solo neurone, tanto appetito | Il cibo cura +60%, +0.4 PS/s, +10 salute | Lancio di Sardine |
| **Neve** | Ragdoll | Morbida come una nuvola | +1 proiettile, -15% danno | Pioggia di Croccantini |

## Le armi

Otto armi potenziabili fino al livello 6, più dieci oggetti passivi (Erba Gatta, Ciotola di
Latte, Zampe Felpate...). Massimo 6 armi e 6 passivi per partita, come da tradizione.

**Graffio Felino** (zampata frontale) • **Gomitolo Rimbalzino** (rimbalza sullo schermo) •
**Palla di Pelo** (lobbata ad arco) • **Aura di Fusa** (danno ad area attorno al gatto) •
**Miagolio Sonico** (onda d'urto perforante) • **Artigli Spettrali** (orbitanti) •
**Lancio di Sardine** (raffica direzionale) • **Pioggia di Croccantini** (bombardamento esplosivo)

## I nemici

Tutto ciò che un gatto teme: **cetrioli** (il classico), piccioni, topi robot, anatre di gomma,
spruzzini d'acqua e aspirapolvere. Ogni minuto arriva un **élite**, e ai minuti 3, 6 e 9
compaiono i boss: il **Roomba 9000**, il **Phon Turbo** e infine il temutissimo
**Dott. Forbici, il Veterinario**. Ogni 2 minuti, un anello di cetrioli ti accerchia.

## Struttura del progetto

```
src/catsurvivors/
├── Main.java        Entry point, finestra e game loop (~60 FPS, BufferStrategy)
├── Game.java        Stato di gioco, simulazione, collisioni, level up, rendering del mondo
├── Player.java      Il gatto: statistiche, armi, esperienza
├── Enemy.java       Inseguimento, contraccolpi
├── Weapons.java     Le 8 armi (statistiche per livello + comportamento)
├── Passives.java    I 10 oggetti passivi
├── Enemies.java     Bestiario, ondate, calendario boss
├── Cats.java        Il roster degli 8 gatti
├── Sprites.java     Tutta la grafica, disegnata proceduralmente con Java2D
├── Sfx.java         Effetti sonori chiptune sintetizzati a runtime
├── Ui.java          Menu, HUD, level up, pausa, schermate finali
├── Input.java       Tastiera e mouse (thread-safe EDT → game loop)
└── Autotest.java    Smoke test headless: `java -cp out catsurvivors.Main --autotest`
```

## Test

```bat
java -cp out catsurvivors.Main --autotest
```

Simula 30 secondi di partita senza finestra (movimento sintetico, level up automatici),
verifica che i nemici muoiano e salva due screenshot (`autotest.png`, `autotest-menu.png`).

## Pubblicare su un remote Git

Il repository è già inizializzato e committato. Per il push:

```bash
git remote add origin https://github.com/<tuo-utente>/cat-survivors.git
git push -u origin main
```

## Licenza

[MIT](LICENSE) — © 2026 SonoKarol
