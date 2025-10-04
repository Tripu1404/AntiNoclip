package tripu1404.gravitypush;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockSand;
import cn.nukkit.block.BlockGravel;
import cn.nukkit.block.BlockConcretePowder;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.UUID;

public class GravityPush extends PluginBase implements Listener {

    private final HashMap<UUID, Block> lastBlock = new HashMap<>();
    private final HashMap<UUID, Integer> pushAttempts = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("GravityPush enabled!");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Block currentBlock = player.getLevel().getBlock(player.floor());
        Block previousBlock = lastBlock.getOrDefault(uuid, null);
        lastBlock.put(uuid, currentBlock);

        if (previousBlock == null || !previousBlock.equals(currentBlock)) {
            pushAttempts.put(uuid, 0);
        }

        if (isGravityBlock(currentBlock) && player.y < currentBlock.getY() + 1) {
            int attempts = pushAttempts.getOrDefault(uuid, 0);

            if (attempts < 3) {
                pushAttempts.put(uuid, attempts + 1);

                // 🔹 Paso 1: Impulso inicial con setMotion()
                Vector3 motion = player.getMotion().add(0, 0.15, 0);
                player.setMotion(motion);

                // 🔹 Paso 2 y 3: Teletransportes suaves para asegurar posición
                for (int i = 1; i <= 2; i++) {
                    final int step = i;
                    getServer().getScheduler().scheduleDelayedTask(this, () -> {
                        if (player.isOnline()) {
                            Vector3 targetPos = player.getLocation().add(0, 0.35 * step, 0);
                            player.teleport(targetPos);
                        }
                    }, i); // i ticks de delay entre cada teletransporte
                }
            }
        }
    }

    private boolean isGravityBlock(Block block) {
        return block instanceof BlockSand
                || block instanceof BlockGravel
                || block instanceof BlockConcretePowder;
    }
}
