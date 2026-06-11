package catsurvivors;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Protocollo binario del co-op (TCP, host autoritativo).
 * Client -> Host: HELLO (gatto scelto), INPUT (movimento+mira), CHOICE (level up).
 * Host -> Client: WELCOME (pid), SNAPSHOT (stato del mondo, ~20 Hz), LEVELUP (scelte).
 */
final class Net {
    static final int DEFAULT_PORT = 7777;
    static final int MAX_PLAYERS = 4;

    static final byte HELLO = 1, INPUT = 2, CHOICE = 3;
    static final byte WELCOME = 10, SNAPSHOT = 11, LEVELUP = 12;

    // indici stabili per non spedire stringhe a ogni frame
    static final List<String> ENEMY_IDS = List.copyOf(Enemies.TYPES.keySet());
    static final List<String> WEAPON_IDS = List.copyOf(Weapons.MAP.keySet());
    static final List<String> PASSIVE_IDS = List.copyOf(Passives.MAP.keySet());
    static final List<String> PROJ_TYPES = List.of("slash", "ball", "lob", "wave", "orbit", "knife", "fall", "boom");

    private Net() {}

    private static int idx(List<String> list, String v) {
        int i = list.indexOf(v);
        return i < 0 ? 0 : i;
    }

    static byte[] welcome(int pid) {
        return build(out -> {
            out.writeByte(WELCOME);
            out.writeInt(pid);
        });
    }

    static byte[] levelUp(List<Choice> cs) {
        return build(out -> {
            out.writeByte(LEVELUP);
            out.writeByte(cs.size());
            for (Choice c : cs) {
                out.writeUTF(c.kind);
                out.writeUTF(c.id);
                out.writeUTF(c.name);
                out.writeUTF(c.desc);
                out.writeUTF(c.lvlText);
                out.writeUTF(c.icon);
            }
        });
    }

