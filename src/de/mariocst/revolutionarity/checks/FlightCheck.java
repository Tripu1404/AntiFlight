package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.potion.Effect;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;
import java.util.UUID;

public class FlightCheck extends PluginBase implements Listener {

    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Integer> airJumpTicks = new HashMap<>();
    private final HashMap<UUID, Long> damageGrace = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("✅ AntiCheatPatch activado: Glide/Timer/Fly/XP/Speed/AirJump.");
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
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            damageGrace.put(p.getUniqueId(), System.currentTimeMillis() + 800); // 0.8s gracia
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        Item i = e.getItem();
        if (i == null) return;

        // Riptide en agua
        if (i.getId() == Item.TRIDENT && i.hasEnchantment(30) && isInWaterOrLava(p)) {
            riptideBypass.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
        }

        // Elytra boost con cohete
        Item chest = p.getInventory().getChestplate();
        if (i.getId() == 401 && chest != null && chest.getId() == Item.ELYTRA) {
            elytraBoost.put(p.getUniqueId(), System.currentTimeMillis() + 5000);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        UUID id = p.getUniqueId();

        if (p.isCreative() || p.isSpectator() || p.getAllowFlight()) return;
        if (p.hasEffect(Effect.LEVITATION) || p.hasEffect(Effect.SLOW_FALLING)) return;
        if (damageGrace.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        if (riptideBypass.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        if (elytraBoost.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        double fromY = e.getFrom().getY();
        double toY = e.getTo().getY();
        double dy = toY - fromY;
        double dxz = Math.sqrt(Math.pow(e.getTo().getX() - e.getFrom().getX(), 2)
                + Math.pow(e.getTo().getZ() - e.getFrom().getZ(), 2));

        boolean onGround = p.isOnGround();
        boolean gliding = p.isGliding();

        // Kick automático: Glide
        if (gliding) {
            p.kick("No se permite usar Glide / vuelo modificado.");
            return;
        }

        // Kick automático: Fly ilegal
        if (!onGround && dxz > 0.7 && dy < 0.4 && !isInWaterOrLava(p) && !isOnIce(p)) {
            p.kick("No se permite usar Fly / vuelo ilegal.");
            return;
        }

        // AirJump: bloquea sin kick
        if (!onGround && dy > 0.45 && !isInWaterOrLava(p)) {
            int count = airJumpTicks.getOrDefault(id, 0) + 1;
            airJumpTicks.put(id, count);
            if (count > 2) {
                e.setCancelled(true);
                return;
            }
        } else if (onGround) {
            airJumpTicks.put(id, 0);
        }

        // Movimiento horizontal imposible (Speed / Timer)
        if (!isOnIce(p) && !isInWaterOrLava(p)) {
            if (dxz > 0.85 && dy >= -0.3 && dy <= 0.5 && !onGround) {
                p.kick("No se permite usar Timer / Speed modificados.");
                return;
            }
        }

        // Hover o caída lenta ilegal
        if (!onGround && Math.abs(dy) < 0.01 && !isInWaterOrLava(p)) {
            int ticks = airJumpTicks.getOrDefault(id, 0) + 1;
            airJumpTicks.put(id, ticks);
            if (ticks > 12) {
                e.setCancelled(true);
            }
        }
    }

    // Detecta uso de XP modificado
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();

        Inventory inv = e.getInventory();
        if (inv == null) return;

        int playerLevel = p.getExperienceLevel(); // nivel real
        int cost = 1; // Ajustar según versión

        // Detecta mesa de encantamientos y yunque
        String type = inv.getName().toLowerCase();
        if (type.contains("enchant") || type.contains("yunque")) {
            if (playerLevel < cost) {
                p.kick("No se permite usar XP modificado / niveles falsos.");
            }
        }
    }
}
