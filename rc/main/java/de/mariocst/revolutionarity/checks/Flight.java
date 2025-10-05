package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
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

        // --- Verificaciones Vanilla ---
        boolean hasElytra = player.isGliding();
        boolean hasLevit = player.hasEffect(Effect.LEVITATION);
        boolean hasSlowFall = player.hasEffect(Effect.SLOW_FALLING);
        boolean usingRiptide = player.isUsingRiptide();

        // Si tiene cualquiera de estas condiciones, no se considera vuelo ilegal
        if (hasElytra || hasLevit || hasSlowFall || usingRiptide) {
            return;
        }

        // --- Detección de vuelo ilegal ---
        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        double deltaY = toY - fromY;

        // Si el jugador está sobre el suelo, no pasa nada
        if (player.isOnGround()) {
            player.getLevel().getBlock(player.getPosition());
            return;
        }

        // Si se mueve hacia arriba sin razón aparente (sin efectos ni elytra)
        if (deltaY > 0.15 && !player.isSwimming() && !player.isInsideOfWater()) {
            // Cancelamos el movimiento y lo detenemos en su posición actual
            event.setCancelled(true);

            // Opcional: mensaje debug (puedes quitarlo)
            if (Server.getInstance().isDebug()) {
                player.sendActionBar("§cVuelo ilegal cancelado.");
            }
        }
    }
}
