package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

public class Flight extends PluginBase implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FlightCheck habilitado correctamente.");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // No revisar si el jugador tiene permisos de vuelo o está en modo creativo
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) {
            return;
        }

        // Elytra
        if (player.isGliding()) {
            if (event.getTo().getY() > event.getFrom().getY() + 0.5) {
                event.setCancelled(true);
                getLogger().info(player.getName() + " intentó volar con Elytra ilegal.");
            }
            return;
        }

        // Caída lenta
        if (player.hasEffect(Effect.SLOW_FALLING)) {
            if (event.getTo().getY() > event.getFrom().getY()) {
                event.setCancelled(true);
                getLogger().info(player.getName() + " intentó subir con Slow Falling ilegal.");
            }
            return;
        }

        // Levitation
        if (player.hasEffect(Effect.LEVITATION)) {
            double dx = event.getTo().getX() - event.getFrom().getX();
            double dz = event.getTo().getZ() - event.getFrom().getZ();
            double horizontalSpeed = Math.sqrt(dx*dx + dz*dz);
            if (horizontalSpeed > 0.5) {
                event.setCancelled(true);
                getLogger().info(player.getName() + " se movió demasiado rápido con Levitation.");
            }
            return;
        }

        // Riptide aproximado (solo agua + tridente con Riptide)
        if (player.isSwimming() && player.getInventory().getItemInHand() != null
                && player.getInventory().getItemInHand().hasEnchantment(28)) { 
            return;
        }

        // Detección de vuelo ilegal normal
        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        if (toY - fromY > 0.15 && !player.isSwimming() && !player.isInsideOfWater()) {
            event.setCancelled(true);
            getLogger().info(player.getName() + " intento de vuelo ilegal.");
        }
    }
}
