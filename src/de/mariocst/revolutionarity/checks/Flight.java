package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.UUID;

public class Flight extends PluginBase implements Listener {

    private final HashMap<Player, Vector3> lastGroundPos = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    // Riptide y Elytra boost
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Riptide en agua (1.8s)
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 1800);
            }
        }

        // Elytra boost con cohete (ID 401, 5s)
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignorar creativo, spectator o vuelo permitido
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Levitation y Slow Falling
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide temporal
        Long bypassTime = riptideBypass.get(player.getUniqueId());
        if (bypassTime != null && bypassTime > System.currentTimeMillis()) return;
        else if (bypassTime != null) riptideBypass.remove(player.getUniqueId());

        double deltaY = event.getTo().getY() - event.getFrom().getY();
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Actualizar última posición en el suelo
        if (player.isOnGround()) {
            lastGroundPos.put(player, event.getTo());
            return;
        }

        // Ignorar caída hacia abajo
        if (deltaY < 0) return;

        // Elytra puesta pero no planeando
        if (player.getInventory().getChestplate() != null &&
            player.getInventory().getChestplate().getId() == Item.ELYTRA &&
            !player.isGliding()) {

            double maxHorizontal = 0.6;
            double maxVertical = 0.5;

            Long boostTime = elytraBoost.get(player.getUniqueId());
            if (boostTime != null && boostTime > System.currentTimeMillis()) {
                maxHorizontal = 2.0;
                maxVertical = 1.0;
            }

            if (horizontalDistance > maxHorizontal || deltaY > maxVertical) {
                // Teletransportar al último bloque seguro en el suelo horizontalmente
                Vector3 lastPos = lastGroundPos.getOrDefault(player, event.getFrom());
                player.teleport(new Vector3(event.getTo().getX(), lastPos.y, event.getTo().getZ()));
                return;
            }
        }

        // Movimiento ilegal detectado (fly sin Elytra, sin Riptide, sin efectos)
        if (!player.isGliding() &&
            (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getId() != Item.ELYTRA)) {

            Vector3 lastPos = lastGroundPos.getOrDefault(player, event.getFrom());
            // Solo corregir vertical hacia arriba
            if (deltaY > 0.42) { // Salto normal permitido
                player.teleport(new Vector3(event.getTo().getX(), lastPos.y, event.getTo().getZ()));
            }
        }
    }
}
