package com.bergerkiller.bukkit.mw;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerRespawnPoint;

public class WorldInventory {
    private static final Set<WorldInventory> inventories = new HashSet<WorldInventory>();
    private static boolean inventoriesLoaded = false;
    private static boolean enforcingCanonical = false;
    private static List<CanonicalGroup> canonicalGroups = null;
    private static int counter = 0;
    private final Set<String> worlds = new HashSet<String>();
    private String worldname;
    private String name;

    public static Collection<WorldInventory> getAll() {
        return inventories;
    }

    public static WorldInventory create(String worldName) {
        return new WorldInventory(worldName).add(worldName);
    }

    public static void load() {
        // Check whether there are any configured entries that would result in saving
        boolean hadExistingInventoriesThatRequiredSaving = false;
        for (WorldInventory inv : inventories) {
            if (inv.isRequiredSaving()) {
                hadExistingInventoriesThatRequiredSaving = true;
                break;
            }
        }

        // Load the new configuration. Replace found settings with already-generated ones.
        // Extract bundled default inventories.yml on first run
        if (!new java.io.File(MyWorlds.plugin.getDataFolder(), "inventories.yml").exists()) {
            MyWorlds.plugin.saveResource("inventories.yml", false);
        }
        FileConfiguration config = new FileConfiguration(MyWorlds.plugin, "inventories.yml");
        config.load();
        for (ConfigurationNode node : config.getNodes()) {
            String sharedWorld = node.get("folder", String.class, null);
            if (sharedWorld == null) {
                continue;
            }
            List<String> worlds = node.getList("worlds", String.class);
            if (worlds.isEmpty()) {
                continue;
            }

            WorldInventory inv = new WorldInventory(WorldConfig.get(sharedWorld).worldname);
            inv.name = node.getName();
            for (String world : worlds) {
                // This assigns inv to WorldConfig. If a previous WorldConfig was set for a world,
                // that one is de-registered.
                inv.addWithoutSaving(world);
            }
        }

        // Only mark as loaded after parsing: WorldConfig.get() above creates missing
        // configurations, whose constructor path calls save(). With the flag raised
        // mid-parse those saves would rewrite inventories.yml from a half-read state,
        // silently truncating every group not yet parsed.
        inventoriesLoaded = true;

        // The groups bundled in the plugin jar are canonical and must survive even
        // when the server's inventories.yml lost them; save() also re-asserts them
        // before every write.
        boolean changed = applyCanonicalGroups();

        // Re-save after loading in case merging of previous default inventories caused changes
        if (hadExistingInventoriesThatRequiredSaving || changed) {
            save();
        }
    }

    public static void save() {
        // Avoid overwriting inventories.yml with incomplete data before it is all loaded in
        if (!inventoriesLoaded) {
            return;
        }

        // A save triggered from within the canonical reconcile below; the outer
        // call writes the final state once
        if (enforcingCanonical) {
            return;
        }

        // Re-assert the canonical groups before every write: save() is the only
        // path that rewrites inventories.yml, so enforcing here guarantees no
        // rewrite, by whatever plugin or command, can persist a broken main bundle
        applyCanonicalGroups();

        FileConfiguration config = new FileConfiguration(MyWorlds.plugin, "inventories.yml");
        Set<String> savedNames = new HashSet<String>();
        for (WorldInventory inventory : inventories) {
            // Count only worlds whose registered configuration still references this
            // group. Stale groups linger in the static set after /world config load
            // re-creates all WorldConfigs and would otherwise be written as duplicates.
            List<String> effectiveWorlds = new ArrayList<String>();
            for (String world : inventory.worlds) {
                WorldConfig wc = WorldConfig.getIfExists(world);
                if (wc != null && wc.inventory == inventory) {
                    effectiveWorlds.add(world);
                }
            }
            if (effectiveWorlds.size() > 1) {
                String name = inventory.name;
                for (int i = 0; i < Integer.MAX_VALUE && !savedNames.add(name.toLowerCase()); i++) {
                    name = inventory.name + i;
                }
                ConfigurationNode node = config.getNode(name);
                node.set("folder", inventory.worldname);
                node.set("worlds", effectiveWorlds);
            }
        }
        config.save();
    }

