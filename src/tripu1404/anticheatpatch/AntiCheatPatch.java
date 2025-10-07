package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockEnderChest;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;

import java.util.HashMap;

public class AntiCheatPatch extends PluginBase implements Listener {

    private final double MAX_VERTICAL_SPEED = 1.2;
    private final double MIN_PLAYER_HEIGHT = 1.2;
    private final HashMap<String, Integer> playerTicks = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("§a[AntiCheatPatch] Activado correctamente.");

        getServer().getScheduler().scheduleRepeatingTask(this, new Task() {
            @Override
            public void onRun(int currentTick) {
                for (Player p : getServer().getOnlinePlayers().values()) {
                    playerTicks.put(p.getName(), playerTicks.getOrDefault(p.getName(), 0) + 1);
                }
            }
        }, 20);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (player.isGliding()) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to.distanceSquared(from) < 0.0001) return;

        // Chequeo vertical
        double deltaY = to.getY() - from.getY();
        int lived = playerTicks.getOrDefault(player.getName(), 0);
        if (isInsideSolidBlock(player, from)) {
            if (!player.getAllowFlight() && player.getGamemode() != Player.CREATIVE) {
                event.setTo(from);
                player.teleport(from);
                event.setCancelled(true);
                return;
            }
            if (Math.abs(deltaY) > MAX_VERTICAL_SPEED) {
                event.setTo(from);
                player.teleport(from);
                event.setCancelled(true);
                return;
            }
            if (!player.isOnGround() && deltaY < -0.5 && lived > 1) {
                event.setTo(from);
                player.teleport(from);
                event.setCancelled(true);
                return;
            }
            AxisAlignedBB box = player.getBoundingBox();
            double boxHeight = box.getMaxY() - box.getMinY();
            if (boxHeight < MIN_PLAYER_HEIGHT) {
                event.setTo(from);
                player.teleport(from);
                event.setCancelled(true);
                return;
            }
        }

        // Chequeo horizontal
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        AxisAlignedBB playerBox = player.getBoundingBox().shrink(0.1, 0.1, 0.1).offset(dx, dy, dz);

        int minX = (int) Math.floor(playerBox.getMinX());
        int maxX = (int) Math.floor(playerBox.getMaxX());
        int minY = (int) Math.floor(playerBox.getMinY());
        int maxY = (int) Math.floor(playerBox.getMaxY());
        int minZ = (int) Math.floor(playerBox.getMinZ());
        int maxZ = (int) Math.floor(playerBox.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z);
                    if (block instanceof BlockAir) continue;

                    // Ignora bloques con gravedad
                    String blockName = block.getName().toLowerCase();
                    if (blockName.contains("sand") || blockName.contains("gravel")) continue;

                    // Ignora bloques incompletos (slabs, stairs)
                    if (block.getBoundingBox() == null) continue;

                    // Bypass chests normales, pero bloquea ender chests
                    if (block instanceof BlockChest) continue;
                    if (block instanceof BlockEnderChest) {
                        event.setTo(from);
                        player.teleport(from);
                        event.setCancelled(true);
                        player.sendTip("§cNo puedes atravesar un Ender Chest.");
                        return;
                    }

                    if (playerBox.intersectsWith(block.getBoundingBox())) {
                        event.setTo(from);
                        player.teleport(from);
                        event.setCancelled(true);
                        player.sendTip("§cMovimiento ilegal detectado y bloqueado.");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTicks.remove(event.getPlayer().getName());
    }

    private boolean isInsideSolidBlock(Player player, Location loc) {
        AxisAlignedBB box = player.getBoundingBox().shrink(0.1, 0.1, 0.1);
        double dx = loc.getX() - player.getLocation().getX();
        double dy = loc.getY() - player.getLocation().getY();
        double dz = loc.getZ() - player.getLocation().getZ();
        AxisAlignedBB movedBox = box.offset(dx, dy, dz);

        int minX = (int) Math.floor(movedBox.getMinX());
        int maxX = (int) Math.floor(movedBox.getMaxX());
        int minY = (int) Math.floor(movedBox.getMinY());
        int maxY = (int) Math.floor(movedBox.getMaxY());
        int minZ = (int) Math.floor(movedBox.getMinZ());
        int maxZ = (int) Math.floor(movedBox.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z);
                    if (block instanceof BlockAir) continue;

                    // Bypass para bloques con gravedad
                    String blockName = block.getName().toLowerCase();
                    if (blockName.contains("sand") || blockName.contains("gravel")) continue;

                    if (block.getBoundingBox() != null && movedBox.intersectsWith(block.getBoundingBox())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
