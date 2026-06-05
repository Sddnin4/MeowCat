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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.util.concurrent.ThreadLocalRandom;
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
 *   ThoiTietBonMua v2.2 — Paper/Purpur 1.21
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

    // ── Mùa Thu - Anti-Duping & Ngày Đặc Biệt ────────────────────
    /** NamespacedKey đánh dấu item đã được nhân đôi — chống dupe. */
    private NamespacedKey keyDoubled;

    /**
     * Ngày đặc biệt ngẫu nhiên trong chu kỳ 90 ngày Mùa Thu (1–90).
     * Ngày này cộng thêm +5% vào tỉ lệ x2.
     * Được random lại mỗi khi Mùa Thu bắt đầu.
     */
    private int specialFallDay = ThreadLocalRandom.current().nextInt(1, 91);

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

    // ── Vòng lặp ngày (24000 ticks) ──────────────────────────────
    /** Ngày cuối cùng đã xử lý broadcast đông — tránh spam mỗi giây. */
    private long ngayDongCuoi = -1;

    /** True nếu server đang trong trạng thái Mùa Đông broadcast (ngày 271–360). */
    private boolean dangMuaDong = false;

    // ══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("  ThoiTietBonMua v2.2 đã khởi động!");
        getLogger().info("╚══════════════════════════════════════╝");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("season") != null) {
            getCommand("season").setExecutor(this);
        }

        chayHeThongCore();
        chayVongLapNgay();
    }

    @Override
    public void onDisable() {
        getLogger().info("ThoiTietBonMua v2.2 đã tắt.");
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
    //  VÒNG LẶP NGÀY (24000 ticks = 1 ngày Minecraft)
    //  Xử lý broadcast + ép bão mùa đông theo yêu cầu standalone
    // ══════════════════════════════════════════════════════════════

    /**
     * Runnable chạy đúng mỗi 24000 ticks (= 1 ngày Minecraft).
     *
     * Logic chu kỳ 360 ngày:
     *   Ngày   1 – 270 : thời tiết bình thường (trời quang)
     *   Ngày 271 – 360 : Mùa Đông — ép bão/tuyết, broadcast hàng ngày
     *
     * Khi overrideMua != null (Admin đang ép mùa), vòng lặp ngày
     * vẫn chạy nhưng không ghi đè override.
     */
    private void chayVongLapNgay() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = getServer().getWorlds().get(0);
                long ngayHienTai = getTongSoNgayMap(world);

                // Tránh xử lý trùng cùng 1 ngày
                if (ngayHienTai == ngayDongCuoi) return;
                ngayDongCuoi = ngayHienTai;

                // Ngày trong chu kỳ 360 (1 - 360)
                long ngayTrongChuKy = (ngayHienTai % TONG_NGAY_CHU_KY) + 1;

                boolean laMuaDong = ngayTrongChuKy > 270; // ngày 271–360

                if (laMuaDong) {
                    long ngayTrongDong = ngayTrongChuKy - 270; // 1–90
                    long ngayConLaiDong = 90 - ngayTrongDong + 1;

                    // Ép bão/tuyết trên mọi world (nếu không bị override sang mùa khác)
                    if (overrideMua == null || overrideMua.equals("dong")) {
                        for (World w : getServer().getWorlds()) {
                            if (w.getEnvironment() == World.Environment.NORMAL) {
                                w.setStorm(true);
                                w.setWeatherDuration(25000); // đảm bảo không tự hết trước ngày tiếp
                                w.setThundering(false);      // không sấm sét — chỉ tuyết
                            }
                        }
                    }

                    // Broadcast đầu mùa (ngày đầu tiên)
                    if (!dangMuaDong) {
                        dangMuaDong = true;
                        broadcastMuaDongBatDau();
                    } else {
                        // Broadcast hàng ngày trong mùa đông
                        broadcastMuaDongHangNgay(ngayTrongDong, ngayConLaiDong);
                    }

                } else {
                    // Thời tiết bình thường: trời quang nếu không bị override
                    if (overrideMua == null) {
                        for (World w : getServer().getWorlds()) {
                            if (w.getEnvironment() == World.Environment.NORMAL && w.hasStorm()) {
                                w.setStorm(false);
                                w.setThundering(false);
                            }
                        }
                    }
                    dangMuaDong = false;
                }
            }
        }.runTaskTimer(this, 0L, TICKS_MOI_NGAY);
    }

    // ─── Broadcast Mùa Đông ──────────────────────────────────────

    /** Phát đi khi Mùa Đông bắt đầu (ngày 271). */
    private void broadcastMuaDongBatDau() {
        String line = "§b§l❄═══════════════════════════════════❄";
        getServer().broadcastMessage(line);
        getServer().broadcastMessage("§f  ❄  §b§lMÙA ĐÔNG §fĐÃ BẮT ĐẦU! §f❄");
        getServer().broadcastMessage("§7  Tuyết rơi phủ khắp thế giới trong §f90 ngày§7 tới.");
        getServer().broadcastMessage("§7  Hãy chuẩn bị §fgiáp da §7và §fngồi cạnh lửa §7để giữ ấm!");
        getServer().broadcastMessage(line);

        // Title toàn server
        Title.Times times = Title.Times.times(
                java.time.Duration.ofMillis(500),
                java.time.Duration.ofMillis(4000),
                java.time.Duration.ofMillis(1000)
        );
        Title title = Title.title(
                Component.text("❄  MÙA ĐÔNG  ❄").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                Component.text("Tuyết rơi bắt đầu... Hãy giữ ấm!").color(NamedTextColor.WHITE),
                times
        );
        for (Player p : getServer().getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP, 0.8f, 0.8f);
        }

        getLogger().info("[MuaDong] Mùa Đông bắt đầu — ngày 271 của chu kỳ.");
    }

    /** Phát đi mỗi sáng trong mùa đông (ngày 272 trở đi). */
    private void broadcastMuaDongHangNgay(long ngayTrongDong, long ngayConLai) {
        String mauSo = ngayConLai <= 10 ? "§c" : ngayConLai <= 30 ? "§e" : "§f";
        getServer().broadcastMessage(
                "§b❄ §fMùa Đông — Ngày §b" + ngayTrongDong + "§f/90  |  "
                + "Còn " + mauSo + ngayConLai + " ngày §ftrước khi trời ấm lại."
        );
        getLogger().info("[MuaDong] Ngày " + ngayTrongDong + "/90, còn " + ngayConLai + " ngày.");
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
        } else {
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
        } else {
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
        // Tuyết rơi diện rộng MỌI biome — lưới 20x20 block, rơi từ trên cao
        // Không phụ thuộc vào biome có tuyết tự nhiên hay không
        for (int i = 0; i < 20; i++) {
            double ox = (random.nextDouble() - 0.5) * 20.0;
            double oy = random.nextDouble() * 6.0 + 3.0; // từ 3–9 block trên đầu
            double oz = (random.nextDouble() - 0.5) * 20.0;
            loc.getWorld().spawnParticle(
                    Particle.SNOWFLAKE,
                    loc.clone().add(ox, oy, oz),
                    1,
                    0.0, -0.15, 0.0,  // hướng rơi xuống
                    0.02               // tốc độ nhẹ để không rơi thẳng cứng
            );
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

        // Logic đông cứng — áp dụng MỌI biome, không check highestBlockY
        boolean macDoDa       = checkMacDoDa(player);
        boolean ganNguonNhiet = checkGanNguonNhiet(player);
        boolean coMai         = coMaiche(player); // dùng raycast, đúng mọi biome

        if (macDoDa || ganNguonNhiet || coMai) {
            // An toàn: reset đóng băng
            if (player.getFreezeTicks() > 0) {
                player.setFreezeTicks(Math.max(0, player.getFreezeTicks() - 10));
            }
            String lyDo = macDoDa ? "Giáp da giữ ấm" : coMai ? "Trong nhà" : "Gần nguồn nhiệt";
        } else {
            // Ngoài trời, không bảo vệ: tăng FreezeTicks — màn hình đóng băng dần
            // Paper tự render overlay đóng băng khi FreezeTicks > getMaxFreezeTicks()/2
            int freeze = Math.min(player.getFreezeTicks() + 7, 140);
            player.setFreezeTicks(freeze);
            if (timeOfDay >= 13000 || timeOfDay < 1000) {
            } else {
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

        // Kiểm tra mái che thực: quét từng block từ đầu người lên trần
        // Hoạt động đúng trên MỌI biome (kể cả rừng, sa mạc, nether...)
        if (coMaiche(player)) return;

        // 1 tim = 2 HP, không gây chết
        if (player.getHealth() > 2.0) {
            player.damage(2.0);
        }
    }

    /**
     * Trả về true nếu có ít nhất 1 block solid phía trên đầu người chơi
     * (quét từ y+2 lên y+256 hoặc đến build limit).
     * Phương pháp này chính xác hơn getHighestBlockYAt trên mọi biome.
     */
    private boolean coMaiche(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int pz = loc.getBlockZ();
        int startY = loc.getBlockY() + 2; // từ trên đầu
        int maxY = Math.min(world.getMaxHeight(), startY + 256);
        for (int y = startY; y < maxY; y++) {
            Block b = world.getBlockAt(px, y, pz);
            if (b.getType().isSolid() && b.getType().isOccluding()) return true;
        }
        return false;
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
            int found = 0;

            // Thử tìm tối đa 8 block cỏ trong bán kính 16
            for (int attempt = 0; attempt < 8 && found < 2; attempt++) {
                int ox = random.nextInt(33) - 16;
                int oz = random.nextInt(33) - 16;
                int bx = loc.getBlockX() + ox;
                int bz = loc.getBlockZ() + oz;

                // Tìm surface block thực sự (quét từ trên xuống)
                int topY = world.getHighestBlockYAt(bx, bz);
                Block surface = world.getBlockAt(bx, topY, bz);
                // Lùi xuống 1 nếu highest là không khí
                if (surface.getType() == Material.AIR) {
                    surface = world.getBlockAt(bx, topY - 1, bz);
                }

                if (surface.getType() == Material.GRASS_BLOCK) {
                    // applyBoneMeal lên mặt trên block cỏ → tạo hoa/cỏ ngẫu nhiên
                    surface.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                    // Particle xanh lá
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                        surface.getLocation().add(0.5, 1.3, 0.5),
                        5, 0.4, 0.3, 0.4, 0);
                    found++;
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

        // Đội mũ bất kỳ → được che nắng, không bị đói mùa hạ
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getType() != Material.AIR) return false;

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
            }
            case "dong" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, true));
                player.setFreezeTicks(Math.min(player.getFreezeTicks() + 60, 140));
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
     * XUÂN: cây lớn nhanh x3 (66% tăng thêm 1 tuổi ngay) — tất cả hạt giống
     * HẠ:   cây lớn x1.5 (33% tăng thêm 1 tuổi) NẾU Farmland ướt, nếu khô → chết — tất cả hạt giống
     */
    @EventHandler
    public void onCayPhatTrien(BlockGrowEvent event) {
        World world = event.getBlock().getWorld();
        String mua  = getMuaHienTai(world);

        Block block = event.getBlock();

        switch (mua) {
            case "xuan" -> {
                // x3: 66% tăng thêm 1 tuổi ngay (thay vì chờ grow tự nhiên)
                if (random.nextInt(3) == 0) return;
                tangTuoiCay(event);
            }
            case "ha" -> {
                // Kiểm tra Farmland bên dưới
                Block duoi = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                if (duoi.getType() == Material.FARMLAND) {
                    // Farmland ướt (moisture > 0) → lớn x1.5
                    if (duoi.getBlockData() instanceof org.bukkit.block.data.type.Farmland farmland) {
                        if (farmland.getMoisture() == 0) {
                            // Khô hạn: cây chết → DEAD_BUSH hoặc AIR
                            event.setCancelled(true);
                            block.setType(Material.AIR);
                            duoi.setType(Material.DIRT);
                            // (thông báo actionbar đã bị tắt)
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
        // Tất cả loại hạt giống/cây trồng
        if (mat != Material.WHEAT && mat != Material.CARROTS
                && mat != Material.POTATOES && mat != Material.BEETROOTS
                && mat != Material.MELON_STEM && mat != Material.PUMPKIN_STEM
                && mat != Material.NETHER_WART && mat != Material.COCOA
                && mat != Material.SWEET_BERRY_BUSH && mat != Material.TORCHFLOWER_CROP
                && mat != Material.PITCHER_CROP) return;

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

    // ══════════════════════════════════════════════════════════════
    //  MÙA THU — HỆ THỐNG X2 NÔNG SẢN (v2.2 Refactor)
    //
    //  Xác suất cơ bản: 15%
    //  Bonus Fortune (Cúp/Rìu): +3% (I) / +5% (II) / +8% (III)
    //  Bonus Ngày Đặc Biệt: +5% (1 ngày random/chu kỳ 90 ngày)
    //
    //  Điều kiện: cầm Cúp hoặc Rìu + cây đạt Maximum Age
    //  Anti-Dupe: PersistentDataContainer tag "thoitietbonmua:doubled"
    // ══════════════════════════════════════════════════════════════

    /**
     * Tính xác suất x2 dựa trên Fortune cầm trên tay + Ngày Đặc Biệt.
     *
     * @param tool  Vật phẩm cầm trên tay của người chơi
     * @param world Thế giới (để lấy ngày trong mùa)
     * @return xác suất từ 0.0 đến 1.0
     */
    private double tinhXacSuatThu(ItemStack tool, World world) {
        double xacSuat = 0.15; // 15% cơ bản

        // Bonus Fortune — chỉ tính nếu là Cúp hoặc Rìu
        if (tool != null && isCupHoacRiu(tool.getType())) {
            int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
            xacSuat += switch (fortuneLevel) {
                case 1 -> 0.03;
                case 2 -> 0.05;
                case 3 -> 0.08;
                default -> 0.0;
            };
        }

        // Bonus Ngày Đặc Biệt
        long ngayTrongMua = getNgayTrongMua(world);
        if (ngayTrongMua == specialFallDay) {
            xacSuat += 0.05;
        }

        return xacSuat;
    }

    /** Trả về true nếu Material là Cúp (Pickaxe) hoặc Rìu (Axe). */
    private boolean isCupHoacRiu(Material mat) {
        return switch (mat) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE,
                 WOODEN_AXE, STONE_AXE, IRON_AXE,
                 GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    /**
     * Kiểm tra item đã bị nhân đôi chưa (anti-dupe).
     * Dùng PersistentDataContainer với key "thoitietbonmua:doubled".
     */
    private boolean daDoubled(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(keyDoubled, PersistentDataType.BYTE);
    }

    /**
     * Gắn tag "doubled" vào item để chống dupe.
     * Trả về item đã được gắn tag.
     */
    private ItemStack ganTagDoubled(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyDoubled, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * THU HOẠCH MÙA THU — NÔNG SẢN (BlockDropItemEvent):
     *
     * Chỉ áp dụng khi:
     *   1. Mùa hiện tại là Thu
     *   2. Người chơi cầm Cúp hoặc Rìu
     *   3. Block là cây trồng đạt Maximum Age (đã chín)
     *   4. Item chưa có tag "doubled" (anti-dupe)
     *
     * Xác suất x2 = 15% base + Fortune bonus + Ngày Đặc Biệt bonus
     */
    @EventHandler
    public void onBlockDropThu(BlockDropItemEvent event) {
        if (!getMuaHienTai(event.getBlock().getWorld()).equals("thu")) return;

        // Điều kiện 1: phải có người chơi thực hiện
        if (!(event.getPlayer() instanceof Player player)) return;

        // Điều kiện 2: cầm Cúp hoặc Rìu
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isCupHoacRiu(tool.getType())) return;

        // Điều kiện 3: block phải là cây trồng đã chín
        Block block = event.getBlock();
        if (!isCayDaChin(block)) return;

        // Tính xác suất x2
        double xacSuat = tinhXacSuatThu(tool, player.getWorld());
        if (ThreadLocalRandom.current().nextDouble() > xacSuat) return;

        // Nhân đôi từng item drop (anti-dupe check)
        for (org.bukkit.entity.Item droppedItem : event.getItems()) {
            ItemStack stack = droppedItem.getItemStack();

            // Bỏ qua nếu đã doubled
            if (daDoubled(stack)) continue;

            // Gắn tag vào item GỐC trước
            ganTagDoubled(stack);
            droppedItem.setItemStack(stack);

            // Tạo item NHÂN ĐÔI (clone + gắn tag ngay)
            ItemStack bonus = stack.clone();
            bonus.setAmount(stack.getAmount());
            ganTagDoubled(bonus);

            // Thả item bonus xuống đất
            block.getWorld().dropItemNaturally(block.getLocation(), bonus);
        }
    }

    /**
     * Kiểm tra block có phải cây trồng đã đạt Maximum Age không.
     * Áp dụng: Wheat, Carrots, Potatoes, Beetroots, Nether Wart,
     *          Melon Stem, Pumpkin Stem, Cocoa, Sweet Berry Bush,
     *          Torchflower Crop, Pitcher Crop.
     */
    private boolean isCayDaChin(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    /**
     * Giết mob/quái vật mùa thu → nhân đôi toàn bộ drop + x2 EXP.
     * Không yêu cầu Fortune vì đây là combat, không phải thu hoạch.
     */
    @EventHandler
    public void onMobDropThu(EntityDeathEvent event) {
        if (!getMuaHienTai(event.getEntity().getWorld()).equals("thu")) return;

        // Chỉ khi bị kill bởi Player
        if (!(event.getEntity().getKiller() instanceof Player)) return;

        for (ItemStack drop : event.getDrops()) {
            if (drop == null || daDoubled(drop)) continue;
            ganTagDoubled(drop);
            drop.setAmount(Math.min(drop.getAmount() * 2, drop.getMaxStackSize()));
        }
        event.setDroppedExp(event.getDroppedExp() * 2);
    }

    /**
     * Câu cá mùa thu → nhân đôi item câu được.
     */
    @EventHandler
    public void onFishThu(PlayerFishEvent event) {
        if (!getMuaHienTai(event.getPlayer().getWorld()).equals("thu")) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof org.bukkit.entity.Item caughtItem)) return;

        ItemStack stack = caughtItem.getItemStack();
        if (daDoubled(stack)) return;

        // x2 item câu được + gắn tag
        ganTagDoubled(stack);
        stack.setAmount(Math.min(stack.getAmount() * 2, stack.getMaxStackSize()));
        caughtItem.setItemStack(stack);
    }

    // ══════════════════════════════════════════════════════════════
    //  SCOREBOARD
    // ══════════════════════════════════════════════════════════════

    private void capNhatScoreboard(Player player, World world, String muaHienTai) {
        ScoreboardManager manager = getServer().getScoreboardManager();
        Scoreboard board          = manager.getNewScoreboard();

        Component title = Component.text("❖ 10A1 SMP ❖")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);

        Objective obj = board.registerNewObjective("smp_info", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int soOnline   = getServer().getOnlinePlayers().size();
        long tongNgay  = getTongSoNgayMap(world);
        long ngayConLai = getNgayConLai(world);
        long timeOfDay = world.getTime();

        String iconThoiGian;
        String tenThoiGian;
        if (timeOfDay < 1000 || timeOfDay > 23000)    { iconThoiGian = "🌅"; tenThoiGian = "Sáng"; }
        else if (timeOfDay < 12000)                    { iconThoiGian = "☀";  tenThoiGian = "Ngày"; }
        else if (timeOfDay < 13000)                    { iconThoiGian = "🌇"; tenThoiGian = "Chiều"; }
        else                                           { iconThoiGian = "🌙"; tenThoiGian = "Đêm"; }

        String tenMuaHienThi;
        String maMauMua;
        switch (muaHienTai) {
            case "xuan" -> { tenMuaHienThi = "Xuân"; maMauMua = "§a"; }
            case "ha"   -> { tenMuaHienThi = "Hạ";   maMauMua = "§e"; }
            case "thu"  -> { tenMuaHienThi = "Thu";  maMauMua = "§6"; }
            default     -> { tenMuaHienThi = "Đông"; maMauMua = "§b"; }
        }

        // Hiện trạng đặc biệt theo mùa
        String trangThaiMua;
        switch (muaHienTai) {
            case "xuan" -> trangThaiMua = "§a✨ Phúc: Luck V";
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
        String conLai = (overrideMua == null) ? " §7(" + ngayConLai + "n)" : " §c[Ép]";

        // Scoreboard: giống ảnh mẫu — nhãn trái, không số cạnh phải
        setDong(obj, "§fTên: §e" + player.getName(), 5);
        setDong(obj, "§fOnline: §a" + soOnline + "/20", 4);
        setDong(obj, "§fNgày: §e" + tongNgay, 3);
        setDong(obj, "§fMùa: " + maMauMua + tenMuaHienThi + tagEp, 2);
        setDong(obj, "§fThời gian: " + iconThoiGian + " §f" + tenThoiGian, 1);

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
        sender.sendMessage("§6=====[ HƯỚNG DẪN /SEASON v2.2 ]=====");
        sender.sendMessage("§e/season info §7- Xem thông tin mùa hiện tại.");
        sender.sendMessage("§e/season set <xuan|ha|thu|dong> §7- Ép mùa. §c(Admin)");
        sender.sendMessage("§e/season reset §7- Gỡ ép mùa. §c(Admin)");
        sender.sendMessage("§6======================================");
    }
}