    /**
     * Applies the inventory groups bundled inside the plugin jar (the shipped
     * inventories.yml resource). These groups are canonical: their listed worlds are
     * always merged into one group, worlds that joined that group but are not listed
     * are moved back into their own group, and the shared storage world is pinned to
     * the configured folder. Groups in the server's inventories.yml that do not
     * involve canonical worlds are never touched. Worlds that are not registered or
     * whose folder no longer exists are ignored, so this is safe on servers with a
     * different level-name. All mutations here avoid save(); callers persist.
     *
     * @return True if any group was changed
     */
    private static boolean applyCanonicalGroups() {
        if (enforcingCanonical) {
            return false;
        }
        enforcingCanonical = true;
        try {
            boolean changed = false;
            for (CanonicalGroup canonical : getCanonicalGroups()) {
                if (!WorldConfigStore.exists(canonical.folder) || !WorldManager.worldExists(canonical.folder)) {
                    continue;
                }

                // Members must be known to the server and exist on disk. Loadedness is
                // deliberately not required: MyWorlds tracks groups for unloaded worlds
                // too, and requiring it would eject a temporarily unloaded dimension.
                List<String> members = new ArrayList<String>();
                Set<String> membersLower = new HashSet<String>();
                for (String worldName : canonical.worlds) {
                    if (WorldConfigStore.exists(worldName) && WorldManager.worldExists(worldName)) {
                        members.add(worldName);
                        membersLower.add(worldName.toLowerCase());
                    }
                }
                if (members.size() < 2 || !membersLower.contains(canonical.folder.toLowerCase())) {
                    continue;
                }

                WorldConfig folderConfig = WorldConfig.get(canonical.folder);
                WorldInventory group = folderConfig.inventory;
                boolean groupChanged = false;

                // Merge in missing members
                for (String member : members) {
                    if (!group.contains(member)) {
                        group.addWithoutSaving(member);
                        groupChanged = true;
                    }
                }

                // Move foreign members back into their own group
                for (String extra : new ArrayList<String>(group.worlds)) {
                    if (!membersLower.contains(extra)) {
                        group.removeWithoutSaving(extra, true);
                        groupChanged = true;
                    }
                }

                // Pin the shared storage world to the canonical folder
                if (!folderConfig.worldname.equalsIgnoreCase(group.worldname)) {
                    group.worldname = folderConfig.worldname;
                    groupChanged = true;
                }

                // Give auto-named groups the canonical name for a recognizable file
                if (groupChanged && group.name.matches("inv\\d+")) {
                    group.name = canonical.name;
                }

                if (groupChanged) {
                    changed = true;
                    MyWorlds.plugin.log(Level.WARNING, "Re-asserted canonical inventory group '" +
                            group.name + "' (folder: " + group.worldname + "): " + group.worlds);
                }
            }
            return changed;
        } finally {
            enforcingCanonical = false;
        }
    }

