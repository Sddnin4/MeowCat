package me.tuicode.thoitietbonmua;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ════════════════════════════════════════════════════════════════
 *  MOB SEASON HANDLER — ThoiTietBonMua v2.3
 *  Xử lý toàn bộ thay đổi AI/hành vi Mob theo mùa.
 *
 *  TRIẾT LÝ:
 *   • KHÔNG phá hủy block người chơi, KHÔNG cháy lan công trình.
 *   • Tập trung thay đổi AI, tương tác vật lý, hiệu ứng Particle.
 *   • Tối ưu hiệu năng: cache UUID, throttle event nặng.
 * ════════════════════════════════════════════════════════════════
 */
public class MobSeasonHandler implements Listener {

    private final ThoiTietBonMua plugin;
    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    // ── Tracking state ────────────────────────────────────────────
    /** Mob đang có giáp băng (Mùa Đông) — giảm 50% damage vật lý. */
    private final Set<UUID> icedMobs = Collections.synchronizedSet(new HashSet<>());

    /** Mob đang nhảy theo nhạc Jukebox (Mùa Xuân). */
    private final Set<UUID> dancingMobs = Collections.synchronizedSet(new HashSet<>());

    /** Cache: các Jukebox đang phát nhạc (Location → tick cuối kiểm tra). */
    private final Set<Location> activeJukeboxes = Collections.synchronizedSet(new HashSet<>());

    /** Thời điểm gió lốc cuối — tránh spam. */
    private long lastWindGust = 0L;

    // ─────────────────────────────────────────────────────────────

    public MobSeasonHandler(ThoiTietBonMua plugin) {
        this.plugin = plugin;
        khoiDongVongLap();
    }

    // ══════════════════════════════════════════════════════════════
    //  VÒNG LẶP ĐỊNH KỲ
    // ══════════════════════════════════════════════════════════════

