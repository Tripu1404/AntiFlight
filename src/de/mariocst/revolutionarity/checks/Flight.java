package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.item.Item;

import java.util.HashMap;

public class Flight extends PluginBase implements Listener {

    private final HashMap<Player, Double> lastOnGroundY = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FlightCheck habilitado correctamente.");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignorar jugadores con permisos de vuelo, creativo o spectator
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Elytra
        if (player.isGliding()) return;

        // Efectos especiales
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide real: jugador en agua + tridente en mano con encantamiento Riptide
        Item item = player.getInventory().getItemInHand();
        if (player.isSwimming() && item != null && item.hasEnchantment(28)) return;

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Actualizar última posición en el suelo
        if (player.isOnGround()) {
            lastOnGroundY.put(player, player.getY());
            return;
        }

        // Saltos normales
        if (deltaY > 0 && deltaY <= 0.42 && horizontalDistance <= 0.3) return;

        // Caídas naturales
        if (deltaY < 0 && Math.abs(deltaY) <= 0.78 && horizontalDistance <= 0.3) return;

        // Movimiento horizontal pequeño permitido
        if (horizontalDistance <= 0.3 && Math.abs(deltaY) <= 0.42) return;

        // Movimiento ilegal detectado (fly vertical u horizontal)
        event.setCancelled(true);
        getLogger().info(player.getName() + " intentó vuelo ilegal.");
    }
}
