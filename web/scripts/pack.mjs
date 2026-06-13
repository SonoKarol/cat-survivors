// Crea cat-survivors-itch.zip dal contenuto di dist/ con percorsi a forward-slash
// (lo standard ZIP che itch.io si aspetta). index.html resta nella radice dello zip.
import JSZip from "jszip";
import { readdirSync, readFileSync, writeFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const DIST = "dist";
const OUT = "cat-survivors-itch.zip";

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

const files = walk(DIST);
if (files.length === 0) {
  console.error("dist/ è vuota: esegui prima `npm run build`.");
  process.exit(1);
}

const zip = new JSZip();
for (const f of files) {
  const rel = relative(DIST, f).split("\\").join("/"); // forza forward-slash
  zip.file(rel, readFileSync(f));
}

const buf = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
writeFileSync(OUT, buf);
console.log(`Creato ${OUT} (${(buf.length / 1024).toFixed(1)} KB) con ${files.length} file:`);
for (const f of files) console.log("  - " + relative(DIST, f).split("\\").join("/"));