    private void khoiDongVongLap() {

        // ── Tick chính (20 ticks = 1 giây) ──────────────────────
        new BukkitRunnable() {
            @Override public void run() {
                String mua = getMua();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    switch (mua) {
                        case "xuan" -> tickXuan(player);
                        case "ha"   -> tickHa(player);
                        case "thu"  -> tickThu(player);
                        case "dong" -> tickDong(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // ── Kiểm tra Jukebox dance (40 ticks) ───────────────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("xuan")) { dancingMobs.clear(); return; }
                quetJukeboxGanNguoiChoi();
            }
        }.runTaskTimer(plugin, 0L, 40L);

        // ── Tự động sinh sản động vật (200 ticks) ───────────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("xuan")) return;
                tuDongSinhSan();
            }
        }.runTaskTimer(plugin, 0L, 200L);

        // ── Gió lốc Mùa Thu (ngẫu nhiên mỗi 5 giây) ────────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("thu")) return;
                if (rng.nextInt(100) < 15) apDungGioLoc();
            }
        }.runTaskTimer(plugin, 0L, 100L);

        // ── Cáo đào báu (mỗi 60 giây) ───────────────────────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("thu")) return;
                caoDaoBau();
            }
        }.runTaskTimer(plugin, 0L, 1200L);

        // ── Mực phát sáng ban đêm Mùa Hạ (mỗi 30 giây) ─────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("ha")) return;
                hienThiMucPhatSang();
            }
        }.runTaskTimer(plugin, 0L, 600L);

        // ── Slime lăn nhanh Mùa Đông (20 ticks) ─────────────────
        new BukkitRunnable() {
            @Override public void run() {
                if (!getMua().equals("dong")) return;
                for (World w : Bukkit.getWorlds()) {
                    for (Slime slime : w.getEntitiesByClass(Slime.class)) {
                        if (slime.getSize() > 0) {
                            // Tăng tốc độ lăn — nhân vận tốc ngang
                            Vector vel = slime.getVelocity();
                            if (vel.length() > 0.05) {
                                vel.setX(vel.getX() * 1.5);
                                vel.setZ(vel.getZ() * 1.5);
                                slime.setVelocity(vel);
                            }
                            // Particle băng
                            w.spawnParticle(Particle.SNOWFLAKE,
                                    slime.getLocation().add(0, 0.5, 0),
                                    2, 0.3, 0.1, 0.3, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── Helper ───────────────────────────────────────────────────
    private String getMua() {
        if (Bukkit.getWorlds().isEmpty()) return "xuan";
        return plugin.getMuaHienTai(Bukkit.getWorlds().get(0));
    }

    // ══════════════════════════════════════════════════════════════
    //  TICK THEO MÙA — hiệu ứng particle liên tục
    // ══════════════════════════════════════════════════════════════

    /** Mùa Xuân: dây leo quanh Zombie/Skeleton, hồi máu khi mưa. */
    private void tickXuan(Player player) {
        for (Entity e : player.getNearbyEntities(20, 15, 20)) {
            if (!(e instanceof Zombie || e instanceof Skeleton)) continue;
            LivingEntity mob = (LivingEntity) e;

            // Dây leo particle (HAPPY_VILLAGER màu xanh)
            e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    e.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0);

            // Hồi máu dưới mưa hoặc trong nước
            if (mob.getWorld().hasStorm() || mob.getLocation().getBlock().isLiquid()) {
                double max = mob.getAttribute(
                        org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                mob.setHealth(Math.min(mob.getHealth() + 0.25, max));
            }
        }
    }

    /** Mùa Hạ: hào quang lửa Zombie/Skeleton, vô hiệu cháy nắng. */
    private void tickHa(Player player) {
        for (Entity e : player.getNearbyEntities(20, 15, 20)) {
            if (e instanceof Zombie zombie) {
                zombie.setFireTicks(0); // Không bị cháy nắng
                e.getWorld().spawnParticle(Particle.FLAME,
                        e.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
            }
            if (e instanceof Skeleton skeleton) {
                skeleton.setFireTicks(0);
                e.getWorld().spawnParticle(Particle.FLAME,
                        e.getLocation().add(0, 1, 0), 1, 0.2, 0.5, 0.2, 0.01);
            }
        }
    }

    /** Mùa Thu: Enderman ngụy trang lá, Iron Golem đội bí ngô. */
    private void tickThu(Player player) {
        for (Entity e : player.getNearbyEntities(30, 20, 30)) {
            if (e instanceof Enderman) {
                // Particle lá xung quanh (ngụy trang thành cây)
                e.getWorld().spawnParticle(Particle.DUST,
                        e.getLocation().add(0, 1.5, 0),
                        4, 0.4, 0.8, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(34, 100, 34), 1.3f));
            }
            if (e instanceof IronGolem) {
                // Hiệu ứng bí ngô phát sáng trên đầu
                e.getWorld().spawnParticle(Particle.FLAME,
                        e.getLocation().add(0, 2.8, 0),
                        1, 0.1, 0.1, 0.1, 0.005);
            }
        }
    }

    /** Mùa Đông: particle băng Zombie/Skeleton, mắt sói xanh. */
    private void tickDong(Player player) {
        for (Entity e : player.getNearbyEntities(20, 15, 20)) {
            if ((e instanceof Zombie || e instanceof Skeleton)
                    && icedMobs.contains(e.getUniqueId())) {
                e.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        e.getLocation().add(0, 1, 0),
                        3, 0.3, 0.5, 0.3, 0);
            }
            if (e instanceof Wolf wolf && !wolf.isTamed()) {
                // Mắt sói xanh dương
                e.getWorld().spawnParticle(Particle.DUST,
                        wolf.getEyeLocation(),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 144, 255), 0.6f));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  🌸 MÙA XUÂN — SỰ KIỆN
    // ══════════════════════════════════════════════════════════════

    // ─── 1. Nhạc Hội Muông Thú (Jukebox) ────────────────────────

    /** Quét Jukebox đang phát trong bán kính 8 block quanh người chơi. */
    private void quetJukeboxGanNguoiChoi() {
        Set<Location> found = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            for (int x = -8; x <= 8; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -8; z <= 8; z++) {
                        Block block = loc.getWorld().getBlockAt(
                                loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                        if (block.getType() == Material.JUKEBOX) {
                            if (block.getState() instanceof Jukebox jb && jb.isPlaying()) {
                                found.add(block.getLocation());
                                dongBangNhayTheo(block.getLocation(), 30);
                            }
                        }
                    }
                }
            }
        }
        activeJukeboxes.clear();
        activeJukeboxes.addAll(found);
        if (found.isEmpty()) dancingMobs.clear();
    }

    /** Đóng băng và cho mob nhảy theo nhịp nhạc trong bán kính r. */
    private void dongBangNhayTheo(Location jukeboxLoc, int radius) {
        long tick = jukeboxLoc.getWorld().getGameTime();
        for (Entity e : jukeboxLoc.getWorld().getNearbyEntities(jukeboxLoc, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            dancingMobs.add(le.getUniqueId());
            // Nhảy lên xuống theo nhịp
            Vector vel = le.getVelocity();
            if (tick % 20 < 3) {
                le.setVelocity(new Vector(vel.getX(), 0.3, vel.getZ()));
            } else {
                le.setVelocity(new Vector(0, vel.getY(), 0)); // Giữ yên ngang
            }
            // Hoa hướng dương particle
            jukeboxLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    e.getLocation().add(0, 1.2, 0), 2, 0.4, 0.4, 0.4, 0);
        }
    }

    // ─── 2. Creeper Nở Hoa ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperNoBoa(EntityExplodeEvent event) {
        if (!getMua().equals("xuan")) return;
        if (!(event.getEntity() instanceof Creeper creeper)) return;

        // HỦY HOÀN TOÀN — không phá block nào
        event.blockList().clear();
        event.setCancelled(true);

        Location loc = creeper.getLocation();
        World world = creeper.getWorld();

        // Pháo hoa particle
        world.spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1.5, 0), 60, 1.5, 1.5, 1.5, 0.4);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 30, 2, 2, 2, 0);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.2f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 0.9f);

        // Hồi máu cho người chơi trong bán kính 10 block
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (e instanceof Player p) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                p.sendMessage(Component.text("🌸 Creeper Hoa nở! Nhận Hồi Máu II!")
                        .color(NamedTextColor.GREEN));
            }
        }

        // Bone-meal bán kính 4: làm hoa/cỏ mọc trên GRASS_BLOCK
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (rng.nextInt(3) != 0) continue;
                Block surface = world.getHighestBlockAt(
                        loc.getBlockX() + dx, loc.getBlockZ() + dz);
                if (surface.getType() == Material.GRASS_BLOCK) {
                    surface.applyBoneMeal(BlockFace.UP);
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                            surface.getLocation().add(0.5, 1.2, 0.5), 2, 0.2, 0.2, 0.2, 0);
                }
            }
        }
        creeper.remove();
    }

    // ─── 3. Zombie & Skeleton "Cội Rễ" — drop khi chết ──────────

    @EventHandler
    public void onMobChetTheoMua(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String mua = getMua();

        if (mua.equals("xuan") && (entity instanceof Zombie || entity instanceof Skeleton)) {
            if (rng.nextInt(3) == 0) { // 33%
                Material seed = switch (rng.nextInt(3)) {
                    case 0 -> Material.MELON_SEEDS;
                    case 1 -> Material.PUMPKIN_SEEDS;
                    default -> Material.BROWN_MUSHROOM;
                };
                event.getDrops().add(new ItemStack(seed, 1));
            }
        }

        // Dọn tracking Mùa Đông
        icedMobs.remove(entity.getUniqueId());
    }

    // ─── 4. Nhện Dệt Lụa — Slowness + Jump Boost ─────────────────

    @EventHandler
    public void onDiVaoCobweb(PlayerMoveEvent event) {
        if (!getMua().equals("xuan")) return;
        Player player = event.getPlayer();
        if (player.getLocation().getBlock().getType() != Material.COBWEB) return;

        // Particle hồng hoa anh đào
        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add(0, 0.8, 0), 5, 0.4, 0.4, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(255, 182, 193), 1.2f));

        // Slowness nhưng bù lại Jump Boost
        if (!player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 1, true, false));
        }
    }

    // ─── 5. Cừu đổi màu lông khi mọc lại ───────────────────────

    @EventHandler
    public void onCuuMocLong(SheepRegrowWoolEvent event) {
        if (!getMua().equals("xuan")) return;
        if (rng.nextInt(3) != 0) return; // 33% cơ hội
        DyeColor[] pastels = {
                DyeColor.PINK, DyeColor.LIGHT_BLUE, DyeColor.LIME,
                DyeColor.YELLOW, DyeColor.WHITE, DyeColor.CYAN
        };
        event.getSheep().setColor(pastels[rng.nextInt(pastels.length)]);
    }

    // ─── 6. Tự động sinh sản ─────────────────────────────────────

    private void tuDongSinhSan() {
        for (World world : Bukkit.getWorlds()) {
            for (Animals animal : world.getEntitiesByClass(Animals.class)) {
                if (!animal.isAdult() || animal.getLoveModeTicks() > 0) continue;
                for (Entity nearby : animal.getNearbyEntities(4, 3, 4)) {
                    if (nearby.getClass() != animal.getClass()) continue;
                    if (!(nearby instanceof Animals partner)) continue;
                    if (!partner.isAdult() || partner.getLoveModeTicks() > 0) continue;
                    animal.setLoveModeTicks(200);
                    partner.setLoveModeTicks(200);
                    world.spawnParticle(Particle.HEART,
                            animal.getLocation().add(0, 1.5, 0), 3, 0.3, 0.2, 0.3, 0);
                    break;
                }
            }
        }
    }

    // ─── 7. Trứng Nhiều của Gà ───────────────────────────────────

    @EventHandler
    public void onNemTrung(PlayerEggThrowEvent event) {
        if (!getMua().equals("xuan")) return;
        if (rng.nextInt(20) == 0) { // 5%
            event.setHatching(true);
            event.setNumHatches((byte) (3 + rng.nextInt(2)));
            event.getEgg().getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    event.getEgg().getLocation(), 10, 0.5, 0.5, 0.5, 0);
        }
    }

    // ─── 8. Câu cá Mùa Xuân — bỏ đồ rác ─────────────────────────

    @EventHandler
    public void onCauCaXuan(PlayerFishEvent event) {
        if (!getMua().equals("xuan")) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;
        if (isDoRac(caughtItem.getItemStack().getType())) {
            caughtItem.setItemStack(new ItemStack(Material.COD, 1));
        }
    }

    private boolean isDoRac(Material mat) {
        return switch (mat) {
            case STRING, LEATHER, ROTTEN_FLESH, STICK,
                 BOWL, WATER_BUCKET, INK_SAC, LILY_PAD -> true;
            default -> false;
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  ☀ MÙA HẠ — SỰ KIỆN
    // ══════════════════════════════════════════════════════════════

    // ─── 9. Hủy cháy nắng Zombie/Skeleton ────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobChayNang(EntityCombustByBlockEvent event) {
        if (!getMua().equals("ha")) return;
        if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobChayBiBlock(EntityCombustEvent event) {
        if (!getMua().equals("ha")) return;
        if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton) {
            event.setCancelled(true);
        }
    }

    // ─── 10. Đòn đánh Mùa Hạ gây cháy người chơi ───────────────

    @EventHandler
    public void onDonDanhTheoMua(EntityDamageByEntityEvent event) {
        String mua = getMua();

        // MÙA HẠ: Zombie/Skeleton đánh → người chơi bốc lửa (không cháy block)
        if (mua.equals("ha") && event.getEntity() instanceof Player player) {
            Entity damager = event.getDamager();
            boolean laMobNong = damager instanceof Zombie || damager instanceof Skeleton
                    || (damager instanceof Arrow a && (a.getShooter() instanceof Zombie
                    || a.getShooter() instanceof Skeleton));
            if (laMobNong) {
                player.setFireTicks(60); // 3 giây, KHÔNG dùng world.setBlock fire
            }
        }

        // MÙA ĐÔNG: Fire Aspect vs mob có giáp băng → x2 damage + tan giáp
        if (mua.equals("dong") && event.getDamager() instanceof Player player) {
            if (!(event.getEntity() instanceof LivingEntity target)) return;
            if (!icedMobs.contains(target.getUniqueId())) return;
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT) > 0) {
                event.setDamage(event.getDamage() * 2.0);
                icedMobs.remove(target.getUniqueId());
                target.getWorld().spawnParticle(Particle.CLOUD,
                        target.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.06);
                target.getWorld().playSound(target.getLocation(),
                        Sound.BLOCK_ICE_BREAK, 1.0f, 1.3f);
                player.sendMessage(Component.text("❄→🔥 Giáp băng tan vỡ! x2 sát thương!")
                        .color(NamedTextColor.AQUA));
            }
        }

        // MÙA THU: Slime bộc lộ đóng băng khi tấn công người chơi
        if (mua.equals("thu") && event.getDamager() instanceof Slime
                && event.getEntity() instanceof Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, true, false));
        }

        // MÙA THU: Iron Golem x3 knockback
        if (mua.equals("thu") && event.getDamager() instanceof IronGolem
                && event.getEntity() instanceof LivingEntity target) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!target.isValid()) return;
                    Vector v = target.getVelocity();
                    target.setVelocity(new Vector(v.getX() * 3, 0.9, v.getZ() * 3));
                    target.getWorld().spawnParticle(Particle.CRIT,
                            target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
                }
            }.runTaskLater(plugin, 1L);
        }

        // MÙA ĐÔNG: Slime đóng băng → gây Slowness khi tông trúng
        if (mua.equals("dong") && event.getDamager() instanceof Slime
                && event.getEntity() instanceof Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, true, true));
            player.setFreezeTicks(Math.min(player.getFreezeTicks() + 40, 140));
            player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05);
        }
    }

    // ─── 11. Creeper Dung Nham (Mùa Hạ) — nung chín block ───────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperDungNham(EntityExplodeEvent event) {
        if (!getMua().equals("ha")) return;
        if (!(event.getEntity() instanceof Creeper)) return;

        Location loc = event.getEntity().getLocation();
        List<Block> affected = new ArrayList<>(event.blockList());

        // HỦY phá block — chỉ giữ lại sóng nhiệt
        event.blockList().clear();

        // Nung chín block tự nhiên (không phá, chỉ biến đổi)
        for (Block block : affected) {
            Material result = nunChinBlock(block.getType());
            if (result != null) {
                block.setType(result);
                block.getWorld().spawnParticle(Particle.FLAME,
                        block.getLocation().add(0.5, 1, 0.5), 2, 0.2, 0.2, 0.2, 0.02);
            }
        }

        // Sóng nhiệt particle
        loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 1, 0), 30, 2, 2, 2, 0.1);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 50, 3, 1, 3, 0.15);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);

        // Gây sát thương người chơi/mob trong vùng (KHÔNG đốt block)
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 5, 4, 5)) {
            if (e instanceof Player p) p.damage(4.0);
            else if (e instanceof Monster m) m.damage(4.0);
        }
    }

    /** Trả về Material sau khi "nung chín". Null = không biến đổi. */
    private Material nunChinBlock(Material mat) {
        return switch (mat) {
            case SAND, RED_SAND -> Material.GLASS;
            case COBBLESTONE, COBBLED_DEEPSLATE -> Material.SMOOTH_STONE;
            case CLAY -> Material.TERRACOTTA;
            case GRAVEL -> Material.COARSE_DIRT;
            case STONE -> Material.SMOOTH_STONE;
            default -> null;
        };
    }

    // ─── 12. Phù Thủy Hỏa Thuật (Mùa Hạ) ────────────────────────

    @EventHandler
    public void onWitchPotion(ProjectileHitEvent event) {
        if (!getMua().equals("ha")) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (!(potion.getShooter() instanceof Witch)) return;

        Location hitLoc = event.getEntity().getLocation();
        World world = hitLoc.getWorld();

        // Vùng lửa tự tắt sau 5 giây — KHÔNG dùng fire block thật
        // Thay bằng damage area + particle
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 100) { cancel(); return; } // 5 giây
                ticks += 10;
                // Flame particle vùng lửa
                world.spawnParticle(Particle.FLAME, hitLoc, 20, 1.5, 0.3, 1.5, 0.05);
                world.spawnParticle(Particle.SMOKE, hitLoc.clone().add(0, 0.5, 0), 10, 1.2, 0.5, 1.2, 0.02);
                // Sát thương lên người chơi và mob trong bán kính 2.5
                for (Entity e : world.getNearbyEntities(hitLoc, 2.5, 2, 2.5)) {
                    if (e instanceof Player p) p.setFireTicks(40);
                    else if (e instanceof Monster m) m.damage(1.0);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);

        // Hủy hiệu ứng potion gốc (để tránh trùng)
        event.getEntity().remove();
    }

    // ─── 13. Mực Phát Sáng ban đêm ───────────────────────────────

    private void hienThiMucPhatSang() {
        for (World world : Bukkit.getWorlds()) {
            long time = world.getTime();
            if (time < 13000 || time > 23000) continue; // Chỉ ban đêm
            for (GlowSquid gs : world.getEntitiesByClass(GlowSquid.class)) {
                gs.setGlowing(true);
                world.spawnParticle(Particle.GLOW,
                        gs.getLocation().add(0, 0.5, 0), 5, 0.5, 0.5, 0.5, 0);
            }
        }
    }

    // ─── 14. Sữa Bò Mùa Hạ → Speed I (20s) ──────────────────────

    @EventHandler
    public void onUongSuaMuaHa(PlayerItemConsumeEvent event) {
        if (!getMua().equals("ha")) return;
        if (event.getItem().getType() != Material.MILK_BUCKET) return;
        new BukkitRunnable() {
            @Override public void run() {
                event.getPlayer().addPotionEffect(
                        new PotionEffect(PotionEffectType.SPEED, 400, 0, true, true));
                event.getPlayer().sendMessage(
                        Component.text("🥛 Sữa mùa hạ đặc biệt! +Tốc Độ I (20 giây)!")
                                .color(NamedTextColor.YELLOW));
            }
        }.runTaskLater(plugin, 1L);
    }

    // ══════════════════════════════════════════════════════════════
    //  🍂 MÙA THU — SỰ KIỆN
    // ══════════════════════════════════════════════════════════════

    // ─── 15. Đống Lá Giảm Sát Thương Rơi ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onRoiVaoLa(EntityDamageEvent event) {
        if (!getMua().equals("thu")) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Block duoi = player.getLocation().subtract(0, 1, 0).getBlock();
        if (!isLaBlock(duoi.getType())) return;

        event.setCancelled(true);
        // Lá bay tung tóe
        duoi.getWorld().spawnParticle(Particle.DUST,
                duoi.getLocation().add(0.5, 1.2, 0.5),
                25, 1.0, 0.3, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(200, 100, 30), 1.3f));
        duoi.getWorld().spawnParticle(Particle.DUST,
                duoi.getLocation().add(0.5, 1.2, 0.5),
                15, 1.0, 0.3, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(220, 50, 20), 1.1f));
        duoi.getWorld().playSound(duoi.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.8f, 0.7f);
    }

    private boolean isLaBlock(Material mat) {
        return switch (mat) {
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, AZALEA_LEAVES,
                 FLOWERING_AZALEA_LEAVES, CHERRY_LEAVES, MANGROVE_LEAVES -> true;
            default -> false;
        };
    }

    // ─── 16. Gió Lốc ─────────────────────────────────────────────

    private void apDungGioLoc() {
        long now = System.currentTimeMillis();
        if (now - lastWindGust < 8000) return; // Tối thiểu 8s giữa các cơn gió
        lastWindGust = now;

        double angle = rng.nextDouble(Math.PI * 2);
        double force = 0.25 + rng.nextDouble(0.3);
        Vector windVec = new Vector(Math.cos(angle) * force, 0.08, Math.sin(angle) * force);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!getMua().equals("thu")) return;
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.WEATHER_WIND, 0.5f, 0.85f);
            // Đẩy người chơi
            player.setVelocity(player.getVelocity().add(windVec));
            // Particle gió
            world.spawnParticle(Particle.CLOUD,
                    player.getLocation().add(0, 1, 0), 12, 1.5, 0.5, 1.5, 0.06);
            // Đẩy mob gần
            for (Entity e : player.getNearbyEntities(15, 10, 15)) {
                if (e instanceof LivingEntity && !(e instanceof Player)) {
                    e.setVelocity(e.getVelocity().add(windVec.clone().multiply(0.6)));
                }
            }
        }
    }

    // ─── 17. Enderman Lá Khô ─────────────────────────────────────

    /** Tắt bê block của Enderman. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanBeMuaThu(EntityChangeBlockEvent event) {
        if (!getMua().equals("thu")) return;
        if (event.getEntity() instanceof Enderman) event.setCancelled(true);
    }

    /** Khi người chơi nhìn/nhắm vào Enderman → teleport ra sau lưng + Blindness ngắn. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanMuaThu(EntityTargetLivingEntityEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getEntity() instanceof Enderman enderman)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        event.setCancelled(true);
        new BukkitRunnable() {
            @Override public void run() {
                if (!enderman.isValid()) return;
                // Teleport ra sau lưng người chơi
                Vector behind = player.getLocation().getDirection().multiply(-3.5);
                Location behindLoc = player.getLocation().clone().add(behind);
                behindLoc.setYaw(player.getLocation().getYaw() + 180);
                enderman.teleport(behindLoc);
                // Jumpscare
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0));
                enderman.getWorld().spawnParticle(Particle.PORTAL,
                        enderman.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.5);
                enderman.getWorld().playSound(enderman.getLocation(),
                        Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.85f);
            }
        }.runTaskLater(plugin, 8L);
    }

    // ─── 18. Phantom Sát Thủ Lặng Im ─────────────────────────────

    @EventHandler
    public void onPhantomTanCong(EntityDamageByEntityEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getDamager() instanceof Phantom phantom)) return;
        World world = phantom.getWorld();
        // Chỉ trong điều kiện sương mù (mưa hoặc gần bình minh/hoàng hôn)
        long t = world.getTime();
        boolean sươngMu = world.hasStorm() || (t > 12000 && t < 14000) || (t > 22000);
        if (!sươngMu) return;
        // Tắt âm thanh rít bằng cách hủy âm thanh tiếp theo
        // (Không có API trực tiếp, nhưng ta có thể spawn particle thay thế âm thanh)
        world.spawnParticle(Particle.DUST,
                phantom.getLocation().add(0, 0.5, 0), 10, 0.5, 0.3, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(120, 100, 60), 1.0f));
    }

    // ─── 19. Nhện Bẫy — Alert Skeleton ───────────────────────────

    @EventHandler
    public void onNhenBay(EntityDamageByEntityEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getDamager() instanceof Spider)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // Chậm người chơi
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, true, false));
        // Kích hoạt Skeleton gần nhất
        for (Entity e : player.getNearbyEntities(15, 10, 15)) {
            if (e instanceof Skeleton sk) sk.setTarget(player);
        }
    }

    // ─── 20. Cáo Tìm Kho Báu ─────────────────────────────────────

    private void caoDaoBau() {
        for (World world : Bukkit.getWorlds()) {
            for (Fox fox : world.getEntitiesByClass(Fox.class)) {
                if (!fox.isTamed()) continue;
                Location foxLoc = fox.getLocation();
                outerLoop:
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        Block block = world.getBlockAt(
                                foxLoc.getBlockX() + x, foxLoc.getBlockY(), foxLoc.getBlockZ() + z);
                        if (block.getType() != Material.COARSE_DIRT) continue;
                        // Hiệu ứng đào
                        world.spawnParticle(Particle.DUST_PLUME,
                                block.getLocation().add(0.5, 0.8, 0.5), 10, 0.4, 0.4, 0.4, 0.1);
                        world.playSound(block.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.8f, 1.0f);
                        // Drop loot
                        world.dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), getLootCao());
                        block.setType(Material.DIRT);
                        break outerLoop;
                    }
                }
            }
        }
    }

    private ItemStack getLootCao() {
        return switch (rng.nextInt(4)) {
            case 0 -> new ItemStack(Material.IRON_NUGGET, 2 + rng.nextInt(5));
            case 1 -> new ItemStack(Material.GOLD_NUGGET, 1 + rng.nextInt(4));
            case 2 -> new ItemStack(Material.EMERALD, 1);
            default -> {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(Enchantment.UNBREAKING, 1 + rng.nextInt(2), false);
                book.setItemMeta(meta);
                yield book;
            }
        };
    }

    // ─── 21. Dân Làng Nông Dân Giảm Giá 70% ─────────────────────

    @EventHandler
    public void onMoCuaHangNongDan(InventoryOpenEvent event) {
        if (!getMua().equals("thu")) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Villager villager)) return;
        if (villager.getProfession() != Villager.Profession.FARMER) return;

        List<MerchantRecipe> original = villager.getRecipes();
        List<MerchantRecipe> discounted = new ArrayList<>();

        for (MerchantRecipe r : original) {
            List<ItemStack> ings = r.getIngredients();
            List<ItemStack> newIngs = new ArrayList<>();
            for (ItemStack ing : ings) {
                if (ing != null) {
                    ItemStack d = ing.clone();
                    d.setAmount(Math.max(1, (int) Math.ceil(ing.getAmount() * 0.30)));
                    newIngs.add(d);
                } else newIngs.add(null);
            }
            MerchantRecipe nr = new MerchantRecipe(
                    r.getResult(), r.getUses(), r.getMaxUses(),
                    r.hasExperienceReward(), r.getVillagerExperience(),
                    r.getPriceMultiplier(), r.getDemand(), r.getSpecialPrice());
            nr.setIngredients(newIngs);
            discounted.add(nr);
        }
        villager.setRecipes(discounted);
        player.sendMessage(Component.text("🍂 Nông dân mùa thu: Giảm giá 70% tất cả giao dịch!")
                .color(NamedTextColor.GOLD));
    }

    // ══════════════════════════════════════════════════════════════
    //  ❄ MÙA ĐÔNG — SỰ KIỆN
    // ══════════════════════════════════════════════════════════════

    // ─── 22. Spawn Mob theo mùa ───────────────────────────────────

    @EventHandler
    public void onMobSpawnTheoMua(CreatureSpawnEvent event) {
        String mua = getMua();

        // ── Mùa Xuân: Ong spawn thêm x3 ─────────────────────────
        if (mua.equals("xuan") && event.getEntity() instanceof Bee bee) {
            if (rng.nextInt(3) == 0) {
                new BukkitRunnable() {
                    @Override public void run() {
                        for (int i = 0; i < 2; i++) {
                            bee.getWorld().spawnEntity(bee.getLocation().add(
                                    rng.nextInt(5) - 2, 0, rng.nextInt(5) - 2), EntityType.BEE);
                        }
                    }
                }.runTaskLater(plugin, 5L);
            }
        }

        // ── Mùa Đông: Zombie/Skeleton → giáp băng ────────────────
        if (mua.equals("dong")) {
            if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton) {
                icedMobs.add(event.getEntity().getUniqueId());
            }

            // Wither Skeleton spawn ngoài trời khi bão tuyết (2%)
            if (event.getEntity() instanceof Skeleton
                    && event.getEntity().getWorld().hasStorm()
                    && event.getEntity().getWorld().getEnvironment() == World.Environment.NORMAL
                    && rng.nextInt(50) == 0) {
                Location loc = event.getEntity().getLocation();
                event.getEntity().remove();
                loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON);
                return;
            }

            // Sói spawn thêm theo đàn (1-2 con nữa)
            if (event.getEntity() instanceof Wolf wolf && !wolf.isTamed()
                    && rng.nextBoolean()) {
                new BukkitRunnable() {
                    @Override public void run() {
                        int count = 1 + rng.nextInt(2);
                        for (int i = 0; i < count; i++) {
                            wolf.getWorld().spawnEntity(wolf.getLocation().add(
                                    rng.nextInt(7) - 3, 0, rng.nextInt(7) - 3), EntityType.WOLF);
                        }
                    }
                }.runTaskLater(plugin, 5L);
            }
        }
    }

    // ─── 23. Giáp Băng Giảm Damage 50% ──────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGiapBangNhanDamage(EntityDamageEvent event) {
        if (!getMua().equals("dong")) return;
        if (!icedMobs.contains(event.getEntity().getUniqueId())) return;

        // Lửa → tan giáp
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            icedMobs.remove(event.getEntity().getUniqueId());
            event.getEntity().getWorld().spawnParticle(Particle.CLOUD,
                    event.getEntity().getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.07);
            event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                    Sound.BLOCK_ICE_BREAK, 1.0f, 1.2f);
            return;
        }

        // Damage vật lý → giảm 50%
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    // ─── 24. Sói Đầu Đàn: Thuần Hóa cả đàn bằng thịt chín ───────

    @EventHandler
    public void onChoSoiAnThit(PlayerInteractEntityEvent event) {
        if (!getMua().equals("dong")) return;
        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        if (wolf.isTamed()) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean laThitChin = switch (hand.getType()) {
            case COOKED_BEEF, COOKED_PORKCHOP, COOKED_MUTTON,
                 COOKED_CHICKEN, COOKED_RABBIT -> true;
            default -> false;
        };
        if (!laThitChin) return;

        event.setCancelled(true);
        // Thuần hóa Sói Đầu Đàn
        wolf.setTamed(true);
        wolf.setOwner(player);
        wolf.getWorld().spawnParticle(Particle.HEART,
                wolf.getLocation().add(0, 1.5, 0), 5, 0.5, 0.3, 0.5, 0);

        // Thuần hóa toàn bộ sói hoang trong bán kính 10
        int count = 0;
        for (Entity e : wolf.getNearbyEntities(10, 5, 10)) {
            if (!(e instanceof Wolf w) || w.isTamed()) continue;
            w.setTamed(true);
            w.setOwner(player);
            w.getWorld().spawnParticle(Particle.HEART, w.getLocation().add(0, 1, 0), 3, 0.3, 0.2, 0.3, 0);
            count++;
        }

        // Tiêu thụ thịt
        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        player.sendMessage(Component.text("❄ Đàn sói thuần phục! +" + count + " con theo bạn!")
                .color(NamedTextColor.AQUA));
    }

    // ─── 25. Sói Hoang Tấn Công Người Đi Một Mình ────────────────

    @EventHandler
    public void onSoiNhamNguoiDoc(EntityTargetLivingEntityEvent event) {
        if (!getMua().equals("dong")) return;
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.isTamed()) return;
        if (!(event.getTarget() instanceof Player player)) return;

        // Chỉ tấn công nếu người chơi đi một mình (không có người chơi khác trong 20 block)
        long banBe = player.getNearbyEntities(20, 10, 20).stream()
                .filter(e -> e instanceof Player).count();
        if (banBe > 0) event.setCancelled(true);
    }

    // ─── 26. Gió Luồng: Boost Elytra hoặc cầm Lá (Mùa Thu) ──────

    @EventHandler
    public void onPlayerDiChuyenGio(PlayerMoveEvent event) {
        if (!getMua().equals("thu")) return;
        Player player = event.getPlayer();

        // Phải đang bay Elytra hoặc cầm lá trên tay
        boolean daBay = player.isGliding();
        boolean camLa = isLaItem(player.getInventory().getItemInMainHand().getType());
        if (!daBay && !camLa) return;

        // Nếu có activeJukeboxes thì bỏ qua (tránh conflict)
        // Tăng tốc nếu đang trong vùng gió
        if (!activeJukeboxes.isEmpty()) return;
        // Chỉ boost nếu đang di chuyển theo hướng nhất định
        if (player.getVelocity().length() > 0.5) {
            Vector boost = player.getLocation().getDirection().multiply(0.3);
            player.setVelocity(player.getVelocity().add(boost));
        }
    }

    private boolean isLaItem(Material mat) {
        return switch (mat) {
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, CHERRY_LEAVES, MANGROVE_LEAVES -> true;
            default -> false;
        };
    }
}
