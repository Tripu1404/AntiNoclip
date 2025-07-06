package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.plugin.PluginBase;

public class AntiCheatPatch extends PluginBase implements Listener {

    private final double MAX_VERTICAL_SPEED = 1.2;
    private final double MIN_Y_DIFF = -3.0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AntiCheatPatch habilitado.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;

        if (Math.abs(deltaY) > MAX_VERTICAL_SPEED) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] Movimiento vertical inusual cancelado.");
            return;
        }

        if (deltaY < MIN_Y_DIFF) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] No puedes atravesar el piso.");
        }

        if (!player.isOnGround() && !player.getAllowFlight()) {
            if (Math.abs(deltaY) < 0.01 && player.getTicksLived() > 20) {
                event.setCancelled(true);
                player.sendMessage("§c[AntiCheat] Movimiento flotante no permitido.");
            }
        }
    }
}
