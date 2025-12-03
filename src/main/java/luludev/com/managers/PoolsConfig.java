package luludev.com.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public class PoolsConfig {

    public static class PoolItem {
        private final ItemStack item;
        private final double min;
        private final double max;
        private final int slot;

        public PoolItem(ItemStack item, double min, double max, int slot) {
            this.item = item;
            this.min = min;
            this.max = max;
            this.slot = slot;
        }

        public ItemStack getItem() {
            return item;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public int getSlot() {
            return slot;
        }
    }

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PoolsConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pools.yml");
        load();
    }

    public void load() {
        try {
            if (!file.exists()) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create pools.yml: " + e.getMessage());
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save pools.yml: " + e.getMessage());
        }
    }

    /** ลบข้อมูล pool ทั้งก้อน (ใช้ตอนเซฟใหม่ทั้ง inventory) */
    public void clearPool(String poolName) {
        config.set("pools." + poolName + ".items", null);
    }

    /** รายชื่อทุก pool ที่มีใน pools.yml */
    public Set<String> getAllPools() {
        ConfigurationSection sec = config.getConfigurationSection("pools");
        if (sec == null) return Collections.emptySet();
        return sec.getKeys(false);
    }

    /** อ่านข้อมูล per-slot ของ pool (slot -> PoolItem) */
    public Map<Integer, PoolItem> getItems(String poolName) {
        Map<Integer, PoolItem> map = new HashMap<>();
        String base = "pools." + poolName + ".items";
        if (!config.isConfigurationSection(base)) {
            return map;
        }
        ConfigurationSection sec = config.getConfigurationSection(base);
        if (sec == null) return map;

        for (String key : sec.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;

            ItemStack item = s.getItemStack("item");
            if (item == null) continue;

            double min = s.getDouble("min", 0);
            double max = s.getDouble("max", min);

            map.put(slot, new PoolItem(item, min, max, slot));
        }

        return map;
    }

    /** อ่านข้อมูลของ slot เดียวใน pool */
    public PoolItem getItem(String poolName, int slot) {
        String base = "pools." + poolName + ".items." + slot;
        ItemStack item = config.getItemStack(base + ".item");
        if (item == null) return null;
        double min = config.getDouble(base + ".min", 0);
        double max = config.getDouble(base + ".max", min);
        return new PoolItem(item, min, max, slot);
    }

    /** เซฟข้อมูล slot เดียวใน pool */
    public void setItem(String poolName, int slot, ItemStack item, double min, double max) {
        String base = "pools." + poolName + ".items." + slot;
        config.set(base + ".item", item);
        config.set(base + ".min", min);
        config.set(base + ".max", max);
    }
}
