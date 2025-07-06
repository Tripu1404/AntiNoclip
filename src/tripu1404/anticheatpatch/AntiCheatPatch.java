package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
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

        // 🪂 Ignorar si el jugador está usando elytras
        if (player.isGliding()) return;

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;
        int lived = playerTicks.getOrDefault(player.getName(), 0);

        // 🧱 Verifica si el jugador está dentro de un bloque sólido
        boolean isInSolidBlock = !player.getLevel().getBlock(player.getLocation()).isTransparent();

        // 🚫 Jugador dentro de un bloque sólido
        if (isPlayerInsideSolidBlock(player) && !player.getAllowFlight() && player.getGamemode() != Player.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] No puedes estar dentro de bloques.");
            return;
        }

        // 🚫 Movimiento vertical excesivo (NoClip)
        if (isInSolidBlock && Math.abs(deltaY) > MAX_VERTICAL_SPEED && !player.getAllowFlight()) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] Movimiento vertical inusual cancelado.");
            return;
        }

        // ⛔ Phaseo hacia abajo (AntiCrystal)
        if (isInSolidBlock
                && !player.isOnGround()
                && !player.getAllowFlight()
                && player.getGamemode() != Player.CREATIVE
                && deltaY < -0.5
                && lived > 1) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] No puedes atravesar el piso.");
            return;
        }

        // 🧍‍♂️ Bounding box alterado (Phase)
        AxisAlignedBB box = player.getBoundingBox();
        double boxHeight = box.getMaxY() - box.getMinY();
        if (isInSolidBlock && boxHeight < MIN_PLAYER_HEIGHT) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] Tamaño corporal anómalo detectado.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTicks.remove(event.getPlayer().getName());
    }

    // 🔍 Verifica si el jugador está dentro de un bloque sólido
    private boolean isPlayerInsideSolidBlock(Player player) {
        AxisAlignedBB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z);
                    if (!block.isTransparent()) {
                        return true; // Está dentro de al menos un bloque sólido
                    }
                }
            }
        }
        return false;
    }
}
