package de.mariocst.revolutionarity.checks;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;

import java.util.HashMap;
import java.util.UUID;

public class AntiCheatPatch extends PluginBase implements Listener {

    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();
    private final HashMap<UUID, Integer> airJumpTicks = new HashMap<>();
    private final HashMap<UUID, Long> damageGrace = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("✅ AntiCheatPatch con kick automático para Glide y Timer activado.");
    }

    private boolean isInWaterOrLava(Player p) {
        Level level = p.getLevel();
        Block block = level.getBlock(p.getPosition().floor());
        int id = block.getId();
        return id == Block.WATER || id == Block.STILL_WATER || id == Block.LAVA || id == Block.STILL_LAVA;
    }

    private boolean isOnIce(Player p) {
        Level level = p.getLevel();
        Block block = level.getBlock(p.getPosition().floor());
        int id = block.getId();
        return id == Block.ICE || id == Block.PACKED_ICE || id == Block.BLUE_ICE || id == Block.FROSTED_ICE;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            damageGrace.put(p.getUniqueId(), System.currentTimeMillis() + 800);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Item i = e.getItem();

        if (i != null && i.getId() == Item.TRIDENT && i.hasEnchantment(30) && isInWaterOrLava(p)) {
            riptideBypass.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
        }

        if (i != null && i.getId() == 401) { // Firework Rocket
            if (p.getInventory().getChestplate() != null &&
                    p.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(p.getUniqueId(), System.currentTimeMillis() + 5000);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
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

        // Kick automático si usa Glide
        if (gliding) {
            p.kick("No se permite usar Glide / vuelo modificado.");
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

        // Hover o vuelo lento: bloquea sin kick
        if (!onGround && Math.abs(dy) < 0.01 && !isInWaterOrLava(p)) {
            long t = System.currentTimeMillis();
            if (!airJumpTicks.containsKey(id)) airJumpTicks.put(id, 0);
            int ticks = airJumpTicks.get(id) + 1;
            airJumpTicks.put(id, ticks);
            if (ticks > 12) {
                e.setCancelled(true);
                return;
            }
        }

        // Anti-Speed/Timer: kickea si velocidad excesiva
        if (!isOnIce(p) && !isInWaterOrLava(p)) {
            if (dxz > 0.85 && dy >= -0.3 && dy <= 0.5 && !onGround) {
                p.kick("No se permite usar Timer / Speed modificados.");
                return;
            }
        }
    }
}
