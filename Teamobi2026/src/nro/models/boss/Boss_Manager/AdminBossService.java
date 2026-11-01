package nro.models.boss.Boss_Manager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.boss.Broly.SuperBroly;
import nro.models.consts.AppearType;
import nro.models.consts.BossStatus;
import nro.models.data.DataGame;
import nro.models.data.LocalManager;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.network.MySession;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.server.Manager;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.Util;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class AdminBossService {

    private static AdminBossService instance;
    public static final int PORT = 14446;

    public static AdminBossService gI() {
        if (instance == null) {
            instance = new AdminBossService();
        }
        return instance;
    }

    public static class BossTemplateInfo {
        public int bossId;
        public String name;
        public short head;
        public short body;
        public short leg;
        public int dame;
        public int hp;
        public int stages;
        public int[] mapJoin;
        public AppearType appearType;
        public int[] companions;
        public int parentBossId;
        public String category;
        public boolean isCustom;
    }

    private final Map<Integer, BossTemplateInfo> catalog = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> mapToBossIds = new LinkedHashMap<>();
    private final Map<Integer, BossData> customBossDataMap = new ConcurrentHashMap<>();
    private HttpServer server;

    private static final int[] ALL_BOSS_IDS = new int[]{
        BossID.KUKU,
        BossID.MAP_DAU_DINH,
        BossID.RAMBO,
        BossID.TIEU_DOI_TRUONG,
        BossID.SO_4,
        BossID.SO_3,
        BossID.SO_2,
        BossID.SO_1,
        BossID.TIEU_DOI_TRUONG_NM,
        BossID.SO_4_NM,
        BossID.SO_3_NM,
        BossID.SO_2_NM,
        BossID.SO_1_NM,
        BossID.FIDE,
        BossID.COOLER,
        BossID.ANDROID_19,
        BossID.DR_KORE,
        BossID.ANDROID_13,
        BossID.ANDROID_14,
        BossID.ANDROID_15,
        BossID.PIC,
        BossID.POC,
        BossID.KING_KONG,
        BossID.XEN_BO_HUNG,
        BossID.SIEU_BO_HUNG,
        BossID.XEN_CON_1,
        BossID.XEN_CON_2,
        BossID.XEN_CON_3,
        BossID.XEN_CON_4,
        BossID.XEN_CON_5,
        BossID.XEN_CON_6,
        BossID.XEN_CON_7,
        BossID.BOJACK,
        BossID.SUPER_BOJACK,
        BossID.BUJIN,
        BossID.KOGU,
        BossID.ZANGYA,
        BossID.BIDO,
        BossID.BLACK_GOKU,
        BossID.CUMBER,
        BossID.BABY,
        BossID.GOLDEN_FRIEZA,
        BossID.DEATH_BEAM_1,
        BossID.DEATH_BEAM_2,
        BossID.DEATH_BEAM_3,
        BossID.DEATH_BEAM_4,
        BossID.DEATH_BEAM_5,
        BossID.BROLY,
        BossID.SUPER_BROLY,
        BossID.TAU_PAY_PAY_DONG_NAM_KARIN,
        BossID.DRABURA,
        BossID.BUI_BUI,
        BossID.BUI_BUI_2,
        BossID.YA_CON,
        BossID.DRABURA_2,
        BossID.GOKU,
        BossID.CADIC,
        BossID.MABU_12H,
        BossID.DRABURA_3,
        BossID.MABU,
        BossID.SUPERBU,
        BossID.TAP_SU_0,
        BossID.TAP_SU_1,
        BossID.TAP_SU_2,
        BossID.TAP_SU_3,
        BossID.TAP_SU_4,
        BossID.TAN_BINH_5,
        BossID.TAN_BINH_0,
        BossID.TAN_BINH_1,
        BossID.TAN_BINH_2,
        BossID.TAN_BINH_3,
        BossID.TAN_BINH_4,
        BossID.CHIEN_BINH_5,
        BossID.CHIEN_BINH_0,
        BossID.CHIEN_BINH_1,
        BossID.CHIEN_BINH_2,
        BossID.CHIEN_BINH_3,
        BossID.CHIEN_BINH_4,
        BossID.DOI_TRUONG_5,
        BossID.AN_TROM,
        BossID.SOI_HEC_QUYN1,
        BossID.O_DO1,
        BossID.Virut,
        BossID.MAT_TROI,
        BossID.RONG_NHI,
        BossID.KHIDOT,
        BossID.NGUYETTHAN,
        BossID.NHATTHAN,
        BossID.BIMA,
        BossID.MATROI,
        BossID.DOI,
        BossID.ONG_GIA_NOEL,
        BossID.SON_TINH,
        BossID.THUY_TINH,
        BossID.LAN_CON
    };

    public synchronized void start() {
        try {
            initCatalog();
            if (server != null) {
                return;
            }
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/boss/state", this::handleState);
            server.createContext("/boss/summon", this::handleSummon);
            server.createContext("/boss/teleport", this::handleTeleport);
            server.createContext("/boss/reload_custom", this::handleReloadCustom);
            server.createContext("/boss/delete_custom", this::handleDeleteCustom);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            Logger.success("AdminBossService started on http://127.0.0.1:" + PORT + "\n");
        } catch (Exception e) {
            Logger.error("Lỗi khởi tạo AdminBossService: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private List<BossManager> getAllBossManagers() {
        List<BossManager> list = new ArrayList<>();
        list.add(BossManager.gI());
        list.add(BrolyManager.gI());
        list.add(FinalBossManager.gI());
        list.add(YardartManager.gI());
        list.add(OtherBossManager.gI());
        list.add(SkillSummonedManager.gI());
        list.add(TrungThuEventManager.gI());
        list.add(HalloweenEventManager.gI());
        list.add(ChristmasEventManager.gI());
        list.add(HungVuongEventManager.gI());
        list.add(LunarNewYearEventManager.gI());
        return list;
    }

    private List<Boss> getAllBossInstances() {
        List<Boss> result = new ArrayList<>();
        Set<Boss> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BossManager bm : getAllBossManagers()) {
            List<Boss> list = bm.getBosses();
            for (int i = 0; i < list.size(); i++) {
                Boss b = list.get(i);
                if (b != null && seen.add(b)) {
                    result.add(b);
                }
            }
        }
        return result;
    }

    private void removeTempBossFromManagers(Boss b) {
        if (b == null) {
            return;
        }
        if (b.bossAppearTogether != null) {
            for (Boss[] arr : b.bossAppearTogether) {
                if (arr != null) {
                    for (Boss child : arr) {
                        if (child != null) {
                            for (BossManager bm : getAllBossManagers()) {
                                bm.removeBoss(child);
                            }
                        }
                    }
                }
            }
        }
        for (BossManager bm : getAllBossManagers()) {
            bm.removeBoss(b);
        }
    }

    private Boss createNewBossById(int bossId, Zone zoneForSuperBroly) {
        try {
            if (customBossDataMap.containsKey(bossId)) {
                BossData bd = customBossDataMap.get(bossId);
                Boss cb = new Boss(bossId, false, false, bd);
                cb.templateBossId = bossId;
                return cb;
            }
            if (bossId == BossID.SUPER_BROLY) {
                Zone z = zoneForSuperBroly != null ? zoneForSuperBroly : MapService.gI().getMapWithRandZone(5);
                int x = z != null && z.map.mapWidth > 200 ? Util.nextInt(100, z.map.mapWidth - 100) : 200;
                int y = z != null ? z.map.yPhysicInTop(x, 100) : 300;
                SuperBroly sb = new SuperBroly(z, x, y);
                sb.templateBossId = BossID.SUPER_BROLY;
                return sb;
            }
            return BossManager.gI().createBoss(bossId);
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureCustomBossTable(Connection con) {
        try (PreparedStatement ps = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `custom_boss` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                + "`boss_id` INT NOT NULL UNIQUE,"
                + "`name` VARCHAR(100) NOT NULL,"
                + "`gender` TINYINT NOT NULL DEFAULT 0,"
                + "`hp` INT NOT NULL DEFAULT 50000000,"
                + "`dame` INT NOT NULL DEFAULT 50000,"
                + "`head_part` SMALLINT NOT NULL,"
                + "`body_part` SMALLINT NOT NULL,"
                + "`leg_part` SMALLINT NOT NULL,"
                + "`skills_json` TEXT NOT NULL,"
                + "`maps_json` TEXT NOT NULL,"
                + "`seconds_rest` INT NOT NULL DEFAULT 60,"
                + "`drawing_json` LONGTEXT DEFAULT NULL,"
                + "`created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci")) {
            ps.executeUpdate();
        } catch (Exception e) {
            Logger.error("Lỗi tạo bảng custom_boss: " + e.getMessage() + "\n");
        }
    }

    public synchronized void loadCustomBossesFromDb() {
        try (Connection con = LocalManager.getConnection()) {
            ensureCustomBossTable(con);
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM custom_boss ORDER BY id DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        int bossId = rs.getInt("boss_id");
                        String name = rs.getString("name");
                        byte gender = rs.getByte("gender");
                        int hp = rs.getInt("hp");
                        int dame = rs.getInt("dame");
                        short head = rs.getShort("head_part");
                        short body = rs.getShort("body_part");
                        short leg = rs.getShort("leg_part");
                        int secondsRest = rs.getInt("seconds_rest");
                        if (secondsRest < 5) {
                            secondsRest = 60;
                        }

                        // Parse skills_json: [[skillId, level, cooldown], ...]
                        List<int[]> skillList = new ArrayList<>();
                        JSONArray skArr = (JSONArray) JSONValue.parse(rs.getString("skills_json"));
                        if (skArr != null) {
                            for (Object o : skArr) {
                                if (o instanceof JSONArray sa && sa.size() >= 2) {
                                    int sId = Integer.parseInt(String.valueOf(sa.get(0)));
                                    int sLv = Math.max(1, Math.min(10, Integer.parseInt(String.valueOf(sa.get(1)))));
                                    int sCd = sa.size() >= 3 ? Math.max(100, Integer.parseInt(String.valueOf(sa.get(2)))) : 1000;
                                    skillList.add(new int[]{sId, sLv, sCd});
                                }
                            }
                        }
                        if (skillList.isEmpty()) {
                            skillList.add(new int[]{Skill.DRAGON, 7, 1000});
                            skillList.add(new int[]{Skill.KAMEJOKO, 7, 2000});
                        }
                        int[][] skillTemp = skillList.toArray(new int[0][]);

                        // Parse maps_json: [mapId, ...]
                        List<Integer> mapList = new ArrayList<>();
                        JSONArray mArr = (JSONArray) JSONValue.parse(rs.getString("maps_json"));
                        if (mArr != null) {
                            for (Object o : mArr) {
                                int mId = Integer.parseInt(String.valueOf(o));
                                if (getGameMap(mId) != null && !mapList.contains(mId)) {
                                    mapList.add(mId);
                                }
                            }
                        }
                        if (mapList.isEmpty()) {
                            mapList.add(5);
                        }
                        int[] mapJoin = new int[mapList.size()];
                        for (int i = 0; i < mapList.size(); i++) {
                            mapJoin[i] = mapList.get(i);
                        }

                        short[] outfit = new short[]{head, body, leg, -1, -1, -1};
                        String[] textS = new String[]{"|-1|Ta là " + name + ", kẻ mạnh nhất vũ trụ!"};
                        String[] textM = new String[]{"|-1|Xem chiêu thức của " + name + " đây!"};
                        String[] textE = new String[]{"|-1|Không thể nào... Ta sẽ còn trở lại!"};

                        BossData bd = new BossData(
                                name,
                                gender,
                                outfit,
                                Math.max(1, dame),
                                new int[]{Math.max(100, hp)},
                                mapJoin,
                                skillTemp,
                                textS,
                                textM,
                                textE,
                                secondsRest
                        );
                        customBossDataMap.put(bossId, bd);

                        BossTemplateInfo info = new BossTemplateInfo();
                        info.bossId = bossId;
                        info.name = name;
                        info.head = head;
                        info.body = body;
                        info.leg = leg;
                        info.dame = dame;
                        info.hp = hp;
                        info.stages = 1;
                        info.mapJoin = mapJoin;
                        info.appearType = AppearType.DEFAULT_APPEAR;
                        info.companions = null;
                        info.category = "Boss Tự Tạo (Custom)";
                        info.isCustom = true;
                        catalog.put(bossId, info);

                        for (int mId : mapJoin) {
                            mapToBossIds.computeIfAbsent(mId, k -> new ArrayList<>());
                            if (!mapToBossIds.get(mId).contains(bossId)) {
                                mapToBossIds.get(mId).add(0, bossId);
                            }
                        }

                        // Ensure at least one instance exists in BossManager
                        boolean hasInstance = false;
                        for (Boss b : getAllBossInstances()) {
                            int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
                            if (tplId == bossId) {
                                hasInstance = true;
                                if (b.data != null && b.data.length > 0) {
                                    b.data[0] = bd;
                                }
                                break;
                            }
                        }
                        if (!hasInstance) {
                            Boss created = new Boss(bossId, false, false, bd);
                            created.templateBossId = bossId;
                        }
                    } catch (Exception ex) {
                        Logger.error("Lỗi load 1 custom boss: " + ex.getMessage() + "\n");
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Lỗi loadCustomBossesFromDb: " + e.getMessage() + "\n");
        }
    }

    private void initCatalog() {
        catalog.clear();
        mapToBossIds.clear();
        List<Boss> existing = getAllBossInstances();
        Map<Integer, Boss> byTemplateId = new HashMap<>();
        for (Boss b : existing) {
            if (b.templateBossId != 0 && !byTemplateId.containsKey(b.templateBossId)) {
                byTemplateId.put(b.templateBossId, b);
            }
        }

        for (int bossId : ALL_BOSS_IDS) {
            try {
                Boss sample = byTemplateId.get(bossId);
                boolean createdTemp = false;
                if (sample == null) {
                    sample = createNewBossById(bossId, null);
                    createdTemp = true;
                }
                if (sample != null && sample.data != null && sample.data.length > 0) {
                    BossData d0 = sample.data[0];
                    BossTemplateInfo info = new BossTemplateInfo();
                    info.bossId = bossId;
                    info.name = cleanBossTemplateName(bossId, d0.getName());
                    short[] outfit = d0.getOutfit();
                    info.head = outfit != null && outfit.length > 0 ? outfit[0] : -1;
                    info.body = outfit != null && outfit.length > 1 ? outfit[1] : -1;
                    info.leg = outfit != null && outfit.length > 2 ? outfit[2] : -1;
                    info.dame = d0.getDame();
                    int[] hps = d0.getHp();
                    info.hp = (hps != null && hps.length > 0) ? hps[hps.length - 1] : 100;
                    info.stages = sample.data.length;
                    info.mapJoin = d0.getMapJoin() != null ? d0.getMapJoin() : new int[0];
                    info.appearType = d0.getTypeAppear();
                    info.companions = d0.getBossesAppearTogether();
                    info.category = categorizeBoss(bossId);
                    info.isCustom = false;
                    catalog.put(bossId, info);

                    for (int mId : info.mapJoin) {
                        mapToBossIds.computeIfAbsent(mId, k -> new ArrayList<>());
                        if (!mapToBossIds.get(mId).contains(bossId)) {
                            mapToBossIds.get(mId).add(bossId);
                        }
                    }
                }
                if (createdTemp && sample != null) {
                    removeTempBossFromManagers(sample);
                }
            } catch (Exception e) {
                Logger.error("Lỗi catalog boss " + bossId + ": " + e.getMessage() + "\n");
            }
        }

        for (BossTemplateInfo info : catalog.values()) {
            if (info.companions != null) {
                for (int compId : info.companions) {
                    BossTemplateInfo child = catalog.get(compId);
                    if (child != null && child.parentBossId == 0) {
                        child.parentBossId = info.bossId;
                    }
                }
            }
        }

        loadCustomBossesFromDb();
    }

    private String cleanBossTemplateName(int bossId, String rawName) {
        if (rawName == null) {
            return "Boss #" + bossId;
        }
        return switch (bossId) {
            case BossID.BROLY -> "Broly";
            case BossID.SUPER_BROLY -> "Super Broly";
            case BossID.AN_TROM -> "Ăn Trộm";
            case BossID.MAT_TROI -> "Mặt Trời";
            case BossID.Virut -> "Virut";
            case BossID.O_DO1 -> "Ở Dơ";
            case BossID.SOI_HEC_QUYN1 -> "Sói Hẹc Quyn";
            case BossID.RONG_NHI -> "Rồng Nhí";
            case BossID.BIMA -> "Bí Ma";
            case BossID.MATROI -> "Ma Trơi";
            case BossID.DOI -> "Dơi Nhí";
            case BossID.LAN_CON -> "Lân Con";
            default -> rawName.replaceAll("\\s*%d\\s*", "").trim();
        };
    }

    private String categorizeBoss(int bossId) {
        return switch (bossId) {
            case BossID.KUKU, BossID.MAP_DAU_DINH, BossID.RAMBO, BossID.FIDE,
                 BossID.TIEU_DOI_TRUONG, BossID.SO_4, BossID.SO_3, BossID.SO_2, BossID.SO_1,
                 BossID.TIEU_DOI_TRUONG_NM, BossID.SO_4_NM, BossID.SO_3_NM, BossID.SO_2_NM, BossID.SO_1_NM -> "Boss Nappa & Fide";
            case BossID.ANDROID_13, BossID.ANDROID_14, BossID.ANDROID_15,
                 BossID.ANDROID_19, BossID.DR_KORE, BossID.PIC, BossID.POC, BossID.KING_KONG,
                 BossID.XEN_BO_HUNG, BossID.SIEU_BO_HUNG,
                 BossID.XEN_CON_1, BossID.XEN_CON_2, BossID.XEN_CON_3, BossID.XEN_CON_4,
                 BossID.XEN_CON_5, BossID.XEN_CON_6, BossID.XEN_CON_7 -> "Boss Android & Xên Bọ Hùng";
            case BossID.BLACK_GOKU, BossID.CUMBER, BossID.COOLER, BossID.BABY,
                 BossID.BOJACK, BossID.SUPER_BOJACK, BossID.BUJIN, BossID.KOGU, BossID.ZANGYA, BossID.BIDO,
                 BossID.GOLDEN_FRIEZA, BossID.DEATH_BEAM_1, BossID.DEATH_BEAM_2, BossID.DEATH_BEAM_3,
                 BossID.DEATH_BEAM_4, BossID.DEATH_BEAM_5 -> "Boss Tương Lai / Cold / Siêu Cấp";
            case BossID.DRABURA, BossID.BUI_BUI, BossID.BUI_BUI_2, BossID.YA_CON,
                 BossID.DRABURA_2, BossID.GOKU, BossID.CADIC, BossID.MABU_12H, BossID.DRABURA_3,
                 BossID.MABU, BossID.SUPERBU -> "Boss Mabư 12h & 14h";
            case BossID.TAP_SU_0, BossID.TAP_SU_1, BossID.TAP_SU_2, BossID.TAP_SU_3, BossID.TAP_SU_4,
                 BossID.TAN_BINH_5, BossID.TAN_BINH_0, BossID.TAN_BINH_1, BossID.TAN_BINH_2, BossID.TAN_BINH_3, BossID.TAN_BINH_4,
                 BossID.CHIEN_BINH_5, BossID.CHIEN_BINH_0, BossID.CHIEN_BINH_1, BossID.CHIEN_BINH_2, BossID.CHIEN_BINH_3, BossID.CHIEN_BINH_4,
                 BossID.DOI_TRUONG_5 -> "Boss Hành Tinh Yardart";
            case BossID.BROLY, BossID.SUPER_BROLY, BossID.TAU_PAY_PAY_DONG_NAM_KARIN,
                 BossID.AN_TROM, BossID.SOI_HEC_QUYN1, BossID.O_DO1, BossID.Virut, BossID.MAT_TROI, BossID.RONG_NHI -> "Boss Broly & Mini Boss";
            default -> "Boss Sự Kiện";
        };
    }

    private Player findOnlineAdminPlayer(String username, String playerName, int accountId) {
        List<Player> players = Client.gI().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p == null) {
                continue;
            }
            MySession s = p.getSession();
            if (accountId > 0 && s != null && s.userId == accountId) {
                return p;
            }
            if (username != null && !username.isEmpty() && s != null && username.equalsIgnoreCase(s.uu)) {
                return p;
            }
            if (playerName != null && !playerName.isEmpty() && playerName.equalsIgnoreCase(p.name)) {
                return p;
            }
        }
        // Fallback: if there is an online admin player
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p != null && p.isAdmin()) {
                return p;
            }
        }
        return null;
    }

    private nro.models.map.Map getGameMap(int mapId) {
        for (int i = 0; i < Manager.MAPS.size(); i++) {
            nro.models.map.Map m = Manager.MAPS.get(i);
            if (m != null && m.mapId == mapId) {
                return m;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private JSONObject buildPlayerJson(Player p) {
        JSONObject obj = new JSONObject();
        if (p != null && p.zone != null && p.zone.map != null) {
            obj.put("online", true);
            obj.put("id", p.id);
            obj.put("name", p.name);
            obj.put("mapId", p.zone.map.mapId);
            obj.put("mapName", p.zone.map.mapName);
            obj.put("zoneId", p.zone.zoneId);
            obj.put("x", p.location != null ? p.location.x : 0);
            obj.put("y", p.location != null ? p.location.y : 0);
        } else {
            obj.put("online", false);
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private JSONObject buildActiveBossJson(Boss b) {
        JSONObject obj = new JSONObject();
        int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
        BossTemplateInfo tpl = catalog.get(tplId);
        obj.put("instanceHash", System.identityHashCode(b));
        obj.put("bossId", tplId);
        obj.put("runtimeId", b.id);
        obj.put("name", b.name != null ? b.name : (tpl != null ? tpl.name : ("Boss " + tplId)));
        obj.put("templateName", tpl != null ? tpl.name : (b.name != null ? b.name : ("Boss " + tplId)));
        obj.put("head", (int) b.getHead());
        obj.put("status", b.bossStatus != null ? b.bossStatus.name() : "UNKNOWN");
        obj.put("currentLevel", b.currentLevel);
        obj.put("hp", b.nPoint != null ? b.nPoint.hp : 0);
        obj.put("hpMax", b.nPoint != null ? b.nPoint.hpMax : 0);
        obj.put("dame", b.nPoint != null ? b.nPoint.dameg : 0);
        if (b.zone != null && b.zone.map != null) {
            obj.put("mapId", b.zone.map.mapId);
            obj.put("mapName", b.zone.map.mapName);
            obj.put("zoneId", b.zone.zoneId);
            obj.put("x", b.location != null ? b.location.x : 0);
            obj.put("y", b.location != null ? b.location.y : 0);
            obj.put("playersInZone", b.zone.getNumOfPlayers());
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private void handleState(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseParams(exchange);
            String username = params.getOrDefault("username", "");
            String playerName = params.getOrDefault("playerName", "");
            int accountId = parseInt(params.get("accountId"), -1);

            Player adminPlayer = findOnlineAdminPlayer(username, playerName, accountId);

            List<Boss> allInstances = getAllBossInstances();
            Map<Integer, List<Boss>> activeByMap = new HashMap<>();
            Map<Integer, List<Boss>> activeByTemplate = new HashMap<>();
            JSONArray activeBossesArr = new JSONArray();

            List<Boss> activeList = new ArrayList<>();
            for (Boss b : allInstances) {
                if (b != null && b.zone != null && b.zone.map != null && !b.isDie()) {
                    activeList.add(b);
                    int mId = b.zone.map.mapId;
                    activeByMap.computeIfAbsent(mId, k -> new ArrayList<>()).add(b);
                    int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
                    activeByTemplate.computeIfAbsent(tplId, k -> new ArrayList<>()).add(b);
                }
            }

            activeList.sort(Comparator
                    .comparingInt((Boss b) -> b.zone.map.mapId)
                    .thenComparingInt(b -> b.zone.zoneId));

            for (Boss b : activeList) {
                activeBossesArr.add(buildActiveBossJson(b));
            }

            JSONArray mapsArr = new JSONArray();
            JSONArray allMapsArr = new JSONArray();

            for (int i = 0; i < Manager.MAPS.size(); i++) {
                nro.models.map.Map m = Manager.MAPS.get(i);
                if (m == null) {
                    continue;
                }
                List<Integer> configuredIds = mapToBossIds.getOrDefault(m.mapId, Collections.emptyList());
                List<Boss> mapActive = activeByMap.getOrDefault(m.mapId, Collections.emptyList());

                JSONObject liteMap = new JSONObject();
                liteMap.put("mapId", m.mapId);
                liteMap.put("mapName", m.mapName);
                liteMap.put("planetId", (int) m.planetId);
                liteMap.put("zonesCount", m.zones != null ? m.zones.size() : 0);
                liteMap.put("configuredBossCount", configuredIds.size());
                liteMap.put("activeBossCount", mapActive.size());
                allMapsArr.add(liteMap);

                if (configuredIds.isEmpty() && mapActive.isEmpty()) {
                    continue;
                }

                JSONObject mapObj = new JSONObject();
                mapObj.put("mapId", m.mapId);
                mapObj.put("mapName", m.mapName);
                mapObj.put("planetId", (int) m.planetId);
                mapObj.put("zonesCount", m.zones != null ? m.zones.size() : 0);

                JSONArray cfgArr = new JSONArray();
                for (int bossId : configuredIds) {
                    BossTemplateInfo tpl = catalog.get(bossId);
                    if (tpl == null) {
                        continue;
                    }
                    JSONObject tplObj = new JSONObject();
                    tplObj.put("bossId", tpl.bossId);
                    tplObj.put("name", tpl.name);
                    tplObj.put("head", (int) tpl.head);
                    tplObj.put("body", (int) tpl.body);
                    tplObj.put("leg", (int) tpl.leg);
                    tplObj.put("dame", tpl.dame);
                    tplObj.put("hp", tpl.hp);
                    tplObj.put("stages", tpl.stages);
                    tplObj.put("category", tpl.category);
                    tplObj.put("isCustom", tpl.isCustom);
                    tplObj.put("appearType", tpl.appearType != null ? tpl.appearType.name() : "DEFAULT_APPEAR");
                    tplObj.put("parentBossId", tpl.parentBossId);

                    JSONArray activeInThisMap = new JSONArray();
                    for (Boss ab : mapActive) {
                        int abTpl = ab.templateBossId != 0 ? ab.templateBossId : (int) ab.id;
                        if (abTpl == bossId) {
                            activeInThisMap.add(buildActiveBossJson(ab));
                        }
                    }
                    tplObj.put("activeInMap", activeInThisMap);
                    cfgArr.add(tplObj);
                }
                mapObj.put("configuredBosses", cfgArr);

                JSONArray mapActiveArr = new JSONArray();
                for (Boss ab : mapActive) {
                    mapActiveArr.add(buildActiveBossJson(ab));
                }
                mapObj.put("activeBosses", mapActiveArr);
                mapsArr.add(mapObj);
            }

            JSONArray templatesArr = new JSONArray();
            for (BossTemplateInfo tpl : catalog.values()) {
                JSONObject tplObj = new JSONObject();
                tplObj.put("bossId", tpl.bossId);
                tplObj.put("name", tpl.name);
                tplObj.put("head", (int) tpl.head);
                tplObj.put("body", (int) tpl.body);
                tplObj.put("leg", (int) tpl.leg);
                tplObj.put("dame", tpl.dame);
                tplObj.put("hp", tpl.hp);
                tplObj.put("stages", tpl.stages);
                tplObj.put("category", tpl.category);
                tplObj.put("isCustom", tpl.isCustom);
                tplObj.put("appearType", tpl.appearType != null ? tpl.appearType.name() : "DEFAULT_APPEAR");
                tplObj.put("parentBossId", tpl.parentBossId);

                JSONArray mapsJoinArr = new JSONArray();
                for (int mId : tpl.mapJoin) {
                    nro.models.map.Map gm = getGameMap(mId);
                    JSONObject mInfo = new JSONObject();
                    mInfo.put("mapId", mId);
                    mInfo.put("mapName", gm != null ? gm.mapName : ("Map " + mId));
                    mapsJoinArr.add(mInfo);
                }
                tplObj.put("maps", mapsJoinArr);

                List<Boss> activeForTpl = activeByTemplate.getOrDefault(tpl.bossId, Collections.emptyList());
                JSONArray activeTplArr = new JSONArray();
                for (Boss ab : activeForTpl) {
                    activeTplArr.add(buildActiveBossJson(ab));
                }
                tplObj.put("activeInstances", activeTplArr);
                templatesArr.add(tplObj);
            }

            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("player", buildPlayerJson(adminPlayer));
            resp.put("maps", mapsArr);
            resp.put("allMaps", allMapsArr);
            resp.put("bossTemplates", templatesArr);
            resp.put("activeBosses", activeBossesArr);
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Lỗi lấy trạng thái Boss: " + e.getMessage());
        }
    }

    private Boss findIdleBossInstance(int bossId) {
        for (Boss b : getAllBossInstances()) {
            int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
            if (tplId == bossId && (b.zone == null || b.isDie() || b.bossStatus == BossStatus.REST)) {
                return b;
            }
        }
        return null;
    }

    private Boss summonSingleBossToZone(int bossId, Zone targetZone) {
        Boss boss = findIdleBossInstance(bossId);
        if (boss == null) {
            boss = createNewBossById(bossId, targetZone);
        }
        if (boss == null) {
            return null;
        }
        if (boss.zone != null) {
            try {
                ChangeMapService.gI().exitMap(boss);
            } catch (Exception ignored) {
            }
        }
        boss.lastZone = null;
        boss.currentLevel = -1;
        boss.summonedByAdmin = true;
        boss.adminSummonTime = System.currentTimeMillis();
        boss.adminTargetZone = targetZone;
        boss.respawn();
        boss.joinMap();
        return boss;
    }

    @SuppressWarnings("unchecked")
    private void handleSummon(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseParams(exchange);
            String username = params.getOrDefault("username", "");
            String playerName = params.getOrDefault("playerName", "");
            int accountId = parseInt(params.get("accountId"), -1);
            int mapId = parseInt(params.get("mapId"), -1);
            int zoneId = parseInt(params.get("zoneId"), -1);
            int bossId = parseInt(params.get("bossId"), 0);
            boolean teleport = "1".equals(params.get("teleport")) || "true".equalsIgnoreCase(params.get("teleport"));

            Player adminPlayer = findOnlineAdminPlayer(username, playerName, accountId);

            // If mapId < 0 and bossId != 0, pick the boss's first pre-configured map
            if (mapId < 0 && bossId != 0) {
                BossTemplateInfo tpl = catalog.get(bossId);
                if (tpl != null && tpl.mapJoin != null && tpl.mapJoin.length > 0) {
                    mapId = tpl.mapJoin[0];
                }
            }

            nro.models.map.Map targetMap = getGameMap(mapId);
            if (targetMap == null || targetMap.zones == null || targetMap.zones.isEmpty()) {
                sendError(exchange, "Không tìm thấy Map hợp lệ (ID: " + mapId + ")!");
                return;
            }

            Zone targetZone;
            if (zoneId >= 0 && zoneId < targetMap.zones.size()) {
                targetZone = targetMap.zones.get(zoneId);
            } else if (adminPlayer != null && adminPlayer.zone != null && adminPlayer.zone.map != null && adminPlayer.zone.map.mapId == mapId) {
                targetZone = adminPlayer.zone;
            } else {
                // Choose a zone that preferably has no bosses yet (starting from zone 2 if normal map, else zone 0)
                int startIdx = targetMap.zones.size() > 2 ? 2 : 0;
                Zone chosen = null;
                for (int i = startIdx; i < targetMap.zones.size(); i++) {
                    if (targetMap.zones.get(i).getBosses().isEmpty()) {
                        chosen = targetMap.zones.get(i);
                        break;
                    }
                }
                if (chosen == null) {
                    chosen = targetMap.zones.get(startIdx);
                }
                targetZone = chosen;
            }

            List<Integer> toSummonIds = new ArrayList<>();
            if (bossId == 0) {
                // Summon all pre-configured primary bosses for this map
                List<Integer> cfg = mapToBossIds.getOrDefault(mapId, Collections.emptyList());
                Set<Integer> added = new LinkedHashSet<>();
                for (int cfgId : cfg) {
                    BossTemplateInfo tpl = catalog.get(cfgId);
                    if (tpl != null && tpl.parentBossId != 0 && tpl.appearType == AppearType.APPEAR_WITH_ANOTHER && cfg.contains(tpl.parentBossId)) {
                        // Will be spawned automatically by parentBoss
                        continue;
                    }
                    if (tpl != null && tpl.parentBossId != 0 && tpl.appearType == AppearType.CALL_BY_ANOTHER && cfg.contains(tpl.parentBossId)) {
                        continue;
                    }
                    added.add(cfgId);
                }
                if (added.isEmpty() && !cfg.isEmpty()) {
                    added.addAll(cfg);
                }
                toSummonIds.addAll(added);
            } else {
                toSummonIds.add(bossId);
            }

            if (toSummonIds.isEmpty()) {
                sendError(exchange, "Map " + targetMap.mapName + " không có Boss nào được thiết lập sẵn! Bạn có thể chọn 1 Boss cụ thể để gọi vào Map này.");
                return;
            }

            List<Boss> summonedBosses = new ArrayList<>();
            for (int idToSpawn : toSummonIds) {
                Boss spawned = summonSingleBossToZone(idToSpawn, targetZone);
                if (spawned != null && spawned.zone != null) {
                    summonedBosses.add(spawned);
                }
            }

            if (summonedBosses.isEmpty()) {
                sendError(exchange, "Không thể tạo Boss trong " + targetMap.mapName + " khu " + targetZone.zoneId + "!");
                return;
            }

            Boss primaryBoss = summonedBosses.get(0);
            int targetX = primaryBoss.location != null && primaryBoss.location.x > 0
                    ? primaryBoss.location.x
                    : (targetZone.map.mapWidth > 200 ? targetZone.map.mapWidth / 2 : 150);
            int targetY = primaryBoss.location != null && primaryBoss.location.y > 0
                    ? primaryBoss.location.y
                    : targetZone.map.yPhysicInTop(targetX, 100);

            boolean teleported = false;
            if (teleport && adminPlayer != null) {
                teleportPlayerToZone(adminPlayer, targetZone, targetX, targetY);
                teleported = true;
                Service.gI().sendThongBao(adminPlayer,
                        "Đã gọi " + summonedBosses.size() + " Boss và dịch chuyển tới "
                        + targetZone.map.mapName + " khu " + targetZone.zoneId);
            } else if (adminPlayer != null) {
                Service.gI().sendThongBao(adminPlayer,
                        "Đã gọi Boss [" + primaryBoss.name + "] tại "
                        + targetZone.map.mapName + " khu " + targetZone.zoneId);
            }

            JSONArray spawnedArr = new JSONArray();
            for (Boss b : targetZone.getBosses().stream().filter(p -> p instanceof Boss && !p.isDie()).map(p -> (Boss) p).toList()) {
                spawnedArr.add(buildActiveBossJson(b));
            }

            StringBuilder names = new StringBuilder();
            for (int i = 0; i < summonedBosses.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(summonedBosses.get(i).name);
            }

            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("teleported", teleported);
            resp.put("mapId", targetZone.map.mapId);
            resp.put("mapName", targetZone.map.mapName);
            resp.put("zoneId", targetZone.zoneId);
            resp.put("x", targetX);
            resp.put("y", targetY);
            resp.put("summonedBosses", spawnedArr);
            resp.put("player", buildPlayerJson(adminPlayer));
            resp.put("message", "Đã gọi Boss [" + names + "] tại " + targetZone.map.mapName + " (Map " + targetZone.map.mapId + ") - Khu " + targetZone.zoneId
                    + (teleported ? " và dịch chuyển nhân vật tới khu có Boss!" : "!"));
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Lỗi gọi Boss: " + e.getMessage());
        }
    }

    private void teleportPlayerToZone(Player player, Zone targetZone, int x, int y) {
        if (player == null || targetZone == null) {
            return;
        }
        if (player.isDie()) {
            Service.gI().hsChar(player, player.nPoint.hpMax, player.nPoint.mpMax);
        }
        int safeX = x > 0 ? x : (targetZone.map.mapWidth > 200 ? targetZone.map.mapWidth / 2 : 150);
        int safeY = y > 0 ? y : targetZone.map.yPhysicInTop(safeX, 100);
        ChangeMapService.gI().changeMapYardrat(player, targetZone, safeX, safeY);
    }

    @SuppressWarnings("unchecked")
    private void handleTeleport(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseParams(exchange);
            String username = params.getOrDefault("username", "");
            String playerName = params.getOrDefault("playerName", "");
            int accountId = parseInt(params.get("accountId"), -1);
            int instanceHash = parseInt(params.get("instanceHash"), 0);
            int bossId = parseInt(params.get("bossId"), 0);
            int mapId = parseInt(params.get("mapId"), -1);
            int zoneId = parseInt(params.get("zoneId"), -1);
            int x = parseInt(params.get("x"), -1);
            int y = parseInt(params.get("y"), -1);

            Player adminPlayer = findOnlineAdminPlayer(username, playerName, accountId);

            Boss targetBoss = null;
            if (instanceHash != 0) {
                for (Boss b : getAllBossInstances()) {
                    if (b != null && System.identityHashCode(b) == instanceHash && b.zone != null && !b.isDie()) {
                        targetBoss = b;
                        break;
                    }
                }
            }
            if (targetBoss == null && bossId != 0) {
                for (Boss b : getAllBossInstances()) {
                    int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
                    if (tplId == bossId && b.zone != null && !b.isDie()) {
                        if (mapId < 0 || b.zone.map.mapId == mapId) {
                            if (zoneId < 0 || b.zone.zoneId == zoneId) {
                                targetBoss = b;
                                break;
                            }
                        }
                    }
                }
            }

            Zone targetZone = null;
            if (targetBoss != null && targetBoss.zone != null) {
                targetZone = targetBoss.zone;
                if (x <= 0 && targetBoss.location != null) {
                    x = targetBoss.location.x;
                }
                if (y <= 0 && targetBoss.location != null) {
                    y = targetBoss.location.y;
                }
            } else if (mapId >= 0) {
                nro.models.map.Map gm = getGameMap(mapId);
                if (gm != null && gm.zones != null && !gm.zones.isEmpty()) {
                    if (zoneId >= 0 && zoneId < gm.zones.size()) {
                        targetZone = gm.zones.get(zoneId);
                    } else {
                        // Look for any zone in this map that currently has a boss
                        for (Zone z : gm.zones) {
                            if (!z.getBosses().isEmpty()) {
                                targetZone = z;
                                Player bp = z.getBosses().get(0);
                                if (x <= 0 && bp.location != null) {
                                    x = bp.location.x;
                                }
                                if (y <= 0 && bp.location != null) {
                                    y = bp.location.y;
                                }
                                break;
                            }
                        }
                        if (targetZone == null) {
                            targetZone = gm.zones.get(0);
                        }
                    }
                }
            }

            if (targetZone == null) {
                sendError(exchange, "Không tìm thấy Khu chứa Boss để dịch chuyển!");
                return;
            }

            if (x <= 0) {
                if (!targetZone.getBosses().isEmpty() && targetZone.getBosses().get(0).location != null) {
                    x = targetZone.getBosses().get(0).location.x;
                    y = targetZone.getBosses().get(0).location.y;
                } else {
                    x = targetZone.map.mapWidth > 200 ? targetZone.map.mapWidth / 2 : 200;
                    y = targetZone.map.yPhysicInTop(x, 100);
                }
            }
            if (y <= 0) {
                y = targetZone.map.yPhysicInTop(x, 100);
            }

            boolean onlineTeleported = false;
            if (adminPlayer != null) {
                teleportPlayerToZone(adminPlayer, targetZone, x, y);
                onlineTeleported = true;
                String bossLabel = targetBoss != null ? (" tới Boss [" + targetBoss.name + "]") : "";
                Service.gI().sendThongBao(adminPlayer,
                        "Đã dịch chuyển" + bossLabel + " tại " + targetZone.map.mapName + " khu " + targetZone.zoneId);
            }

            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("onlineTeleported", onlineTeleported);
            resp.put("mapId", targetZone.map.mapId);
            resp.put("mapName", targetZone.map.mapName);
            resp.put("zoneId", targetZone.zoneId);
            resp.put("x", x);
            resp.put("y", y);
            resp.put("player", buildPlayerJson(adminPlayer));
            resp.put("message", onlineTeleported
                    ? ("Đã dịch chuyển nhân vật [" + adminPlayer.name + "] tới " + targetZone.map.mapName + " (Map " + targetZone.map.mapId + ") - Khu " + targetZone.zoneId + " (" + x + ", " + y + ")!")
                    : ("Đã cập nhật vị trí về " + targetZone.map.mapName + " (Map " + targetZone.map.mapId + ") - Khu " + targetZone.zoneId + " (" + x + ", " + y + ")!"));
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Lỗi dịch chuyển: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleReloadCustom(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseParams(exchange);
            String username = params.getOrDefault("username", "");
            String playerName = params.getOrDefault("playerName", "");
            int accountId = parseInt(params.get("accountId"), -1);
            boolean reloadPart = "1".equals(params.get("reloadPart")) || "true".equalsIgnoreCase(params.get("reloadPart"));
            int spawnBossId = parseInt(params.get("bossId"), 0);
            boolean spawnNow = "1".equals(params.get("spawnNow")) || "true".equalsIgnoreCase(params.get("spawnNow"));
            boolean teleport = "1".equals(params.get("teleport")) || "true".equalsIgnoreCase(params.get("teleport"));
            int mapId = parseInt(params.get("mapId"), -1);
            int zoneId = parseInt(params.get("zoneId"), -1);

            if (reloadPart) {
                Manager.loadPart();
                List<Player> onlinePlayers = Client.gI().getPlayers();
                for (int i = 0; i < onlinePlayers.size(); i++) {
                    Player p = onlinePlayers.get(i);
                    if (p != null && p.getSession() != null) {
                        try {
                            DataGame.updateData(p.getSession());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            loadCustomBossesFromDb();

            Player adminPlayer = findOnlineAdminPlayer(username, playerName, accountId);
            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("reloadedPart", reloadPart);

            if (spawnNow && spawnBossId != 0 && catalog.containsKey(spawnBossId)) {
                BossTemplateInfo tpl = catalog.get(spawnBossId);
                if (mapId < 0 && tpl.mapJoin != null && tpl.mapJoin.length > 0) {
                    mapId = tpl.mapJoin[0];
                }
                nro.models.map.Map targetMap = getGameMap(mapId);
                if (targetMap != null && targetMap.zones != null && !targetMap.zones.isEmpty()) {
                    Zone targetZone;
                    if (zoneId >= 0 && zoneId < targetMap.zones.size()) {
                        targetZone = targetMap.zones.get(zoneId);
                    } else if (adminPlayer != null && adminPlayer.zone != null && adminPlayer.zone.map != null && adminPlayer.zone.map.mapId == mapId) {
                        targetZone = adminPlayer.zone;
                    } else {
                        int startIdx = targetMap.zones.size() > 2 ? 2 : 0;
                        targetZone = targetMap.zones.get(startIdx);
                    }
                    Boss spawned = summonSingleBossToZone(spawnBossId, targetZone);
                    if (spawned != null && spawned.zone != null) {
                        int targetX = spawned.location != null && spawned.location.x > 0
                                ? spawned.location.x
                                : (targetZone.map.mapWidth > 200 ? targetZone.map.mapWidth / 2 : 150);
                        int targetY = spawned.location != null && spawned.location.y > 0
                                ? spawned.location.y
                                : targetZone.map.yPhysicInTop(targetX, 100);
                        boolean teleported = false;
                        if (teleport && adminPlayer != null) {
                            teleportPlayerToZone(adminPlayer, targetZone, targetX, targetY);
                            teleported = true;
                            Service.gI().sendThongBao(adminPlayer,
                                    "Đã tạo & gọi Boss [" + spawned.name + "] tại "
                                    + targetZone.map.mapName + " khu " + targetZone.zoneId);
                        }
                        resp.put("spawned", buildActiveBossJson(spawned));
                        resp.put("teleported", teleported);
                        resp.put("mapId", targetZone.map.mapId);
                        resp.put("mapName", targetZone.map.mapName);
                        resp.put("zoneId", targetZone.zoneId);
                        resp.put("x", targetX);
                        resp.put("y", targetY);
                    }
                }
            }

            resp.put("player", buildPlayerJson(adminPlayer));
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Lỗi đồng bộ Boss mới: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleDeleteCustom(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = parseParams(exchange);
            int bossId = parseInt(params.get("bossId"), 0);
            if (bossId == 0) {
                sendError(exchange, "Thiếu bossId để xóa!");
                return;
            }
            customBossDataMap.remove(bossId);
            catalog.remove(bossId);
            for (List<Integer> list : mapToBossIds.values()) {
                list.remove(Integer.valueOf(bossId));
            }
            for (Boss b : getAllBossInstances()) {
                int tplId = b.templateBossId != 0 ? b.templateBossId : (int) b.id;
                if (tplId == bossId) {
                    if (b.zone != null) {
                        try {
                            ChangeMapService.gI().exitMap(b);
                        } catch (Exception ignored) {
                        }
                    }
                    removeTempBossFromManagers(b);
                }
            }
            JSONObject resp = new JSONObject();
            resp.put("success", true);
            resp.put("message", "Đã xóa Boss tự tạo #" + bossId + " khỏi hệ thống!");
            sendJson(exchange, 200, resp);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Lỗi xóa Boss tự tạo: " + e.getMessage());
        }
    }

    private Map<String, String> parseParams(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null && !query.isEmpty()) {
            parseQueryString(query, map);
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            String body = baos.toString(StandardCharsets.UTF_8);
            if (!body.isEmpty()) {
                parseQueryString(body, map);
            }
        }
        return map;
    }

    private void parseQueryString(String qs, Map<String, String> out) {
        String[] pairs = qs.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                out.put(k, v);
            }
        }
    }

    private int parseInt(String val, int def) {
        if (val == null || val.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private void sendError(HttpExchange exchange, String msg) throws IOException {
        JSONObject obj = new JSONObject();
        obj.put("success", false);
        obj.put("message", msg);
        sendJson(exchange, 200, obj);
    }

    private void sendJson(HttpExchange exchange, int status, JSONObject obj) throws IOException {
        byte[] bytes = obj.toJSONString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

