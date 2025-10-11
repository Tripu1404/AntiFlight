package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.Listener;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;
import java.util.UUID;

public class FlightCheck extends PluginBase implements Listener {

    private final HashMap<String, Vector3> lastPosition = new HashMap<>();
    private final HashMap<String, Integer> airTicks = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Long> damageGrace = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[AntiMove] Activado silenciosamente con todos los checks.");
    }

    private boolean isInWaterOrLava(Player p) {
        Level level = p.getLevel();
        if (level == null) return false;
        Block block = level.getBlock(p.getPosition().floor());
        int id = block.getId();
        return id == Block.WATER || id == Block.STILL_WATER || id == Block.LAVA || id == Block.STILL_LAVA;
    }

    private boolean isOnIce(Player p) {
        Level level = p.getLevel();
        if (level == null) return false;
        Block block = level.getBlock(p.getPosition().floor());
        int id = block.getId();
        return id == Block.ICE || id == Block.PACKED_ICE || id == Block.BLUE_ICE || id == Block.FROSTED_ICE;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            damageGrace.put(p.getUniqueId(), System.currentTimeMillis() + 800);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        Item i = e.getItem();
        if (i == null) return;

        if (i.getId() == Item.TRIDENT && i.hasEnchantment(30) && isInWaterOrLava(p)) {
            riptideBypass.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
        }

        Item chest = p.getInventory().getChestplate();
        if (i.getId() == 401 && chest != null && chest.getId() == Item.ELYTRA) {
            elytraBoost.put(p.getUniqueId(), System.currentTimeMillis() + 5000);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p == null || p.isCreative() || p.isSpectator() || p.isOp()) return;

        UUID id = p.getUniqueId();
        Vector3 from = event.getFrom();
        Vector3 to = event.getTo();

        if (!lastPosition.containsKey(p.getName())) {
            lastPosition.put(p.getName(), from);
            airTicks.put(p.getName(), 0);
            return;
        }

        Vector3 last = lastPosition.get(p.getName());
        double dx = to.x - last.x;
        double dz = to.z - last.z;
        double dy = to.y - last.y;

        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        double maxSpeed = 0.46;
        if (p.isSprinting()) maxSpeed = 0.48;
        if (isInWaterOrLava(p)) maxSpeed = 0.60;
        if (p.isOnGround() && !p.isSprinting()) maxSpeed = 0.36;
        if (isOnIce(p)) maxSpeed += 0.15;

        long now = System.currentTimeMillis();

        if (damageGrace.getOrDefault(id, 0L) > now ||
            riptideBypass.getOrDefault(id, 0L) > now ||
            elytraBoost.getOrDefault(id, 0L) > now) {
            lastPosition.put(p.getName(), to);
            airTicks.put(p.getName(), 0);
            return;
        }

        // ====== Anti Speed / Timer ======
        if (horizontalSpeed > maxSpeed && !isOnIce(p)) {
            event.setCancelled(true);
            p.teleport(last);
            return;
        }

        // ====== Anti Glide ======
        int ticks = airTicks.getOrDefault(p.getName(), 0);
        if (!p.isOnGround()) {
            ticks++;
            if (ticks > 15 && dy > -0.02 && dy < 0.02 && !isInWaterOrLava(p)) {
                event.setCancelled(true);
                p.teleport(last);
                ticks = 0;
            }
        } else {
            ticks = 0;
        }

        // ====== Anti Fly ======
        if (!p.isOnGround() && dy < 0.4 && horizontalSpeed > 0.7 && !isInWaterOrLava(p) && !isOnIce(p)) {
            event.setCancelled(true);
            p.teleport(last);
            return;
        }

        // ====== Anti AirJump ======
        if (!p.isOnGround() && dy > 0.45 && !isInWaterOrLava(p)) {
            event.setCancelled(true);
            p.teleport(last);
            return;
        }

        airTicks.put(p.getName(), ticks);
        lastPosition.put(p.getName(), to);
    }

    // ====== Anti XP Mod ======
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        if (inv == null) return;

        int playerLevel = p.getExperienceLevel();
        int cost = 1;
        String name = inv.getName().toLowerCase();

        if (name.contains("enchant") || name.contains("anvil")) {
            if (playerLevel < cost) {
                e.setCancelled(true);
            }
        }
    }
}
