package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.main.cmd.GrowthCommand;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import io.github.eariver.wayfarer.main.gui.GrowthGui;
import io.github.eariver.wayfarer.main.gui.ShopGui;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wayfarer_Main — Growth Pickaxe gameplay for the Main server (PoC alpha).
 *
 * <p>Progress is uniform 1.00 per qualifying block break (block weights withdrawn,
 * DEC-REQ-007). The physical item is authoritative for durability; the database stores
 * the logical tool (progress, tier, branch, status). Item loss, death and container
 * moves are vanilla-neutral; recovery uses a strictly-priced paid reissue.
 */
public final class WayfarerMainPlugin extends JavaPlugin implements Listener {

    private EvolutionPlan plan;
    private RepairPricing pricing;
    private GrowthRepository repository;
    private Economy economy;
    private Set<String> progressWorlds;
    private GrowthGui growthGui;
    private ShopGui shopGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        plan = EvolutionPlan.defaults();
        pricing = RepairPricing.defaults();
        repository = new GrowthRepository(new File(getDataFolder(), "growth.db"));
        progressWorlds = Set.copyOf(cfg.getStringList("worlds.progress-allowlist"));

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();

        growthGui = new GrowthGui(this);
        shopGui = new ShopGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginCommand("wayfarer-main").setExecutor(new GrowthCommand(this));
        getLogger().info("Wayfarer_Main ready (growth pickaxe, worlds=" + progressWorlds + ")");
    }

    @Override
    public void onDisable() {
        if (repository != null) repository.close();
    }

    public EvolutionPlan plan() {
        return plan;
    }

    public RepairPricing pricing() {
        return pricing;
    }

    public GrowthRepository repository() {
        return repository;
    }

    public Economy economy() {
        return economy;
    }

    public Set<String> progressWorlds() {
        return progressWorlds;
    }

    public GrowthGui growthGui() {
        return growthGui;
    }

    public ShopGui shopGui() {
        return shopGui;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    /** Tier for a cumulative progress count. */
    public int tierFor(long progress) {
        return plan.tierFor(progress);
    }

    /**
     * Grant a fresh stone pickaxe to a player with no active tool (first login only).
     * Existing tools are never auto-redelivered — recovery is the paid reissue path.
     */
    public void ensureFirstTool(Player player) {
        if (repository.findActive(player.getUniqueId()).isPresent()) return;
        UUID toolId = UUID.randomUUID();
        GrowthTool tool = new GrowthTool(toolId, player.getUniqueId(), player.getName(), 0, 0L,
                EvolutionPlan.Branch.FORTUNE, GrowthTool.Status.ACTIVE);
        repository.insert(tool);
        giveCurrentItem(player, tool);
        player.sendMessage(io.github.eariver.wayfarer.common.Text.mm(
                "<gold>You received a <white>Pickaxe of Growth</white>. Mine to make it evolve!"));
    }

    /** Give/refresh the player's current tool item (full inventory preferred, then drop at feet). */
    public void giveCurrentItem(Player player, GrowthTool tool) {
        int tier = tierFor(tool.progress());
        ItemStack item = GrowthItems.build(this, tool, tier);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    /** Register break progress for the player if the held item is their active pickaxe. */
    public void registerBreak(Player player, ItemStack held) {
        GrowthTool.Identity id = GrowthItems.identify(this, held);
        if (id == null || !id.ownerUuid().equals(player.getUniqueId().toString())) return;
        GrowthTool tool = repository.findActive(player.getUniqueId()).orElse(null);
        if (tool == null || tool.status() == GrowthTool.Status.REVOKED) return;
        if (!tool.toolId().toString().equals(id.toolId()) || tool.epoch() != id.epoch()) return;
        if (tool.status() == GrowthTool.Status.BROKEN) return;
        if (!progressWorlds.contains(player.getWorld().getName())) return;

        int before = tierFor(tool.progress());
        GrowthTool updated = tool.withProgress(tool.progress() + 1);
        repository.update(updated);
        int after = tierFor(updated.progress());
        if (after > before) {
            updated = updated.withStatus(GrowthTool.Status.ACTIVE);
            repository.update(updated);
            // physical evolution: rebuild item in hand, restore durability
            ItemStack evolved = GrowthItems.build(this, updated, after);
            player.getInventory().setItemInMainHand(evolved);
            player.sendMessage(io.github.eariver.wayfarer.common.Text.mm(
                    "<gold>Your Pickaxe of Growth evolved to <white><tier></white>!",
                    "tier", GrowthItems.tierName(after)));
        } else {
            GrowthItems.applyDisplay(this, held, updated, after);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Initial database load happens on the main thread with zero I/O cost per tick;
        // the H2 file is local and fast, matching production's MariaDB path in shape only.
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                ensureFirstTool(event.getPlayer());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "first-tool grant failed for " + event.getPlayer().getName(), e);
            }
        });
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!progressWorlds.contains(player.getWorld().getName())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (GrowthItems.identify(this, held) == null) return;
        if (!GrowthItems.isPickaxe(held)) return;
        // keep it on the main thread for inventory mutation but persistence is fast/local
        registerBreak(player, held);
    }

    @EventHandler
    public void onDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        GrowthTool.Identity id = GrowthItems.identify(this, item);
        if (id == null) return;
        Player player = event.getPlayer();
        if (!id.ownerUuid().equals(player.getUniqueId().toString())) return;
        GrowthTool tool = repository.findActive(player.getUniqueId()).orElse(null);
        if (tool == null) return;
        if (tool.toolId().toString().equals(id.toolId()) && tool.epoch() == id.epoch()
                && tool.status() != GrowthTool.Status.REVOKED) {
            // The physical item stays authoritative, but we prevent the silent destruction
            // of a tool whose progress lives in the database: clamp at one point of damage
            // left and record the BROKEN state so repair/reissue can be priced.
            ItemMetaProbe probe = new ItemMetaProbe(item);
            if (probe.damage() + event.getDamage() >= probe.maxDurability()) {
                event.setCancelled(true);
                event.getItem().setDurability((short) (probe.maxDurability() - 1));
                GrowthTool broken = tool.withStatus(GrowthTool.Status.BROKEN);
                repository.update(broken);
                player.sendMessage(io.github.eariver.wayfarer.common.Text.mm(
                        "<red>Your Pickaxe of Growth is broken. <gold>Repair it with /wayfarer-main repair"));
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        GrowthTool.Identity id = GrowthItems.identify(this, event.getItem());
        if (id == null) return;
        if (!id.ownerUuid().equals(event.getPlayer().getUniqueId().toString())) return;
        if (event.getAction().name().startsWith("RIGHT_CLICK")) {
            event.setCancelled(true);
            growthGui.open(event.getPlayer());
        }
    }

    /** Durability probe helper (kept tiny; see onDamage). */
    private record ItemMetaProbe(short damage, short maxDurability) {
        ItemMetaProbe(ItemStack item) {
            this(item.getDurability(), item.getType().getMaxDurability());
        }
    }
}
