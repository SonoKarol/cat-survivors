import { Color } from "./color.ts";
import { CatDef, CatSprite, Stats } from "./types.ts";

const c = (hex: number) => Color.rgb(hex);

// Ordine parametri Stats: maxHp, speed, might, area, cooldown, amount, magnet, armor, regen, luck, xpGain
/** Il roster: otto gatti di razze e caratteri diversi, ognuno con bonus e arma iniziale. */
export const CATS: readonly CatDef[] = [
  new CatDef("romeo", "Romeo", "European Shorthair",
    "The golden-hearted stray: he's seen things, scratched worse.",
    "+10% experience", "graffio",
    new Stats(100, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0, 1.0, 1.1),
    new CatSprite(c(0xd29a55), c(0xf3e3c5), c(0xa8743a), null, c(0x7db95c), false, false), 1.0),

  new CatDef("duchessa", "Duchessa", "Persian",
    "Aristocratic and terribly lazy: barely moves, but her purrs are lethal.",
    "+20% area, -10% speed", "fusa",
    new Stats(100, 0.9, 1.0, 1.2, 1.0, 0, 1.0, 0, 0, 1.0, 1.0),
    new CatSprite(c(0xdcdce4), c(0xf2f2f6), null, null, c(0xc9722f), true, false), 1.0),

  new CatDef("diesel", "Diesel", "Maine Coon",
    "A gentle giant. Until he spots a cucumber.",
    "+50 health, +20% damage, +1 armor, -15% speed", "pallapelo",
    new Stats(150, 0.85, 1.2, 1.0, 1.0, 0, 1.0, 1, 0, 1.0, 1.0),
    new CatSprite(c(0x6b5a4e), c(0xcbb9a8), c(0x4e4138), null, c(0xd8a13a), true, false), 1.0),

  new CatDef("luna", "Luna", "Siamese",
    "Hyperactive and chatty: runs faster than her own shadow.",
    "+25% speed, cooldown -8%, -20 health", "miagolio",
    new Stats(80, 1.25, 1.0, 1.0, 0.92, 0, 1.0, 0, 0, 1.0, 1.0),
    new CatSprite(c(0xefe6d8), null, null, c(0x7a5b46), c(0x5aa7e8), false, false), 1.0),

  new CatDef("felix", "Felix", "Black Cat",
    "Bad luck for enemies, good luck for himself. Cross his path if you dare.",
    "+40% luck (more choices, drops and double gems)", "gomitolo",
    new Stats(100, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0, 1.4, 1.0),
    new CatSprite(c(0x2d2d34), null, null, null, c(0xe8c63f), false, false), 1.0),

  new CatDef("cleo", "Cleo", "Sphynx",
    "Mystical, hairless, and fearless. Sees things other cats don't.",
    "Cooldown -15%, -10 health", "artigli",
    new Stats(90, 1.0, 1.0, 1.0, 0.85, 0, 1.0, 0, 0, 1.0, 1.0),
    new CatSprite(c(0xe7c3b1), c(0xf0d8cb), null, null, c(0x79c9a8), false, true), 1.0),

  new CatDef("pallino", "Pallino", "European Red",
    "One neuron, shared with every ginger cat in the world. But what an appetite!",
    "Food heals +60%, +0.4 HP/s, +10 health", "sardine",
    new Stats(110, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0.4, 1.0, 1.0),
    new CatSprite(c(0xe0863c), c(0xf6e0c0), c(0xb5641f), null, c(0x67b14e), false, false), 1.6),

  new CatDef("neve", "Neve", "Ragdoll",
    "Soft as a cloud, precise as a kibble storm.",
    "+1 projectile, -15% damage", "croccantini",
    new Stats(100, 1.0, 0.85, 1.0, 1.0, 1, 1.0, 0, 0, 1.0, 1.0),
    new CatSprite(c(0xf4f1ec), null, null, c(0xb9a18c), c(0x6fb1e8), true, false), 1.0),
];
