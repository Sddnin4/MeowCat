package me.tuicode.thoitietbonmua;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * ╔══════════════════════════════════════════════════════╗
 *   ThoiTietBonMua v2.1 — Paper 1.21
 *   Tác giả: Xnos  |  Nâng cấp: Claude
 * ╠══════════════════════════════════════════════════════╣
 *   Chu kỳ 360 ngày / 4 mùa / 90 ngày mỗi mùa
 *
 *   XUÂN (ngày 1–90)
 *     • Particle HAPPY_VILLAGER, cây x2, sinh sản x2
 *     • Luck V liên tục
 *     • Bee spawn nhiều hơn
 *     • Bone-meal cỏ tự động bán kính 16
 *     • Shader: trời trong, particle hoa xanh lá
 *
 *   HẠ (ngày 91–180)
 *     • Đói dưới nắng, quái đốt cháy người chơi 4s
 *     • Giải nhiệt: nước / uống Water Potion → Speed II 10s
 *     • Tốc độ đói x2 (Food exhaustion tăng)
 *     • Sét mùa hạ: xác suất nhỏ rơi gần người, 2 tim damage, không cháy nhà
 *     • Tăng tốc cây x1.5 nếu Farmland ướt, chết nếu khô
 *     • Magma Cube spawn ngoài trời ban đêm
 *     • Shader: trời nắng rực, particle tia lửa nhỏ
 *
 *   THU (ngày 181–270)
 *     • Thu hoạch Wheat x2 drop
 *     • Mưa axit khi trời mưa: 0.5 tim / 5 giây ngoài trời (không chết)
 *     • Witch tăng spawn 30%
 *     • Shader: particle lá cam/đỏ dày đặc, mưa nhẹ
 *
 *   ĐÔNG (ngày 271–360)
 *     • Đông cứng ngoài trời (FreezeTicks), giáp da + lửa bảo vệ
 *     • Quái: Slowness II 5s + +60 FreezeTicks
 *     • Mining Fatigue I khi FreezeTicks > 70
 *     • Cây chết 5% nếu không có mái che
 *     • Stray thay Skeleton ngoài trời
 *     • Sương mù ban đêm: particle CLOUD dày quanh người
 *     • Shader: trời tuyết, particle SNOWFLAKE liên tục
 *
 *   CHUYỂN MÙA: Title toàn server + Sound + broadcast khi bước sang mùa mới
 * ╚══════════════════════════════════════════════════════╝
 */
public class ThoiTietBonMua extends JavaPlugin implements Listener, CommandExecutor {

    // ══════════════════════════════════════════════════════════════
    //  HẰNG SỐ
    // ══════════════════════════════════════════════════════════════

    private static final int  NGAY_MOI_MUA      = 90;
    private static final int  TONG_NGAY_CHU_KY  = 360;
    private static final long TICKS_MOI_NGAY    = 24000L;

    // Mưa axit: kiểm tra mỗi 5 giây = 100 ticks
    private static final int TICKS_MUA_AXIT     = 100;

    // ══════════════════════════════════════════════════════════════
    //  TRẠNG THÁI
    // ══════════════════════════════════════════════════════════════

    private final Random random = new Random();

    /** Biến ép mùa của Admin. null = tự động theo ngày. */
    private String overrideMua = null;

    /** Ghi nhớ mùa trước để phát hiện khi nào chuyển mùa mới. */
    private String muaTruoc = null;

    /** Đếm ticks để trigger mưa axit đúng chu kỳ 5 giây. */
    private int tickerMuaAxit = 0;

    /** Đếm ticks để trigger bone-meal mùa xuân (mỗi 3 giây = 60 ticks). */
    private int tickerBoneMeal = 0;

    /** Đếm ticks để trigger sét mùa hạ (mỗi 10 giây = 200 ticks). */
    private int tickerSet = 0;

