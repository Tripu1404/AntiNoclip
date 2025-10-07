package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.Listener;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.level.Level;
import cn.nukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FlightCheck: Un único archivo que agrupa:
 * - AntiSpeed / AntiBhop
 * - AntiGlide / AntiFly
 * - AntiTimer
 * - AntiXPHack (enchants / anvil validation)
 *
 * Diseño: silencioso (sin mensajes de spam), no hace daño, no teletransporta para AntiSpeed,
 * cancela movimientos ilegales volviendo el movimiento al estado anterior
 * o usando event.setTo(event.getFrom()) y reduciendo inercia.
 */
public class FlightCheck extends PluginBase implements Listener {

    // Movimiento
    private final Map<UUID, Vector3> lastPosition = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Integer> speedViolationTicks = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();

    // Elytra / Riptide bypasses (compatibilidad)
    private final Map<UUID, Long> riptideBypass = new HashMap<>();
    private final Map<UUID, Long> elytraBoost = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(TextFormat.GREEN + "[FlightCheck] Activado: AntiSpeed/AntiGlide/AntiFly/AntiTimer/AntiXP");
    }

    // --- Riptide y Elytra boost (compatibilidad cliente legítimo) ---
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();
        if (player == null || item == null) return;

        // Riptide en agua (tridente con enchant riptide = id 30)
        if (item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 1800L);
            }
        }

        // Elytra + cohete (cohete ID clásico 401). Da un boost temporal
        if (item.getId() == 401) {
            if (player.getInventory() != null && player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 5000L);
            }
        }
    }

    // --- Movimiento principal: AntiSpeed, AntiBhop, AntiGlide, AntiFly, AntiTimer ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Ignorar modos/condiciones legítimas
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        UUID id = player.getUniqueId();

        // Riptide bypass temporal
        Long bypass = riptideBypass.get(id);
        if (bypass != null) {
            if (bypass > System.currentTimeMillis()) return;
            else riptideBypass.remove(id);
        }

        Vector3 from = event.getFrom();
        Vector3 to = event.getTo();
        if (to == null || from == null) return;

        // Guardar última posición inicial si no existe
        Vector3 last = lastPosition.getOrDefault(id, from);

        double dx = to.x - last.x;
        double dz = to.z - last.z;
        double dy = to.y - last.y;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // ----- ANTI-TIMER -----
        long now = System.currentTimeMillis();
        long lastTime = lastMoveTime.getOrDefault(id, 0L);
        if (lastTime != 0L) {
            long diff = now - lastTime;
            // Si el cliente genera eventos mucho más rápido que 20 TPS (~50ms),
            // y además el movimiento es significativo, consideramos timer modificado.
            if (diff < 45 && (horizontalDistance > 0.01 || Math.abs(dy) > 0.01)) {
                // cancelar el movimiento ilegal (silencioso)
                event.setTo(event.getFrom());
                // reducir inercia
                player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y, player.getMotion().z * 0.1));
                resetMovementState(id);
                lastMoveTime.put(id, now);
                return;
            }
        }
        lastMoveTime.put(id, now);

        // ----- ANTI-SPEED / ANTI-BHOP -----
        // Calcula límite permitido dinámico
        double allowed = getAllowedHorizontalSpeed(player); // bloques por tick aproximado
        double margin = 0.05; // margen por lag/jitter
        int sv = speedViolationTicks.getOrDefault(id, 0);

        // Detección: speed incrementado de forma sostenida (ej. bhop/mod)
        if (horizontalDistance > allowed * 1.5 + margin) {
            sv++;
            // cancelar movimiento ilegal
            event.setTo(event.getFrom());
            // frenar inercia horizontal
            player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y, player.getMotion().z * 0.1));
            speedViolationTicks.put(id, sv);
            // no retornar; se interrumpe aquí para evitar demás checks este tick
            lastPosition.put(id, event.getFrom());
            return;
        } else {
            if (sv > 0) speedViolationTicks.put(id, sv - 1);
        }

        // ----- ANTI-FLY / ANTI-GLIDE -----
        // Actualizar contador de ticks en aire basado en isOnGround() real del servidor.
        int ticks = airTicks.getOrDefault(id, 0);

        // Compatibilidad Elytra: si lleva Elytra puesta y está planeando no considerarlo vuelo ilegal.
        boolean wearingElytra = player.getInventory() != null &&
                player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getId() == Item.ELYTRA;

        // Si hay boost de cohete reciente, permitir mayores márgenes si no está planeando
        Long boost = elytraBoost.get(id);
        boolean hasElytraBoost = (boost != null && boost > System.currentTimeMillis());

        // Si está en suelo de verdad, resetear contador y actualizar lastPosition
        if (isActuallyOnGround(player)) {
            airTicks.put(id, 0);
            lastPosition.put(id, to);
            return;
        }

        // Si no está en suelo:
        if (!player.isOnGround()) {
            ticks++;

            // Detectar Glide (flotación): lleva demasiado tiempo en el aire y el dy es casi 0
            if (ticks > 15 && dy > -0.03 && dy < 0.03 && !player.isInWater() && !player.isInLava()) {
                // cancelar movimiento (silencioso)
                event.setTo(event.getFrom());
                player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y - 0.1, player.getMotion().z * 0.1));
                airTicks.put(id, 0);
                lastPosition.put(id, event.getFrom());
                return;
            }

            // Detectar Fly ascendente (ganancia vertical anormal)
            if (ticks > 20 && dy > 0.15 && !wearingElytra) {
                event.setTo(event.getFrom());
                player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y - 0.2, player.getMotion().z * 0.1));
                airTicks.put(id, 0);
                lastPosition.put(id, event.getFrom());
                return;
            }

            // Detectar Fly prolongado (sostenido sin tocar suelo)
            if (ticks > 40 && !wearingElytra && !hasElytraBoost) {
                event.setTo(event.getFrom());
                player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y - 0.2, player.getMotion().z * 0.1));
                airTicks.put(id, 0);
                lastPosition.put(id, event.getFrom());
                return;
            }

            airTicks.put(id, ticks);
            lastPosition.put(id, to);
            return;
        }

        // Si llega aquí el movimiento es considerado válido, actualizamos estado
        airTicks.put(id, 0);
        lastPosition.put(id, to);
    }

    // --- XP / Enchant / Anvil protection ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        int realLevel = player.getExperienceLevel();
        int required = event.getExpLevelCost();
        if (realLevel < required) {
            // Cancelar uso de mesa si el servidor-level es menor al requerido
            event.setCancelled(true);
            getLogger().warning("[AntiXPHack] Cancelado enchant: jugador " + player.getName() + " no tiene niveles reales (" + realLevel + " < " + required + ")");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryTransaction(InventoryTransactionEvent event) {
        // Si la transacción involucra AnvilInventory (yunque), comprobar niveles reales.
        try {
            for (InventoryAction action : event.getTransaction().getActions()) {
                Inventory inv = action.getInventory();
                if (inv instanceof AnvilInventory) {
                    Player source = event.getTransaction().getSource();
                    if (source == null) continue;
                    int realLevel = source.getExperienceLevel();
                    // regla simple: si intenta usar anvil sin niveles reales (0) cancelamos
                    if (realLevel <= 0) {
                        event.setCancelled(true);
                        getLogger().warning("[AntiXPHack] Cancelada transacción en yunque por niveles insuficientes (jugador: " + source.getName() + ")");
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            // no fallar por excepciones en parsing de transacción
            getLogger().warning("[AntiXPHack] Error comprobando transacción: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = event.getPlayer();
        Inventory inv = event.getInventory();

        // Si abre mesa de encantamientos o yunque y su experiencia real parece sospechosa, cancelamos.
        if (inv instanceof EnchantInventory || inv instanceof AnvilInventory) {
            // heurística: si el cliente reporta niveles altísimos pero getExperience() está a 0, puede ser spoof
            if (player.getExperienceLevel() > 100 && player.getExperience() <= 0.0f) {
                event.setCancelled(true);
                getLogger().warning("[AntiXPHack] Jugador " + player.getName() + " abrió inventario de encantamiento/yunque con niveles sospechosos. Apertura cancelada.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Item source = event.getSourceItem();
        if (source == null) return;

        // Revertir encantamientos que superen el nivel máximo vanilla
        Enchantment[] enchants = source.getEnchantments();
        if (enchants == null || enchants.length == 0) return;

        boolean modified = false;
        for (Enchantment e : enchants) {
            if (e.getLevel() > e.getMaxLevel()) {
                // remover el enchant ilegal
                source.removeEnchantment(e.getId());
                modified = true;
            }
        }
        if (modified) {
            // aplicar corrección silenciosa en el inventario del jugador
            player.getInventory().setItemInHand(source);
            getLogger().info("[AntiXPHack] Encantamiento ilegal removido del jugador " + player.getName());
            event.setCancelled(true);
        }
    }

    // ----- Helpers -----

    private double getAllowedHorizontalSpeed(Player player) {
        // Estimación del movimiento horizontal permitido por tick (ajustable)
        double base = 0.36; // caminar normal
        if (player.isSprinting()) base *= 1.3; // sprint permite más
        if (player.hasEffect(Effect.SPEED)) {
            int amp = player.getEffect(Effect.SPEED).getAmplifier(); // 0 = Speed I
            // aproximación: cada nivel de Speed añade ~0.06 b/tick
            base += 0.06 * (amp + 1);
        }
        // condiciones especiales
        if (player.isInWater() || player.isInLava()) base = 0.65;
        if (player.isOnGround() && !player.isSprinting()) base = 0.36;
        return base;
    }

    /**
     * Comprueba de forma servidor-side si hay bloque sólido justo debajo del jugador.
     * Evita confiar en player.isOnGround() (que puede ser falsificado por hacks).
     */
    private boolean isActuallyOnGround(Player player) {
        Level level = player.getLevel();
        Vector3 pos = player.getPosition();
        double checkY = pos.getY() - 0.2; // comprobar ligeramente por debajo de los pies

        // revisa un área 3x3 bajo los pies para mayor robustez
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Vector3 blockPos = new Vector3(pos.getX() + x, checkY, pos.getZ() + z);
                try {
                    Block b = level.getBlock(blockPos);
                    if (b != null && !b.isTransparent() && !b.isAir()) {
                        return true;
                    }
                } catch (Exception ex) {
                    // en caso de error con getBlock, ignorar
                }
            }
        }
        return false;
    }

    private void resetMovementState(UUID id) {
        airTicks.put(id, 0);
        speedViolationTicks.put(id, 0);
        lastMoveTime.put(id, System.currentTimeMillis());
    }
}
