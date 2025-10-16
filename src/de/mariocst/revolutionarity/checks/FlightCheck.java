package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;
import cn.nukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.UUID;

public class FlightCheck extends PluginBase implements Listener {

    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Integer> airTicks = new HashMap<>();
    private final HashMap<UUID, Double> airDamage = new HashMap<>();
    private final HashMap<UUID, Double> lastVerticalSpeedBps = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Tarea repetitiva: aplica daño progresivo cada segundo
        getServer().getScheduler().scheduleRepeatingTask(this, new Task() {
            @Override
            public void onRun(int currentTick) {
                for (Player player : getServer().getOnlinePlayers().values()) {
                    UUID id = player.getUniqueId();

                    if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) {
                        airTicks.remove(id);
                        airDamage.remove(id);
                        continue;
                    }

                    if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) continue;

                    boolean inAir = !player.isOnGround() && !player.isGliding() && !player.isInsideOfWater();
                    double verticalBps = lastVerticalSpeedBps.getOrDefault(id, 0.0);

                    // Caída normal (2–3 bloques/segundo) no aplica daño
                    if (inAir && verticalBps > 2.0 && verticalBps < 3.0) {
                        airTicks.remove(id);
                        airDamage.remove(id);
                        continue;
                    }

                    if (inAir) {
                        int ticks = airTicks.getOrDefault(id, 0) + 1;
                        airTicks.put(id, ticks);

                        if (ticks > 20) { // tras 1 segundo en el aire
                            double damage = airDamage.getOrDefault(id, 6.0);
                            float dmg = (float) damage;
                            player.attack(new EntityDamageEvent(
                                    player,
                                    EntityDamageEvent.DamageCause.VOID,
                                    dmg
                            ));
                            airDamage.put(id, damage * 2);
                        }
                    } else {
                        airTicks.remove(id);
                        airDamage.remove(id);
                    }
                }
            }
        }, 20); // repetir cada 20 ticks = 1 segundo
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
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        Long bypassTime = riptideBypass.get(id);
        if (bypassTime != null && bypassTime > System.currentTimeMillis()) return;
        else if (bypassTime != null && bypassTime <= System.currentTimeMillis()) riptideBypass.remove(id);

        double fromX = event.getFrom().getX();
        double fromY = event.getFrom().getY();
        double fromZ = event.getFrom().getZ();

        double toX = event.getTo().getX();
        double toY = event.getTo().getY();
        double toZ = event.getTo().getZ();

        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double dy = toY - fromY;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Guardamos la velocidad vertical en bloques/segundo
        double verticalBps = Math.abs(dy) * 20.0;
        lastVerticalSpeedBps.put(id, verticalBps);

        // Anti-fly / saltos ilegales
        if (!player.isOnGround() && !player.isGliding()) {
            if (dy < 0 && verticalBps > 2.0 && verticalBps < 3.0 && horizontalDistance <= 0.8) return;
            if (dy > 0 && dy <= 0.6 && horizontalDistance <= 0.8) return;

            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA &&
                    !player.isGliding()) {
                double maxHorizontal = 1.0;
                double maxVertical = 1.0;

                Long boostTime = elytraBoost.get(id);
                if (boostTime != null && boostTime > System.currentTimeMillis()) {
                    maxHorizontal = 2.5;
                    maxVertical = 1.5;
                }

                if (horizontalDistance > maxHorizontal || Math.abs(dy) > maxVertical) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (horizontalDistance > 1.0 || Math.abs(dy) > 1.0) {
                event.setCancelled(true);
            }
        }
    }
}
