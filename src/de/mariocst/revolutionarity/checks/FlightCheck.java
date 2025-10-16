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
    private final HashMap<UUID, Double> airDamage = new HashMap<>();
    private final HashMap<UUID, Double> lastVerticalSpeedBps = new HashMap<>();
    private final HashMap<UUID, Long> damageBypass = new HashMap<>(); // <-- bypass por recibir daño (3s)

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Tarea repetitiva: aplica daño progresivo / empuje cada segundo
        getServer().getScheduler().scheduleRepeatingTask(this, new Task() {
            @Override
            public void onRun(int currentTick) {
                long now = System.currentTimeMillis();
                for (Player player : getServer().getOnlinePlayers().values()) {
                    UUID id = player.getUniqueId();

                    if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) {
                        airTicks.remove(id);
                        airDamage.remove(id);
                        continue;
                    }

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

                        if (ticks > 60) { // 3 segundos en el aire
                            // Empujar hacia abajo (teletransportar 1 bloque hacia abajo)
                            try {
                                player.teleport(player.getLocation().add(0, -1, 0));
                            } catch (Exception ignored) {}

                            // Aplicar daño de caída completo (estimación basada en tiempo en aire)
                            double fallDistance = ticks / 20.0 * 3.0; // aproximación bloques
                            float damage = (float) (fallDistance * 3.0); // factor ajustable
                            player.attack(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, damage));

                            airTicks.remove(id);
                            airDamage.remove(id);
                        }
                    } else {
                        airTicks.remove(id);
                        airDamage.remove(id);
                    }

                    // Limpiar bypass expirados (opcional, para mantener mapas pequeños)
                    Long ript = riptideBypass.get(id);
                    if (ript != null && ript <= now) riptideBypass.remove(id);
                    Long ely = elytraBoost.get(id);
                    if (ely != null && ely <= now) elytraBoost.remove(id);
                    Long dmg = damageBypass.get(id);
                    if (dmg != null && dmg <= now) damageBypass.remove(id);
                }
            }
        }, 20); // repetir cada 20 ticks = 1 segundo
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Riptide (Tridente con Riptide = id de encantamiento 30)
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 2200);
            }
        }

        // Elytra + cohete (cohete id 401)
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 6500);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // Si la entidad dañada es un jugador, le damos bypass del anticheat por 3 segundos
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        damageBypass.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Respetar bypasss: riptide, damage
        Long now = System.currentTimeMillis();
        Long bypassTime = riptideBypass.get(id);
        if (bypassTime != null && bypassTime > now) return;
        else if (bypassTime != null && bypassTime <= now) riptideBypass.remove(id);

        Long dmgBypassTime = damageBypass.get(id);
        if (dmgBypassTime != null && dmgBypassTime > now) return; // si recibió daño recientemente, no aplicar anticheat
        else if (dmgBypassTime != null && dmgBypassTime <= now) damageBypass.remove(id);

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
                return;
            }
        }

        // Anti-Speed hack tipo GodSpeed (límites básicos)
        // Nota: estos límites son conservadores; ajusta según la sensibilidad que quieras.
        if (horizontalDistance > 0.42 || verticalBps > 0.6) {
            event.setCancelled(true);
            return;
        }
        if (player.isSneaking() && horizontalDistance > 0.42) {
            event.setCancelled(true);
        }
    }
}
