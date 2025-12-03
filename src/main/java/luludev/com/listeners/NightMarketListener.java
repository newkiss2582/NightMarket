package luludev.com.listeners;

import luludev.com.NightMarket;
import luludev.com.guis.PriceEditorGUI;
import luludev.com.managers.NightMarketManager;
import luludev.com.managers.PoolsConfig;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class NightMarketListener implements Listener {

    private final NightMarket plugin;
    private final NightMarketManager manager;

    public NightMarketListener(NightMarket plugin, NightMarketManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        if (rawTitle == null) return;

        String title = ChatColor.stripColor(rawTitle);

        // =====================================================
        // 1) หน้าจอ Edit Pool
        // =====================================================
        if (title.startsWith("Edit Pool: ")) {

            int rawSlot = event.getRawSlot();
            int topSize = view.getTopInventory().getSize();

            // คลิกในช่อง GUI ด้านบน
            if (rawSlot < topSize) {

                // คลิกขวา = เปิด GUI ปรับราคา
                if (event.getClick() == ClickType.RIGHT) {
                    event.setCancelled(true);

                    ItemStack clicked = event.getCurrentItem();
                    if (clicked == null || clicked.getType() == Material.AIR) return;

                    Player player = (Player) event.getWhoClicked();
                    String pool = title.substring("Edit Pool: ".length()).trim();
                    int slot = rawSlot;

                    PoolsConfig pools = plugin.getPoolsConfig();
                    PoolsConfig.PoolItem data = pools.getItem(pool, slot);

                    // ถ้ายังไม่มี entry ใน pools.yml → สร้างใหม่ด้วย default min/max
                    if (data == null) {
                        double defMin = manager.getDefaultPriceMin();
                        double defMax = manager.getDefaultPriceMax();
                        data = new PoolsConfig.PoolItem(clicked.clone(), defMin, defMax, slot);
                        pools.setItem(pool, slot, clicked.clone(), defMin, defMax);
                        pools.save();
                    }

                    // เปิดหน้าแก้ราคา
                    player.openInventory(
                            PriceEditorGUI.createEditor(plugin, pool, slot)
                    );
                    return;
                }

                // คลิกซ้าย / drag-drop = แก้ตำแหน่งของใน pool ตามปกติ
                return;
            }

            // คลิกใน inventory ผู้เล่นด้านล่าง → ปล่อยตามปกติ
            return;
        }

        // =====================================================
        // 2) หน้าจอ Night Market (ซื้อของ)
        // =====================================================
        if (title.startsWith("Night Market: ")) {
            int rawSlot = event.getRawSlot();
            int topSize = view.getTopInventory().getSize();

            if (rawSlot < topSize) {
                event.setCancelled(true);

                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    Inventory top = view.getTopInventory();
                    String pool = title.substring("Night Market: ".length()).trim();

                    manager.handleMarketClick(player, top, rawSlot, pool);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        if (rawTitle == null) return;

        String title = ChatColor.stripColor(rawTitle);

        // ปิดหน้า Edit Pool → บันทึกของใน pool กลับไปที่ pools.yml
        if (title.startsWith("Edit Pool: ")) {
            String pool = title.substring("Edit Pool: ".length()).trim();
            Inventory top = view.getTopInventory();
            manager.savePoolItems(pool, top.getContents());

            if (event.getPlayer() instanceof Player) {
                event.getPlayer().sendMessage(
                        ChatColor.GREEN + "บันทึก Pool \"" + pool + "\" เรียบร้อย!"
                );
            }
        }
    }
}
