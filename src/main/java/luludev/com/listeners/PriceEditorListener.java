package luludev.com.listeners;

import luludev.com.NightMarket;
import luludev.com.guis.PriceEditorGUI;
import luludev.com.managers.PoolsConfig;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class PriceEditorListener implements Listener {

    private final NightMarket plugin;

    public PriceEditorListener(NightMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        if (rawTitle == null) return;

        String title = ChatColor.stripColor(rawTitle);
        if (!title.startsWith("Edit Price: ")) return;

        int rawSlot = event.getRawSlot();
        int topSize = view.getTopInventory().getSize();

        // เราสนใจเฉพาะคลิกบน GUI ด้านบน
        if (rawSlot >= topSize) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // ดึง pool กับ slot จาก title: "Edit Price: <pool>#<slot>"
        String dataPart = title.substring("Edit Price: ".length()); // "<pool>#<slot>"
        int hashIndex = dataPart.lastIndexOf('#');
        if (hashIndex <= 0) {
            player.closeInventory();
            return;
        }

        String pool = dataPart.substring(0, hashIndex).trim();
        int poolSlot;
        try {
            poolSlot = Integer.parseInt(dataPart.substring(hashIndex + 1).trim());
        } catch (NumberFormatException e) {
            player.closeInventory();
            return;
        }

        PoolsConfig pools = plugin.getPoolsConfig();
        PoolsConfig.PoolItem data = pools.getItem(pool, poolSlot);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "ไม่พบข้อมูลราคาไอเท็มนี้");
            player.closeInventory();
            return;
        }

        double min = data.getMin();
        double max = data.getMax();
        ItemStack baseItem = data.getItem().clone();

        // อ่านปุ่มที่กดจากตำแหน่ง (slot)
        switch (rawSlot) {
            // Min
            case 10: // -10 Min
                min -= 10;
                break;
            case 14: // +10 Min
                min += 10;
                break;

            // Max
            case 19: // -10 Max
                max -= 10;
                break;
            case 23: // +10 Max
                max += 10;
                break;

            // Close
            case 25:
                player.closeInventory();
                return;

            default:
                return;
        }

        // ไม่ให้ min ติดลบ, max < min
        if (min < 0) min = 0;
        if (max < min) max = min;

        // เซฟค่ากลับเข้า pools.yml (ใน memory) และเขียนลงไฟล์ทันที
        pools.setItem(pool, poolSlot, baseItem, min, max);
        pools.save();

        // เปิด GUI ใหม่ แสดงค่าที่อัปเดตแล้ว
        player.openInventory(
                PriceEditorGUI.createEditor(plugin, pool, poolSlot)
        );
    }
}
