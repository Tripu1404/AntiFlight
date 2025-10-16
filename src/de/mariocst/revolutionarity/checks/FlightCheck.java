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
    private final HashMap<UUID, Long> damageBypass = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Tarea repetitiva: aplica daño progresivo / empuje
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

                    boolean inAir = !player.isOnGround() && !player.isGliding() && !player.isInsideOfWater();
                    double verticalBps = lastVerticalSpeedBps.getOrDefault(id, 0.0);

                    if (inAir) {
                        int ticks = airTicks.getOrDefault(id, 0) + 1;
                        airTicks.put(id, ticks);

                        if (ticks > 60) { // 3 segundos en el aire
                            // Empujar al suelo una vez
                            try {
                                player.teleport(player.getLocation().add(0, -1, 0));
                            } catch (Exception ignored) {}

                            // Aplicar daño de caída
                            double fallDistance = ticks / 20.0 * 3.0; // aproximación
                            float damage = (float) (fallDistance * 3.0);
                            player.attack(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, damage));

                            // Reiniciar contadores y dar bypass temporal para no cancelar movimiento
                            airTicks.remove(id);
                            damageBypass.put(id, System.currentTimeMillis() + 100); // 0.1s bypass inmediato
                        }
                    } else {
                        airTicks.remove(id);
                    }

                    // Limpiar bypass expirados
                    Long ript = riptideBypass.get(id);
                    if (ript != null && ript <= now) riptideBypass.remove(id);
                    Long ely = elytraBoost.get(id);
                    if (ely != null && ely <= now) elytraBoost.remove(id);
                    Long dmg = damageBypass.get(id);
                    if (dmg != null && dmg <= now) damageBypass.remove(id);
                }
            }
        }, 20); // cada segundo
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
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        damageBypass.put(player.getUniqueId(), System.currentTimeMillis() + 3000); // 3 segundos de bypass
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        long now = System.currentTimeMillis();

        // Bypasses
        Long riptideTime = riptideBypass.get(id);
        if (riptideTime != null && riptideTime > now) return;
        Long dmgTime = damageBypass.get(id);
        if (dmgTime != null && dmgTime > now) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalBps = Math.abs(dy) * 20.0;
        lastVerticalSpeedBps.put(id, verticalBps);

        // Anti-Fly / saltos ilegales
        if (!player.isOnGround() && !player.isGliding()) {
            // Permitir saltos normales
            if (verticalBps <= 3.5 && horizontalDistance <= 1.5) return;

            // Elytra boost legítimo
            if (player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getId() == Item.ELYTRA &&
                !player.isGliding()) {

                double maxHorizontal = 2.5;
                double maxVertical = 1.5;
                Long boostTime = elytraBoost.get(id);
                if (boostTime != null && boostTime > now) {
                    maxHorizontal = 3.5;
                    maxVertical = 2.0;
                }

                if (horizontalDistance > maxHorizontal || Math.abs(dy) > maxVertical) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (horizontalDistance > 3.0 || Math.abs(dy) > 3.5) {
                event.setCancelled(true);
            }
        }

        // Anti-Speed hack
        if (horizontalDistance > 1.0 || verticalBps > 1.0) {
            event.setCancelled(true);
        }
        if (player.isSneaking() && horizontalDistance > 0.8) {
            event.setCancelled(true);
        }
    }
}
