package catsurvivors;

import java.awt.Color;
import java.util.List;

/** Il roster: otto gatti di razze e caratteri diversi, ognuno con bonus e arma iniziale. */
final class Cats {
    private Cats() {}

    private static Color c(int rgb) { return new Color(rgb); }

    // Ordine parametri Stats: maxHp, speed, might, area, cooldown, amount, magnet, armor, regen, luck, xpGain
    static final List<CatDef> ALL = List.of(
        new CatDef("romeo", "Romeo", "Soriano Europeo",
            "Il randagio col cuore d'oro: ha visto cose, ha graffiato di peggio.",
            "+10% esperienza", "graffio",
            new Stats(100, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0, 1.0, 1.1),
            new CatSprite(c(0xd29a55), c(0xf3e3c5), c(0xa8743a), null, c(0x7db95c), false, false), 1.0),

        new CatDef("duchessa", "Duchessa", "Persiano",
            "Aristocratica e pigrissima: si muove poco, ma le sue fusa sono letali.",
            "+20% area, -10% velocità", "fusa",
            new Stats(100, 0.9, 1.0, 1.2, 1.0, 0, 1.0, 0, 0, 1.0, 1.0),
            new CatSprite(c(0xdcdce4), c(0xf2f2f6), null, null, c(0xc9722f), true, false), 1.0),

        new CatDef("diesel", "Diesel", "Maine Coon",
            "Un gigante buono. Finché non vede un cetriolo.",
            "+50 salute, +20% danno, +1 armatura, -15% velocità", "pallapelo",
            new Stats(150, 0.85, 1.2, 1.0, 1.0, 0, 1.0, 1, 0, 1.0, 1.0),
            new CatSprite(c(0x6b5a4e), c(0xcbb9a8), c(0x4e4138), null, c(0xd8a13a), true, false), 1.0),

        new CatDef("luna", "Luna", "Siamese",
            "Iperattiva e chiacchierona: corre più veloce della sua ombra.",
            "+25% velocità, ricarica -8%, -20 salute", "miagolio",
            new Stats(80, 1.25, 1.0, 1.0, 0.92, 0, 1.0, 0, 0, 1.0, 1.0),
            new CatSprite(c(0xefe6d8), null, null, c(0x7a5b46), c(0x5aa7e8), false, false), 1.0),

        new CatDef("felix", "Felix", "Gatto Nero",
            "Porta sfortuna ai nemici e fortuna a sé stesso. Attraversagli la strada, se osi.",
            "+40% fortuna (più scelte, drop e gemme doppie)", "gomitolo",
            new Stats(100, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0, 1.4, 1.0),
            new CatSprite(c(0x2d2d34), null, null, null, c(0xe8c63f), false, false), 1.0),

        new CatDef("cleo", "Cleo", "Sphynx",
            "Mistica, senza pelo e senza paura. Vede cose che gli altri gatti non vedono.",
            "Ricarica -15%, -10 salute", "artigli",
            new Stats(90, 1.0, 1.0, 1.0, 0.85, 0, 1.0, 0, 0, 1.0, 1.0),
            new CatSprite(c(0xe7c3b1), c(0xf0d8cb), null, null, c(0x79c9a8), false, true), 1.0),

        new CatDef("pallino", "Pallino", "Rosso Europeo",
            "Un solo neurone, condiviso con tutti i gatti rossi del mondo. Ma che appetito!",
            "Il cibo cura +60%, +0.4 PS/s, +10 salute", "sardine",
            new Stats(110, 1.0, 1.0, 1.0, 1.0, 0, 1.0, 0, 0.4, 1.0, 1.0),
            new CatSprite(c(0xe0863c), c(0xf6e0c0), c(0xb5641f), null, c(0x67b14e), false, false), 1.6),

        new CatDef("neve", "Neve", "Ragdoll",
            "Morbida come una nuvola, precisa come una tempesta di croccantini.",
            "+1 proiettile, -15% danno", "croccantini",
            new Stats(100, 1.0, 0.85, 1.0, 1.0, 1, 1.0, 0, 0, 1.0, 1.0),
            new CatSprite(c(0xf4f1ec), null, null, c(0xb9a18c), c(0x6fb1e8), true, false), 1.0)
    );
}
