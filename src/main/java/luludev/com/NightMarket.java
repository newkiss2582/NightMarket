package luludev.com;

import luludev.com.listeners.NightMarketListener;
import luludev.com.listeners.PriceEditorListener;
import luludev.com.managers.NightMarketManager;
import luludev.com.managers.PoolsConfig;
import luludev.com.managers.PurchaseDatabase;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class NightMarket extends JavaPlugin {

    private static NightMarket instance;
    private static Economy economy;

    private NightMarketManager nightMarketManager;
    private PurchaseDatabase purchaseDatabase;
    private PoolsConfig poolsConfig;

    @Override
    public void onEnable() {
        instance = this;

        // โหลด config.yml ถ้ายังไม่มีจะ copy จาก resources
        saveDefaultConfig();

        // ตั้งค่า Vault Economy
        if (!setupEconomy()) {
            getLogger().severe("ไม่พบ Vault หรือ Economy provider ปลั๊กอินจะถูกปิด!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // ฐานข้อมูล (SQLite) สำหรับเก็บ log การซื้อ
        purchaseDatabase = new PurchaseDatabase(this);
        purchaseDatabase.init();

        // ไฟล์ pools.yml สำหรับเก็บข้อมูล pool + ราคา min/max
        poolsConfig = new PoolsConfig(this);

        // ตัวจัดการระบบ Night Market
        nightMarketManager = new NightMarketManager(this, purchaseDatabase, poolsConfig);

        // คำสั่ง /nightmarket
        NightMarketCommand command = new NightMarketCommand(this, nightMarketManager);
        if (getCommand("nightmarket") != null) {
            getCommand("nightmarket").setExecutor(command);
            getCommand("nightmarket").setTabCompleter(command);
        } else {
            getLogger().warning("ไม่พบคำสั่ง 'nightmarket' ใน plugin.yml");
        }

        // ลงทะเบียน Listener ต่าง ๆ
        getServer().getPluginManager().registerEvents(
                new NightMarketListener(this, nightMarketManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PriceEditorListener(this),
                this
        );

        // สร้าง / โหลดตลาดประจำวัน + ตั้ง schedule รีเฟรชทุกวัน
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

    // ================== Vault / Economy ==================

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

    public static Economy getEconomy() {
        return economy;
    }

    public static NightMarket getInstance() {
        return instance;
    }

    // ================== Getters อื่น ๆ ==================

    public NightMarketManager getNightMarketManager() {
        return nightMarketManager;
    }

    public PurchaseDatabase getPurchaseDatabase() {
        return purchaseDatabase;
    }

    public PoolsConfig getPoolsConfig() {
        return poolsConfig;
    }
}
