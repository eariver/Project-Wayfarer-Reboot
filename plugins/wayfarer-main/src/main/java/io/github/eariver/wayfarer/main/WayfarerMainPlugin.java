package io.github.eariver.wayfarer.main;

import io.github.eariver.wayfarer.common.Text;
import io.github.eariver.wayfarer.main.cmd.GrowthCommand;
import io.github.eariver.wayfarer.main.domain.EvolutionPlan;
import io.github.eariver.wayfarer.main.domain.GrowthTool;
import io.github.eariver.wayfarer.main.domain.ProgressBuffer;
import io.github.eariver.wayfarer.main.domain.RepairPricing;
import io.github.eariver.wayfarer.main.gui.GrowthGui;
import io.github.eariver.wayfarer.main.gui.ShopGui;
import io.github.eariver.wayfarer.main.gui.ShopCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wayfarer_Main — Growth Pickaxe gameplay for the Main server (concept v0.0.5 aligned).
 *
 * <p>Progress is uniform 1.00 per qualifying break (block weights withdrawn, DEC-REQ-007
 * defaults). The logical tool lives in H2; the physical item carries PDC identity and is
 * the authority for durability. Mining-time writes are buffered in memory and flushed by
 * a checkpoint task (no per-break main-thread DB I/O).
 */
public final class WayfarerMainPlugin extends JavaPlugin implements Listener {

    private static volatile boolean debug = false;

    private EvolutionPlan plan;
    private RepairPricing pricing;
    private GrowthRepository repository;
    private Economy economy;
    private Set<String> progressWorlds;
    private GrowthGui growthGui;
    private ShopGui shopGui;

