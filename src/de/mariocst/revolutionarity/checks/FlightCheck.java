package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;

import java.util.HashMap;
import java.util.UUID;

public class FlightCheck extends PluginBase implements Listener {

    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Integer> airTicks = new HashMap<>();
    private final HashMap<UUID, Double> lastVerticalSpeedBps = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Tarea repetitiva: controla empuje y fly hack
        getServer().getScheduler().scheduleRepeatingTask(this, new Task() {
            @Override
            public void onRun(int currentTick) {
                long now = System.currentTimeMillis();
                for (Player player : getServer().getOnlinePlayers().values()) {
                    UUID id = player.getUniqueId();

                    if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) {
                        airTicks.remove(id);
                        continue;
                    }

                    // Ignorar natación
                    if (player.isInsideOfWater() || player.isSwimming() || player.isClimbing() || player.isOnLadder()) continue;

                    boolean inAir = !player.isOnGround() && !player.isGliding();

                    if (inAir) {
                        int ticks = airTicks.getOrDefault(id, 0) + 1;
                        airTicks.put(id, ticks);

                        if (ticks > 60) { // 3 segundos en el aire → fly hack
                            try {
                                player.teleport(player.getLocation().add(0, -1, 0));
                            } catch (Exception ignored) {}

                            double fallDistance = ticks / 20.0 * 3.0;
                            float damage = (float) (fallDistance * 3.0);
                            player.attack(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, damage));

                            airTicks.remove(id);
                        }
                    } else {
                        airTicks.remove(id);
                    }

                    // Limpiar bypass expirados
                    if (riptideBypass.get(id) != null && riptideBypass.get(id) <= now) riptideBypass.remove(id);
                    if (elytraBoost.get(id) != null && elytraBoost.get(id) <= now) elytraBoost.remove(id);
                }
            }
        }, 20);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Riptide
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 2200);
            }
        }

        // Elytra + cohete
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 6500);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Ignorar natación y escaleras
        if (player.isInsideOfWater() || player.isSwimming() || player.isClimbing() || player.isOnLadder()) return;

        long now = System.currentTimeMillis();

        // Bypass Elytra / Riptide
        if ((riptideBypass.get(id) != null && riptideBypass.get(id) > now) ||
            (elytraBoost.get(id) != null && elytraBoost.get(id) > now)) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        lastVerticalSpeedBps.put(id, Math.abs(dy * 20.0));

        // Detecta si realmente está en aire
        boolean inAir = !player.isOnGround() && !player.isGliding();

        if (inAir) {
            boolean normalJump = dy > 0 && dy <= 1.0; // saltos normales permitidos
            boolean flyingUp = dy > 1.0; // vuelo hacia arriba
            boolean flyingHoriz = horizontalDistance > 1.0; // vuelo horizontal ilegítimo

            if (!normalJump && (flyingUp || flyingHoriz)) {
                event.setCancelled(true);
            }
        }
    }
}
