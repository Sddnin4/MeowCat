package me.tuicode.thoitietbonmua;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ════════════════════════════════════════════════════════════════
 *  PLAYER PERK HANDLER — ThoiTietBonMua v2.3
 *  Xử lý toàn bộ đặc quyền / buff của Người Chơi theo mùa.
 *
 *  MÙA XUÂN:
 *   • Mưa + không mũ → Saturation + Regen I
 *   • Cưỡi Ngựa/Lừa/Heo không cần yên + tốc độ +30%
 *   • Câu cá nhanh hơn, không có đồ rác (Junk)
 *
 *  MÙA HẠ:
 *   • Ban ngày ngoài trời → Speed I + Haste I
 *   • Tiếp xúc lửa/dung nham → Fire Resistance 5s (cd 15s)
 *   • Dưới nước → Dolphin's Grace + Night Vision
 *
 *  MÙA THU:
 *   • Cầm Lá rơi → Slow Falling
 *   • Sneaking → giảm 70% tầm nhìn của mob
 *   • Sneaking + đánh từ sau lưng → x3 sát thương
 *
 *  MÙA ĐÔNG:
 *   • Chạy ngoài trời → Speed I (đường băng)
 *   • Trong vùng ấm 10 giây → Strength I + Resistance I khi ra ngoài (3 phút)
 *   • Fire Aspect vs mob giáp băng → x2 (xử lý trong MobSeasonHandler)
 * ════════════════════════════════════════════════════════════════
 */
public class PlayerPerkHandler implements Listener {

    private final ThoiTietBonMua plugin;
    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    // ── State tracking ────────────────────────────────────────────

    /** Cooldown Fire Resistance Mùa Hạ (UUID → timestamp ms). */
    private final Map<UUID, Long> fireResCooldown  = new HashMap<>();

    /** Thời điểm người chơi bắt đầu ở trong vùng ấm (UUID → ms). */
    private final Map<UUID, Long> warmZoneEntered  = new HashMap<>();

    /** Người chơi đã tích đủ 10s trong vùng ấm, chờ ra ngoài để nhận buff. */
    private final Set<UUID>       warmZoneReady    = new HashSet<>();

    /** AttributeModifier key cho tốc độ cưỡi (UUID ngựa → modifier UUID). */
    private final Map<UUID, UUID> rideSpeedMods    = new HashMap<>();

    // ─────────────────────────────────────────────────────────────

    public PlayerPerkHandler(ThoiTietBonMua plugin) {
        this.plugin = plugin;
        khoiDongVongLap();
    }

    // ══════════════════════════════════════════════════════════════
    //  VÒNG LẶP ĐỊNH KỲ (20 ticks = 1 giây)
    // ══════════════════════════════════════════════════════════════