    // ══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("  ThoiTietBonMua v2.1 đã khởi động!");
        getLogger().info("╚══════════════════════════════════════╝");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("season") != null) {
            getCommand("season").setExecutor(this);
        }

        chayHeThongCore();
    }

    @Override
    public void onDisable() {
        getLogger().info("ThoiTietBonMua v2.1 đã tắt.");
    }

    // ══════════════════════════════════════════════════════════════
    //  VÒNG LẶP CHÍNH (20 ticks = 1 giây thực)
    // ══════════════════════════════════════════════════════════════

    private void chayHeThongCore() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickerMuaAxit++;
                tickerBoneMeal++;
                tickerSet++;

                World world = getServer().getWorlds().get(0);
                String muaHienTai = getMuaHienTai(world);

                // ── Phát hiện chuyển mùa ──
                kiemTraChuyenMua(world, muaHienTai);

                // ── Thời tiết + shader Bukkit theo mùa ──
                apDungThoiTietMua(world, muaHienTai);

                // ── Bone-meal mùa xuân (mỗi 3 giây) ──
                if (muaHienTai.equals("xuan") && tickerBoneMeal >= 60) {
                    tickerBoneMeal = 0;
                    boneMealMuaXuan(world);
                }

                // ── Sét mùa hạ (mỗi 10 giây, xác suất) ──
                if (muaHienTai.equals("ha") && tickerSet >= 200) {
                    tickerSet = 0;
                    setMuaHa(world);
                }

                // ── Xử lý từng người chơi ──
                for (Player player : getServer().getOnlinePlayers()) {
                    if (!player.getWorld().equals(world)) {
                        world = player.getWorld();
                        muaHienTai = getMuaHienTai(world);
                    }

                    // Particle + hiệu ứng cá nhân theo mùa
                    apDungHieuUngNguoiChoi(player, world, muaHienTai);

                    // Giải nhiệt mùa hạ khi vào nước
                    kiemTraGiaiNhietNuoc(player, world, muaHienTai);

                    // Mưa axit mùa thu (mỗi 5 giây)
                    if (muaHienTai.equals("thu") && tickerMuaAxit >= TICKS_MUA_AXIT) {
                        apDungMuaAxit(player, world);
                    }

                    // Mệt mỏi đông cứng
                    apDungMiningFatigueDong(player, muaHienTai);

                    // Tốc độ đói gấp đôi mùa hạ
                    apDungDoiMuaHa(player, world, muaHienTai);

                    // Scoreboard
                    capNhatScoreboard(player, world, muaHienTai);
                }

                // Reset ticker mưa axit sau khi dùng xong
                if (tickerMuaAxit >= TICKS_MUA_AXIT) tickerMuaAxit = 0;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ══════════════════════════════════════════════════════════════
    //  TÍNH MÙA
    // ══════════════════════════════════════════════════════════════

    private String getMuaHienTai(World world) {
        if (overrideMua != null) return overrideMua;
        long ngayTrongNam = getNgayTrongNam(world);
        if (ngayTrongNam < NGAY_MOI_MUA)      return "xuan";
        if (ngayTrongNam < NGAY_MOI_MUA * 2)  return "ha";
        if (ngayTrongNam < NGAY_MOI_MUA * 3)  return "thu";
        return "dong";
    }

    private long getNgayTrongNam(World world) {
        return (world.getFullTime() / TICKS_MOI_NGAY) % TONG_NGAY_CHU_KY;
    }

    private long getNgayTrongMua(World world) {
        if (overrideMua != null) return 1;
        return (getNgayTrongNam(world) % NGAY_MOI_MUA) + 1;
    }

    private long getNgayConLai(World world) {
        if (overrideMua != null) return 0;
        return NGAY_MOI_MUA - getNgayTrongMua(world);
    }

    private long getTongSoNgayMap(World world) {
        return world.getFullTime() / TICKS_MOI_NGAY;
    }

    // ══════════════════════════════════════════════════════════════
    //  CHUYỂN MÙA — TITLE + SOUND + BROADCAST
    // ══════════════════════════════════════════════════════════════

    /**
     * Phát hiện thời điểm chuyển sang mùa mới.
     * Phát Title toàn server, âm thanh ấn tượng và broadcast chat.
     */
    private void kiemTraChuyenMua(World world, String muaHienTai) {
        if (muaTruoc == null) {
            muaTruoc = muaHienTai;
            return;
        }
        if (muaTruoc.equals(muaHienTai)) return;

        // Vừa chuyển sang mùa mới!
        muaTruoc = muaHienTai;

        String tenMuaMoi;
        NamedTextColor mauTitle;
        Sound amThanh;

        switch (muaHienTai) {
            case "xuan" -> {
                tenMuaMoi  = "🌸  MÙA XUÂN  🌸";
                mauTitle   = NamedTextColor.GREEN;
                amThanh    = Sound.ENTITY_PLAYER_LEVELUP;
            }
            case "ha" -> {
                tenMuaMoi  = "☀  MÙA HẠ  ☀";
                mauTitle   = NamedTextColor.GOLD;
                amThanh    = Sound.AMBIENT_BASALT_DELTAS_MOOD;
            }
            case "thu" -> {
                tenMuaMoi  = "🍂  MÙA THU  🍂";
                mauTitle   = NamedTextColor.RED;
                amThanh    = Sound.AMBIENT_CAVE;
            }
            default -> {
                tenMuaMoi  = "❄  MÙA ĐÔNG  ❄";
                mauTitle   = NamedTextColor.AQUA;
                amThanh    = Sound.AMBIENT_UNDERWATER_LOOP;
            }
        }

        // Title + subtitle cho từng người chơi
        Component titleComp    = Component.text(tenMuaMoi)
                .color(mauTitle).decorate(TextDecoration.BOLD);
        Component subtitleComp = Component.text("Vạn vật đã thay đổi theo dòng chảy thời gian...")
                .color(NamedTextColor.WHITE);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(500),   // fade in
                Duration.ofMillis(3000),  // stay
                Duration.ofMillis(1000)   // fade out
        );
        Title title = Title.title(titleComp, subtitleComp, times);

        Sound finalAmThanh = amThanh;
        for (Player p : getServer().getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), finalAmThanh, 1.0f, 1.0f);
        }

        // Broadcast chat
        getServer().broadcast(Component.text(
                "═══════════════════════════════════════"
        ).color(NamedTextColor.DARK_GRAY));
        getServer().broadcast(Component.text(
                "  🌍  Thời gian chuyển dịch! Server bước vào " + tenMuaMoi
        ).color(mauTitle).decorate(TextDecoration.BOLD));
        getServer().broadcast(Component.text(
                "═══════════════════════════════════════"
        ).color(NamedTextColor.DARK_GRAY));

        getLogger().info("[ChuyenMua] Bước vào " + muaHienTai.toUpperCase());
    }

    // ══════════════════════════════════════════════════════════════
    //  THỜI TIẾT + SHADER THEO MÙA
    // ══════════════════════════════════════════════════════════════

    /**
     * Điều khiển thời tiết Bukkit theo mùa (rain/thunder/clear).
     * Kết hợp với particle để tạo "shader" thị giác riêng từng mùa.
     *
     * XUÂN : trời trong sáng, không mưa
     * HẠ   : trời nắng rực, không mưa, thỉnh thoảng sấm (không mưa) tạo sét
     * THU  : mưa nhẹ xác suất cao
     * ĐÔNG : mưa/tuyết nặng, thỉnh thoảng bão
     */
    private void apDungThoiTietMua(World world, String mua) {
        switch (mua) {
            case "xuan" -> {
                // Xuân: trời luôn trong
                if (world.hasStorm())     world.setStorm(false);
                if (world.isThundering()) world.setThundering(false);
            }
            case "ha" -> {
                // Hạ: trời nắng rực, không mưa thường
                // Sét sẽ được xử lý riêng qua setMuaHa() không dùng thundering
                if (world.hasStorm())     world.setStorm(false);
                if (world.isThundering()) world.setThundering(false);
            }
            case "thu" -> {
                // Thu: xác suất mưa nhẹ cao (1/60 = ~mỗi phút)
                if (!world.hasStorm() && random.nextInt(60) == 0) {
                    world.setStorm(true);
                    world.setWeatherDuration(2400); // 2 phút thực
                }
                // Tắt bão nếu đang sấm sét (chỉ mưa thường)
                if (world.isThundering()) world.setThundering(false);
            }
            case "dong" -> {
                // Đông: gần như luôn mưa tuyết
                if (!world.hasStorm() && random.nextInt(30) == 0) {
                    world.setStorm(true);
                    world.setWeatherDuration(6000); // 5 phút thực
                }
                // Thỉnh thoảng có bão (1/300)
                if (!world.isThundering() && random.nextInt(300) == 0) {
                    world.setThundering(true);
                    world.setThunderDuration(1200);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HIỆU ỨNG NGƯỜI CHƠI THEO MÙA (PARTICLE + POTION)
    // ══════════════════════════════════════════════════════════════

    private void apDungHieuUngNguoiChoi(Player player, World world, String mua) {
        Location loc = player.getLocation();
        switch (mua) {
            case "xuan" -> hieuUngXuan(player, loc);
            case "ha"   -> hieuUngHa(player, loc, world);
            case "thu"  -> hieuUngThu(player, loc);
            case "dong" -> hieuUngDong(player, loc, world);
        }
    }

    // ─── MÙA XUÂN ────────────────────────────────────────────────────────

    /**
     * Shader Xuân:
     *   - Particle HAPPY_VILLAGER (hoa nhỏ) bay quanh người
     *   - Particle COMPOSTER (xanh lá nhạt) từ dưới đất lên
     *   - Luck V liên tục (thay Luck I của v2.0)
     */
    private void hieuUngXuan(Player player, Location loc) {
        // Hạt hoa mùa xuân bay xung quanh
        for (int i = 0; i < 4; i++) {
            double ox = (random.nextDouble() - 0.5) * 2.5;
            double oy = random.nextDouble() * 2.2;
            double oz = (random.nextDouble() - 0.5) * 2.5;
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    loc.clone().add(ox, oy, oz), 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Particle xanh từ mặt đất — gợi ý chồi non
        for (int i = 0; i < 2; i++) {
            double ox = (random.nextDouble() - 0.5) * 3.0;
            double oz = (random.nextDouble() - 0.5) * 3.0;
            loc.getWorld().spawnParticle(Particle.COMPOSTER,
                    loc.clone().add(ox, 0.1, oz), 1, 0.0, 0.3, 0.0, 0.0);
        }

        // Luck V — phúc lớn mùa xuân
        player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 60, 4, true, false));

        player.sendActionBar(Component.text("🌸 Mùa Xuân | Phúc lành tràn đầy — Vạn vật sinh sôi!")
                .color(NamedTextColor.GREEN));
    }

    // ─── MÙA HẠ ──────────────────────────────────────────────────────────

    /**
     * Shader Hạ:
     *   - Particle SMALL_FLAME (tia lửa nhỏ) xung quanh khi đứng ngoài nắng
     *   - WHITE_SMOKE phía trên đầu (hơi nhiệt)
     *   - Đói I dưới nắng
     */
    private void hieuUngHa(Player player, Location loc, World world) {
        if (isDungDuoiTroiNang(player, world)) {
            // Hơi nóng: khói trắng trên đầu
            for (int i = 0; i < 2; i++) {
                double ox = (random.nextDouble() - 0.5) * 1.5;
                double oz = (random.nextDouble() - 0.5) * 1.5;
                loc.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                        loc.clone().add(ox, 2.1, oz), 1);
            }
            // Tia lửa nhỏ quanh người
            for (int i = 0; i < 2; i++) {
                double ox = (random.nextDouble() - 0.5) * 1.8;
                double oy = random.nextDouble() * 1.5;
                double oz = (random.nextDouble() - 0.5) * 1.8;
                loc.getWorld().spawnParticle(Particle.SMALL_FLAME,
                        loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0);
            }

            // Đói I
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 80, 0, true, false));

            player.sendActionBar(Component.text("☀ Mùa Hạ | Nóng bức! Nhảy xuống nước hoặc uống nước giải nhiệt!")
                    .color(NamedTextColor.GOLD));
        } else {
            player.sendActionBar(Component.text("☀ Mùa Hạ | Mát mẻ dưới bóng râm.")
                    .color(NamedTextColor.YELLOW));
        }
    }

    // ─── MÙA THU ─────────────────────────────────────────────────────────

    /**
     * Shader Thu:
     *   - DUST màu cam/đỏ (lá rụng) dày đặc hơn v2.0
     *   - Khi mưa: thêm FALLING_WATER quanh người giả lập mưa axit
     */
    private void hieuUngThu(Player player, Location loc) {
        // Lá rụng mùa thu: màu cam + đỏ + vàng xen kẽ
        int rand3 = random.nextInt(3);
        Color mauLa = switch (rand3) {
            case 0  -> Color.fromRGB(210, 90, 10);   // cam đậm
            case 1  -> Color.fromRGB(180, 30, 10);   // đỏ lá
            default -> Color.fromRGB(220, 160, 20);  // vàng thu
        };
        Particle.DustOptions dust = new Particle.DustOptions(mauLa, 1.3f);
        for (int i = 0; i < 5; i++) {
            double ox = (random.nextDouble() - 0.5) * 4.0;
            double oy = random.nextDouble() * 3.5;
            double oz = (random.nextDouble() - 0.5) * 4.0;
            loc.getWorld().spawnParticle(Particle.DUST,
                    loc.clone().add(ox, oy, oz), 1, dust);
        }

        // Khi trời mưa: FALLING_WATER nhỏ giọt để gợi mưa axit
        if (loc.getWorld().hasStorm()) {
            for (int i = 0; i < 3; i++) {
                double ox = (random.nextDouble() - 0.5) * 3.0;
                double oz = (random.nextDouble() - 0.5) * 3.0;
                loc.getWorld().spawnParticle(Particle.FALLING_WATER,
                        loc.clone().add(ox, 2.5, oz), 1);
            }
            player.sendActionBar(Component.text("🍂 Mùa Thu | Mưa axit! Vào nhà ngay!")
                    .color(NamedTextColor.RED));
        } else {
            player.sendActionBar(Component.text("🍂 Mùa Thu | Lá vàng rơi — Mùa bội thu!")
                    .color(NamedTextColor.GOLD));
        }
    }

    // ─── MÙA ĐÔNG ────────────────────────────────────────────────────────

    /**
     * Shader Đông:
     *   - SNOWFLAKE liên tục dày đặc (tuyết rơi)
     *   - Ban đêm: CLOUD dày xung quanh (sương mù)
     *   - FreezeTicks tăng ngoài trời nếu không có bảo vệ
     */
    private void hieuUngDong(Player player, Location loc, World world) {
        // Tuyết rơi luôn luôn
        for (int i = 0; i < 5; i++) {
            double ox = (random.nextDouble() - 0.5) * 4.0;
            double oy = random.nextDouble() * 4.0;
            double oz = (random.nextDouble() - 0.5) * 4.0;
            loc.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    loc.clone().add(ox, oy, oz), 1, 0, -0.1, 0, 0);
        }

        // Sương mù ban đêm: CLOUD dày
        long timeOfDay = world.getTime();
        if (timeOfDay >= 13000 || timeOfDay < 1000) {
            for (int i = 0; i < 6; i++) {
                double ox = (random.nextDouble() - 0.5) * 5.0;
                double oy = (random.nextDouble() - 0.5) * 1.5 + 1.0;
                double oz = (random.nextDouble() - 0.5) * 5.0;
                loc.getWorld().spawnParticle(Particle.CLOUD,
                        loc.clone().add(ox, oy, oz), 1, 0.05, 0.02, 0.05, 0.01);
            }
        }

        // Logic đông cứng
        boolean macDoDa       = checkMacDoDa(player);
        boolean ganNguonNhiet = checkGanNguonNhiet(player);

        if (macDoDa || ganNguonNhiet) {
            player.setFreezeTicks(0);
            String lyDo = macDoDa ? "Giáp da giữ ấm" : "Gần nguồn nhiệt";
            player.sendActionBar(Component.text("❄ Mùa Đông | " + lyDo + " — An toàn!")
                    .color(NamedTextColor.AQUA));
        } else {
            int freeze = Math.min(player.getFreezeTicks() + 5, 140);
            player.setFreezeTicks(freeze);
            if (timeOfDay >= 13000 || timeOfDay < 1000) {
                player.sendActionBar(Component.text("❄ Mùa Đông | Sương mù lạnh giá bao phủ! Mặc giáp da hoặc đứng gần lửa!")
                        .color(NamedTextColor.RED));
            } else {
                player.sendActionBar(Component.text("❄ Mùa Đông | Lạnh buốt xương! Tìm nguồn nhiệt ngay!")
                        .color(NamedTextColor.RED));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MINING FATIGUE KHI ĐÔNG CỨNG CAO
    // ══════════════════════════════════════════════════════════════

    /**
     * Khi FreezeTicks > 70 vào mùa đông → Mining Fatigue I (cơ thể tê cóng, đào chậm).
     * Tự hết ngay khi FreezeTicks về 0.
     */
    private void apDungMiningFatigueDong(Player player, String mua) {
        if (!mua.equals("dong")) {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            return;
        }
        if (player.getFreezeTicks() > 70) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0, true, false));
        } else {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MƯA AXIT MÙA THU (mỗi 5 giây)
    // ══════════════════════════════════════════════════════════════

    /**
     * Khi trời mưa mùa thu, người đứng ngoài trời (không có mái che) bị
     * 0.5 tim damage (1 HP). Không bao giờ membunuh: kiểm tra health > 1.0.
     */
    private void apDungMuaAxit(Player player, World world) {
        if (!world.hasStorm()) return;
        if (player.isInWater()) return;

        Location loc = player.getLocation();
        // Kiểm tra có mái che không (highest block tại vị trí)
        int highestY = world.getHighestBlockYAt(loc);
        if (loc.getBlockY() < highestY) return; // có mái che

        // Không gây chết: chỉ damage nếu health > 1
        if (player.getHealth() > 1.0) {
            player.damage(1.0); // 0.5 tim = 1 HP
            player.sendActionBar(Component.text("🌧 Mưa Axit! Vào mái che ngay!")
                    .color(NamedTextColor.DARK_RED));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  TỐC ĐỘ ĐÓI X2 MÙA HẠ
    // ══════════════════════════════════════════════════════════════

    /**
     * Mùa hạ: tăng food exhaustion thêm 0.1 mỗi giây để thực phẩm cạn nhanh hơn.
     * Chỉ áp dụng khi người chơi đứng ngoài trời.
     */
    private void apDungDoiMuaHa(Player player, World world, String mua) {
        if (!mua.equals("ha")) return;
        if (!isDungDuoiTroiNang(player, world)) return;
        // Thêm exhaustion: vanilla max = 4.0 → saturation cạn. Mỗi giây thêm 0.1 extra
        player.setExhaustion(Math.min(player.getExhaustion() + 0.1f, 4.0f));
    }

    // ══════════════════════════════════════════════════════════════
    //  SÉT MÙA HẠ (không cháy nhà)
    // ══════════════════════════════════════════════════════════════

    /**
     * Mỗi 10 giây, xác suất 5% sét rơi gần một người chơi ngẫu nhiên đứng ngoài trời.
     * Sét dùng strikeLightningEffect() — KHÔNG gây cháy block, không gây ung tích.
     * Người chơi trong bán kính 5 block nhận 2 tim damage (4 HP).
     */
    private void setMuaHa(World world) {
        // 5% xác suất mỗi 10 giây
        if (random.nextInt(20) != 0) return;

        // Chọn ngẫu nhiên một người chơi đứng ngoài trời
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) continue;
            if (!isDungDuoiTroiNang(player, world)) continue;

            // Sét rơi cách người chơi 2–8 block ngẫu nhiên
            double offsetX = (random.nextDouble() - 0.5) * 16.0;
            double offsetZ = (random.nextDouble() - 0.5) * 16.0;
            Location setLoc = player.getLocation().clone().add(offsetX, 0, offsetZ);
            setLoc.setY(world.getHighestBlockYAt(setLoc));

            // strikeLightningEffect = không cháy block, không mob damage
            world.strikeLightningEffect(setLoc);

            // Âm thanh sấm gần người chơi
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

            // Damage 2 tim nếu người chơi trong bán kính 5 block
            if (setLoc.distance(player.getLocation()) <= 5.0) {
                if (player.getHealth() > 4.0) {
                    player.damage(4.0); // 2 tim = 4 HP
                    player.sendMessage(Component.text("[Mùa Hạ] ⚡ Sét suýt đánh trúng bạn! Mất 2 tim!")
                            .color(NamedTextColor.YELLOW));
                }
            }

            // Chỉ xử lý 1 người mỗi lần trigger
            break;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  BONE-MEAL MÙA XUÂN (hoa nở tự động)
    // ══════════════════════════════════════════════════════════════

    /**
     * Mỗi 3 giây, tìm 1–2 block GRASS_BLOCK ngẫu nhiên trong bán kính 16 block
     * quanh mỗi người chơi và bone-meal chúng (tạo hoa/cỏ mọc thêm).
     */
    private void boneMealMuaXuan(World world) {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) continue;

            Location loc = player.getLocation();
            int attempts = 0;
            int maxAttempts = 5;

            while (attempts < maxAttempts) {
                attempts++;
                int ox = random.nextInt(33) - 16;
                int oz = random.nextInt(33) - 16;
                int bx = loc.getBlockX() + ox;
                int bz = loc.getBlockZ() + oz;
                int by = world.getHighestBlockYAt(bx, bz) - 1;

                Block block = world.getBlockAt(bx, by, bz);
                if (block.getType() == Material.GRASS_BLOCK) {
                    // Áp dụng bone-meal: tạo hoa/cỏ mọc
                    block.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                    // Particle xanh lá tại vị trí nảy mầm
                    Location blockLoc = block.getLocation().add(0.5, 1.2, 0.5);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, blockLoc, 3, 0.3, 0.3, 0.3, 0);
                    break;
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  KIỂM TRA HỖ TRỢ
    // ══════════════════════════════════════════════════════════════

    private boolean isDungDuoiTroiNang(Player player, World world) {
        if (world.hasStorm() || world.isThundering()) return false;
        long time = world.getTime();
        if (time >= 13000 && time <= 23000) return false;
        if (player.isInWater()) return false;

        Location loc = player.getLocation();
        int highestY = loc.getWorld().getHighestBlockYAt(loc);
        return loc.getBlockY() >= highestY;
    }

    private boolean checkMacDoDa(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] armorPieces = { inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots() };
        Set<Material> leatherMats = Set.of(
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
        );
        for (ItemStack armor : armorPieces) {
            if (armor != null && leatherMats.contains(armor.getType())) return true;
        }
        return false;
    }

    private boolean checkGanNguonNhiet(Player player) {
        Location loc = player.getLocation();
        int radius = 4;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material mat = loc.getWorld()
                            .getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z)
                            .getType();
                    if (isNguonNhiet(mat)) return true;
                }
            }
        }
        return false;
    }

    private boolean isNguonNhiet(Material mat) {
        return switch (mat) {
            case CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE,
                 TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                 LANTERN, SOUL_LANTERN, BLAST_FURNACE, FURNACE,
                 SMOKER, MAGMA_BLOCK, LAVA -> true;
            default -> false;
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  GIẢI NHIỆT MÙA HẠ
    // ══════════════════════════════════════════════════════════════

    /** Nhảy xuống nước → giải nhiệt. */
    private void kiemTraGiaiNhietNuoc(Player player, World world, String mua) {
        if (!mua.equals("ha")) return;
        if (!player.isInWater()) return;
        if (player.hasPotionEffect(PotionEffectType.HUNGER)) {
            player.removePotionEffect(PotionEffectType.HUNGER);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, true, true));
            player.sendActionBar(Component.text("💧 Mát lạnh! Giải nhiệt bằng nước! +Tốc Độ II (10s)")
                    .color(NamedTextColor.AQUA));
        }
    }

    /** Uống Water Potion → giải nhiệt. */
    @EventHandler
    public void onDrinkWater(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String mua = getMuaHienTai(player.getWorld());
        if (!mua.equals("ha")) return;

        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
        if (meta.getBasePotionType() != PotionType.WATER) return;

        player.removePotionEffect(PotionEffectType.HUNGER);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, true, true));
        player.sendMessage(Component.text("[Mùa Hạ] ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("Mát lạnh! Giải nhiệt thành công! +Tốc Độ II (10s)")
                        .color(NamedTextColor.YELLOW)));
    }

    // ══════════════════════════════════════════════════════════════
    //  QUÁI VẬT ĐÒN ĐÁNH THEO MÙA
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onQuaiTanCongNguoiChoi(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        LivingEntity attacker = getAttacker(event.getDamager());
        if (attacker == null || !isQuaiCanXuLy(attacker)) return;

        String mua = getMuaHienTai(player.getWorld());
        switch (mua) {
            case "ha" -> {
                player.setFireTicks(80);
                player.sendActionBar(Component.text("🔥 Mùa Hạ | Đòn lửa từ quái! Cháy 4 giây!")
                        .color(NamedTextColor.RED));
            }
            case "dong" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, true));
                player.setFreezeTicks(Math.min(player.getFreezeTicks() + 60, 140));
                player.sendActionBar(Component.text("❄ Mùa Đông | Đòn băng từ quái! Chậm II & Đóng Băng!")
                        .color(NamedTextColor.BLUE));
            }
        }
    }

    private LivingEntity getAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private boolean isQuaiCanXuLy(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom;
    }

    // ══════════════════════════════════════════════════════════════
    //  QUÁI THEO MÙA ĐẶC TRƯNG
    // ══════════════════════════════════════════════════════════════

    /**
     * Điều chỉnh spawn quái theo mùa:
     *
     *  XUÂN : Bee xác suất spawn tăng x3 (nếu spawn bình thường sẽ cancel + spawn 2 thêm)
     *  HẠ   : Magma Cube xác suất nhỏ spawn ngoài trời ban đêm
     *  THU  : Witch tăng 30% (1/3 quái thông thường thay bằng Witch)
     *  ĐÔNG : Skeleton ngoài trời → Stray
     */
    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        World world = event.getEntity().getWorld();
        String mua  = getMuaHienTai(world);
        EntityType type = event.getEntityType();
        Location loc = event.getLocation();

        switch (mua) {
            case "xuan" -> {
                // Bee tự nhiên spawn → spawn thêm 2 con
                if (type == EntityType.BEE
                        && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                    for (int i = 0; i < 2; i++) {
                        double ox = (random.nextDouble() - 0.5) * 3.0;
                        double oz = (random.nextDouble() - 0.5) * 3.0;
                        world.spawnEntity(loc.clone().add(ox, 0, oz), EntityType.BEE);
                    }
                }
            }
            case "ha" -> {
                // Ban đêm, ngoài trời: 2% xác suất Magma Cube thay Monster bình thường
                long time = world.getTime();
                boolean laBanDem = time >= 13000 && time <= 23000;
                if (laBanDem && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                    int highestY = world.getHighestBlockYAt(loc);
                    boolean ngoaiTroi = loc.getBlockY() >= highestY - 1;
                    if (ngoaiTroi && random.nextInt(50) == 0) {
                        event.setCancelled(true);
                        world.spawnEntity(loc, EntityType.MAGMA_CUBE);
                    }
                }
            }
            case "thu" -> {
                // Monster tự nhiên → 30% thay bằng Witch
                if (event.getEntity() instanceof Monster
                        && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                        && !(event.getEntity() instanceof Witch)) {
                    if (random.nextInt(10) < 3) { // 30%
                        event.setCancelled(true);
                        world.spawnEntity(loc, EntityType.WITCH);
                    }
                }
            }
            case "dong" -> {
                // Skeleton ngoài trời → Stray
                if (type == EntityType.SKELETON
                        && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                    int highestY = world.getHighestBlockYAt(loc);
                    boolean ngoaiTroi = loc.getBlockY() >= highestY - 1;
                    if (ngoaiTroi) {
                        event.setCancelled(true);
                        world.spawnEntity(loc, EntityType.STRAY);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  NÔNG NGHIỆP THEO MÙA
    // ══════════════════════════════════════════════════════════════

    /**
     * XUÂN: cây lớn nhanh x2 (50% tăng thêm 1 tuổi ngay)
     * HẠ:   cây lớn x1.5 (33% tăng thêm 1 tuổi) NẾU Farmland ướt, nếu khô → chết
     */
    @EventHandler
    public void onCayPhatTrien(BlockGrowEvent event) {
        World world = event.getBlock().getWorld();
        String mua  = getMuaHienTai(world);

        Block block = event.getBlock();

        switch (mua) {
            case "xuan" -> {
                // x2: 50% tăng thêm 1 tuổi
                if (random.nextBoolean()) return;
                tangTuoiCay(event);
            }
            case "ha" -> {
                // Kiểm tra Farmland bên dưới
                Block duoi = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                if (duoi.getType() == Material.FARMLAND) {
                    // Farmland ướt (moisture > 0) → lớn x1.5
                    if (duoi.getBlockData() instanceof org.bukkit.block.data.Farmland farmland) {
                        if (farmland.getMoisture() == 0) {
                            // Khô hạn: cây chết → DEAD_BUSH hoặc AIR
                            event.setCancelled(true);
                            block.setType(Material.AIR);
                            duoi.setType(Material.DIRT);
                            // Thông báo nếu có người gần
                            for (Player p : block.getWorld().getNearbyPlayers(block.getLocation(), 16)) {
                                p.sendActionBar(Component.text("🌵 Mùa Hạ | Cây trồng chết vì hạn hán! Tưới nước đi!")
                                        .color(NamedTextColor.GOLD));
                            }
                            return;
                        }
                    }
                }
                // Farmland ướt: x1.5 — 33% tăng thêm 1 tuổi
                if (random.nextInt(3) != 0) return;
                tangTuoiCay(event);
            }
            case "dong" -> {
                // Đông: cây không lớn thêm (block grow event bị hủy)
                event.setCancelled(true);
            }
        }
        // Thu: không thay đổi tốc độ mọc, chỉ tăng drop
    }

    /** Helper: tăng thêm 1 tuổi cho cây trong BlockGrowEvent. */
    private void tangTuoiCay(BlockGrowEvent event) {
        BlockData newData = event.getNewState().getBlockData();
        if (!(newData instanceof Ageable ageable)) return;
        int maxAge     = ageable.getMaximumAge();
        int currentAge = ageable.getAge();
        if (currentAge < maxAge) {
            ageable.setAge(Math.min(currentAge + 1, maxAge));
            event.getNewState().setBlockData(ageable);
        }
    }

    /**
     * CÂY CHẾT MÙA ĐÔNG:
     * Mỗi lần BlockGrowEvent trigger (nếu không bị cancel) ở cây lúa mì/cà rốt/khoai tây
     * ngoài trời → 5% chuyển thành Dead Bush.
     * Logic chạy song song với việc cancel grow ở trên
     * → dùng event riêng kiểm tra hàng giờ qua BukkitRunnable là tốt hơn,
     * nhưng ở đây xử lý bằng BlockGrowEvent để tránh scan toàn world.
     *
     * Thực ra: vì mùa đông đã cancel grow event, cây sẽ không lớn thêm.
     * Logic "cây chết 5%" được trigger khi cây thử mọc nhưng bị cancel:
     * ta dùng một runnable riêng quét khu vực người chơi.
     */
    @EventHandler
    public void onCayCoTheThoiMuaDong(BlockGrowEvent event) {
        World world = event.getBlock().getWorld();
        if (!getMuaHienTai(world).equals("dong")) return;

        Block block = event.getBlock();
        Material mat = block.getType();
        if (mat != Material.WHEAT && mat != Material.CARROTS && mat != Material.POTATOES) return;

        // Kiểm tra không có mái che
        int highestY = world.getHighestBlockYAt(block.getLocation());
        if (block.getY() < highestY - 1) return; // có mái che

        // 5% xác suất chết
        if (random.nextInt(20) != 0) return;
        event.setCancelled(true);
        block.setType(Material.AIR);
        Block farmland = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (farmland.getType() == Material.FARMLAND) farmland.setType(Material.DIRT);
    }

    /**
     * MÙA XUÂN: sinh sản x2 (sinh thêm 1 con non bổ sung).
     */
    @EventHandler
    public void onAnimalGrow(EntityBreedEvent event) {
        World world = event.getEntity().getWorld();
        if (!getMuaHienTai(world).equals("xuan")) return;
        if (random.nextBoolean()) return;

        if (event.getEntity() instanceof Ageable parent) {
            Location loc = ((Entity) parent).getLocation();
            loc.getWorld().spawnEntity(loc, ((Entity) parent).getType());
        }
    }

    /**
     * THU HOẠCH MÙA THU: Wheat chín rơi x2 (thêm 1 lúa mì bổ sung).
     * Thêm: Carrot, Potato, Beetroot cũng được x2.
     */
    @EventHandler
    public void onHarvestAutumn(BlockBreakEvent event) {
        World world = event.getBlock().getWorld();
        if (!getMuaHienTai(world).equals("thu")) return;

        Block block = event.getBlock();

        // Chỉ khi cây đã chín
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        Material dropMat = switch (block.getType()) {
            case WHEAT    -> Material.WHEAT;
            case CARROTS  -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            default -> null;
        };
        if (dropMat == null) return;

        // Rơi thêm 1 vật phẩm
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(dropMat, 1));
    }

    // ══════════════════════════════════════════════════════════════
    //  SCOREBOARD
    // ══════════════════════════════════════════════════════════════

    private void capNhatScoreboard(Player player, World world, String muaHienTai) {
        ScoreboardManager manager = getServer().getScoreboardManager();
        Scoreboard board          = manager.getNewScoreboard();

        Component title = Component.text("❖  10A1 SMP  ❖")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);

        Objective obj = board.registerNewObjective("smp_info", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int soOnline   = getServer().getOnlinePlayers().size();
        long tongNgay  = getTongSoNgayMap(world);
        long ngayConLai = getNgayConLai(world);
        long timeOfDay = world.getTime();

        String iconThoiGian;
        String tenThoiGian;
        if (timeOfDay < 1000 || timeOfDay > 23000)    { iconThoiGian = "🌅"; tenThoiGian = "Bình Minh"; }
        else if (timeOfDay < 12000)                    { iconThoiGian = "☀";  tenThoiGian = "Ban Ngày"; }
        else if (timeOfDay < 13000)                    { iconThoiGian = "🌇"; tenThoiGian = "Hoàng Hôn"; }
        else                                           { iconThoiGian = "🌙"; tenThoiGian = "Ban Đêm"; }

        String tenMuaHienThi;
        String maMauMua;
        switch (muaHienTai) {
            case "xuan" -> { tenMuaHienThi = "🌸 Xuân"; maMauMua = "§a"; }
            case "ha"   -> { tenMuaHienThi = "☀ Hạ";   maMauMua = "§e"; }
            case "thu"  -> { tenMuaHienThi = "🍂 Thu";  maMauMua = "§6"; }
            default     -> { tenMuaHienThi = "❄ Đông"; maMauMua = "§b"; }
        }

        // Hiện trạng đặc biệt theo mùa
        String trangThaiMua;
        switch (muaHienTai) {
            case "xuan" -> trangThaiMua = "§aPháp: Luck V";
            case "ha"   -> {
                boolean nangNong = isDungDuoiTroiNang(player, world);
                trangThaiMua = nangNong ? "§6☀ Nóng bức!" : "§eMát mẻ";
            }
            case "thu"  -> trangThaiMua = world.hasStorm() ? "§4☠ Mưa Axit!" : "§6Thu hoạch x2";
            default     -> {
                int freeze = player.getFreezeTicks();
                trangThaiMua = freeze > 70 ? "§b❄ Đông Cứng: " + freeze + "/140" : "§7Nhiệt độ ổn";
            }
        }

        String tagEp = (overrideMua != null) ? " §c[Ép]" : "";

        setDong(obj, "§7──────────────────", 15);
        setDong(obj, "§f👤 " + player.getName(), 14);
        setDong(obj, "§7──────────────────", 13);
        setDong(obj, "§aOnline: §f" + soOnline + " người", 12);
        setDong(obj, "§eNgày Map: §f" + tongNgay, 11);
        setDong(obj, "§7──────────────────", 10);
        setDong(obj, maMauMua + "Mùa: " + tenMuaHienThi + tagEp, 9);
        if (overrideMua == null) {
            setDong(obj, "§7Còn lại: §f" + ngayConLai + " ngày", 8);
        } else {
            setDong(obj, "§cChế độ ép mùa", 8);
        }
        setDong(obj, trangThaiMua, 7);
        setDong(obj, "§7──────────────────", 6);
        setDong(obj, "§7Thời Gian:", 5);
        setDong(obj, iconThoiGian + " §f" + tenThoiGian, 4);
        setDong(obj, "§7──────────────────", 3);

        player.setScoreboard(board);
    }

    private void setDong(Objective obj, String text, int score) {
        String uniqueText = text + "§r" + " ".repeat(score % 16);
        obj.getScore(uniqueText).setScore(score);
    }

    // ══════════════════════════════════════════════════════════════
    //  LỆNH /season
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("season")) return false;

        if (args.length == 0) { hienThiHelpSeason(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "info"  -> xuLyInfo(sender);
            case "set"   -> xuLySet(sender, args);
            case "reset" -> xuLyReset(sender);
            default      -> hienThiHelpSeason(sender);
        }
        return true;
    }

    private void xuLyInfo(CommandSender sender) {
        World world = getServer().getWorlds().get(0);
        String mua  = getMuaHienTai(world);

        String tenMua;
        String maMau;
        switch (mua) {
            case "xuan" -> { tenMua = "Mùa Xuân 🌸"; maMau = "§a"; }
            case "ha"   -> { tenMua = "Mùa Hạ ☀";   maMau = "§e"; }
            case "thu"  -> { tenMua = "Mùa Thu 🍂";  maMau = "§6"; }
            default     -> { tenMua = "Mùa Đông ❄"; maMau = "§b"; }
        }

        long tongNgay   = getTongSoNgayMap(world);
        long ngayConLai = getNgayConLai(world);
        String tagEp    = (overrideMua != null) ? " §c(Đang bị ép bởi Admin)" : "";

        sender.sendMessage("§6=====[ §eTHỜI TIẾT 4 MÙA v2.1 §6]=====");
        sender.sendMessage("§7Mùa Hiện Tại : " + maMau + tenMua + tagEp);
        sender.sendMessage("§7Tổng Ngày Map : §f" + tongNgay + " ngày");
        if (overrideMua == null) {
            sender.sendMessage("§7Còn lại       : §f" + ngayConLai + " ngày");
        } else {
            sender.sendMessage("§cChế độ ép mùa đang hoạt động. Dùng /season reset để tắt.");
        }
        sender.sendMessage("§7Thời tiết     : " + (world.hasStorm() ? "§9Mưa/Tuyết" : "§eTrời trong"));
        sender.sendMessage("§6======================================");
    }

    private void xuLySet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thoitiet.admin")) {
            sender.sendMessage("§cBạn không có quyền Admin!"); return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cThiếu tham số! Chọn: §exuan, ha, thu, dong"); return;
        }

        String nhapMua = args[1].toLowerCase();
        if (!nhapMua.equals("xuan") && !nhapMua.equals("ha")
                && !nhapMua.equals("thu") && !nhapMua.equals("dong")) {
            sender.sendMessage("§cMùa sai! Chọn: §exuan / ha / thu / dong"); return;
        }

        String tenMuaHienThi = switch (nhapMua) {
            case "xuan" -> "Mùa Xuân 🌸";
            case "ha"   -> "Mùa Hạ ☀";
            case "thu"  -> "Mùa Thu 🍂";
            default     -> "Mùa Đông ❄";
        };

        overrideMua = nhapMua;
        muaTruoc    = nhapMua; // Tránh trigger chuyển mùa giả

        getServer().broadcast(Component.text(
                "[Admin] Server đã ép sang " + tenMuaHienThi + "! (Chế độ ép mùa)"
        ).color(NamedTextColor.YELLOW));

        sender.sendMessage("§a[Thành công] Đã ép server sang " + tenMuaHienThi + ".");
        getLogger().info("[Admin] " + sender.getName() + " ép mùa → " + nhapMua);
    }

    private void xuLyReset(CommandSender sender) {
        if (!sender.hasPermission("thoitiet.admin")) {
            sender.sendMessage("§cBạn không có quyền Admin!"); return;
        }
        if (overrideMua == null) {
            sender.sendMessage("§eServer đang chạy tự động, không cần reset."); return;
        }

        overrideMua = null;
        // Cập nhật muaTruoc để tránh trigger chuyển mùa giả
        muaTruoc = getMuaHienTai(getServer().getWorlds().get(0));

        getServer().broadcast(Component.text(
                "[Admin] Hệ thống mùa quay về tự động theo ngày game!"
        ).color(NamedTextColor.GREEN));

        sender.sendMessage("§a[Thành công] Đã gỡ ép mùa. Server tự tính theo ngày.");
        getLogger().info("[Admin] " + sender.getName() + " reset ép mùa.");
    }

    private void hienThiHelpSeason(CommandSender sender) {
        sender.sendMessage("§6=====[ HƯỚNG DẪN /SEASON v2.1 ]=====");
        sender.sendMessage("§e/season info §7- Xem thông tin mùa hiện tại.");
        sender.sendMessage("§e/season set <xuan|ha|thu|dong> §7- Ép mùa. §c(Admin)");
        sender.sendMessage("§e/season reset §7- Gỡ ép mùa. §c(Admin)");
        sender.sendMessage("§6======================================");
    }
}
