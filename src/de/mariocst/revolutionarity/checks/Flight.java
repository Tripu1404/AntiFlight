package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;

public class Flight extends PluginBase implements Listener {

    // Guardar la última posición en el suelo
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

        // Elytra, Levitation o Slow Falling permiten movimiento
        if (player.isGliding() || player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide aproximado (agua + tridente con Riptide)
        if (player.isSwimming() && player.getInventory().getItemInHand() != null
                && player.getInventory().getItemInHand().hasEnchantment(28)) {
            return;
        }

        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;

        // Saltos normales
        if (player.isOnGround()) {
            lastOnGroundY.put(player, player.getY());
            return;
        }

        // Permitir salto vanilla
        if (deltaY <= 0.42) return;

        // Detectar vuelo ilegal (Fly)
        if (lastOnGroundY.containsKey(player)) {
            double maxAllowed = lastOnGroundY.get(player) + 0.42;
            if (toY > maxAllowed) {
                event.setCancelled(true);
                getLogger().info(player.getName() + " intentó vuelo ilegal (Fly).");
                return;
            }
        }
    }
}
