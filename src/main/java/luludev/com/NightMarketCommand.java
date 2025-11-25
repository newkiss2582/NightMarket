package luludev.com;

import luludev.com.managers.NightMarketManager;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NightMarketCommand implements CommandExecutor, TabCompleter {

    private final NightMarket plugin;
    private final NightMarketManager manager;

    public NightMarketCommand(NightMarket plugin, NightMarketManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String msg(String key) {
        return manager.getMessage(key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "edit": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg("only-player"));
                    return true;
                }
                if (!sender.hasPermission("nightmarket.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "ใช้: /" + label + " edit <pool>");
                    return true;
                }
                String pool = args[1];
                manager.openEditPoolGUI((Player) sender, pool);
                return true;
            }

            case "open": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg("only-player"));
                    return true;
                }
                if (!sender.hasPermission("nightmarket.use")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "ใช้: /" + label + " open <pool>");
                    return true;
                }
                String pool = args[1];
                manager.openMarketGUI((Player) sender, pool);
                return true;
            }

            case "reload": {
                if (!sender.hasPermission("nightmarket.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                manager.initDailyMarket();
                sender.sendMessage(msg("reload-success"));
                return true;
            }
        }

        sendHelp(sender, label);
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "=== NightMarket Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " open <pool> " + ChatColor.WHITE + "- เปิด Night Market");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " edit <pool> " + ChatColor.WHITE + "- แก้ไข pool (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.WHITE + "- รีโหลด config + market (admin)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("nightmarket.admin")) {
                if ("edit".startsWith(args[0].toLowerCase())) list.add("edit");
                if ("reload".startsWith(args[0].toLowerCase())) list.add("reload");
            }
            if (sender.hasPermission("nightmarket.use")) {
                if ("open".startsWith(args[0].toLowerCase())) list.add("open");
            }
        }
        return list;
    }
}
