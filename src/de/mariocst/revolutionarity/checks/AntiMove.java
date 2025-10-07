package tripu1404.anticheatpatch;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.Listener;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Vector3;

import java.util.HashMap;
import java.util.UUID;

public class FlightCheck extends PluginBase implements Listener {

    private final HashMap<Player, double[]> lastGroundPos = new HashMap<>();
    private final HashMap<UUID, Long> riptideBypass = new HashMap<>();
    private final HashMap<UUID, Long> elytraBoost = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean isInWaterOrLava(Player player) {
        Level level = player.getLevel();
        Vector3 pos = player.getPosition().floor();
        Block block = level.getBlock(pos);
        int id = block.getId();
        return id == Block.WATER || id == Block.STILL_WATER || id == Block.LAVA || id == Block.STILL_LAVA;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        // Riptide en agua
        if (item != null && item.getId() == Item.TRIDENT && item.hasEnchantment(30)) {
            if (isInWaterOrLava(player)) {
                riptideBypass.put(player.getUniqueId(), System.currentTimeMillis() + 1800);
            }
        }

        // Elytra boost con cohete (ID 401)
        if (item != null && item.getId() == 401) {
            if (player.getInventory().getChestplate() != null &&
                    player.getInventory().getChestplate().getId() == Item.ELYTRA) {
                elytraBoost.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.isCreative() || player.isSpectator() || player.getAllowFlight()) return;
        if (player.hasEffect(cn.nukkit.potion.Effect.LEVITATION) ||
            player.hasEffect(cn.nukkit.potion.Effect.SLOW_FALLING)) return;

        Long bypassTime = riptideBypass.get(player.getUniqueId());
        if (bypassTime != null && bypassTime > System.currentTimeMillis()) return;
        else if (bypassTime != null) riptideBypass.remove(player.getUniqueId());

        double fromX = event.getFrom().getX();
        double fromY = event.getFrom().getY();
        double fromZ = event.getFrom().getZ();

        double toX = event.getTo().getX();
        double toY = event.getTo().getY();
        double toZ = event.getTo().getZ();

        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double deltaY = toY - fromY;

        boolean onGround = player.onGround();

        // Guardar última posición en suelo
        if (onGround) {
            lastGroundPos.put(player, new double[]{toX, toY, toZ});
            return;
        }

        // Saltos y caídas normales
        if (deltaY > 0 && deltaY <= 0.42 && horizontalDistance <= 0.5) return;
        if (deltaY < 0 && Math.abs(deltaY) <= 0.78 && horizontalDistance <= 0.5) return;

        // Elytra con boost
        if (player.getInventory().getChestplate() != null &&
            player.getInventory().getChestplate().getId() == Item.ELYTRA &&
            !player.isGliding()) {

            double maxHorizontal = 0.6;
            double maxVertical = 0.5;

            Long boostTime = elytraBoost.get(player.getUniqueId());
            if (boostTime != null && boostTime > System.currentTimeMillis()) {
                maxHorizontal = 2.0;
                maxVertical = 1.0;
            }

            if (horizontalDistance > maxHorizontal || Math.abs(deltaY) > maxVertical) {
                event.setCancelled(true);
                return;
            }
        }

        // Movimiento ilegal sin Elytra ni Riptide
        if (!player.isGliding() &&
            (player.getInventory().getChestplate() == null ||
            player.getInventory().getChestplate().getId() != Item.ELYTRA)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getWho();
        if (player == null) return;

        // Cancelar bypass de niveles de XP
        int levelCost = event.getLevelCost();
        if (levelCost > player.getLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryTransaction(InventoryTransactionEvent event) {
        // Cancelar cualquier encantamiento ilegal o manipulación de yunque
        for (InventoryAction action : event.getTransaction().getActions()) {
            Item source = action.getSourceItem();
            if (source == null) continue;

            // Eliminar encantamientos ilegales
            for (Enchantment e : source.getEnchantments()) {
                int maxLevel = Enchantment.getEnchantment(e.getTypeId()).getMaxLevel();
                if (e.getLevel() > maxLevel) {
                    source.removeEnchantment(e.getTypeId());
                }
            }
        }
    }
}
