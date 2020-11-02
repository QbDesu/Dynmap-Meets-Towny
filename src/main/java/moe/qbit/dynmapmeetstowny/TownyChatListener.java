package moe.qbit.dynmapmeetstowny;

import com.github.mustachejava.Mustache;
import com.google.common.collect.Maps;
import com.palmergames.bukkit.TownyChat.channels.channelTypes;
import com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapWebChatEvent;

import java.util.Map;

public class TownyChatListener implements Listener {
    private static final boolean LOGIN_MESSAGES = true;
    private static final boolean LOGOUT_MESSAGES = true;
    private final DynmapAPI dynmapAPI;
    private final Mustache messageTemplate;

    public TownyChatListener(DynmapAPI dynmapAPI, Mustache messageTemplate){
        this.dynmapAPI = dynmapAPI;
        this.messageTemplate = messageTemplate;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSeverMessage(AsyncChatHookEvent event) {
        if(!event.isCancelled() && event.getChannel().getType() == channelTypes.GLOBAL) {
            this.dynmapAPI.postPlayerMessageToWeb(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWebChatEvent(DynmapWebChatEvent event) {
        if(!event.isCancelled() && !event.isProcessed()) {
            event.setProcessed();
            Map<String,Object> properties = Maps.newHashMap();
            // TODO: titles etc.
            properties.put("name", event.getName());
            properties.put("message", event.getMessage());

            String msg = TemplateHelper.render(messageTemplate, properties);
            Bukkit.broadcastMessage(msg);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if(LOGIN_MESSAGES && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            Player player = event.getPlayer();
            dynmapAPI.postPlayerJoinQuitToWeb(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if(LOGOUT_MESSAGES) {
            Player player = event.getPlayer();
            dynmapAPI.postPlayerJoinQuitToWeb(player, false);
        }
    }
}
