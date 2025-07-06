package tripu1404.anticheatpatch;

import cn.nukkit.Player;
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
    private final double MIN_Y_DIFF = -3.0;
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

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;
        int lived = playerTicks.getOrDefault(player.getName(), 0);

        if (Math.abs(deltaY) > MAX_VERTICAL_SPEED && !player.getAllowFlight()) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] Movimiento vertical inusual cancelado.");
            return;
        }

        if (!player.isOnGround()
                && !player.getAllowFlight()
                && player.getGamemode() != Player.CREATIVE
                && deltaY < -0.5
                && lived > 1) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] No puedes atravesar el piso.");
            return;
        }

        AxisAlignedBB box = player.getBoundingBox();
        double boxHeight = box.maxY - box.minY;
        if (boxHeight < MIN_PLAYER_HEIGHT) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] Tamaño corporal anómalo detectado.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTicks.remove(event.getPlayer().getName());
    }
}
