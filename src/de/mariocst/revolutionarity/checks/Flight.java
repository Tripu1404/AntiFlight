package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;

public class Flight extends PluginBase implements Listener {

    // Última posición en el suelo
    private final HashMap<Player, double[]> lastGroundPos = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FlightCheck habilitado correctamente.");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignorar creativo, spectator o vuelo permitido
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Elytra, Levitation o Slow Falling permiten movimiento
        if (player.isGliding() || player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide: permitir cualquier movimiento si jugador tiene tridente con Riptide en mano
        Item item = player.getInventory().getItemInHand();
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(28)) {
            return;
        }

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

        // Caídas naturales
        if (deltaY < 0 && Math.abs(deltaY) <= 0.78 && horizontalDistance <= 0.5) return;

        // Movimiento horizontal permitido en el aire
        if (horizontalDistance <= 0.5 && Math.abs(deltaY) <= 0.42) return;

        // Si excede límites, cancelar
        event.setCancelled(true);
        getLogger().info(player.getName() + " intentó vuelo ilegal.");
    }
}
