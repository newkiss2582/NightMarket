package luludev.com.managers;

import luludev.com.NightMarket;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NightMarketManager {

    public static class MarketEntry {
        private final ItemStack item;
        private final double price;

        public MarketEntry(ItemStack item, double price) {
            this.item = item;
            this.price = price;
        }

        public ItemStack getItem() {
            return item;
        }

        public double getPrice() {
            return price;
        }
    }

    private final NightMarket plugin;
    private final PurchaseDatabase database;
    private final PoolsConfig poolsConfig;

    private final String EDIT_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Edit Pool: ";
    private final String MARKET_TITLE_PREFIX = ChatColor.GOLD + "Night Market: ";

    // pool(lowercase) -> รายการของ "วันนี้" (เก็บแค่ใน memory)
    private final Map<String, List<MarketEntry>> dailyMarkets = new HashMap<>();

    public NightMarketManager(NightMarket plugin, PurchaseDatabase database, PoolsConfig poolsConfig) {
        this.plugin = plugin;
        this.database = database;
        this.poolsConfig = poolsConfig;
    }

    public String getEditTitle(String pool) {
        return EDIT_TITLE_PREFIX + pool;
    }

    public String getMarketTitle(String pool) {
        return MARKET_TITLE_PREFIX + pool;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ======================================================
    // CONFIG VALUE
    // ======================================================

    public int getMarketSize() {
        int size = cfg().getInt("market.size", 27);
        if (size <= 0 || size % 9 != 0) size = 27;
        return size;
    }

    public int getMarketRandomCount() {
        return cfg().getInt("market.random-count", 9);
    }

    public boolean isOnePurchasePerDay() {
        return cfg().getBoolean("market.one-purchase-per-day", true);
    }

    /** default min/max ถ้ายังไม่ตั้ง per-item */
    public double getDefaultPriceMin() {
        return Math.max(cfg().getDouble("market.price.min", 500.0), 0);
    }

    public double getDefaultPriceMax() {
        return Math.max(cfg().getDouble("market.price.max", 2000.0), 0);
    }

    public String getMessage(String key) {
        String prefix = ChatColor.translateAlternateColorCodes('&',
                cfg().getString("messages.prefix", "&8[&dNightMarket&8] "));
        String msg = cfg().getString("messages." + key, "");
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        return prefix + msg;
    }

    public String formatPrice(double price) {
        return String.format(Locale.US, "%.0f", price);
    }

    // ======================================================
    // EDIT POOL GUI (อ่าน/เขียนจาก pools.yml เท่านั้น)
    // ======================================================

    public void openEditPoolGUI(Player player, String pool) {
        int size = cfg().getInt("pools." + pool + ".size", 54);
        if (size % 9 != 0) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, getEditTitle(pool));

        Map<Integer, PoolsConfig.PoolItem> poolItems = poolsConfig.getItems(pool);
        for (Map.Entry<Integer, PoolsConfig.PoolItem> e : poolItems.entrySet()) {
            int slot = e.getKey();
            if (slot < 0 || slot >= size) continue;

            PoolsConfig.PoolItem pItem = e.getValue();
            ItemStack item = pItem.getItem().clone();

            // แสดง min/max ไว้ใน lore ให้เห็นตอน edit
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ChatColor.GRAY + "Min: " + ChatColor.GOLD + formatPrice(pItem.getMin()));
                lore.add(ChatColor.GRAY + "Max: " + ChatColor.GOLD + formatPrice(pItem.getMax()));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    /** เซฟของจากหน้า Edit Pool กลับไปที่ pools.yml (ไม่ยุ่ง config.yml) */
    public void savePoolItems(String pool, ItemStack[] contents) {
        poolsConfig.clearPool(pool);

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) continue;

            // ถ้ามีราคาเดิมอยู่แล้วก็ใช้ต่อ ไม่งั้นใช้ default
            PoolsConfig.PoolItem old = poolsConfig.getItem(pool, slot);
            double min = old != null ? old.getMin() : getDefaultPriceMin();
            double max = old != null ? old.getMax() : getDefaultPriceMax();

            poolsConfig.setItem(pool, slot, item.clone(), min, max);
        }

        poolsConfig.save();
    }

    // ======================================================
    // DAILY MARKET (เก็บแค่ใน memory + สุ่มใหม่ทุกวัน)
    // ======================================================

    /** เรียกตอน onEnable: สุ่มตลาดรอบแรก + ตั้ง schedule 8:00 */
    public void initDailyMarket() {
        // สุ่มตลาดทันทีตอนเปิดปลั๊กอิน
        refreshDailyMarket();

        // ตั้งเวลาให้สุ่มใหม่ทุกวัน
        scheduleDailyRefresh();
    }

    /** สุ่มรายการขายของแต่ละ pool สำหรับ "วันนี้" (memory only) */
    public void refreshDailyMarket() {
        dailyMarkets.clear();

        Set<String> pools = poolsConfig.getAllPools();
        if (pools.isEmpty()) {
            plugin.getLogger().warning("NightMarket: ไม่พบ pool ใดใน pools.yml");
            return;
        }

        for (String pool : pools) {
            generateDailyMarketForPool(pool);
        }

        String msg = getMessage("daily-refreshed")
                .replace("{date}", LocalDate.now().toString());
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    /** สุ่มตลาดจาก pool หนึ่ง (ใช้ข้อมูลจาก pools.yml เท่านั้น) */
    private void generateDailyMarketForPool(String pool) {
        Map<Integer, PoolsConfig.PoolItem> items = poolsConfig.getItems(pool);
        if (items.isEmpty()) return;

        int size = getMarketSize();
        int randomCount = getMarketRandomCount();
        if (randomCount > size) randomCount = size;
        if (randomCount > items.size()) randomCount = items.size();

        List<PoolsConfig.PoolItem> list = new ArrayList<>(items.values());
        Collections.shuffle(list);

        List<MarketEntry> entries = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < randomCount; i++) {
            PoolsConfig.PoolItem pItem = list.get(i);
            ItemStack original = pItem.getItem().clone();

            double min = pItem.getMin();
            double max = pItem.getMax();
            if (min <= 0 && max <= 0) {
                min = getDefaultPriceMin();
                max = getDefaultPriceMax();
            }
            if (max < min) {
                double t = min;
                min = max;
                max = t;
            }

            double price = min + (max - min) * rand.nextDouble();

            ItemMeta meta = original.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ChatColor.YELLOW + "ราคา: " + ChatColor.GOLD + formatPrice(price));
                meta.setLore(lore);
                original.setItemMeta(meta);
            }

            entries.add(new MarketEntry(original, price));
        }

        String key = pool.toLowerCase(Locale.ROOT);
        dailyMarkets.put(key, entries);
    }

    /** ตั้ง schedule ให้สุ่มใหม่ทุกวันตามเวลาใน config (market.refresh-time) */
    private void scheduleDailyRefresh() {
        String timeStr = cfg().getString("market.refresh-time", "08:00");
        LocalTime targetTime;
        try {
            targetTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            targetTime = LocalTime.of(8, 0);
        }

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now.withHour(targetTime.getHour()).withMinute(targetTime.getMinute())
                .withSecond(0).withNano(0);

        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }

        long initialDelaySeconds = Duration.between(now, next).toSeconds();
        long periodSeconds = 24 * 60 * 60;

        long initialDelayTicks = initialDelaySeconds * 20L;
        long periodTicks = periodSeconds * 20L;

        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::refreshDailyMarket,
                initialDelayTicks,
                periodTicks
        );
    }

    // ======================================================
    // NIGHT MARKET GUI (ฝั่งคนซื้อ)
    // ======================================================

    public void openMarketGUI(Player player, String pool) {
        String key = pool.toLowerCase(Locale.ROOT);

        // ถ้ายังไม่มีตลาดของ pool นี้ (เช่น เพิ่งสร้าง pool ใหม่)
        if (!dailyMarkets.containsKey(key)) {
            generateDailyMarketForPool(pool);
        }

        List<MarketEntry> entries = dailyMarkets.get(key);
        if (entries == null || entries.isEmpty()) {
            player.sendMessage(getMessage("pool-empty").replace("{pool}", pool));
            return;
        }

        int size = getMarketSize();
        Inventory inv = Bukkit.createInventory(null, size, getMarketTitle(pool));

        for (int i = 0; i < entries.size() && i < size; i++) {
            MarketEntry entry = entries.get(i);
            if (entry != null) {
                inv.setItem(i, entry.getItem().clone());
            }
        }

        player.openInventory(inv);
    }

    public void handleMarketClick(Player player, Inventory marketInv, int slot, String pool) {
        String poolKey = pool.toLowerCase(Locale.ROOT);
        List<MarketEntry> entries = dailyMarkets.get(poolKey);
        if (entries == null) return;
        if (slot < 0 || slot >= entries.size()) return;

        MarketEntry entry = entries.get(slot);
        if (entry == null) return;

        ItemStack clicked = marketInv.getItem(slot);
        if (clicked == null) return;

        if (isOnePurchasePerDay() &&
                database.hasPurchasedToday(player.getUniqueId().toString(), poolKey)) {
            player.sendMessage(getMessage("already-bought-today"));
            return;
        }

        Economy eco = NightMarket.getEconomy();
        double price = entry.getPrice();
        double balance = eco.getBalance(player);
        if (balance < price) {
            player.sendMessage(
                    getMessage("not-enough-money")
                            .replace("{price}", formatPrice(price))
            );
            return;
        }

        eco.withdrawPlayer(player, price);

        ItemStack reward = clicked.clone();
        if (player.getInventory().addItem(reward).isEmpty()) {
            marketInv.clear(slot);
            entries.set(slot, null);

            String itemName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                    ? ChatColor.stripColor(reward.getItemMeta().getDisplayName())
                    : reward.getType().name();

            player.sendMessage(
                    getMessage("buy-success")
                            .replace("{item}", itemName)
                            .replace("{price}", formatPrice(price))
            );

            database.logPurchase(player.getUniqueId().toString(),
                    player.getName(), poolKey, itemName, price);
        } else {
            player.sendMessage(getMessage("inventory-full"));
        }
    }
}
