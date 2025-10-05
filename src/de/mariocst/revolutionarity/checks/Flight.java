package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector3;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;
import java.util.UUID;

public class Flight extends PluginBase implements Listener {

    // Duraciones y límites
    private static final long RIPTIDE_BYPASS_MS = 1800L;
    private static final long ELYTRA_BOOST_MS = 5000L;

    private static final double DEFAULT_MAX_HORIZONTAL = 0.6;
    private static final double DEFAULT_MAX_VERTICAL = 0.5;

    // Fuerza del empuje hacia abajo
    private static final double PUSH_VELOCITY_Y = -1.6;

    // Registros de estado
    private final HashMap<UUID, Vector3> lastGroundPos = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Double> accumulatedDamage = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item != null) {
            // Riptide
            if (item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
                if (player.isInsideOfWater() || player.isSwimming()) {
                    riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + RIPTIDE_BYPASS_MS);
                }
            }

            // Elytra boost
            if (item.getId() == 401) {
                if (player.getInventory().getChestplate() != null &&
                        player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                    elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + ELYTRA_BOOST_MS);
                }
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignorar creativo, spectator o vuelo permitido
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Ignorar efectos que alteren movimiento
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide bypass
        Long rbt = riptideBypass.get(uuid);
        if (rbt != null && rbt > System.currentTimeMillis()) return;
        else if (rbt != null) riptideBypass.remove(uuid);

        // Última posición en suelo
        if (player.isOnGround()) {
            lastGroundPos.put(uuid, event.getTo());
            accumulatedDamage.remove(uuid); // resetear daño acumulado al tocar suelo
            return;
        }

        // Deltas
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double deltaY = event.getTo().getY() - event.getFrom().getY();

        // IGNORAR caída natural
        if (deltaY < 0) return;

        // Elytra planear -> permitido
        if (player.isGliding()) return;

        // Límites Elytra si no está planeando
        boolean hasElytraEquipped = player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getId() == Item.ELYTRA;
        if (hasElytraEquipped && !player.isGliding()) {
            double maxH = DEFAULT_MAX_HORIZONTAL;
            double maxV = DEFAULT_MAX_VERTICAL;
            Long boostT = elytraBoost.get(uuid);
            if (boostT != null && boostT > System.currentTimeMillis()) {
                maxH = 2.0;
                maxV = 1.0;
            }

            if (horizontalDistance > maxH || deltaY > maxV) {
                sanctionPush(player, event);
                return;
            }
            return;
        }

        // Movimiento ilegal normal: deltaY excesivo o horizontal extremo
        if (deltaY > 0.42 || horizontalDistance > 1.0) {
            sanctionPush(player, event);
        }
    }

    /**
     * Aplica sanción empujando al jugador al suelo y causando daño.
     * Si no llega al suelo, el daño se acumula y se duplica progresivamente.
     */
    private void sanctionPush(Player player, PlayerMoveEvent event) {
        UUID uuid = player.getUniqueId();
        Vector3 last = lastGroundPos.getOrDefault(uuid, event.getFrom());

        // Empujar al jugador hacia abajo
        player.setMotion(new Vector3(0, PUSH_VELOCITY_Y, 0));

        // Verificar si tocó suelo
        if (player.isOnGround()) {
            accumulatedDamage.remove(uuid); // reset
        } else {
            // Calcular daño acumulado
            double current = accumulatedDamage.getOrDefault(uuid, 1.0);
            player.attack(current); // aplica daño
            accumulatedDamage.put(uuid, current * 2); // duplicar para la próxima vez
        }
    }
}