    static List<Choice> readChoices(DataInputStream in) throws IOException {
        int n = in.readUnsignedByte();
        List<Choice> cs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cs.add(new Choice(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF()));
        }
        return cs;
    }

    /** Fotografia del mondo per i client. */
    static byte[] snapshot(Game g) {
        return build(out -> {
            out.writeByte(SNAPSHOT);
            out.writeDouble(g.time);
            out.writeInt(g.kills);
            out.writeByte(g.state.ordinal());
            out.writeByte(g.leveling != null ? g.leveling.pid : -1);

            out.writeByte(g.players.size());
            for (Player p : g.players) {
                out.writeByte(p.pid);
                out.writeByte(Cats.ALL.indexOf(p.cat));
                out.writeFloat((float) p.x);
                out.writeFloat((float) p.y);
                int flags = (p.alive ? 1 : 0) | (p.faceX < 0 ? 2 : 0) | (p.moving ? 4 : 0) | (p.iframe > 0 ? 8 : 0);
                out.writeByte(flags);
                out.writeFloat((float) (p.hp / p.stats.maxHp));
                out.writeShort(p.level);
                out.writeFloat((float) Util.clamp(p.xp / p.xpNext, 0, 1));
                out.writeFloat((float) (p.getWeapon("fusa") != null ? p.auraR : 0));
                out.writeByte(p.weapons.size());
                for (Player.WeaponInst w : p.weapons) {
                    out.writeByte(idx(WEAPON_IDS, w.def.id));
                    out.writeByte(w.level);
                }
                out.writeByte(p.passives.size());
                for (Map.Entry<String, Integer> en : p.passives.entrySet()) {
                    out.writeByte(idx(PASSIVE_IDS, en.getKey()));
                    out.writeByte(en.getValue());
                }
            }

            int ne = Math.min(g.enemies.size(), 220);
            out.writeShort(ne);
            for (int i = 0; i < ne; i++) {
                Enemy e = g.enemies.get(i);
                out.writeShort(e.id & 0x7fff);
                out.writeByte(idx(ENEMY_IDS, e.type));
                out.writeFloat((float) e.x);
                out.writeFloat((float) e.y);
                out.writeByte((e.elite ? 1 : 0) | (e.boss ? 2 : 0) | (e.flash > 0 ? 4 : 0));
                out.writeFloat((float) Util.clamp(e.hp / e.maxHp, 0, 1));
            }

            int np = Math.min(g.projectiles.size(), 250);
            out.writeShort(np);
            for (int i = 0; i < np; i++) {
                Projectile pr = g.projectiles.get(i);
                out.writeByte(idx(PROJ_TYPES, pr.type));
                out.writeFloat((float) pr.x);
                out.writeFloat((float) pr.y);
                out.writeFloat((float) pr.r);
                out.writeFloat((float) pr.ang);
                float lifeRatio = pr.maxLife > 0 ? (float) Util.clamp(pr.life / pr.maxLife, 0, 1) : 1f;
                if (pr.delay > 0) lifeRatio = -1; // non ancora attivo: il client non lo disegna
                out.writeFloat(lifeRatio);
                out.writeFloat((float) (pr.type.equals("fall") ? pr.ty : pr.rot));
            }

            int ng = Math.min(g.gems.size(), 220);
            out.writeShort(ng);
            for (int i = 0; i < ng; i++) {
                Gem gm = g.gems.get(i);
                out.writeFloat((float) gm.x);
                out.writeFloat((float) gm.y);
                out.writeInt(gm.value);
            }

            out.writeByte(Math.min(g.pickups.size(), 120));
            for (int i = 0; i < Math.min(g.pickups.size(), 120); i++) {
                Pickup pk = g.pickups.get(i);
                out.writeByte(pk.kind.equals("croccantino") ? 0 : 1);
                out.writeFloat((float) pk.x);
                out.writeFloat((float) pk.y);
            }

            int nf = Math.min(g.floats.size(), 24);
            out.writeByte(nf);
            for (int i = g.floats.size() - nf; i < g.floats.size(); i++) {
                FloatText f = g.floats.get(i);
                out.writeFloat((float) f.x);
                out.writeFloat((float) f.y);
                out.writeInt(f.color.getRGB());
                out.writeFloat((float) f.life);
                out.writeUTF(f.text);
            }

            Enemy boss = null;
            for (Enemy e : g.enemies) {
                if (e.boss && !e.dead) { boss = e; break; }
            }
            out.writeBoolean(boss != null);
            if (boss != null) {
                out.writeUTF(boss.def.name);
                out.writeFloat((float) Util.clamp(boss.hp / boss.maxHp, 0, 1));
            }
        });
    }

    static Snapshot readSnapshot(DataInputStream in) throws IOException {
        Snapshot s = new Snapshot();
        s.time = in.readDouble();
        s.kills = in.readInt();
        s.state = in.readUnsignedByte();
        s.levelingPid = in.readByte();

        int npl = in.readUnsignedByte();
        s.players = new Snapshot.PlayerS[npl];
        for (int i = 0; i < npl; i++) {
            Snapshot.PlayerS p = new Snapshot.PlayerS();
            p.pid = in.readUnsignedByte();
            p.catIdx = in.readUnsignedByte();
            p.x = in.readFloat();
            p.y = in.readFloat();
            int flags = in.readUnsignedByte();
            p.alive = (flags & 1) != 0;
            p.flipped = (flags & 2) != 0;
            p.moving = (flags & 4) != 0;
            p.hurt = (flags & 8) != 0;
            p.hpRatio = in.readFloat();
            p.level = in.readShort();
            p.xpRatio = in.readFloat();
            p.auraR = in.readFloat();
            int nw = in.readUnsignedByte();
            p.weapons = new int[nw][2];
            for (int j = 0; j < nw; j++) { p.weapons[j][0] = in.readUnsignedByte(); p.weapons[j][1] = in.readUnsignedByte(); }
            int np2 = in.readUnsignedByte();
            p.passives = new int[np2][2];
            for (int j = 0; j < np2; j++) { p.passives[j][0] = in.readUnsignedByte(); p.passives[j][1] = in.readUnsignedByte(); }
            s.players[i] = p;
        }

        int ne = in.readShort();
        s.enemies = new Snapshot.EnemyS[ne];
        for (int i = 0; i < ne; i++) {
            Snapshot.EnemyS e = new Snapshot.EnemyS();
            e.id = in.readShort();
            e.typeIdx = in.readUnsignedByte();
            e.x = in.readFloat();
            e.y = in.readFloat();
            int flags = in.readUnsignedByte();
            e.elite = (flags & 1) != 0;
            e.boss = (flags & 2) != 0;
            e.flash = (flags & 4) != 0;
            e.hpRatio = in.readFloat();
            s.enemies[i] = e;
        }

        int np = in.readShort();
        s.projs = new Snapshot.ProjS[np];
        for (int i = 0; i < np; i++) {
            Snapshot.ProjS pr = new Snapshot.ProjS();
            pr.typeIdx = in.readUnsignedByte();
            pr.x = in.readFloat();
            pr.y = in.readFloat();
            pr.r = in.readFloat();
            pr.ang = in.readFloat();
            pr.lifeRatio = in.readFloat();
            pr.extra = in.readFloat();
            s.projs[i] = pr;
        }

        int ng = in.readShort();
        s.gems = new Snapshot.GemS[ng];
        for (int i = 0; i < ng; i++) {
            Snapshot.GemS gm = new Snapshot.GemS();
            gm.x = in.readFloat();
            gm.y = in.readFloat();
            gm.value = in.readInt();
            s.gems[i] = gm;
        }

        int npk = in.readUnsignedByte();
        s.picks = new Snapshot.PickS[npk];
        for (int i = 0; i < npk; i++) {
            Snapshot.PickS pk = new Snapshot.PickS();
            pk.kind = in.readUnsignedByte();
            pk.x = in.readFloat();
            pk.y = in.readFloat();
            s.picks[i] = pk;
        }

        int nf = in.readUnsignedByte();
        s.floats = new Snapshot.FloatS[nf];
        for (int i = 0; i < nf; i++) {
            Snapshot.FloatS f = new Snapshot.FloatS();
            f.x = in.readFloat();
            f.y = in.readFloat();
            f.rgb = in.readInt();
            f.life = in.readFloat();
            f.text = in.readUTF();
            s.floats[i] = f;
        }

        s.hasBoss = in.readBoolean();
        if (s.hasBoss) {
            s.bossName = in.readUTF();
            s.bossRatio = in.readFloat();
        }
        return s;
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private static byte[] build(Writer w) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
            DataOutputStream out = new DataOutputStream(bos);
            w.write(out);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // impossibile su ByteArrayOutputStream
        }
    }
}
