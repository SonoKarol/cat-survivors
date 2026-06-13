import { defineConfig } from "vite";

// base "./" => percorsi relativi: indispensabile per l'embed HTML5 di itch.io,
// che serve il gioco da una sottocartella con dominio randomico.
export default defineConfig({
  base: "./",
  build: {
    target: "es2020",
    outDir: "dist",
    assetsInlineLimit: 0,
  },
});
