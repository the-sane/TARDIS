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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.eccentric_nz.TARDIS.TARDIS;
import me.eccentric_nz.TARDIS.achievement.TARDISAchievementFactory;
import me.eccentric_nz.TARDIS.database.QueryFactory;
import me.eccentric_nz.TARDIS.database.ResultSetPlayerPrefs;
import me.eccentric_nz.TARDIS.enumeration.COMPASS;
import me.eccentric_nz.TARDIS.rooms.TARDISCondenserData;
import me.eccentric_nz.TARDIS.rooms.TARDISRoomBuilder;
import me.eccentric_nz.TARDIS.rooms.TARDISRoomDirection;
import me.eccentric_nz.TARDIS.rooms.TARDISSeedData;
import me.eccentric_nz.TARDIS.utility.TARDISMessage;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The Doctor kept some of the clothes from his previous regenerations, as well
 * as clothing for other people in the TARDIS wardrobe. At least some of the
 * clothes had pockets that were bigger on the inside.
 *
 * @author eccentric_nz
 */
public class TARDISRoomSeeder implements Listener {

    private final TARDIS plugin;
    private final List<String> ars = new ArrayList<String>();

    public TARDISRoomSeeder(TARDIS plugin) {
        this.plugin = plugin;
        ars.add("ars");
        ars.add("budget");
        ars.add("plank");
        ars.add("steampunk");
        ars.add("tom");
        ars.add("war");
    }

    /**
     * Listens for player interaction with one of the blocks required to seed a
     * room. If the block is clicked with the TARDIS key after running the
     * command /tardis room [room type], the seed block will start growing into
     * a passageway or the room type specified.
     *
     * Requires the TARDIS to have sufficient Artron Energy to grow the room.
     *
     * @param event a player clicking a block
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeedBlockInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // check that player is in TARDIS
        if (!plugin.getTrackerKeeper().getRoomSeed().containsKey(uuid)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null) {
            Material blockType = block.getType();
            Material inhand = player.getItemInHand().getType();
            String key;
            HashMap<String, Object> where = new HashMap<String, Object>();
            where.put("uuid", player.getUniqueId().toString());
            ResultSetPlayerPrefs rsp = new ResultSetPlayerPrefs(plugin, where);
            if (rsp.resultSet()) {
                key = (!rsp.getKey().isEmpty()) ? rsp.getKey() : plugin.getConfig().getString("preferences.key");
            } else {
                key = plugin.getConfig().getString("preferences.key");
            }
            // only proceed if they are clicking a seed block with the TARDIS key!
            if (plugin.getBuildKeeper().getSeeds().containsKey(blockType) && inhand.equals(Material.getMaterial(key))) {
                // check they are still in the TARDIS world
                if (!plugin.getUtils().inTARDISWorld(player)) {
                    TARDISMessage.send(player, "ROOM_IN_WORLD");
                    return;
                }
                // get clicked block location
                Location b = block.getLocation();
                // get the growing direction
                TARDISRoomDirection trd = new TARDISRoomDirection(block);
                trd.getDirection();
                if (!trd.isFound()) {
                    TARDISMessage.send(player, "PLATE_NOT_FOUND");
                    return;
                }
                COMPASS d = trd.getCompass();
                BlockFace facing = trd.getFace();
                // check there is not a block in the direction the player is facing
                Block check_block = b.getBlock().getRelative(BlockFace.DOWN).getRelative(facing, 9);
                if (!check_block.getType().equals(Material.AIR)) {
                    TARDISMessage.send(player, "ROOM_VOID");
                    return;
                }
                // get seed data
                TARDISSeedData sd = plugin.getTrackerKeeper().getRoomSeed().get(uuid);
                // check they are not in an ARS chunk
                if (ars.contains(sd.getSchematic().getPermission()) && sd.hasARS()) {
                    Chunk c = b.getWorld().getChunkAt(block.getRelative(BlockFace.valueOf(d.toString()), 4));
                    int cx = c.getX();
                    int cy = block.getY();
                    int cz = c.getZ();
                    if ((cx >= sd.getMinx() && cx <= sd.getMaxx()) && (cy >= 48 && cy <= 96) && (cz >= sd.getMinz() && cz <= sd.getMaxz())) {
                        TARDISMessage.send(player, "ROOM_USE_ARS");
                        return;
                    }
                }
                // get room schematic
                String r = plugin.getBuildKeeper().getSeeds().get(blockType);
                // check that the blockType is the same as the one they ran the /tardis room [type] command for
                if (!sd.getRoom().equals(r)) {
                    TARDISMessage.send(player, "ROOM_SEED_NOT_VALID", plugin.getTrackerKeeper().getRoomSeed().get(uuid).getRoom());
                    return;
                }
                // adjust the location three/four blocks out
                Location l = block.getRelative(facing, 3).getLocation();
                // build the room
                TARDISRoomBuilder builder = new TARDISRoomBuilder(plugin, r, l, d, player);
                if (builder.build()) {
                    // remove seed block and set block above it to AIR as well
                    block.setType(Material.AIR);
                    Block doorway = block.getRelative(facing, 2);
                    doorway.setType(Material.AIR);
                    doorway.getRelative(BlockFace.UP).setType(Material.AIR);
                    plugin.getTrackerKeeper().getRoomSeed().remove(uuid);
                    // ok, room growing was successful, so take their energy!
                    int amount = plugin.getRoomsConfig().getInt("rooms." + r + ".cost");
                    QueryFactory qf = new QueryFactory(plugin);
                    HashMap<String, Object> set = new HashMap<String, Object>();
                    set.put("uuid", player.getUniqueId().toString());
                    qf.alterEnergyLevel("tardis", -amount, set, player);
                    // remove blocks from condenser table if rooms_require_blocks is true
                    if (plugin.getConfig().getBoolean("growth.rooms_require_blocks")) {
                        TARDISCondenserData c_data = plugin.getGeneralKeeper().getRoomCondenserData().get(uuid);
                        for (Map.Entry<String, Integer> entry : c_data.getBlockIDCount().entrySet()) {
                            HashMap<String, Object> wherec = new HashMap<String, Object>();
                            wherec.put("tardis_id", c_data.getTardis_id());
                            wherec.put("block_data", entry.getKey());
                            qf.alterCondenserBlockCount(entry.getValue(), wherec);
                        }
                        plugin.getGeneralKeeper().getRoomCondenserData().remove(uuid);
                    }
                    // are we doing an achievement?
                    if (plugin.getAchievementConfig().getBoolean("rooms.enabled")) {
                        TARDISAchievementFactory taf = new TARDISAchievementFactory(plugin, player, "rooms", plugin.getBuildKeeper().getSeeds().size());
                        taf.doAchievement(r);
                    }
                }
            }
        }
    }
}
