package io.github.eariver.wayfarer.frontier.item;

import io.github.eariver.wayfarer.frontier.WayfarerFrontierPlugin;
import io.github.eariver.wayfarer.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

/**
 * Theme-bound traversal items (elytra, grappling hook, launchpad, navigation, waystone tool).
 * Identity is stored in PDC; theme enforcement happens in {@code FrontierListener}.
 */
public final class ThemeItems {

    public static final String TYPE = "wf_item_type";
    public static final String OWNER = "wf_item_owner";
    public static final String THEME = "wf_item_theme";
    public static final String EPOCH = "wf_item_epoch";
    public static final String USES = "wf_item_uses";

    public enum ItemType { ELYTRA, GRAPPLING_HOOK, LAUNCHPAD, NAVIGATION, WAYSTONE_TOOL }

    private final WayfarerFrontierPlugin plugin;
    private final Object hookService;
    private final Method createHookItem;

    public ThemeItems(WayfarerFrontierPlugin plugin) {
        this.plugin = plugin;
        Object service = null;
        Method create = null;
        try {
            Object leaf = Bukkit.getPluginManager().getPlugin("LeafGrapple");
            if (leaf != null) {
                Method m = leaf.getClass().getMethod("hookItemService");
                service = m.invoke(leaf);
                create = service.getClass().getMethod("createHookItem",
                        Class.forName("me.LeafPixel.leafgrapple.hook.HookTier"));
            }
        } catch (Exception e) {
            service = null;
            create = null;
        }
        this.hookService = service;
        this.createHookItem = create;
    }

    /** True when the LeafGrapple adapter can generate official hook items. */
    public boolean probeGrapple() {
        return hookService != null && createHookItem != null;
    }

    public boolean themeWorld(org.bukkit.World world) {
        String name = world.getName();
        return plugin.worldsBeyond().contains(name) || plugin.ruinedFrontier().contains(name);
    }

    /** Build the official LeafGrapple hook item (durability-disabled tier preferred). */
    public ItemStack createHook() {
        try {
            var tiers = (Iterable<?>) hookService.getClass().getMethod("getTiers").invoke(hookService);
            Object chosen = null;
            for (Object tier : tiers) {
                boolean durability = (boolean) tier.getClass().getMethod("durabilityEnabled").invoke(tier);
                if (!durability) {
                    chosen = tier;
                    break;
                }
                if (chosen == null) chosen = tier;
            }
            if (chosen == null) return null;
            return (ItemStack) createHookItem.invoke(hookService, chosen);
        } catch (Exception e) {
            plugin.getLogger().warning("LeafGrapple hook creation failed: " + e);
            return null;
        }
    }

    public ItemStack build(ItemType type, java.util.UUID owner, String theme, int epoch) {
        return switch (type) {
            case ELYTRA -> simple(Material.ELYTRA, "Wayfarer's Elytra",
                    List.of("Worlds Beyond traversal loadout", "Theme-bound; does not leave this theme"));
            case GRAPPLING_HOOK -> {
                ItemStack hook = createHook();
                yield hook != null ? hook : new ItemStack(Material.FISHING_ROD);
            }
            case LAUNCHPAD -> simple(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "Launchpad",
                    List.of("Place to launch yourself with your elytra", "3 successful launches per pad"));
            case NAVIGATION -> simple(Material.COMPASS, "Wayfarer Navigation",
                    List.of("Right-click to open the Frontier navigation menu"));
            case WAYSTONE_TOOL -> simple(Material.AMETHYST_SHARD, "Waystone Placement Tool",
                    List.of("Right-click solid ground in Worlds Beyond to place a Waystone"));
        };
    }

    private ItemStack simple(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<aqua>" + name));
        java.util.List<net.kyori.adventure.text.Component> loreC = new java.util.ArrayList<>();
        for (String l : lore) loreC.add(Text.mm("<gray>" + l));
        meta.lore(loreC);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public void stamp(ItemStack item, ItemType type, java.util.UUID owner, String theme, int epoch) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.key(TYPE), PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(plugin.key(OWNER), PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(plugin.key(THEME), PersistentDataType.STRING, theme);
        meta.getPersistentDataContainer().set(plugin.key(EPOCH), PersistentDataType.INTEGER, epoch);
        item.setItemMeta(meta);
    }

    public record Identity(ItemType type, String owner, String theme, int epoch) {
    }

    /** First entry into Worlds Beyond: grant the permanent loadout once per player. */
    public void grantLoadoutOnEntry(Player player) {
        String uuid = player.getUniqueId().toString();
        boolean already = plugin.db().tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT item_type FROM loadout WHERE player_uuid=?")) {
                ps.setString(1, uuid);
                return ps.executeQuery().next();
            }
        });
        if (already) return;
        long now = plugin.now();
        for (ItemType type : new ItemType[]{ItemType.ELYTRA, ItemType.GRAPPLING_HOOK}) {
            ItemStack item = build(type, player.getUniqueId(), "worlds_beyond", 0);
            if (item == null) continue;
            stamp(item, type, player.getUniqueId(), "worlds_beyond", 0);
            var left = player.getInventory().addItem(item);
            left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            record(uuid, type, now);
        }
        ItemStack pad = build(ItemType.LAUNCHPAD, player.getUniqueId(), "worlds_beyond", 0);
        stamp(pad, ItemType.LAUNCHPAD, player.getUniqueId(), "worlds_beyond", 0);
        pad.setAmount(2);
        var left = player.getInventory().addItem(pad);
        left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        record(uuid, ItemType.LAUNCHPAD, now);
        player.sendMessage(Text.mm("<gold>You received the Worlds Beyond traversal loadout: <white>Elytra, Grappling Hook, 2 Launchpads</white>."));
        if (!probeGrapple()) {
            player.sendMessage(Text.mm("<red>Grappling Hook unavailable (LeafGrapple adapter not ready)."));
        }
    }

    private void record(String uuid, ItemType type, long now) {
        plugin.db().tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "MERGE INTO loadout(player_uuid, item_type, epoch, granted_at) KEY(player_uuid, item_type) VALUES(?,?,?,?)")) {
                ps.setString(1, uuid);
                ps.setString(2, type.name());
                ps.setInt(3, 0);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Find a stamped item of the given type in the player's inventory. */
    public ItemStack findInInventory(Player player, ItemType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            Identity id = identify(item);
            if (id != null && id.type() == type && id.owner().equals(player.getUniqueId().toString())) {
                return item;
            }
        }
        return null;
    }

    /** Convenience: any navigation item in the inventory. */
    public ItemStack findInInventory(Player player) {
        return findInInventory(player, ItemType.NAVIGATION);
    }

    public Identity identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(plugin.key(TYPE), PersistentDataType.STRING);
        String owner = pdc.get(plugin.key(OWNER), PersistentDataType.STRING);
        String theme = pdc.get(plugin.key(THEME), PersistentDataType.STRING);
        Integer epoch = pdc.get(plugin.key(EPOCH), PersistentDataType.INTEGER);
        if (type == null || owner == null || theme == null || epoch == null) return null;
        try {
            return new Identity(ItemType.valueOf(type), owner, theme, epoch);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
