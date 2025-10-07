package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockFalling;
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

        // si está planeando (elytra) no procesamos
        if (player.isGliding()) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // evitar procesar si no hay movimiento real
        if (to.distanceSquared(from) < 0.0001) return;

        double fromY = from.getY();
        double toY = to.getY();
        double deltaY = toY - fromY;
        int lived = playerTicks.getOrDefault(player.getName(), 0);

        // Chequeo vertical (igual que antes)
        boolean isInSolidBlockAtFrom = isPlayerInsideSolidBlockAtLocation(player, from);
        if (isInSolidBlockAtFrom && !player.getAllowFlight() && player.getGamemode() != Player.CREATIVE) {
            event.setCancelled(true);
            event.setTo(from);
            player.teleport(from);
            return;
        }

        if (isInSolidBlockAtFrom && Math.abs(deltaY) > MAX_VERTICAL_SPEED && !player.getAllowFlight()) {
            event.setCancelled(true);
            event.setTo(from);
            player.teleport(from);
            return;
        }

        if (isInSolidBlockAtFrom
                && !player.isOnGround()
                && !player.getAllowFlight()
                && player.getGamemode() != Player.CREATIVE
                && deltaY < -0.5
                && lived > 1) {
            event.setCancelled(true);
            event.setTo(from);
            player.teleport(from);
            return;
        }

        AxisAlignedBB playerBox = player.getBoundingBox();
        double boxHeight = playerBox.getMaxY() - playerBox.getMinY();
        if (isInSolidBlockAtFrom && boxHeight < MIN_PLAYER_HEIGHT) {
            event.setCancelled(true);
            event.setTo(from);
            player.teleport(from);
            return;
        }

        // --- NUEVA PARTE: detección horizontal robusta ---
        // Creamos la "inner box" del jugador en la posición destino (movida)
        double shrink = 0.1;
        AxisAlignedBB baseBox = player.getBoundingBox();
        // desplazamiento desde 'from' hasta 'to'
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        // Shrink la caja actual para evitar falsos positivos por rozar
        AxisAlignedBB innerBoxAtFrom = baseBox.shrink(shrink, shrink, shrink);
        // Movemos la caja a la posición destino
        AxisAlignedBB innerBoxAtTo = innerBoxAtFrom.offset(dx, dy, dz);

        // Comprobamos todos los bloques que intersecta la caja destino
        int minX = (int) Math.floor(innerBoxAtTo.getMinX());
        int maxX = (int) Math.floor(innerBoxAtTo.getMaxX());
        int minY = (int) Math.floor(innerBoxAtTo.getMinY());
        int maxY = (int) Math.floor(innerBoxAtTo.getMaxY());
        int minZ = (int) Math.floor(innerBoxAtTo.getMinZ());
        int maxZ = (int) Math.floor(innerBoxAtTo.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z);

                    // Saltar aire y bloques transparentes
                    if (block instanceof BlockAir || block.isTransparent()) continue;

                    // Ignorar bloques con gravedad (sand, red_sand, gravel...) -> bypass solicitado
                    if (block instanceof BlockFalling) continue;

                    // Si no tiene bounding box, ignoramos
                    if (block.getBoundingBox() == null) continue;

                    AxisAlignedBB bb = block.getBoundingBox();

                    // Solo considerar cubos completos (1x1x1) para evitar falsos positivos
                    if (!(bb.getMinX() == block.getX() && bb.getMinY() == block.getY() && bb.getMinZ() == block.getZ()
                            && bb.getMaxX() == block.getX() + 1 && bb.getMaxY() == block.getY() + 1 && bb.getMaxZ() == block.getZ() + 1)) {
                        continue;
                    }

                    // Si la caja destino intersecta con la bounding box del bloque -> intento de traspasar
                    if (innerBoxAtTo.intersectsWith(bb)) {
                        // Movimiento horizontal (o en general) bloqueado
                        event.setCancelled(true);
                        event.setTo(from);
                        player.teleport(from);
                        // Tip opcional: comentar si no quieres spam
                        player.sendTip("§cIntento de atravesar bloque detectado y bloqueado.");
                        return;
                    }
                }
            }
        }

        // --- fin detección horizontal ---
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTicks.remove(event.getPlayer().getName());
    }

    /**
     * Comprueba si el jugador está dentro de un bloque sólido en una location concreta.
     * Similar a versiones anteriores pero trabajando con la location pasada.
     */
    private boolean isPlayerInsideSolidBlockAtLocation(Player player, Location loc) {
        AxisAlignedBB baseBox = player.getBoundingBox();
        // desplazamiento desde la posición actual (getFrom) al loc solicitado:
        double dx = loc.getX() - player.getLocation().getX();
        double dy = loc.getY() - player.getLocation().getY();
        double dz = loc.getZ() - player.getLocation().getZ();

        double shrink = 0.1;
        AxisAlignedBB innerBox = baseBox.shrink(shrink, shrink, shrink).offset(dx, dy, dz);

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

                    if (block.isTransparent()) continue;
                    if (block.getBoundingBox() == null) continue;

                    // Ignorar bloques con gravedad (bypass para sand/gravel/...)
                    if (block instanceof BlockFalling) continue;

                    AxisAlignedBB bb = block.getBoundingBox();

                    // Solo cubos completos
                    if (!(bb.getMinX() == block.getX() && bb.getMinY() == block.getY() && bb.getMinZ() == block.getZ()
                            && bb.getMaxX() == block.getX() + 1 && bb.getMaxY() == block.getY() + 1 && bb.getMaxZ() == block.getZ() + 1)) {
                        continue;
                    }

                    if (innerBox.intersectsWith(bb)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
