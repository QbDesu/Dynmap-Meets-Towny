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
    private DynmapAPI dynmapAPI;
    private boolean sendLoginMessages = true;
    private boolean sendLogoutMessages = true;
    private Mustache messageTemplate;

    public TownyChatListener(){}

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
        if(sendLoginMessages && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            Player player = event.getPlayer();
            this.dynmapAPI.postPlayerJoinQuitToWeb(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if(sendLogoutMessages) {
            Player player = event.getPlayer();
            dynmapAPI.postPlayerJoinQuitToWeb(player, false);
        }
    }

    public void setDynmapAPI(DynmapAPI dynmapAPI) {
        this.dynmapAPI = dynmapAPI;
    }

    public void setMessageTemplate(Mustache messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public void setSendLoginMessages(boolean sendLoginMessages) {
        this.sendLoginMessages = sendLoginMessages;
    }

    public void setSendLogoutMessages(boolean sendLogoutMessages) {
        this.sendLogoutMessages = sendLogoutMessages;
    }
}
