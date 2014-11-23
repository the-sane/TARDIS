/*
 * Copyright (C) 2014 eccentric_nz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.eccentric_nz.TARDIS.listeners;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import me.eccentric_nz.TARDIS.TARDIS;
import me.eccentric_nz.TARDIS.api.Parameters;
import me.eccentric_nz.TARDIS.database.QueryFactory;
import me.eccentric_nz.TARDIS.database.ResultSetCurrentLocation;
import me.eccentric_nz.TARDIS.database.ResultSetTardis;
import me.eccentric_nz.TARDIS.database.ResultSetTravellers;
import me.eccentric_nz.TARDIS.enumeration.FLAG;
import me.eccentric_nz.TARDIS.travel.TARDISAreasInventory;
import me.eccentric_nz.TARDIS.utility.TARDISMessage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 *
 * @author eccentric_nz
 */
public class TARDISSaveSignListener extends TARDISMenuListener implements Listener {

    private final TARDIS plugin;

    public TARDISSaveSignListener(TARDIS plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    /**
     * Listens for player clicking inside an inventory. If the inventory is a
     * TARDIS GUI, then the click is processed accordingly.
     *
     * @param event a player clicking an inventory slot
     */
    @EventHandler(ignoreCancelled = true)
    public void onSaveTerminalClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        String name = inv.getTitle();
        if (name.equals("§4TARDIS saves")) {
            final Player player = (Player) event.getWhoClicked();
            UUID uuid = player.getUniqueId();
            // get the TARDIS the player is in
            HashMap<String, Object> wheres = new HashMap<String, Object>();
            wheres.put("uuid", player.getUniqueId().toString());
            ResultSetTravellers rst = new ResultSetTravellers(plugin, wheres, false);
            if (rst.resultSet()) {
                int id = rst.getTardis_id();
                int slot = event.getRawSlot();
                if (plugin.getTrackerKeeper().getArrangers().contains(uuid)) {
                    if (slot >= 1 && slot < 45) {
                        if (event.getClick().equals(ClickType.SHIFT_LEFT) || event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                            event.setCancelled(true);
                            return;
                        }
                        // allow
                    } else {
                        event.setCancelled(true);
                    }
                } else {
                    event.setCancelled(true);
                    if (slot >= 0 && slot < 45) {
                        HashMap<String, Object> where = new HashMap<String, Object>();
                        where.put("tardis_id", id);
                        ResultSetCurrentLocation rsc = new ResultSetCurrentLocation(plugin, where);
                        Location current = null;
                        if (rsc.resultSet()) {
                            current = new Location(rsc.getWorld(), rsc.getX(), rsc.getY(), rsc.getZ());
                        }
                        ItemStack is = inv.getItem(slot);
                        if (is != null) {
                            ItemMeta im = is.getItemMeta();
                            List<String> lore = im.getLore();
                            Location save_dest = getLocation(lore);
                            if (save_dest != null) {
                                // check the player is allowed!
                                if (!plugin.getPluginRespect().getRespect(save_dest, new Parameters(player, FLAG.getDefaultFlags()))) {
                                    close(player);
                                    return;
                                }
                                // get tardis artron level
                                HashMap<String, Object> wherel = new HashMap<String, Object>();
                                wherel.put("tardis_id", id);
                                ResultSetTardis rs = new ResultSetTardis(plugin, wherel, "", false);
                                if (!rs.resultSet()) {
                                    close(player);
                                    return;
                                }
                                int level = rs.getArtron_level();
                                int travel = plugin.getArtronConfig().getInt("travel");
                                if (level < travel) {
                                    TARDISMessage.send(player, "NOT_ENOUGH_ENERGY");
                                    close(player);
                                    return;
                                }
                                if (!plugin.getTardisArea().areaCheckInExisting(save_dest)) {
                                    // save is in a TARDIS area, so check that the spot is not occupied
                                    HashMap<String, Object> wheresave = new HashMap<String, Object>();
                                    wheresave.put("world", lore.get(0));
                                    wheresave.put("x", lore.get(1));
                                    wheresave.put("y", lore.get(2));
                                    wheresave.put("z", lore.get(3));
                                    ResultSetCurrentLocation rsz = new ResultSetCurrentLocation(plugin, wheresave);
                                    if (rsz.resultSet()) {
                                        TARDISMessage.send(player, "TARDIS_IN_SPOT", ChatColor.AQUA + "/tardistravel area [name]" + ChatColor.RESET + " command instead.");
                                        close(player);
                                        return;
                                    }
                                }
                                if (!save_dest.equals(current)) {
                                    HashMap<String, Object> set = new HashMap<String, Object>();
                                    set.put("world", lore.get(0));
                                    set.put("x", plugin.getUtils().parseInt(lore.get(1)));
                                    set.put("y", plugin.getUtils().parseInt(lore.get(2)));
                                    set.put("z", plugin.getUtils().parseInt(lore.get(3)));
                                    int l_size = lore.size();
                                    if (l_size >= 5) {
                                        if (!lore.get(4).isEmpty() && !lore.get(4).equals("§6Current location")) {
                                            set.put("direction", lore.get(4));
                                        }
                                        if (l_size > 5 && !lore.get(5).isEmpty() && lore.get(5).equals("true")) {
                                            set.put("submarine", 1);
                                        } else {
                                            set.put("submarine", 0);
                                        }
                                    }
                                    HashMap<String, Object> wheret = new HashMap<String, Object>();
                                    wheret.put("tardis_id", id);
                                    new QueryFactory(plugin).doUpdate("next", set, wheret);
                                    plugin.getTrackerKeeper().getHasDestination().put(id, travel);
                                    if (plugin.getTrackerKeeper().getRescue().containsKey(id)) {
                                        plugin.getTrackerKeeper().getRescue().remove(id);
                                    }
                                    close(player);
                                    TARDISMessage.send(player, "DEST_SET_TERMINAL", im.getDisplayName(), true);
                                } else if (!lore.contains("§6Current location")) {
                                    lore.add("§6Current location");
                                    im.setLore(lore);
                                    is.setItemMeta(im);
                                }
                            } else {
                                close(player);
                                TARDISMessage.send(player, "DEST_NOT_VALID", im.getDisplayName());
                            }
                        }
                    }
                }
                if (slot == 45) {
                    // check it is this player's TARDIS
                    HashMap<String, Object> wherez = new HashMap<String, Object>();
                    wherez.put("tardis_id", id);
                    wherez.put("uuid", player.getUniqueId().toString());
                    ResultSetTardis rs = new ResultSetTardis(plugin, wherez, "", false);
                    if (rs.resultSet()) {
                        if (!plugin.getTrackerKeeper().getArrangers().contains(uuid)) {
                            // Only add one at a time
                            plugin.getTrackerKeeper().getArrangers().add(uuid);
                        }
                        TARDISMessage.send(player, "SAVE_ARRANGE");
                    } else {
                        TARDISMessage.send(player, "NOT_OWNER");
                    }
                }
                if (slot == 49) {
                    // load TARDIS areas
                    close(player);
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            TARDISAreasInventory sst = new TARDISAreasInventory(plugin, player);
                            ItemStack[] items = sst.getTerminal();
                            Inventory areainv = plugin.getServer().createInventory(player, 54, "§4TARDIS areas");
                            areainv.setContents(items);
                            player.openInventory(areainv);
                        }
                    }, 2L);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSaveSignClose(InventoryCloseEvent event) {
        final Inventory inv = event.getInventory();
        String inv_name = inv.getTitle();
        if (inv_name.equals("§4TARDIS saves")) {
            UUID uuid = ((Player) event.getPlayer()).getUniqueId();
            // get the TARDIS the player is in
            HashMap<String, Object> wheres = new HashMap<String, Object>();
            wheres.put("uuid", uuid.toString());
            ResultSetTravellers rst = new ResultSetTravellers(plugin, wheres, false);
            if (rst.resultSet()) {
                int id = rst.getTardis_id();
                QueryFactory qf = new QueryFactory(plugin);
                ItemStack[] stack = inv.getContents();
                for (int i = 1; i < 45; i++) {
                    if (stack[i] != null) {
                        ItemMeta im = stack[i].getItemMeta();
                        String save = im.getDisplayName();
                        HashMap<String, Object> set = new HashMap<String, Object>();
                        set.put("slot", i);
                        HashMap<String, Object> where = new HashMap<String, Object>();
                        where.put("tardis_id", id);
                        where.put("dest_name", save);
                        qf.doUpdate("destinations", set, where);
                    }
                }
            }
            if (plugin.getTrackerKeeper().getArrangers().contains(uuid)) {
                event.getPlayer().setItemOnCursor(new ItemStack(Material.AIR));
                plugin.getTrackerKeeper().getArrangers().remove(uuid);
            }
        }
    }

    /**
     * Converts an Item Stacks lore to a Location.
     *
     * @param lore the lore to read
     * @return a Location
     */
    private Location getLocation(List<String> lore) {
        World w = plugin.getServer().getWorld(lore.get(0));
        if (w == null) {
            return null;
        }
        int x = plugin.getUtils().parseInt(lore.get(1));
        int y = plugin.getUtils().parseInt(lore.get(2));
        int z = plugin.getUtils().parseInt(lore.get(3));
        return new Location(w, x, y, z);
    }
}
