package catsurvivors;

import java.awt.Color;

/** Statistiche di un personaggio: usate sia come base/moltiplicatori sia come valori effettivi calcolati. */
final class Stats {
    double maxHp, speed, might, area, cooldown, magnet, armor, regen, luck, xpGain;
    int amount;

    Stats(double maxHp, double speed, double might, double area, double cooldown,
          int amount, double magnet, double armor, double regen, double luck, double xpGain) {
        this.maxHp = maxHp;
        this.speed = speed;
        this.might = might;
        this.area = area;
        this.cooldown = cooldown;
        this.amount = amount;
        this.magnet = magnet;
        this.armor = armor;
        this.regen = regen;
        this.luck = luck;
        this.xpGain = xpGain;
    }
}

/** Parametri estetici per disegnare proceduralmente un gatto. */
final class CatSprite {
    final Color body, belly, stripes, mask, eyes;
    final boolean fluffy, sphynx;

    CatSprite(Color body, Color belly, Color stripes, Color mask, Color eyes, boolean fluffy, boolean sphynx) {
        this.body = body;
        this.belly = belly;
        this.stripes = stripes;
        this.mask = mask;
        this.eyes = eyes;
        this.fluffy = fluffy;
        this.sphynx = sphynx;
    }
}

/** Definizione di un gatto giocabile: razza, carattere, statistiche e arma iniziale. */
final class CatDef {
    final String id, name, breed, personality, bonus, startWeapon;
    final Stats base;
    final CatSprite sprite;
    final double foodBonus; // moltiplicatore di cura del cibo raccolto

    CatDef(String id, String name, String breed, String personality, String bonus,
           String startWeapon, Stats base, CatSprite sprite, double foodBonus) {
        this.id = id;
        this.name = name;
        this.breed = breed;
        this.personality = personality;
        this.bonus = bonus;
        this.startWeapon = startWeapon;
        this.base = base;
        this.sprite = sprite;
        this.foodBonus = foodBonus;
    }
}
