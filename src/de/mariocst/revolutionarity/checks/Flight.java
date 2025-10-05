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

        // Ignorar si tiene permisos de vuelo o modo creativo
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Elytra, Levitation o Slow Falling permiten movimiento
        if (player.isGliding() || player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        // Riptide aproximado (agua + tridente con Riptide)
        if (player.isSwimming() && player.getInventory().getItemInHand() != null
                && player.getInventory().getItemInHand().hasEnchantment(28)) {
            return;
        }

        // Solo verificar si está en el aire y no estaba en el suelo
        if (!player.isOnGround()) {
            double fromY = event.getFrom().getY();
            double toY = event.getTo().getY();
            double deltaY = toY - fromY;

            // Altura máxima de salto vanilla ≈ 0.42 bloques
            if (deltaY > 0.42) {
                event.setCancelled(true);
                getLogger().info(player.getName() + " intentó vuelo ilegal.");
            }
        }
    }
}
