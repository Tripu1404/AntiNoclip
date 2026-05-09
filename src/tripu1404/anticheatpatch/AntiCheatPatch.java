package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockEnderChest;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;

import java.util.HashMap;

public class AntiCheatPatch extends PluginBase implements Listener {

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
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGamemode() == Player.CREATIVE || player.getAllowFlight()) return;
        if (player.isGliding() || player.isInsideOfWater() || player.isOnLadder()) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || from == null || to.distanceSquared(from) < 0.0001) return;

        AxisAlignedBB box = player.getBoundingBox().shrink(0.05, 0.05, 0.05);

        if (isInsideSolidBlock(player, box)) {
            event.setTo(from);
            player.teleport(from);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerTicks.remove(e.getPlayer().getName());
    }

    //  Nuevo: bloquear interacción con Ender Chest en condiciones anómalas
    @EventHandler
    public void onEnderChestUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        Block block = event.getBlock();
        if (block instanceof BlockEnderChest) {
            AxisAlignedBB playerBox = player.getBoundingBox().shrink(0.05, 0.05, 0.05);
            if (isInsideSolidBlock(player, playerBox)) {
                event.setCancelled(true);
                player.sendPopup("§cNo puedes usar Ender Chest dentro de bloques.");
            }
        }
    }

    // Método corregido y dentro de la clase
    private boolean isInsideSolidBlock(Player player, AxisAlignedBB playerBox) {
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
                    
                    // 1. Filtros básicos rápidos
                    if (block instanceof BlockAir || block.getBoundingBox() == null) continue;
                    if (block instanceof BlockChest) continue;

                    String name = block.getName().toLowerCase();

                    // 2. EXCEPCIONES: Ignorar bloques donde el jugador PUEDE estar dentro
                    if (name.contains("shulker") || 
                        name.contains("frame") || 
                        name.contains("sand") || 
                        name.contains("gravel") ||
                        name.contains("slab") ||
                        name.contains("stair")) { 
                        continue;
                    }

                    AxisAlignedBB blockBox = block.getBoundingBox();

                    // 3. Verificación de intersección real
                    if (blockBox.shrink(0.1, 0.1, 0.1).intersectsWith(playerBox)) {
                        double overlapY = Math.min(blockBox.getMaxY(), playerBox.getMaxY()) - Math.max(blockBox.getMinY(), playerBox.getMinY());
                        double overlapX = Math.min(blockBox.getMaxX(), playerBox.getMaxX()) - Math.max(blockBox.getMinX(), playerBox.getMinX());
                        double overlapZ = Math.min(blockBox.getMaxZ(), playerBox.getMaxZ()) - Math.max(blockBox.getMinZ(), playerBox.getMinZ());

                        // Aumentamos ligeramente el margen a 0.4 para evitar falsos positivos por lag
                        if (overlapX > 0.4 && overlapY > 0.4 && overlapZ > 0.4) {
                            if (block instanceof BlockEnderChest) return true;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
