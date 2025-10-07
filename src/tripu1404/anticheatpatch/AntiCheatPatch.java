package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.Listener;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

public class AntiCheatPatch extends PluginBase implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(TextFormat.GREEN + "AntiCheatPatch cargado correctamente (Clip / NoClip bloqueado).");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        // Evita procesar si el jugador no se movió realmente
        if (to.distanceSquared(from) < 0.0001) return;

        // Bloque actual en la posición a la que se mueve
        Block blockAt = player.getLevel().getBlock(to.floor());
        if (blockAt != null && blockAt.isSolid() && blockAt.getId() != Block.AIR) {
            // Si el bloque es sólido, cancelar el movimiento
            event.setTo(from);
            player.teleport(from);
            player.sendTip(TextFormat.RED + "Movimiento ilegal detectado (Clip/NoClip bloqueado)");
            return;
        }

        // Detección de movimiento vertical anormal
        if (Math.abs(to.y - from.y) > 1.5) {
            Block below = player.getLevel().getBlock(new Vector3(to.x, to.y - 0.5, to.z));
            if (below.isSolid()) {
                event.setTo(from);
                player.teleport(from);
                player.sendTip(TextFormat.RED + "Movimiento vertical sospechoso bloqueado");
                return;
            }
        }

        // Detección de NoClip (atravesar paredes)
        Block front = player.getLevel().getBlock(new Vector3(to.x, to.y, to.z));
        if (front.isSolid()) {
            event.setTo(from);
            player.teleport(from);
            player.sendTip(TextFormat.RED + "Intento de atravesar bloque detectado (NoClip)");
        }
    }
}
