package tripu1404.anticheatpatch;

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
        getLogger().info("✅ AntiCheatPatch activado (sin falsos positivos tras daño).");
    }

    private boolean isInWaterOrLava(Player p) {
        Level level = p.getLevel();
        Block block = level.getBlock(p.getPosition().floor());
        int id = block.getId();
        return id == Block.WATER || id == Block.STILL_WATER || id == Block.LAVA || id == Block.STILL_LAVA;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            damageGrace.put(p.getUniqueId(), System.currentTimeMillis() + 600);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Item i = e.getItem();

        // Permitir Riptide temporalmente (bypass 1.5s)
        if (i != null && i.getId() == Item.TRIDENT && i.hasEnchantment(30) && isInWaterOrLava(p)) {
            riptideBypass.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
        }

        // Permitir impulso con cohete (Elytra) temporalmente (5s)
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

        // Excepciones legítimas
        if (p.isCreative() || p.isSpectator() || p.getAllowFlight()) return;
        if (p.hasEffect(Effect.LEVITATION) || p.hasEffect(Effect.SLOW_FALLING)) return;

        // Gracia tras daño (para evitar falsos positivos)
        if (damageGrace.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        // Bypass Riptide/Elytra temporal
        if (riptideBypass.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        if (elytraBoost.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        double fromY = e.getFrom().getY();
        double toY = e.getTo().getY();
        double dy = toY - fromY;
        double dxz = Math.sqrt(Math.pow(e.getTo().getX() - e.getFrom().getX(), 2)
                + Math.pow(e.getTo().getZ() - e.getFrom().getZ(), 2));

        boolean onGround = p.isOnGround();
        boolean gliding = p.isGliding();

        // 🔒 Anti-Glide
        if (gliding) {
            e.setCancelled(true);
            return;
        }

        // 🔒 Anti-AirJump
        if (!onGround) {
            int count = airJumpTicks.getOrDefault(id, 0) + 1;
            airJumpTicks.put(id, count);
            if (count > 6 && dy > 0.25) {
                e.setCancelled(true);
                return;
            }
        } else {
            airJumpTicks.put(id, 0);
        }

        // 🔒 Anti-Flight / Hover
        if (!onGround && Math.abs(dy) < 0.01 && !isInWaterOrLava(p)) {
            e.setCancelled(true);
            return;
        }

        // 🔒 Anti-Speed horizontal / Timer
        if (dxz > 0.9 && !isInWaterOrLava(p)) {
            e.setCancelled(true);
            return;
        }

        // 🔒 Anti-Fly vertical
        if (!onGround && dy > 0.7 && !isInWaterOrLava(p)) {
            e.setCancelled(true);
        }
    }
}
