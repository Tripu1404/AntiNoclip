package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;

import java.util.HashMap;

public class AntiCheatPatch extends PluginBase implements Listener {

    private final double MAX_VERTICAL_SPEED = 1.2;
    private final double MIN_Y_DIFF = -3.0;
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
        }, 20); // cada 20 ticks = 1 segundo
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
            player.sendMessage("§c[AntiCheat] Movimiento vertical excesivo cancelado.");
            return;
        }

        if (deltaY < MIN_Y_DIFF) {
            event.setCancelled(true);
            player.sendMessage("§c[AntiCheat] No puedes atravesar el piso.");
            return;
        }

        int lived = playerTicks.getOrDefault(player.getName(), 0);

        // 🛡️ Detectar flotamiento sospechoso (NoClip)
        if (!player.getAllowFlight() && !player.isFlying()) {
            if (!player.isOnGround() && Math.abs(deltaY) < 0.01 && lived > 1) {
                event.setCancelled(true);
                player.sendMessage("§c[AntiCheat] Movimiento flotante no permitido.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        playerTicks.remove(name); // 🧹 Limpieza al salir
    }
}
