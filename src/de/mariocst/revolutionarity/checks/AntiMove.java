package com.tripu1404.anticheat;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.UUID;

public class AntiMove extends PluginBase implements Listener {

    private final HashMap<Player, double[]> lastGroundPos = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Integer> speedViolationTicks = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    // --- Riptide y Elytra Boost ---
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Tridente con Riptide
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (player.isInsideOfWater() || player.isSwimming()) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 1800);
            }
        }

        // Elytra + cohete (ID 401)
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
            }
        }
    }

    // --- Control principal de movimiento ---
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignorar creativo, espectador o vuelo permitido
        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;

        // Ignorar efectos que alteran movimiento
        if (player.hasEffect(Effect.LEVITATION) || player.hasEffect(Effect.SLOW_FALLING)) return;

        UUID id = player.getUniqueId();

        // Bypass temporal de Riptide
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

        // Actualiza última posición en el suelo
        if (player.isOnGround()) {
            lastGroundPos.put(player, new double[]{toX, toY, toZ});
        }

        // --- ANTI-FLY ---
        if (!player.isOnGround() && !player.isGliding()) {
            // Si no se mueve horizontalmente mucho, puede ser salto normal
            if (dy > 0 && dy <= 0.42 && horizontalDistance <= 0.5) return;
            // Caída natural permitida
            if (dy < 0 && Math.abs(dy) <= 0.78 && horizontalDistance <= 0.5) return;

            // Elytra equipada pero no planeando
            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA &&
                    !player.isGliding()) {

                double maxHorizontal = 0.6;
                double maxVertical = 0.5;

                Long boostTime = elytraBoost.get(id);
                if (boostTime != null && boostTime > System.currentTimeMillis()) {
                    maxHorizontal = 2.0;
                    maxVertical = 1.0;
                }

                if (horizontalDistance > maxHorizontal || Math.abs(dy) > maxVertical) {
                    event.setTo(event.getFrom());
                    player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y, player.getMotion().z * 0.1));
                    return;
                }
            }

            // Si sigue en el aire sin justificación → cancelar movimiento
            event.setTo(event.getFrom());
            player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y - 0.1, player.getMotion().z * 0.1));
            return;
        }

        // --- ANTI-SPEED ---
        double allowed = getAllowedHorizontalSpeed(player);
        double margin = 0.05;
        int currentViolations = speedViolationTicks.getOrDefault(id, 0);

        if (horizontalDistance > allowed * 1.5 + margin) {
            currentViolations++;
            event.setTo(event.getFrom());
            player.setMotion(new Vector3(player.getMotion().x * 0.1, player.getMotion().y, player.getMotion().z * 0.1));
            speedViolationTicks.put(id, currentViolations);
        } else {
            if (currentViolations > 0) speedViolationTicks.put(id, currentViolations - 1);
        }
    }

    private double getAllowedHorizontalSpeed(Player player) {
        double base = 0.36; // velocidad base caminando
        if (player.isSprinting()) base *= 1.3;
        if (player.hasEffect(Effect.SPEED)) {
            base += 0.06 * (player.getEffect(Effect.SPEED).getAmplifier() + 1);
        }
        return base;
    }
}
