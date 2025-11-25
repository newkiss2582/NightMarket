package luludev.com.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import luludev.com.managers.NightMarketManager;

public class NightMarketListener implements Listener {

    private final NightMarketManager manager;

    public NightMarketListener(NightMarketManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        if (rawTitle == null) return;

        String stripped = ChatColor.stripColor(rawTitle);

        // Edit Pool GUI
        if (stripped.startsWith("Edit Pool: ")) {
            // ให้ drag/drop ได้ปกติ
            return;
        }

        // Night Market GUI
        if (stripped.startsWith("Night Market: ")) {
            if (event.getRawSlot() < view.getTopInventory().getSize()) {
                event.setCancelled(true);

                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    Inventory top = view.getTopInventory();
                    int slot = event.getRawSlot();

                    String pool = stripped.substring("Night Market: ".length()).trim();
                    manager.handleMarketClick(player, top, slot, pool);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        if (rawTitle == null) return;

        String stripped = ChatColor.stripColor(rawTitle);

        if (stripped.startsWith("Edit Pool: ")) {
            String pool = stripped.substring("Edit Pool: ".length()).trim();
            Inventory top = view.getTopInventory();
            manager.savePoolItems(pool, top.getContents());

            if (event.getPlayer() instanceof Player) {
                ((Player) event.getPlayer()).sendMessage(
                        ChatColor.GREEN + "บันทึก pool \"" + pool + "\" แล้ว"
                );
            }
        }
    }
}
