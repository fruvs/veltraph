package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;

/** Callback-free evidence for the two concrete final local stores. Unknown implementations refuse. */
public final class BazaarStandaloneStoredConfig {
    private BazaarStandaloneStoredConfig() {}
    private static final ConfigKey<?>[] KEYS={SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED,MiscConfig.AUTO_SELL_ENABLED,
            MiscConfig.AUTO_SELL_MODE,MiscConfig.AUTO_SELL_SACKS,SellingConfig.SELLING_BAZAAR_PROFILE_JSON};
    public static State capture(ConfigService store){
        if(!(store instanceof MapConfigService)&&!(store instanceof JsonConfigService))return null;
        long[] revisions=new long[KEYS.length];for(int i=0;i<KEYS.length;i++){var value=store.revision(KEYS[i]);if(value.isEmpty())return null;revisions[i]=value.getAsLong();}
        return new State(store,store.get(SellingConfig.SELLING_BAZAAR_CUSTODY_ENABLED),store.get(MiscConfig.AUTO_SELL_ENABLED),
                store.get(MiscConfig.AUTO_SELL_MODE),store.get(MiscConfig.AUTO_SELL_SACKS),store.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON),revisions);
    }
    /** Value/epoch snapshot, not authority. Constructor is private and revisions are not exposed. */
    public static final class State {
        private final ConfigService store;private final boolean custody,selling,sacks;private final AutoSellMode mode;private final String profile;private final long[] revisions;
        private State(ConfigService store,boolean custody,boolean selling,AutoSellMode mode,boolean sacks,String profile,long[] revisions){this.store=store;this.custody=custody;this.selling=selling;this.mode=mode;this.sacks=sacks;this.profile=profile;this.revisions=revisions.clone();}
        public boolean same(State other){return other!=null&&store==other.store&&custody==other.custody&&selling==other.selling&&sacks==other.sacks
                &&mode==other.mode&&java.util.Objects.equals(profile,other.profile)&&java.util.Arrays.equals(revisions,other.revisions);}
    }
}
