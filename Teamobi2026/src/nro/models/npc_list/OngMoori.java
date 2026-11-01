package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.map.service.NpcService;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.services_func.Input;

public class OngMoori extends OngGohan {

    public OngMoori(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Con cố gắng theo Trưởng Lão Guru học thành tài, đừng lo lắng cho ta.",
                        "Nhập mã\nquà tặng");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (player.idMark.isBaseMenu()) {
                switch (select) {
                    case 0 -> {
                        Input.gI().createFormGiftCode(player);
                    }
                }
            }
        }
    }
}
