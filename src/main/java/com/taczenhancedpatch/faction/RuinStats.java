package com.taczenhancedpatch.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 遗迹战斗统计。
 * 从第一名玩家进入领地时创建并做一次快照, 之后累计击杀与伤害;
 * 领地内没有玩家超过配置的空闲时间后整体清空, 夺旗成功时冻结为纪念品数据。
 */
public final class RuinStats {
    /* 一次击杀记录: 击杀者, 该生物原本携带的武器与剩余弹药 */
    public record Kill(UUID player, String name, int weaponValue, ItemStack weapon, Map<String, Integer> ammo) {
    }

    private int snapshotWeaponValue;
    private final Map<String, Integer> snapshotAmmo = new LinkedHashMap<>();
    private final List<Kill> kills = new ArrayList<>();
    private final Map<UUID, Float> damage = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private int idleTicks;

    public void beginSnapshot(int weaponValue, Map<String, Integer> ammo) {
        snapshotWeaponValue = Math.max(0, weaponValue);
        snapshotAmmo.clear();
        snapshotAmmo.putAll(ammo);
    }

    public void addKill(Kill kill) {
        kills.add(kill);
        names.putIfAbsent(kill.player(), kill.name());
    }

    public void addDamage(UUID player, String name, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        damage.merge(player, amount, Float::sum);
        names.putIfAbsent(player, name);
    }

    public void resetIdle() {
        idleTicks = 0;
    }

    public int idleTicks() {
        return idleTicks;
    }

    public void tickIdle() {
        idleTicks++;
    }

    public int snapshotWeaponValue() {
        return snapshotWeaponValue;
    }

    public Map<String, Integer> snapshotAmmo() {
        return snapshotAmmo;
    }

    public List<Kill> kills() {
        return kills;
    }

    public Map<UUID, Float> damage() {
        return damage;
    }

    public Map<UUID, String> names() {
        return names;
    }

    public int killCount(UUID player) {
        int count = 0;
        for (Kill kill : kills) {
            if (kill.player().equals(player)) {
                count++;
            }
        }
        return count;
    }

    public float totalDamage() {
        float total = 0.0F;
        for (float value : damage.values()) {
            total += value;
        }
        return total;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("snapshotWeaponValue", snapshotWeaponValue);
        tag.put("snapshotAmmo", ammoTag(snapshotAmmo));
        tag.putInt("idleTicks", idleTicks);
        ListTag killList = new ListTag();
        for (Kill kill : kills) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", kill.player());
            entry.putString("name", kill.name());
            entry.putInt("weaponValue", kill.weaponValue());
            if (!kill.weapon().isEmpty()) {
                entry.put("weapon", kill.weapon().save(new CompoundTag()));
            }
            entry.put("ammo", ammoTag(kill.ammo()));
            killList.add(entry);
        }
        tag.put("kills", killList);
        ListTag damageList = new ListTag();
        for (Map.Entry<UUID, Float> entry : damage.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putFloat("amount", entry.getValue());
            item.putString("name", names.getOrDefault(entry.getKey(), ""));
            damageList.add(item);
        }
        tag.put("damage", damageList);
        return tag;
    }

    public static RuinStats load(CompoundTag tag) {
        RuinStats stats = new RuinStats();
        stats.snapshotWeaponValue = tag.getInt("snapshotWeaponValue");
        stats.idleTicks = tag.getInt("idleTicks");
        readAmmo(tag.getList("snapshotAmmo", Tag.TAG_COMPOUND), stats.snapshotAmmo);
        ListTag killList = tag.getList("kills", Tag.TAG_COMPOUND);
        for (int i = 0; i < killList.size(); i++) {
            CompoundTag entry = killList.getCompound(i);
            Map<String, Integer> ammo = new LinkedHashMap<>();
            readAmmo(entry.getList("ammo", Tag.TAG_COMPOUND), ammo);
            ItemStack weapon = entry.contains("weapon", Tag.TAG_COMPOUND)
                    ? ItemStack.of(entry.getCompound("weapon")) : ItemStack.EMPTY;
            stats.kills.add(new Kill(entry.getUUID("player"), entry.getString("name"),
                    entry.getInt("weaponValue"), weapon, ammo));
            stats.names.putIfAbsent(entry.getUUID("player"), entry.getString("name"));
        }
        ListTag damageList = tag.getList("damage", Tag.TAG_COMPOUND);
        for (int i = 0; i < damageList.size(); i++) {
            CompoundTag entry = damageList.getCompound(i);
            stats.damage.put(entry.getUUID("player"), entry.getFloat("amount"));
            stats.names.putIfAbsent(entry.getUUID("player"), entry.getString("name"));
        }
        return stats;
    }

    private static CompoundTag ammoTag(Map<String, Integer> ammo) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : ammo.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("id", entry.getKey());
            item.putInt("count", entry.getValue());
            list.add(item);
        }
        tag.put("entries", list);
        return tag;
    }

    private static void readAmmo(ListTag list, Map<String, Integer> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            target.merge(item.getString("id"), item.getInt("count"), Integer::sum);
        }
    }
}