    private void khoiDongVongLap() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String mua = getMua();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    switch (mua) {
                        case "xuan" -> apDungPerkXuan(player);
                        case "ha"   -> apDungPerkHa(player);
                        case "thu"  -> apDungPerkThu(player);
                        case "dong" -> apDungPerkDong(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String getMua() {
        if (Bukkit.getWorlds().isEmpty()) return "xuan";
        return plugin.getMuaHienTai(Bukkit.getWorlds().get(0));
    }

    // ══════════════════════════════════════════════════════════════
    //  🌸 MÙA XUÂN — PERK
    // ══════════════════════════════════════════════════════════════

    private void apDungPerkXuan(Player player) {
        World world = player.getWorld();

        // ── Mưa Xuân + không mũ → Saturation + Regen I ──────────
        if (world.hasStorm() && !coMaiChe(player)) {
            ItemStack helmet = player.getInventory().getHelmet();
            boolean khongMu = helmet == null || helmet.getType() == Material.AIR;
            if (khongMu) {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SATURATION, 40, 0, true, false));
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false));
            }
        }

        // ── Cầm lá đang rơi → Slow Falling ──────────────────────
        if (isLaItem(player.getInventory().getItemInMainHand().getType())
                && player.getVelocity().getY() < -0.4
                && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false));
        }
    }

    // ─── Cưỡi Ngựa/Lừa/Heo không cần yên ───────────────────────

    @EventHandler
    public void onCuoiKhongYen(PlayerInteractEntityEvent event) {
        if (!getMua().equals("xuan")) return;
        Player player = event.getPlayer();
        Entity target  = event.getRightClicked();

        // Chỉ xử lý ngựa đã thuần nhưng chưa có yên
        if (target instanceof AbstractHorse horse && horse.isTamed()) {
            if (horse.getInventory().getSaddle() == null
                    || horse.getInventory().getSaddle().getType() == Material.AIR) {
                event.setCancelled(true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!horse.isValid()) return;
                        horse.addPassenger(player);
                        // Tốc độ +30% — kiểm tra xem đã thêm chưa
                        var attr = horse.getAttribute(Attribute.MOVEMENT_SPEED);
                        if (attr != null) {
                            UUID modId = rideSpeedMods.get(horse.getUniqueId());
                            if (modId == null) {
                                UUID newId = UUID.randomUUID();
                                rideSpeedMods.put(horse.getUniqueId(), newId);
                                attr.addModifier(new AttributeModifier(
                                        newId, "spring_ride_speed", 0.30,
                                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                            }
                        }
                        player.sendMessage(Component.text(
                                "🌸 Mùa Xuân: Cưỡi không cần yên! +30% tốc độ!")
                                .color(NamedTextColor.GREEN));
                    }
                }.runTaskLater(plugin, 2L);
            }
        }

        // Heo không cần yên (cưỡi bình thường)
        if (target instanceof Pig pig) {
            event.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!pig.isValid()) return;
                    pig.addPassenger(player);
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    /** Dọn modifier tốc độ khi người chơi xuống ngựa. */
    @EventHandler
    public void onXuongNgua(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof AbstractHorse horse)) return;
        UUID modId = rideSpeedMods.remove(horse.getUniqueId());
        if (modId == null) return;
        var attr = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(modId))
                .findFirst()
                .ifPresent(attr::removeModifier);
    }

    // ── Câu cá Mùa Xuân đã xử lý trong MobSeasonHandler ─────────

    // ══════════════════════════════════════════════════════════════
    //  ☀ MÙA HẠ — PERK
    // ══════════════════════════════════════════════════════════════

    private void apDungPerkHa(Player player) {
        World world = player.getWorld();
        long time = world.getTime();
        boolean laBanNgay = time < 12000;

        // ── Ban ngày ngoài trời → Speed I + Haste I ──────────────
        if (laBanNgay && !coMaiChe(player) && !player.isInWater()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.HASTE, 40, 0, true, false));
        }

        // ── Dưới nước → Dolphin's Grace + Night Vision ───────────
        if (player.isUnderWater()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false));
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.NIGHT_VISION, 40, 0, true, false));
        }
    }

    /** Tiếp xúc lửa/dung nham → Fire Resistance 5s (cd 15s). */
    @EventHandler
    public void onTiepXucLua(EntityDamageEvent event) {
        if (!getMua().equals("ha")) return;
        if (!(event.getEntity() instanceof Player player)) return;
        boolean laLua = switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> true;
            default -> false;
        };
        if (!laLua) return;

        long now = System.currentTimeMillis();
        Long last = fireResCooldown.get(player.getUniqueId());
        if (last != null && now - last < 15_000L) return; // Còn cooldown

        fireResCooldown.put(player.getUniqueId(), now);
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0)); // 5 giây
        player.sendMessage(Component.text(
                "☀ Sức chịu nhiệt mùa hạ kích hoạt! +Kháng Lửa 5 giây! (CD: 15s)")
                .color(NamedTextColor.GOLD));
    }

    // ══════════════════════════════════════════════════════════════
    //  🍂 MÙA THU — PERK
    // ══════════════════════════════════════════════════════════════

    private void apDungPerkThu(Player player) {
        // ── Cầm Lá đang rơi → Slow Falling ───────────────────────
        if (isLaItem(player.getInventory().getItemInMainHand().getType())
                && player.getVelocity().getY() < -0.4
                && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false));
        }
    }

    /** Mob nhắm người chơi đang Sneak → giảm 70% tầm phát hiện. */
    @EventHandler
    public void onMobPhatHienNguoiSneaking(EntityTargetLivingEntityEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!player.isSneaking()) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;

        double distance = mob.getLocation().distance(player.getLocation());
        // Mob chỉ nhắm nếu gần hơn 30% tầm bình thường (16 block → ~5 block)
        if (distance > 16.0 * 0.30) {
            event.setCancelled(true);
        }
    }

    /** Sneaking + đánh từ sau lưng mob → x3 sát thương. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDanhSauLung(EntityDamageByEntityEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!player.isSneaking()) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Kiểm tra góc: mob đang nhìn theo hướng ngược chiều người chơi
        Vector mobFacing   = target.getLocation().getDirection().normalize();
        Vector mobToPlayer = player.getLocation()
                .subtract(target.getLocation()).toVector().normalize();

        // dot < -0.5 → mob quay lưng lại phía người chơi
        if (mobFacing.dot(mobToPlayer) < -0.5) {
            event.setDamage(event.getDamage() * 3.0);
            // Critical particle
            target.getWorld().spawnParticle(Particle.CRIT,
                    target.getLocation().add(0, 1, 0),
                    20, 0.4, 0.4, 0.4, 0.25);
            target.getWorld().playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.3f);
            player.sendMessage(Component.text("🍂 Đòn Bí Ẩn! x3 sát thương!")
                    .color(NamedTextColor.GOLD));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ❄ MÙA ĐÔNG — PERK
    // ══════════════════════════════════════════════════════════════

    private void apDungPerkDong(Player player) {
        UUID uid = player.getUniqueId();

        // ── Đường Đua Băng: Speed I khi chạy ngoài trời ──────────
        if (!coMaiChe(player) && !player.isFlying()) {
            Block ground = player.getLocation().subtract(0, 1, 0).getBlock();
            if (ground.getType().isSolid()) {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
                // Particle băng nhẹ dưới chân
                if (rng.nextInt(4) == 0) {
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            player.getLocation(), 2, 0.3, 0.05, 0.3, 0.01);
                }
            }
        }

        // ── Vùng Ấm (10 giây trong nhà + gần lửa) ────────────────
        boolean trongVungAm = coMaiChe(player) && coNguonNhietGan(player);

        if (trongVungAm) {
            warmZoneEntered.putIfAbsent(uid, System.currentTimeMillis());
            long gioTrong = (System.currentTimeMillis() - warmZoneEntered.get(uid)) / 1000L;
            if (gioTrong >= 10 && !warmZoneReady.contains(uid)) {
                warmZoneReady.add(uid);
                player.sendMessage(Component.text(
                        "🔥 Ấm đủ rồi! Ra ngoài để nhận: Sức Mạnh I + Kháng Thương I (3 phút)!")
                        .color(NamedTextColor.GOLD));
            }
        } else {
            // Vừa rời vùng ấm — phát buff nếu đã tích đủ
            if (warmZoneReady.contains(uid)) {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.STRENGTH, 3_600, 0, true, true));
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.RESISTANCE, 3_600, 0, true, true));
                player.sendMessage(Component.text(
                        "❄ Sức Mạnh I + Kháng Thương I kích hoạt! (3 phút)")
                        .color(NamedTextColor.AQUA));
            }
            warmZoneReady.remove(uid);
            warmZoneEntered.remove(uid);
        }
    }

    /** Kiểm tra gần nguồn nhiệt (Campfire, Furnace, v.v.) trong bán kính 5. */
    private boolean coNguonNhietGan(Player player) {
        Location loc = player.getLocation();
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    Material mat = loc.getWorld().getBlockAt(
                            loc.getBlockX() + x,
                            loc.getBlockY() + y,
                            loc.getBlockZ() + z).getType();
                    if (switch (mat) {
                        case CAMPFIRE, SOUL_CAMPFIRE,
                             FURNACE, BLAST_FURNACE, SMOKER,
                             MAGMA_BLOCK, LAVA -> true;
                        default -> false;
                    }) return true;
                }
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPER CHUNG
    // ══════════════════════════════════════════════════════════════

    /**
     * Raycast kiểm tra có block solid phía trên đầu người chơi không.
     * Hoạt động đúng trên mọi biome, dùng chung với MobSeasonHandler.
     */
    private boolean coMaiChe(Player player) {
        Location loc = player.getLocation();
        World world   = loc.getWorld();
        int px = loc.getBlockX(), pz = loc.getBlockZ();
        int startY = loc.getBlockY() + 2;
        int maxY   = Math.min(world.getMaxHeight(), startY + 128);
        for (int y = startY; y < maxY; y++) {
            Block b = world.getBlockAt(px, y, pz);
            if (b.getType().isSolid() && b.getType().isOccluding()) return true;
        }
        return false;
    }

    private boolean isLaItem(Material mat) {
        return switch (mat) {
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, AZALEA_LEAVES,
                 FLOWERING_AZALEA_LEAVES, CHERRY_LEAVES, MANGROVE_LEAVES -> true;
            default -> false;
        };
    }
}