    /** In-memory progress overlay; flushed by the checkpoint task (R-03). */
    private final ProgressBuffer progressBuffer = new ProgressBuffer();
    private final Map<UUID, GrowthTool> toolCache = new HashMap<>();
    private final Set<UUID> reissueLocks = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var cfg = getConfig();
        plan = EvolutionPlan.defaults();
        pricing = RepairPricing.defaults();
        repository = new GrowthRepository(new File(getDataFolder(), "growth.db"));
        progressWorlds = Set.copyOf(cfg.getStringList("worlds.progress-allowlist"));

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            getLogger().info("Using economy provider: " + economy.getName());
        }

        growthGui = new GrowthGui(this);
        shopGui = new ShopGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginCommand("wayfarer-main").setExecutor(new GrowthCommand(this));

        // P-1: never touch another plugin's command map. Our fallback /shop is only
        // registered when no other shop plugin (e.g. EconomyShopGUI, which registers
        // /shop dynamically at runtime) is installed and enabled.
        var shopPlugin = getServer().getPluginManager().getPlugin("EconomyShopGUI");
        boolean shopTaken = shopPlugin != null && shopPlugin.isEnabled();
        if (shopTaken) {
            getLogger().info("Another plugin provides /shop; fallback available via /wayfarer-shop");
        } else {
            getServer().getPluginCommand("wayfarer-shop").setExecutor(new ShopCommand(this));
            try {
                Object map = getServer().getClass().getMethod("getCommandMap").invoke(getServer());
                Object cmdObj = getServer().getPluginCommand("wayfarer-shop");
                map.getClass().getMethod("register", String.class, org.bukkit.command.Command.class)
                        .invoke(map, "wayfarer", (org.bukkit.command.Command) cmdObj);
                var known = (java.util.Map<String, org.bukkit.command.Command>) map.getClass()
                        .getMethod("getKnownCommands").invoke(map);
                known.put("shop", (org.bukkit.command.Command) cmdObj);
                getLogger().info("Registered fallback /shop (no other shop provider present)");
            } catch (Exception e) {
                getLogger().warning("Fallback /shop not registered: " + e);
            }
        }

        // checkpoint: buffered progress writes every 5 seconds
        Bukkit.getScheduler().runTaskTimer(this, this::flushDirty, 100L, 100L);
        getLogger().info("Wayfarer_Main ready (growth pickaxe, worlds=" + progressWorlds + ")");
    }

    @Override
    public void onDisable() {
        flushAll();
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

    public boolean economyAvailable() {
        return economy != null && economy.isEnabled();
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

    public static void setDebug(boolean value) {
        debug = value;
    }

    public int tierFor(long progress) {
        return plan.tierFor(progress);
    }

    /** Live progress for a player: buffered overlay first, then DB cache. */
    public long progressOf(UUID owner) {
        GrowthTool cached = toolCache.get(owner);
        return progressBuffer.latest(owner, cached != null ? cached.progress() : 0);
    }

    public GrowthTool toolOf(UUID owner) {
        GrowthTool cached = toolCache.get(owner);
        if (cached != null) return cached;
        GrowthTool active = repository.findActive(owner).orElse(null);
        if (active != null) toolCache.put(owner, active);
        return active;
    }

    /**
     * R-03/R-09: the authoritative live view of a player's tool. Progress comes from the
     * buffered overlay when a checkpoint is still pending, otherwise from the cached/DB
     * row. GUI, status, item lore and evolution all read this, so a pending checkpoint
     * can never make the displayed progress roll backwards.
     */
    public GrowthTool viewOf(UUID owner) {
        GrowthTool base = toolOf(owner);
        if (base == null) return null;
        Long pending = progressBuffer.pendingOf(owner);
        if (pending == null || pending == base.progress()) return base;
        return base.withProgress(pending);
    }

    /** Latest progress value including any not-yet-checkpointed breaks. */
    public long latestProgress(UUID owner) {
        GrowthTool cached = toolCache.get(owner);
        return progressBuffer.latest(owner, cached != null ? cached.progress() : 0);
    }

    /** R-03: accumulate one break on top of the newest value (buffered first). */
    public long accumulateBreak(UUID owner) {
        GrowthTool cached = toolCache.get(owner);
        return progressBuffer.addBreak(owner, cached != null ? cached.progress() : 0);
    }

    /** Called after a checkpoint was persisted, so the overlay can be dropped. */
    public void markChecked(UUID owner) {
        progressBuffer.clearOne(owner);
    }

    /**
     * Register break progress. Runs on the main thread; the DB write is deferred to the
     * checkpoint task so block breaking does no synchronous I/O.
     */
    public void registerBreak(Player player, ItemStack held) {
        GrowthTool.Identity id = GrowthItems.identify(this, held);
        if (id == null || !id.ownerUuid().equals(player.getUniqueId().toString())) return;
        GrowthTool tool = toolOf(player.getUniqueId());
        if (tool == null || tool.status() == GrowthTool.Status.REVOKED) return;
        if (!tool.toolId().toString().equals(id.toolId()) || tool.epoch() != id.epoch()) return;
        if (tool.status() == GrowthTool.Status.BROKEN) return;
        if (!progressWorlds.contains(player.getWorld().getName())) return;

        // R-03: add on top of the NEWEST value (buffered pending progress included), not the
        // last checkpointed row, otherwise breaks between checkpoints overwrite each other.
        long previous = latestProgress(player.getUniqueId());
        long next = accumulateBreak(player.getUniqueId());
        if (next != previous + 1) {
            throw new IllegalStateException("progress accumulation expected " + (previous + 1) + " but got " + next);
        }
        int before = tierFor(previous);
        int after = tierFor(next);
        if (after > before) {
            // evolution: persist immediately (material/enchants change) and rebuild the item
            GrowthTool evolved = tool.withProgress(next);
            repository.update(evolved);
            toolCache.put(player.getUniqueId(), evolved);
            markChecked(player.getUniqueId());
            ItemStack item = Items.rebuildPreservingDamage(this, evolved, held);
            player.getInventory().setItemInMainHand(item);
            player.sendMessage(Text.mm("<gold>Your Pickaxe of Growth evolved to <white><tier></white>!",
                    "tier", GrowthItems.tierName(after, evolved.netheriteUpgraded())));
            if (debug) {
                getLogger().info("[growth-debug] " + player.getName() + " evolved to " + after);
            }
        } else {
            GrowthItems.applyDisplay(this, held, tool.withProgress(next), after, held.getDurability());
        }
        if (debug) {
            getLogger().info("[growth-debug] " + player.getName() + " break world=" + player.getWorld().getName()
                    + " progress=" + next + " allowed=" + progressWorlds.contains(player.getWorld().getName()));
        }
    }

    private void flushDirty() {
        for (UUID owner : progressBuffer.dirtyOwners()) {
            Long progress = progressBuffer.pendingOf(owner);
            if (progress == null) {
                markChecked(owner);
                continue;
            }
            GrowthTool tool = toolCache.get(owner);
            if (tool == null || tool.progress() == progress) {
                markChecked(owner);
                continue;
            }
            GrowthTool updated = tool.withProgress(progress);
            try {
                repository.update(updated);
                toolCache.put(owner, updated);
            } catch (Exception e) {
                // R-03: a failed checkpoint keeps the pending value; the next tick retries
                getLogger().log(Level.WARNING, "progress checkpoint failed for " + owner, e);
                continue;
            }
            markChecked(owner);
        }
    }

    private void flushAll() {
        for (Map.Entry<UUID, Long> e : progressBuffer.pendingOfAll().entrySet()) {
            GrowthTool tool = toolCache.get(e.getKey());
            if (tool != null && tool.progress() != e.getValue()) {
                tool = tool.withProgress(e.getValue());
                repository.update(tool);
                toolCache.put(e.getKey(), tool);
            }
        }
        progressBuffer.clearAll();
    }

    public void cacheTool(Player player, GrowthTool tool) {
        toolCache.put(player.getUniqueId(), tool);
        progressBuffer.setBaseline(player.getUniqueId(), tool.progress());
    }

    public boolean tryReissueLock(Player player) {
        return reissueLocks.add(player.getUniqueId());
    }

    public void releaseReissueLock(Player player) {
        reissueLocks.remove(player.getUniqueId());
    }

    /** Grant a fresh stone tool for a player with no logical tool (first join only). */
    public void ensureFirstTool(Player player) {
        if (repository.findActive(player.getUniqueId()).isPresent()) return;
        UUID toolId = UUID.randomUUID();
        GrowthTool tool = new GrowthTool(toolId, player.getUniqueId(), player.getName(), 0, 0L,
                EvolutionPlan.Branch.FORTUNE, GrowthTool.Status.ACTIVE, false);
        repository.insert(tool);
    }

    /** Sync variant of the first-tool grant, for admin commands. */
    public void ensureFirstToolTarget(Player player) {
        if (repository.findActive(player.getUniqueId()).isPresent()) return;
        UUID toolId = UUID.randomUUID();
        GrowthTool tool = new GrowthTool(toolId, player.getUniqueId(), player.getName(), 0, 0L,
                EvolutionPlan.Branch.FORTUNE, GrowthTool.Status.ACTIVE, false);
        repository.insert(tool);
    }

    /**
     * Async join flow (concept §5.1): only DB work happens off the main thread; every
     * Bukkit inventory call is scheduled back and guarded by an isOnline() recheck.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                ensureFirstTool(player);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "first-tool lookup failed for " + player.getName(), e);
                return;
            }
            GrowthTool tool = repository.findActive(player.getUniqueId()).orElse(null);
            if (tool == null) return;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!player.isOnline()) return;
                cacheTool(player, tool);
                if (!hasOwnedItem(player, tool)) {
                    giveCurrentItem(player, tool);
                    player.sendMessage(Text.mm(
                            "<gold>You received a <white>Pickaxe of Growth</white>. Mine to make it evolve!"));
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GrowthTool tool = toolCache.get(player.getUniqueId());
        Long pending = progressBuffer.pendingOf(player.getUniqueId());
        if (tool != null && pending != null && tool.progress() != pending) {
            GrowthTool updated = tool.withProgress(pending);
            try {
                repository.update(updated);
                toolCache.put(player.getUniqueId(), updated);
            } catch (Exception e) {
                // keep the overlay: a reconnect applies the still-pending progress again
                getLogger().log(Level.WARNING, "final progress flush failed for " + player.getUniqueId(), e);
            }
        }
        toolCache.remove(player.getUniqueId());
        progressBuffer.clearOne(player.getUniqueId());
        reissueLocks.remove(player.getUniqueId());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!progressWorlds.contains(player.getWorld().getName())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        GrowthTool.Identity id = GrowthItems.identify(this, held);
        if (id == null) return;
        GrowthTool tool = toolOf(player.getUniqueId());
        if (tool == null || tool.status() != GrowthTool.Status.BROKEN) {
            // P-16: a BROKEN tool is a gray-dye shell and must not break blocks at all.
            if (tool != null && tool.status() == GrowthTool.Status.BROKEN) {
                event.setCancelled(true);
            }
        }
        if (event.isCancelled()) return;
        if (tool == null || !tool.toolId().toString().equals(id.toolId()) || tool.epoch() != id.epoch()) return;
        if (!GrowthItems.isPickaxe(held)) return;
        registerBreak(player, held);
    }

    /** P-16: a broken tool cannot mine, attack or interact with blocks. */
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        GrowthTool.Identity id = GrowthItems.identify(this, item);
        if (id == null || !id.ownerUuid().equals(event.getPlayer().getUniqueId().toString())) return;
        GrowthTool tool = toolOf(event.getPlayer().getUniqueId());
        if (tool == null || tool.status() != GrowthTool.Status.BROKEN) return;
        // still allow the GUI so the repair route stays reachable
        if (event.getAction().name().startsWith("RIGHT_CLICK")) {
            event.setCancelled(true);
            growthGui.open(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        GrowthTool.Identity id = GrowthItems.identify(this, held);
        if (id == null) return;
        GrowthTool tool = toolOf(player.getUniqueId());
        if (tool != null && tool.status() == GrowthTool.Status.BROKEN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        GrowthTool.Identity id = GrowthItems.identify(this, item);
        if (id == null) return;
        Player player = event.getPlayer();
        if (!id.ownerUuid().equals(player.getUniqueId().toString())) return;
        GrowthTool tool = toolOf(player.getUniqueId());
        if (tool == null) return;
        if (!tool.toolId().toString().equals(id.toolId()) || tool.epoch() != id.epoch()) return;
        if (tool.status() == GrowthTool.Status.REVOKED) return;
        short max = item.getType().getMaxDurability();
        int projected = item.getDurability() + event.getDamage();
        if (projected >= max) {
            // clamp before destruction and record BROKEN; the physical item becomes the
            // gray-dye shell so it cannot keep mining (concept v0.0.5 §19)
            event.setCancelled(true);
            GrowthTool broken = tool.withStatus(GrowthTool.Status.BROKEN);
            repository.update(broken);
            toolCache.put(player.getUniqueId(), broken);
            progressBuffer.setBaseline(player.getUniqueId(), broken.progress());
            ItemStack shell = GrowthItems.buildBroken(this, broken);
            player.getInventory().setItemInMainHand(shell);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendMessage(Text.mm(
                    "<red>Your Pickaxe of Growth is broken. <gold>Repair it with /wayfarer-main repair"));
        } else {
            GrowthItems.applyDisplay(this, item, tool, tierFor(tool.progress()), projected);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        GrowthTool.Identity id = GrowthItems.identify(this, event.getItemDrop().getItemStack());
        if (id == null || !id.ownerUuid().equals(event.getPlayer().getUniqueId().toString())) return;
        // owner bind: no manual drop (concept §4)
        event.setCancelled(true);
    }

    /** The tool is kept on death (concept §19 死亡時保持); everything else behaves vanilla. */
    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            GrowthTool.Identity id = GrowthItems.identify(this, it.next());
            if (id != null && id.ownerUuid().equals(player.getUniqueId().toString())) {
                it.remove();
            }
        }
    }

    /** Death drops aside, give the item back in the main hand slot when missing. */
    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            GrowthTool tool = toolOf(player.getUniqueId());
            if (tool == null) return;
            if (!hasOwnedItem(player, tool)) giveCurrentItem(player, tool);
        });
    }

    private boolean hasOwnedItem(Player player, GrowthTool tool) {
        for (ItemStack item : player.getInventory().getContents()) {
            GrowthTool.Identity id = GrowthItems.identify(this, item);
            if (id != null && id.toolId().equals(tool.toolId().toString()) && id.epoch() == tool.epoch()) {
                return true;
            }
        }
        return false;
    }

    /** Give/refresh the current tool item. */
    public void giveCurrentItem(Player player, GrowthTool tool) {
        ItemStack item = GrowthItems.build(this, tool);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }
}
