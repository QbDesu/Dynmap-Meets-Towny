package moe.qbit.dynmapmeetstowny;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Charsets;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class DynmapMeetsTowny extends JavaPlugin {
    public static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();
    public static Logger LOGGER;

    private InfoWindow info_window_template;
    private static final String NATION_NONE = "_none_";

    Plugin dynmap;
    DynmapAPI dynmapAPI;
    MarkerAPI markerapi;
    Towny towny;
    TownyUniverse tuniv;

    int townblocksize;
    boolean reload = false;
    private boolean playersbytown;
    private boolean playersbynation;
    private boolean dynamicNationColorsEnabled;
    
    private HashMap<Town, Long> townBankCache = new HashMap<>();
    private HashMap<Town, Double> townBankBalanceCache = new HashMap<>();

    FileConfiguration cfg;
    MarkerSet set;

    long updperiod;
    boolean use3d;
    AreaStyle defstyle;
    Map<String, AreaStyle> cusstyle;
    Map<String, AreaStyle> nationstyle;
    Set<String> visible;
    Set<String> hidden;
    boolean show_shops;
    boolean show_arenas;
    boolean show_embassies;
    boolean show_wilds;
    boolean stop;
    
    @Override
    public void onLoad() {
        LOGGER = this.getLogger();
        this.initInfoWindowTemplate();
    }

    private void initInfoWindowTemplate(){
        InputStream info_window_template_stream = this.getResource("info_window_template.html");
        assert info_window_template_stream != null;
        Mustache template = MUSTACHE_FACTORY.compile(new InputStreamReader(info_window_template_stream, Charsets.UTF_8),"info_window");
        this.info_window_template = new InfoWindow(template);
    }

    private class TownyUpdate implements Runnable {
        public void run() {
            if(!stop) {
                updateTowns();
                updateTownPlayerSets();
                updateNationPlayerSets();
                if (TownySettings.isUsingEconomy())
                    updateTownBanks(System.currentTimeMillis());
            }
        }
    }
    
    private void updateTown(Town town) {
        if(!playersbytown) return;
        Set<String> plids = new HashSet<String>();
        List<Resident> res = town.getResidents();
        for(Resident r : res) {
            plids.add(r.getName());
        }
        String setid = "towny.town." + town.getName();
        PlayerSet set = markerapi.getPlayerSet(setid);  /* See if set exists */
        if(set == null) {
            set = markerapi.createPlayerSet(setid, true, plids, false);
            LOGGER.info("Added player visibility set '" + setid + "' for town " + town.getName());
        }
        else {
            set.setPlayers(plids);
        }
    }

    private void updateTownPlayerSets() {
        if(!playersbytown) return;
        for(Town t : TownyUniverse.getInstance().getTownsMap().values()) {
            updateTown(t);
        }
    }

    private void updateNation(Nation nat) {
        if(!playersbynation) return;
        Set<String> plids = new HashSet<String>();
        List<Resident> res = nat.getResidents();
        for(Resident r : res) {
            plids.add(r.getName());
        }
        String setid = "towny.nation." + nat.getName();
        PlayerSet set = markerapi.getPlayerSet(setid);  /* See if set exists */
        if(set == null) {
            set = markerapi.createPlayerSet(setid, true, plids, false);
            LOGGER.info("Added player visibility set '" + setid + "' for nation " + nat.getName());
        }
        else {
            set.setPlayers(plids);
        }
    }

    private void updateNationPlayerSets() {
        if(!playersbynation) return;
        for(Nation n : TownyUniverse.getInstance().getNationsMap().values()) {
            updateNation(n);
        }
    }

    private void updateTownBanks(long time) {
		for (Town town : TownyUniverse.getInstance().getTownsMap().values())
			if (townBankCache.containsKey(town)) {
				if (time > townBankCache.get(town))
					updateTownBank(town);    			
    		} else {
				updateTownBank(town);
    		}
    }
    
    private void updateTownBank(Town town) {

    	double balance = 0.0;
        try {
			balance = town.getAccount().getHoldingBalance();
		} catch (EconomyException | NullPointerException e) {
			return;
		}
		townBankBalanceCache.put(town, balance);
		townBankCache.put(town, System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(300000, 360000)); // 5-6 Minutes added before next refresh.
    }
    
    private Map<String, AreaMarker> resareas = new HashMap<String, AreaMarker>();
    private Map<String, Marker> resmark = new HashMap<String, Marker>();
    
    private boolean isVisible(String id, String worldname) {
        if((visible != null) && (visible.size() > 0)) {
            if(!visible.contains(id) && !visible.contains("world:" + worldname)) {
                return false;
            }
        }
        if((hidden != null) && (hidden.size() > 0)) {
            return !hidden.contains(id) && !hidden.contains("world:" + worldname);
        }
        return true;
    }
        
    private void addStyle(Town town, String resid, String natid, AreaMarker m, TownBlockType btype) {
        AreaStyle as = cusstyle.get(resid);	/* Look up custom style for town, if any */
        AreaStyle ns = nationstyle.get(natid);	/* Look up nation style, if any */
        
        if(btype == null) {
            m.setLineStyle(defstyle.getStrokeWeight(as, ns), defstyle.getStrokeOpacity(as, ns), defstyle.getStrokeColor(as, ns));
        }
        else {
            m.setLineStyle(1, 0, 0);
        }
        m.setFillStyle(defstyle.getFillOpacity(as, ns), defstyle.getFillColor(as, ns, btype));
        double y = defstyle.getY(as, ns);
        m.setRangeY(y, y);
        m.setBoostFlag(defstyle.getBoost(as, ns));

        //If dynamic nation colors is enabled, read the color from the nation object
        try {
            if(dynamicNationColorsEnabled && town.hasNation()) {
                Nation nation = town.getNation();

                if(nation.getMapColorHexCode() != null) {
                    String colorAsString = nation.getMapColorHexCode();
                    int nationColor =  Integer.parseInt(colorAsString, 16);

                    //Set stroke style
                    double strokeOpacity = m.getLineOpacity();
                    int strokeWeight = m.getLineWeight();
                    m.setLineStyle(strokeWeight, strokeOpacity, nationColor);

                    //Set fill style
                    double fillOpacity = m.getFillOpacity();
                    m.setFillStyle(fillOpacity, nationColor);
                }
            }
        } catch (Exception ex) {}

    }

    private MarkerIcon getMarkerIcon(Town town) {
        String id = town.getName();
        AreaStyle as = cusstyle.get(id);
        String natid = NATION_NONE;
        try {
        	if(town.getNation() != null)
        		natid = town.getNation().getName();
        } catch (Exception ex) {}
        AreaStyle ns = nationstyle.get(natid);
        
        if(town.isCapital())
            return defstyle.getCapitalMarker(as, ns);
        else
            return defstyle.getHomeMarker(as, ns);
    }
 
    enum direction { XPLUS, ZPLUS, XMINUS, ZMINUS };
        
    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    private int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[] { x, y });
        
        while(!stack.isEmpty()) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if(src.getFlag(x, y)) { /* Set in src */
                src.setFlag(x, y, false);   /* Clear source */
                dest.setFlag(x, y, true);   /* Set in destination */
                cnt++;
                if(src.getFlag(x+1, y))
                    stack.push(new int[] { x+1, y });
                if(src.getFlag(x-1, y))
                    stack.push(new int[] { x-1, y });
                if(src.getFlag(x, y+1))
                    stack.push(new int[] { x, y+1 });
                if(src.getFlag(x, y-1))
                    stack.push(new int[] { x, y-1 });
            }
        }
        return cnt;
    }
    
    /* Handle specific town */
    private void handleTown(Town town, Map<String, AreaMarker> newmap, Map<String, Marker> newmark, TownBlockType btype) {
        String name = town.getName();
        double[] x = null;
        double[] z = null;
        int poly_index = 0; /* Index of polygon for given town */
                
        /* Handle areas */
    	Collection<TownBlock> blocks = town.getTownBlocks();
    	if(blocks.isEmpty())
    	    return;
        /* Build popup */
        String desc = this.info_window_template.render(town, btype);

    	HashMap<String, TileFlags> blkmaps = new HashMap<String, TileFlags>();
        LinkedList<TownBlock> nodevals = new LinkedList<TownBlock>();
        TownyWorld curworld = null;
        TileFlags curblks = null;
        boolean vis = false;
    	/* Loop through blocks: set flags on blockmaps for worlds */
    	for(TownBlock b : blocks) {
    	    /* If we're scanning for specific type, and this isn't it, skip */
    	    if((btype != null) && (b.getType() != btype)) {
    	        continue;
    	    }
    	    if(b.getWorld() != curworld) { /* Not same world */
    	        String wname = b.getWorld().getName();
    	        vis = isVisible(name, wname);  /* See if visible */
    	        if(vis) {  /* Only accumulate for visible areas */
    	            curblks = blkmaps.get(wname);  /* Find existing */
    	            if(curblks == null) {
    	                curblks = new TileFlags();
    	                blkmaps.put(wname, curblks);   /* Add fresh one */
    	            }
    	        }
    	        curworld = b.getWorld();
    	    }
    	    if(vis) {
    	        curblks.setFlag(b.getX(), b.getZ(), true); /* Set flag for block */
    	        nodevals.addLast(b);
    	    }
    	}
        /* Loop through until we don't find more areas */
        while(nodevals != null) {
            LinkedList<TownBlock> ournodes = null;
            LinkedList<TownBlock> newlist = null;
            TileFlags ourblks = null;
            int minx = Integer.MAX_VALUE;
            int minz = Integer.MAX_VALUE;
            for(TownBlock node : nodevals) {
                int nodex = node.getX();
                int nodez = node.getZ();
                if(ourblks == null) {   /* If not started, switch to world for this block first */
                    if(node.getWorld() != curworld) {
                        curworld = node.getWorld();
                        curblks = blkmaps.get(curworld.getName());
                    }
                }
                /* If we need to start shape, and this block is not part of one yet */
                if((ourblks == null) && curblks.getFlag(nodex, nodez)) {
                    ourblks = new TileFlags();  /* Create map for shape */
                    ournodes = new LinkedList<TownBlock>();
                    floodFillTarget(curblks, ourblks, nodex, nodez);   /* Copy shape */
                    ournodes.add(node); /* Add it to our node list */
                    minx = nodex; minz = nodez;
                }
                /* If shape found, and we're in it, add to our node list */
                else if((ourblks != null) && (node.getWorld() == curworld) &&
                    (ourblks.getFlag(nodex, nodez))) {
                    ournodes.add(node);
                    if(nodex < minx) {
                        minx = nodex; minz = nodez;
                    }
                    else if((nodex == minx) && (nodez < minz)) {
                        minz = nodez;
                    }
                }
                else {  /* Else, keep it in the list for the next polygon */
                    if(newlist == null) newlist = new LinkedList<TownBlock>();
                    newlist.add(node);
                }
            }
            nodevals = newlist; /* Replace list (null if no more to process) */
            if(ourblks != null) {
                /* Trace outline of blocks - start from minx, minz going to x+ */
                int init_x = minx;
                int init_z = minz;
                int cur_x = minx;
                int cur_z = minz;
                direction dir = direction.XPLUS;
                ArrayList<int[]> linelist = new ArrayList<int[]>();
                linelist.add(new int[] { init_x, init_z } ); // Add start point
                while((cur_x != init_x) || (cur_z != init_z) || (dir != direction.ZMINUS)) {
                    switch(dir) {
                        case XPLUS: /* Segment in X+ direction */
                            if(!ourblks.getFlag(cur_x+1, cur_z)) { /* Right turn? */
                                linelist.add(new int[] { cur_x+1, cur_z }); /* Finish line */
                                dir = direction.ZPLUS;  /* Change direction */
                            }
                            else if(!ourblks.getFlag(cur_x+1, cur_z-1)) {  /* Straight? */
                                cur_x++;
                            }
                            else {  /* Left turn */
                                linelist.add(new int[] { cur_x+1, cur_z }); /* Finish line */
                                dir = direction.ZMINUS;
                                cur_x++; cur_z--;
                            }
                            break;
                        case ZPLUS: /* Segment in Z+ direction */
                            if(!ourblks.getFlag(cur_x, cur_z+1)) { /* Right turn? */
                                linelist.add(new int[] { cur_x+1, cur_z+1 }); /* Finish line */
                                dir = direction.XMINUS;  /* Change direction */
                            }
                            else if(!ourblks.getFlag(cur_x+1, cur_z+1)) {  /* Straight? */
                                cur_z++;
                            }
                            else {  /* Left turn */
                                linelist.add(new int[] { cur_x+1, cur_z+1 }); /* Finish line */
                                dir = direction.XPLUS;
                                cur_x++; cur_z++;
                            }
                            break;
                        case XMINUS: /* Segment in X- direction */
                            if(!ourblks.getFlag(cur_x-1, cur_z)) { /* Right turn? */
                                linelist.add(new int[] { cur_x, cur_z+1 }); /* Finish line */
                                dir = direction.ZMINUS;  /* Change direction */
                            }
                            else if(!ourblks.getFlag(cur_x-1, cur_z+1)) {  /* Straight? */
                                cur_x--;
                            }
                            else {  /* Left turn */
                                linelist.add(new int[] { cur_x, cur_z+1 }); /* Finish line */
                                dir = direction.ZPLUS;
                                cur_x--; cur_z++;
                            }
                            break;
                        case ZMINUS: /* Segment in Z- direction */
                            if(!ourblks.getFlag(cur_x, cur_z-1)) { /* Right turn? */
                                linelist.add(new int[] { cur_x, cur_z }); /* Finish line */
                                dir = direction.XPLUS;  /* Change direction */
                            }
                            else if(!ourblks.getFlag(cur_x-1, cur_z-1)) {  /* Straight? */
                                cur_z--;
                            }
                            else {  /* Left turn */
                                linelist.add(new int[] { cur_x, cur_z }); /* Finish line */
                                dir = direction.XMINUS;
                                cur_x--; cur_z--;
                            }
                            break;
                    }
                }
                /* Build information for specific area */
                String polyid = town.getName() + "__" + poly_index;
                if(btype != null) {
                    polyid += "_" + btype;
                }
                int sz = linelist.size();
                x = new double[sz];
                z = new double[sz];
                for(int i = 0; i < sz; i++) {
                    int[] line = linelist.get(i);
                    x[i] = (double)line[0] * (double)townblocksize;
                    z[i] = (double)line[1] * (double)townblocksize;
                }
                /* Find existing one */
                AreaMarker m = resareas.remove(polyid); /* Existing area? */
                if(m == null) {
                    m = set.createAreaMarker(polyid, name, false, curworld.getName(), x, z, false);
                    if(m == null) {
                        LOGGER.info("error adding area marker " + polyid);
                        return;
                    }
                }
                else {
                    m.setCornerLocations(x, z); /* Replace corner locations */
                    m.setLabel(name);   /* Update label */
                }
                m.setDescription(desc); /* Set popup */
            
                /* Set line and fill properties */
                String nation = NATION_NONE;
                try {
                	if(town.getNation() != null)
                		nation = town.getNation().getName();
                } catch (Exception ex) {}
                addStyle(town, town.getName(), nation, m, btype);

                /* Add to map */
                newmap.put(polyid, m);
                poly_index++;
            }
        }
        if(btype == null) {
            /* Now, add marker for home block */
            TownBlock blk = null;
            try {
                blk = town.getHomeBlock();
            } catch(Exception ex) {
                LOGGER.severe("getHomeBlock exception " + ex);
            }
            if((blk != null) && isVisible(name, blk.getWorld().getName())) {
                String markid = town.getName() + "__home";
                MarkerIcon ico = getMarkerIcon(town);
                if(ico != null) {
                    Marker home = resmark.remove(markid);
                    double xx = townblocksize*blk.getX() + (townblocksize/2);
                    double zz = townblocksize*blk.getZ() + (townblocksize/2);
                    if(home == null) {
                        home = set.createMarker(markid, name, blk.getWorld().getName(), 
                                xx, 64, zz, ico, false);
                        if(home == null)
                            return;
                    }
                    else {
                        home.setLocation(blk.getWorld().getName(), xx, 64, zz);
                        home.setLabel(name);   /* Update label */
                        home.setMarkerIcon(ico);
                    }
                    home.setDescription(desc); /* Set popup */
                    newmark.put(markid, home);
                }
            }
        }
    }
    
    /* Update Towny information */
    private void updateTowns() {
        Map<String,AreaMarker> newmap = new HashMap<String,AreaMarker>(); /* Build new map */
        Map<String,Marker> newmark = new HashMap<String,Marker>(); /* Build new map */
        
        /* Loop through towns */
        List<Town> towns = TownyAPI.getInstance().getDataSource().getTowns();
        for(Town t : towns) {
    		handleTown(t, newmap, newmark, null);
    		if(show_shops) {
                handleTown(t, newmap, newmark, TownBlockType.COMMERCIAL);
    		}
            if(show_arenas) {
                handleTown(t, newmap, newmark, TownBlockType.ARENA);
            }
            if(show_embassies) {
                handleTown(t, newmap, newmark, TownBlockType.EMBASSY);
            }
            if(show_wilds) {
                handleTown(t, newmap, newmark, TownBlockType.WILDS);
            }
        }
        /* Now, review old map - anything left is gone */
        for(AreaMarker oldm : resareas.values()) {
            oldm.deleteMarker();
        }
        for(Marker oldm : resmark.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        resareas = newmap;
        resmark = newmark;
                
    }

    public void onEnable() {
        LOGGER.info("initializing");
        PluginManager pm = getServer().getPluginManager();

        /* Get dynmap */
        Plugin dynmap_plugin = pm.getPlugin("dynmap");
        assert dynmap_plugin != null;
        assert dynmap_plugin instanceof DynmapAPI;
        this.dynmap = dynmap_plugin;
        this.dynmapAPI = (DynmapAPI) dynmap_plugin;

        /* Get Towny */
        Plugin p = pm.getPlugin("Towny");
        if(p == null) {
            LOGGER.severe("Cannot find Towny!");
            return;
        }
        towny = (Towny)p;

        getServer().getPluginManager().registerEvents(new DMTServerListener(this), this);
        /* If both enabled, activate */
        if(dynmap.isEnabled() && towny.isEnabled()) {
            LOGGER.info("Activating Dynmap-Meets-Towny plugin.");
            activate();
        }
    }
    
    private void activate() {
        markerapi = dynmapAPI.getMarkerAPI();
        if(markerapi == null) {
            LOGGER.severe("Error loading dynmap marker API!");
            return;
        }
        /* Connect to towny API */
        tuniv = TownyUniverse.getInstance();
        townblocksize = Coord.getCellSize();
        
        /* Load configuration */
        if(reload) {
            reloadConfig();
            if(set != null) {
                set.deleteMarkerSet();
                set = null;
            }
        }
        else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */

        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.getMarkerSet("towny.markerset");
        if(set == null)
            set = markerapi.createMarkerSet("towny.markerset", cfg.getString("layer.name", "Towny"), null, false);
        else
            set.setMarkerSetLabel(cfg.getString("layer.name", "Towny"));
        if(set == null) {
            LOGGER.severe("Error creating marker set");
            return;
        }
        int minzoom = cfg.getInt("layer.minzoom", 0);
        if(minzoom > 0)
            set.setMinZoom(minzoom);
        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        use3d = cfg.getBoolean("use3dregions", false);
        /* See if we need to show commercial areas */
        show_shops = cfg.getBoolean("layer.showShops", false);
        show_arenas = cfg.getBoolean("layer.showArenas", false);
        show_embassies = cfg.getBoolean("layer.showEmbassies", false);
        show_wilds = cfg.getBoolean("layer.showWilds", false);

        /* Get style information */
        defstyle = new AreaStyle(cfg, "regionstyle", markerapi);
        cusstyle = new HashMap<String, AreaStyle>();
        nationstyle = new HashMap<String, AreaStyle>();
        ConfigurationSection sect = cfg.getConfigurationSection("custstyle");
        if(sect != null) {
            Set<String> ids = sect.getKeys(false);
            
            for(String id : ids) {
                cusstyle.put(id, new AreaStyle(cfg, "custstyle." + id, markerapi));
            }
        }
        sect = cfg.getConfigurationSection("nationstyle");
        if(sect != null) {
            Set<String> ids = sect.getKeys(false);
            
            for(String id : ids) {
                nationstyle.put(id, new AreaStyle(cfg, "nationstyle." + id, markerapi));
            }
        }
        List<String> vis = cfg.getStringList("visibleregions");
        if(vis != null) {
            visible = new HashSet<String>(vis);
        }
        List<String> hid = cfg.getStringList("hiddenregions");
        if(hid != null) {
            hidden = new HashSet<String>(hid);
        }

        /* Check if player sets enabled */
        playersbytown = cfg.getBoolean("visibility-by-town", false);
        if(playersbytown) {
            try {
                if(!dynmapAPI.testIfPlayerInfoProtected()) {
                    playersbytown = false;
                    LOGGER.info("Dynmap does not have player-info-protected enabled - visibility-by-town will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersbytown = false;
                LOGGER.info("Dynmap does not support function needed for 'visibility-by-town' - need to upgrade to 0.60 or later");
            }
        }
        playersbynation = cfg.getBoolean("visibility-by-nation", false);
        if(playersbynation) {
            try {
                if(!dynmapAPI.testIfPlayerInfoProtected()) {
                    playersbynation = false;
                    LOGGER.info("Dynmap does not have player-info-protected enabled - visibility-by-nation will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersbynation = false;
                LOGGER.info("Dynmap does not support function needed for 'visibility-by-nation' - need to upgrade to 0.60 or later");
            }
        }

        dynamicNationColorsEnabled = cfg.getBoolean("dynamic-nation-colors", true);

        /* Set up update job - based on periond */
        int per = cfg.getInt("update.period", 300);
        if(per < 15) per = 15;
        updperiod = (per*20);
        stop = false;

        getServer().getScheduler().runTaskTimerAsynchronously(this, new TownyUpdate(), 40, per);

        LOGGER.info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        resareas.clear();
        townBankCache.clear();
        townBankBalanceCache.clear();
        stop = true;
    }

}