package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;
import java.util.UUID;

public class Flight extends PluginBase implements Listener {

    private final HashMap<Player, double[]> lastGroundPos = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Riptide en agua
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 1800);
            }
        }

        // Elytra boost con cohete (ID 401)
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 5000); // 5 segundos
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
        else if (bypassTime != null && bypassTime <= System.currentTimeMillis()) riptideBypass.remove(player.getUniqueId());

        double fromX = event.getFrom().getX();
        double fromY = event.getFrom().getY();
        double fromZ = event.getFrom().getZ();

        double toX = event.getTo().getX();
        double toY = event.getTo().getY();
        double toZ = event.getTo().getZ();

        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double deltaY = toY - fromY;

        // Actualizar última posición en el suelo
        if (player.isOnGround()) {
            lastGroundPos.put(player, new double[]{toX, toY, toZ});
            return;
        }

        // Saltos normales
        if (deltaY > 0 && deltaY <= 0.42 && horizontalDistance <= 0.5) return;

        // Caídas naturales -> **no cancelar para preservar daño**
        if (deltaY < 0 && Math.abs(deltaY) <= 0.78 && horizontalDistance <= 0.5) return;

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

            if (horizontalDistance > maxHorizontal || Math.abs(deltaY) > maxVertical) {
                event.setCancelled(true);
                return;
            }
        }

        // Movimiento ilegal detectado solo si no Elytra, sin boost y sin Riptide
        if (!player.isGliding() &&
            (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getId() != Item.ELYTRA)) {
            event.setCancelled(true);
        }
    }
}
