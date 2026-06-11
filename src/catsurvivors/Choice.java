package catsurvivors;

/** Una scelta proposta al level up. */
final class Choice {
    final String kind; // "weapon" | "passive" | "heal" | "xp"
    final String id, name, desc, lvlText, icon;

    Choice(String kind, String id, String name, String desc, String lvlText, String icon) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.lvlText = lvlText;
        this.icon = icon;
    }
}
