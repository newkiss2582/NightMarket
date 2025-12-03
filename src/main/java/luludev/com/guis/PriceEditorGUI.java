package luludev.com.guis;

import luludev.com.NightMarket;
import luludev.com.managers.PoolsConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PriceEditorGUI {

    // layout:
    // size: 27 (3 แถว)
    // 4  = ไอเทมตัวอย่าง + โชว์ min/max
    //
    // 10 = -10 Min (แดง)
    // 12 = แสดงค่า Min
    // 14 = +10 Min (เขียว)
    //
    // 19 = -10 Max (แดง)
    // 21 = แสดงค่า Max
    // 23 = +10 Max (เขียว)
    //
    // 25 = ปุ่ม Close

    public static Inventory createEditor(NightMarket plugin, String pool, int slot) {
        Inventory inv = Bukkit.createInventory(
                null,
                27,
                ChatColor.GOLD + "Edit Price: " + pool + "#" + slot
        );

        PoolsConfig pools = plugin.getPoolsConfig();
        PoolsConfig.PoolItem data = pools.getItem(pool, slot);

        if (data == null) {
            // ถ้าไม่มีข้อมูลเลย (กันกรณีผิดพลาด) จะใส่ dummy
            ItemStack air = new ItemStack(Material.BARRIER);
            ItemMeta m = air.getItemMeta();
            m.setDisplayName(ChatColor.RED + "No data");
            air.setItemMeta(m);
            inv.setItem(4, air);
            return inv;
        }

        double min = data.getMin();
        double max = data.getMax();

        // ไอเทมโชว์ตรงกลาง พร้อม lore แสดง min/max
        ItemStack display = data.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.GRAY + "Min: " + ChatColor.GOLD + String.format("%.0f", min));
            lore.add(ChatColor.GRAY + "Max: " + ChatColor.GOLD + String.format("%.0f", max));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(4, display);

        // ปุ่ม Min
        inv.setItem(10, button(Material.RED_CONCRETE, ChatColor.RED + "-10 Min"));
        inv.setItem(12, valueItem("Min", min));
        inv.setItem(14, button(Material.LIME_CONCRETE, ChatColor.GREEN + "+10 Min"));

        // ปุ่ม Max
        inv.setItem(19, button(Material.RED_CONCRETE, ChatColor.RED + "-10 Max"));
        inv.setItem(21, valueItem("Max", max));
        inv.setItem(23, button(Material.LIME_CONCRETE, ChatColor.GREEN + "+10 Max"));

        // ปุ่ม Close
        inv.setItem(25, button(Material.BARRIER, ChatColor.RED + "Close"));

        return inv;
    }

    private static ItemStack button(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack valueItem(String label, double value) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + label + ": " + ChatColor.GOLD + String.format("%.0f", value));
        paper.setItemMeta(meta);
        return paper;
    }
}
