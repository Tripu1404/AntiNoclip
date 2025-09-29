package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockStairs;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
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
        }, 20); // Cada 20 ticks = 1 segundo
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (player.isGliding()) return;

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;
        int lived = playerTicks.getOrDefault(player.getName(), 0);

        boolean isInSolidBlock = isPlayerInsideSolidBlock(player);

        if (isInSolidBlock && !player.getAllowFlight() && player.getGamemode() != Player.CREATIVE) {
            event.setCancelled(true);
            return;
        }

        if (isInSolidBlock && Math.abs(deltaY) > MAX_VERTICAL_SPEED && !player.getAllowFlight()) {
            event.setCancelled(true);
            return;
        }

        if (isInSolidBlock
                && !player.isOnGround()
                && !player.getAllowFlight()
                && player.getGamemode() != Player.CREATIVE
                && deltaY < -0.5
                && lived > 1) {
            event.setCancelled(true);
            return;
        }

        AxisAlignedBB box = player.getBoundingBox();
        double boxHeight = box.getMaxY() - box.getMinY();
        if (isInSolidBlock && boxHeight < MIN_PLAYER_HEIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTicks.remove(event.getPlayer().getName());
    }

    private boolean isPlayerInsideSolidBlock(Player player) {
        AxisAlignedBB box = player.getBoundingBox();

        // Reducimos la caja un poco para evitar falsos positivos en bordes
        double shrink = 0.1;
        AxisAlignedBB innerBox = new AxisAlignedBB(
                box.getMinX() + shrink,
                box.getMinY() + shrink,
                box.getMinZ() + shrink,
                box.getMaxX() - shrink,
                box.getMaxY() - shrink,
                box.getMaxZ() - shrink
        );

        int minX = (int) Math.floor(innerBox.getMinX());
        int maxX = (int) Math.floor(innerBox.getMaxX());
        int minY = (int) Math.floor(innerBox.getMinY());
        int maxY = (int) Math.floor(innerBox.getMaxY());
        int minZ = (int) Math.floor(innerBox.getMinZ());
        int maxZ = (int) Math.floor(innerBox.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z);

                    // Ignorar aire y escaleras
                    if (block instanceof BlockAir || block instanceof BlockStairs) {
                        continue;
                    }

                    // Solo cuenta si es sólido con bounding box
                    if (!block.isTransparent() && block.getBoundingBox() != null) {
                        if (innerBox.intersects(block.getBoundingBox())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
