package moe.qbit.dynmapmeetstowny;

import com.github.mustachejava.Mustache;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyFormatter;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Collectors;

public class InfoWindow {
    protected Mustache template;

    public InfoWindow(Mustache template){
        this.template = template;
    }

    public String render(@Nonnull Town town, @Nullable TownBlockType townBlockType, BankCache bankCache) {
        Map<String,Object> properties = TemplateHelper.createScope();
        properties.put("town_name",town.getName());
        if(townBlockType != null)
            properties.put("town_block_type",townBlockType.toString());

        if(town.hasNation()) {
            try {
                Nation nation = town.getNation();
                properties.put("nation", nation);
                properties.put("nation_prefix", TownySettings.getNationPrefix(nation));
                properties.put("nation_name", nation.getName().replaceAll("_", " "));
                properties.put("nation_postfix", TownySettings.getNationPostfix(nation));
                //TODO: pre-formatted name
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        if(town.hasMayor()) {
            Resident mayor = town.getMayor();
            properties.put("mayor_prefix", mayor.getNamePrefix());
            properties.put("mayor_name", mayor.getName());
            properties.put("mayor_postfix", mayor.getNamePostfix());
            //TODO: pre-formatted name
        }

        properties.put("residents",
                town.getResidents().stream()
                        .map(TownyObject::getName)
                        .collect(Collectors.toList()));

        properties.put("assistants",
                town.getRank("assistant").stream()
                        .map(TownyObject::getName)
                        .collect(Collectors.toList()));

        if(town.getRegistered() != 0)
            properties.put("founded", TownyFormatter.registeredFormat.format(town.getRegistered()));

        properties.put("upkeep", town.hasUpkeep());
        properties.put("pvp", town.isPVP());
        properties.put("mobs", town.hasMobs());
        properties.put("public", town.isPublic());
        properties.put("explosion", town.isBANG());
        properties.put("fire", town.isFire());

        properties.put("board", town.getBoard());

        properties.put("war", town.isAdminEnabledPVP());
        properties.put("capital", town.isCapital());

        properties.put("balance", TownyEconomyHandler.getFormattedBalance(bankCache.get(town)));

        if (town.isTaxPercentage())
            properties.put("taxes", town.getTaxes() + "%");
        else
            properties.put("taxes", TownyEconomyHandler.getFormattedBalance(town.getTaxes()));

        /*
        String dispNames = "";
        for (Resident r: town.getResidents()) {
            @SuppressWarnings("deprecation")
			Player p = Bukkit.getPlayer(r.getName());
            if(dispNames.length()>0) mgrs += ", ";

            if (p == null) {
                dispNames += r.getFormattedName();
                continue;
            }

            dispNames += p.getDisplayName();
        }

        v = v.replace("%residentdisplaynames%", dispNames);

        String townicon = "";
        if (town.isCapital()) {
            townicon = "<img src=\"tiles/_markers_/king.png\">  ";
        } else if (town.hasNation()) {
            townicon = "<img src=\"tiles/_markers_/blueflag.png\">  ";
        } else {
            townicon = "<img src=\"tiles/_markers_/greenflag.png\">  ";
        }

        //v = v.replace("%upkeep%", TownyEconomyHandler.getFormattedBalance(TownySettings.getTownUpkeepCost(town)));

         */

        return TemplateHelper.render(this.template, properties);
    }
}
