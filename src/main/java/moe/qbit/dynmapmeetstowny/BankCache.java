package moe.qbit.dynmapmeetstowny;

import com.google.common.collect.Maps;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.object.Town;

import java.util.HashMap;

public class BankCache {
    public static final long TTL = 300000;

    HashMap<Town, Long> lastUpdated = Maps.newHashMap();
    HashMap<Town, Double> bankBalances = Maps.newHashMap();

    public BankCache() {}

    public double get(Town town){
        long time = System.currentTimeMillis();

        if (this.lastUpdated.containsKey(town) && time - this.lastUpdated.get(town) < TTL) {
            return this.bankBalances.get(town);
        } else {
            double balance = 0d;

            try {
                balance = town.getAccount().getHoldingBalance();
            } catch (EconomyException ignored) {}

            this.bankBalances.put(town, balance);
            this.lastUpdated.put(town, time);
            return balance;
        }
    }

    public void invalidate(){
        lastUpdated.clear();
        bankBalances.clear();
    }

    public void invalidate(Town town){
        lastUpdated.remove(town);
        bankBalances.remove(town);
    }
}
