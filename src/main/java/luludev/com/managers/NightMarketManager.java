package luludev.com.managers;

import luludev.com.NightMarket;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
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

    private final String EDIT_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Edit Pool: ";
    private final String MARKET_TITLE_PREFIX = ChatColor.GOLD + "Night Market: ";

    // pool -> รายการของวันนี้
    private final Map<String, List<MarketEntry>> dailyMarkets = new HashMap<>();

    public NightMarketManager(NightMarket plugin, PurchaseDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    public String getEditTitle(String pool) {
        return EDIT_TITLE_PREFIX + pool;
    }

    public String getMarketTitle(String pool) {
        return MARKET_TITLE_PREFIX + pool;
    }

    // ================== CONFIG ==================

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

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

    public double getPriceMin(String pool) {
        double min = cfg().getDouble("pools." + pool + ".price-min",
                cfg().getDouble("market.price.min", 500.0));
        return Math.max(min, 0);
    }

    public double getPriceMax(String pool) {
        double max = cfg().getDouble("pools." + pool + ".price-max",
                cfg().getDouble("market.price.max", 2000.0));
        return Math.max(max, 0);
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

    // ================== POOL ITEMS ==================

    public List<ItemStack> getPoolItems(String pool) {
        ConfigurationSection poolSection = cfg().getConfigurationSection("pools." + pool);
        List<ItemStack> result = new ArrayList<>();
        if (poolSection == null) return result;

        List<?> rawList = poolSection.getList("items");
        if (rawList != null) {
            for (Object o : rawList) {
                if (o instanceof ItemStack) {
                    result.add(((ItemStack) o).clone());
                }
            }
        }
        return result;
    }

    public void savePoolItems(String pool, ItemStack[] contents) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null) list.add(item.clone());
        }
        cfg().set("pools." + pool + ".items", list);
        plugin.saveConfig();
    }

    // ================== DAILY MARKET ==================

    public void initDailyMarket() {
        String lastDate = cfg().getString("daily.last-refresh-date", "");
        String today = LocalDate.now().toString();

        if (!today.equals(lastDate)) {
            refreshDailyMarket();
        } else {
            loadDailyFromConfig();
        }

        scheduleDailyRefresh();
    }

    private void loadDailyFromConfig() {
        dailyMarkets.clear();
        ConfigurationSection poolsSec = cfg().getConfigurationSection("daily.pools");
        if (poolsSec == null) return;

        for (String pool : poolsSec.getKeys(false)) {
            ConfigurationSection poolSec = poolsSec.getConfigurationSection(pool);
            if (poolSec == null) continue;

            List<?> itemsRaw = poolSec.getList("items");
            List<?> pricesRaw = poolSec.getList("prices");

            if (itemsRaw == null || pricesRaw == null) continue;
            List<MarketEntry> entries = new ArrayList<>();

            for (int i = 0; i < itemsRaw.size() && i < pricesRaw.size(); i++) {
                Object itemObj = itemsRaw.get(i);
                Object priceObj = pricesRaw.get(i);
                if (!(itemObj instanceof ItemStack)) continue;
                double price;
                if (priceObj instanceof Number) {
                    price = ((Number) priceObj).doubleValue();
                } else {
                    continue;
                }
                entries.add(new MarketEntry(((ItemStack) itemObj).clone(), price));
            }

            dailyMarkets.put(pool.toLowerCase(Locale.ROOT), entries);
        }
    }

    public void refreshDailyMarket() {
        dailyMarkets.clear();

        ConfigurationSection poolsSec = cfg().getConfigurationSection("pools");
        if (poolsSec == null) return;

        for (String pool : poolsSec.getKeys(false)) {
            List<ItemStack> poolItems = getPoolItems(pool);
            if (poolItems.isEmpty()) continue;

            int size = getMarketSize();
            int randomCount = getMarketRandomCount();
            if (randomCount > size) randomCount = size;
            if (randomCount > poolItems.size()) randomCount = poolItems.size();

            List<ItemStack> shuffled = new ArrayList<>(poolItems);
            Collections.shuffle(shuffled);

            double min = getPriceMin(pool);
            double max = getPriceMax(pool);
            if (max < min) {
                double tmp = min;
                min = max;
                max = tmp;
            }

            List<MarketEntry> entries = new ArrayList<>();
            Random rand = new Random();

            for (int i = 0; i < randomCount; i++) {
                ItemStack original = shuffled.get(i).clone();
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

            dailyMarkets.put(pool.toLowerCase(Locale.ROOT), entries);

            cfg().set("daily.pools." + pool + ".items",
                    entries.stream().map(MarketEntry::getItem).toList());
            cfg().set("daily.pools." + pool + ".prices",
                    entries.stream().map(MarketEntry::getPrice).toList());
        }

        cfg().set("daily.last-refresh-date", LocalDate.now().toString());
        plugin.saveConfig();

        String msg = getMessage("daily-refreshed")
                .replace("{date}", LocalDate.now().toString());
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

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

        long initialDelaySeconds = java.time.Duration.between(now, next).toSeconds();
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

    // ================== GUI ==================

    public void openEditPoolGUI(Player player, String pool) {
        int size = cfg().getInt("pools." + pool + ".size", 54);
        if (size % 9 != 0) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, getEditTitle(pool));

        List<ItemStack> poolItems = getPoolItems(pool);
        int index = 0;
        for (ItemStack item : poolItems) {
            if (index >= size) break;
            inv.setItem(index++, item);
        }

        player.openInventory(inv);
    }

    public void openMarketGUI(Player player, String pool) {
        String key = pool.toLowerCase(Locale.ROOT);
        if (!dailyMarkets.containsKey(key)) {
            player.sendMessage(getMessage("unknown-pool").replace("{pool}", pool));
            return;
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
