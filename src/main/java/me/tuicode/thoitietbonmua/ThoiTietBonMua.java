package me.tuicode.thoitietbonmua;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

public final class ThoiTietBonMua extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Plugin Thoi Tiet Bon Mua + Scoreboard cho 1.21 da san sang!");
        
        // Đăng ký bộ lắng nghe sự kiện trồng trọt mùa xuân
        getServer().getPluginManager().registerEvents(new TrongTrotListener(), this);
        
        // Tạo Task chạy ngầm lặp lại mỗi 1 giây (20 ticks) để cập nhật bảng liên tục
        new BukkitRunnable() {
            @Override
            public void run() {
                chayHeThongChinh();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void chayHeThongChinh() {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            
            // TÍNH TOÁN NGÀY VÀ MÙA TRONG GAME
            long totalTicks = world.getFullTime();
            long tongSoNgay = totalTicks / 24000; 
            long ngayTrongNam = tongSoNgay % 360; 
            long ngayTrongMua = (ngayTrongNam % 90) + 1;

            String muaHienTai = "";
            NamedTextColor mauMua = NamedTextColor.WHITE;

            // XỬ LÝ HIỆU ỨNG & MÔI TRƯỜNG THEO TỪNG MÙA
            if (ngayTrongNam < 90) {
                muaHienTai = "Xuân";
                mauMua = NamedTextColor.LIGHT_PURPLE; // Màu hồng hoa đào
                
                if (ngayTrongMua == 1 && !world.hasStorm()) {
                    world.setStorm(true);
                    world.setWeatherDuration(12000); 
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 40, 0, false, false, false));
                
            } else if (ngayTrongNam < 180) {
                muaHienTai = "Hạ";
                mauMua = NamedTextColor.GOLD; // Màu cam nắng gắt
                
                // ÉP TẮT MƯA TUYỆT ĐỐI TRONG MÙA HẠ
                if (world.hasStorm()) {
                    world.setStorm(false);
                    world.setThundering(false);
                }
                if (isDungDuoiTroiNang(player)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 0, false, false, false));
                }
                
            } else if (ngayTrongNam < 270) {
                muaHienTai = "Thu";
                mauMua = NamedTextColor.YELLOW; // Màu vàng lá úa
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
                
            } else {
                muaHienTai = "Đông";
                mauMua = NamedTextColor.AQUA; // Màu xanh băng giá
                
                // ÉP BẢO TUYẾT RƠI LIÊN TỤC TRONG MÙA ĐÔNG
                if (!world.hasStorm()) {
                    world.setStorm(true);
                    world.setWeatherDuration(24000);
                }
                if (world.hasStorm() && isDungDuoiTroiNang(player)) {
                    player.setInPowderSnow(true);
                } else {
                    player.setInPowderSnow(false);
                }
            }

            // KIỂM TRA TRẠNG THÁI NGÀY / ĐÊM
            long timeOfDay = world.getTime();
            String iconThoiGian = (timeOfDay >= 0 && timeOfDay < 12000) ? "☀️ Ngày" : "🌙 Đêm";

            // KHỞI TẠO VÀ ĐẨY DỮ LIỆU LÊN SCOREBOARD
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            Scoreboard board = manager.getNewScoreboard();
            
            Objective obj = board.registerNewObjective("smp_info", Criteria.DUMMY, 
                    Component.text("   10A1 SMP   ").color(NamedTextColor.GREEN).bold(true));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            // Đặt các dòng hiển thị theo đúng thiết kế của ông
            obj.getScore(Component.text("Tên: ").color(NamedTextColor.WHITE)
                    .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))).setScore(5);
            
            obj.getScore(Component.text("Online: ").color(NamedTextColor.WHITE)
                    .append(Component.text(onlineCount + "/20").color(NamedTextColor.GREEN))).setScore(4);
            
            obj.getScore(Component.text("Ngày: ").color(NamedTextColor.WHITE)
                    .append(Component.text(tongSoNgay).color(NamedTextColor.GOLD))).setScore(3);
            
            obj.getScore(Component.text("Mùa: ").color(NamedTextColor.WHITE)
                    .append(Component.text(muaHienTai).color(mauMua))).setScore(2);
            
            obj.getScore(Component.text("Thời gian: ").color(NamedTextColor.WHITE)
                    .append(Component.text(iconThoiGian).color(NamedTextColor.AQUA))).setScore(1);

            player.setScoreboard(board);
        }
    }

    private boolean isDungDuoiTroiNang(Player player) {
        Location loc = player.getLocation();
        int highestBlockY = player.getWorld().getHighestBlockYAt(loc);
        return loc.getBlockY() >= highestBlockY;
    }
}
