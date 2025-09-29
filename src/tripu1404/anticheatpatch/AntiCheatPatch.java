package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
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

        // Contador de ticks vividos por jugador (1 incremento cada segundo)
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
        AxisAlignedBB innerBox = box.shrink(shrink, shrink, shrink);

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

                    // Saltar aire y bloques transparentes
                    if (block instanceof BlockAir || block.isTransparent()) continue;
                    if (block.getBoundingBox() == null) continue;

                    AxisAlignedBB bb = block.getBoundingBox();

                    // Solo considerar cubos completos (1x1x1)
                    if (bb.getMinX() == block.getX() && bb.getMinY() == block.getY() && bb.getMinZ() == block.getZ()
                            && bb.getMaxX() == block.getX() + 1 && bb.getMaxY() == block.getY() + 1 && bb.getMaxZ() == block.getZ() + 1) {
                        if (innerBox.intersectsWith(bb)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