    private static List<CanonicalGroup> getCanonicalGroups() {
        if (canonicalGroups != null) {
            return canonicalGroups;
        }

        List<CanonicalGroup> result = new ArrayList<CanonicalGroup>();
        try (InputStream stream = MyWorlds.plugin.getResource("inventories.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String groupName : defaults.getKeys(false)) {
                    ConfigurationSection groupConfig = defaults.getConfigurationSection(groupName);
                    if (groupConfig == null) {
                        continue;
                    }
                    String folder = groupConfig.getString("folder");
                    List<String> worlds = groupConfig.getStringList("worlds");
                    if (folder != null && !worlds.isEmpty()) {
                        result.add(new CanonicalGroup(groupName, folder, worlds));
                    }
                }
            }
        } catch (IOException ex) {
            MyWorlds.plugin.getLogger().log(Level.WARNING, "Failed to read bundled inventories.yml", ex);
        }
        canonicalGroups = result;
        return result;
    }

    private static final class CanonicalGroup {
        public final String name;
        public final String folder;
        public final List<String> worlds;

        public CanonicalGroup(String name, String folder, List<String> worlds) {
            this.name = name;
            this.folder = folder;
            this.worlds = worlds;
        }
    }

    public static void detach(Collection<String> worldnames) {
        // Collect all the loaded Bukkit worlds impacted by this
        Set<World> loadedWorlds = new HashSet<>();
        for (String world : worldnames) {
            for (String invworld : WorldConfig.get(world).inventory.getWorlds()) {
                World w = WorldConfig.get(invworld).getWorld();
                if (w != null) {
                    loadedWorlds.add(w);
                }
            }
        }

        // Modify
        if (!worldnames.isEmpty()) {
            for (String world : worldnames) {
                WorldConfig wc = WorldConfig.get(world);
                wc.inventory.removeWithoutSaving(world, true);
            }
            save();

            // Validate the bed spawn points of all worlds impacted, to make sure none of them
            // refer to a now-inaccessible world.
            for (World loadedWorld : loadedWorlds) {
                for (Player player : loadedWorld.getPlayers()) {
                    if (!MWPlayerDataController.isValidRespawnPoint(
                            loadedWorld,
                            PlayerRespawnPoint.forPlayer(player))
                    ) {
                        PlayerRespawnPoint.NONE.applyToPlayer(player);
                    }
                }
            }
        }
    }

    public static void merge(Collection<String> worldnames) {
        if (!worldnames.isEmpty()) {
            WorldInventory inv = new WorldInventory(null);
            for (String world : worldnames) {
                inv.addWithoutSaving(world);
            }
            save();
        }
    }

    private WorldInventory(String sharedWorldName) {
        inventories.add(this);
        this.name = "inv" + counter++;
        this.worldname = sharedWorldName;
    }

    public Collection<String> getWorlds() {
        return this.worlds;
    }

    /**
     * Gets whether this inventory configuration must be written to inventories.yml.
     * Default single-world isolated confogurations don't need to be written out
     *
     * @return True if this entry must be saved for proper persistence
     */
    private boolean isRequiredSaving() {
        return this.worlds.size() > 1;
    }

    /**
     * Changes the shared world name where inventory data is stored. Should only be called
     * from inventory migration.
     *
     * @param worldName
     */
    public void setSharedWorldName(String worldName) {
        if (!this.worlds.contains(worldName.toLowerCase())) {
            throw new IllegalArgumentException("World name " + worldName + " is not part of this inventory group");
        }
        this.worldname = worldName;
        save();
    }

    /**
     * Gets the World name in which all the inventories of this bundle are saved
     * 
     * @return shared world name
     */
    public String getSharedWorldName() {
        if (this.worldname == null || !WorldUtil.getWorldFolder(this.worldname).exists()) {
            this.worldname = getSharedWorldName(this.worlds);
            if (this.worldname == null) {
                throw new RuntimeException("Unable to locate a valid World folder to use for player data");
            }
        }
        return this.worldname;
    }

    private static String getSharedWorldName(Collection<String> worlds) {
        for (String world : worlds) {
            if (WorldConfig.get(world).getWorldFolder().exists()) {
                return world;
            }
        }
        return null;
    }

    public boolean contains(String worldname) {
        return this.worlds.contains(worldname.toLowerCase());
    }

    public boolean contains(World world) {
        return world != null && contains(world.getName());
    }

    public boolean remove(String worldname) {
        boolean result = removeWithoutSaving(worldname, false);
        if (result) {
            save();
        }
        return result;
    }

    private boolean removeWithoutSaving(String worldname, boolean createNew) {
        boolean removed = false;
        if (this.worlds.remove(worldname.toLowerCase())) {
            removed = true;

            //constructor handles world config update
            if (createNew) {
                new WorldInventory(worldname).addWithoutSaving(worldname);
            }
        }
        if (this.worlds.isEmpty()) {
            removed = true;
            inventories.remove(this);
        } else if (worldname.equalsIgnoreCase(this.worldname)) {
            removed = true;
            this.worldname = getSharedWorldName(this.worlds);
            if (this.worldname == null) {
                inventories.remove(this);
            }
        }
        return removed;
    }

    public WorldInventory add(String worldname) {
        WorldInventory inv = this.addWithoutSaving(worldname);
        save();
        return inv;
    }

    private WorldInventory addWithoutSaving(String worldname) {
        WorldConfig config = WorldConfig.get(worldname);
        if (config.inventory != null) {
            config.inventory.removeWithoutSaving(config.worldname, false);
        }
        config.inventory = this;
        this.worlds.add(worldname.toLowerCase());
        if (this.worldname == null) {
            this.worldname = getSharedWorldName(this.worlds);
        }
        return this;
    }
}
