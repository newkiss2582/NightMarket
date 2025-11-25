package luludev.com;

import luludev.com.managers.NightMarketManager;
import luludev.com.managers.PurchaseDatabase;
import luludev.com.listeners.NightMarketListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class NightMarket extends JavaPlugin {

    private static NightMarket instance;
    private static Economy economy;

    private NightMarketManager nightMarketManager;
    private PurchaseDatabase purchaseDatabase;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault/Economy not found! Disabling NightMarket...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // DB
        purchaseDatabase = new PurchaseDatabase(this);
        purchaseDatabase.init();

        // Manager
        nightMarketManager = new NightMarketManager(this, purchaseDatabase);

        // Commands
        NightMarketCommand command = new NightMarketCommand(this, nightMarketManager);
        getCommand("nightmarket").setExecutor(command);
        getCommand("nightmarket").setTabCompleter(command);

        // Listeners
        getServer().getPluginManager().registerEvents(
                new NightMarketListener(nightMarketManager),
                this
        );

        // Daily market
        nightMarketManager.initDailyMarket();

        getLogger().info("NightMarket enabled.");
    }

    @Override
    public void onDisable() {
        if (purchaseDatabase != null) {
            purchaseDatabase.close();
        }
        getLogger().info("NightMarket disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static NightMarket getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public NightMarketManager getNightMarketManager() {
        return nightMarketManager;
    }

    public PurchaseDatabase getPurchaseDatabase() {
        return purchaseDatabase;
    }
}
