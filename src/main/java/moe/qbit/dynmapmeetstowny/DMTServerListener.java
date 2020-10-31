package moe.qbit.dynmapmeetstowny;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.PluginManager;

class DMTServerListener implements Listener {

    private final DynmapMeetsTowny dynmapMeetsTowny;

    public DMTServerListener(DynmapMeetsTowny dynmapMeetsTowny) {
        this.dynmapMeetsTowny = dynmapMeetsTowny;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    private void onDynMapReload(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("dynmap")) {
            PluginManager pluginManager = dynmapMeetsTowny.getServer().getPluginManager();
            if (pluginManager.isPluginEnabled("Dynmap-Meets-Towny")) {
                pluginManager.disablePlugin(dynmapMeetsTowny);
                pluginManager.enablePlugin(dynmapMeetsTowny);
            }
        }
    }
}
