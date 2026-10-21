package dev.veltraph.client.gui;

import java.util.*;

/** Frozen local contracts. Salted production identities deliberately have no inferred defaults. */
public record ExperimentsParentSettings(String tableTitle,String tableCommand,List<Stage> stages,
        List<Button> xpPath,List<Button> xpReturnPath,String xpBottleName,String xpAvailableLore,XpCreditMode xpCreditMode,String xpLevelsPrefix,
        String xpCostHeader,String xpCostSuffix,String purchaseReceipt,String currencyPrefix,
        boolean renewals,int maxRenewals,Button renewButton,String renewalCostPrefix,
        String renewalCountPrefix,String renewalResourcePrefix,String renewalReceipt,
        long maxXpSpend,long maxRenewalSpend,int maxPurchases,long clickDelayMillis,
        long timeoutMillis,long maxSessionMillis) {
    public enum XpCreditMode { FINAL_LEVEL, ADDED_LEVELS }
    public record Button(String title,String name,String itemId,String requiredLore,String nextTitle) {
        public Button {
            title=clean(title);name=clean(name);requiredLore=clean(requiredLore);nextTitle=clean(nextTitle);itemId=clean(itemId);
            if(List.of(title,name,itemId,requiredLore,nextTitle).stream().anyMatch(String::isBlank)||!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Experiment button needs exact title, name, item ID, state lore and destination");
        }
        public boolean matches(MenuSnapshot.Slot slot) {
            return !slot.playerInventory()&&!slot.locked()&&slot.count()>0&&slot.itemId().equals(itemId)
                    &&clean(slot.name()).equals(name)&&slot.lore().stream().map(ExperimentsParentSettings::clean).anyMatch(requiredLore::equals);
        }
    }
    public record RewardThresholdContract(String tierLoreMarker,String rewardMenuTitle) {
        public RewardThresholdContract {
            tierLoreMarker=clean(tierLoreMarker);rewardMenuTitle=clean(rewardMenuTitle);
            if(tierLoreMarker.isBlank()||rewardMenuTitle.isBlank())throw new IllegalArgumentException("Minigame threshold needs exact tier lore marker and reward title");
        }
    }
    public record Stage(Button select,Button tier,String requiredLevelPrefix,String levelCostPrefix,Button xpEntry,RewardThresholdContract rewardThreshold) {
        public Stage(Button select,Button tier,String requiredLevelPrefix,String levelCostPrefix,Button xpEntry) {
            this(select,tier,requiredLevelPrefix,levelCostPrefix,xpEntry,null);
        }
        public Stage {
            Objects.requireNonNull(select);Objects.requireNonNull(tier);
            requiredLevelPrefix=clean(requiredLevelPrefix);levelCostPrefix=clean(levelCostPrefix);
            if(requiredLevelPrefix.isBlank()||levelCostPrefix.isBlank()||requiredLevelPrefix.equals(levelCostPrefix)
                    ||!select.nextTitle().equals(tier.title())||ExperimentsRuntime.identify(tier.nextTitle())==null)
                throw new IllegalArgumentException("Experiment stage needs exact selection/tier chain and distinct XP quote prefixes");
        }
    }
    public ExperimentsParentSettings {
        tableTitle=clean(tableTitle);tableCommand=clean(tableCommand);stages=List.copyOf(stages);
        xpPath=List.copyOf(xpPath);xpReturnPath=List.copyOf(xpReturnPath);
        xpBottleName=clean(xpBottleName);xpAvailableLore=clean(xpAvailableLore);xpLevelsPrefix=clean(xpLevelsPrefix);xpCostHeader=clean(xpCostHeader);
        xpCostSuffix=clean(xpCostSuffix);purchaseReceipt=clean(purchaseReceipt);currencyPrefix=clean(currencyPrefix);
        renewalCostPrefix=clean(renewalCostPrefix);renewalCountPrefix=clean(renewalCountPrefix);
        renewalResourcePrefix=clean(renewalResourcePrefix);renewalReceipt=clean(renewalReceipt);
        if(tableTitle.isBlank()||stages.isEmpty()||stages.size()>3)
            throw new IllegalArgumentException("Experiments parent needs one to three table stages");
        for(var stage:stages)if(!stage.select().title().equals(tableTitle))throw new IllegalArgumentException("Each stage must begin at the exact table");
        if(!tableCommand.isBlank()&&!tableCommand.matches("/?[a-zA-Z0-9 _:-]+"))throw new IllegalArgumentException("Invalid explicit table command");
        if(clickDelayMillis<0||clickDelayMillis>2000||timeoutMillis<100||timeoutMillis>30000||timeoutMillis<=clickDelayMillis
                ||maxSessionMillis<timeoutMillis||maxSessionMillis>3600000||maxPurchases<0||maxPurchases>20||maxRenewals<0||maxRenewals>10
                ||maxXpSpend<0||maxRenewalSpend<0)throw new IllegalArgumentException("Invalid experiment bounds/budget");
        if(maxPurchases>0) {
            Objects.requireNonNull(xpCreditMode,"Exact XP credit semantics must be bound from local observation");
            if(List.of(xpBottleName,xpAvailableLore,xpLevelsPrefix,xpCostHeader,xpCostSuffix,purchaseReceipt,currencyPrefix).stream().anyMatch(String::isBlank)
                    ||!purchaseReceipt.contains("{cost}")||!purchaseReceipt.contains("{levels}")||xpPath.isEmpty()||xpReturnPath.isEmpty())
                throw new IllegalArgumentException("XP purchase needs exact bottle, quote, currency, receipt and navigation contracts");
            chain(xpPath);chain(xpReturnPath);
            for(var stage:stages)if(stage.xpEntry()==null||!stage.xpEntry().title().equals(stage.tier().title())||!stage.xpEntry().nextTitle().equals(xpPath.getFirst().title()))
                throw new IllegalArgumentException("Each tier needs an exact go-back contract into the XP route");
            if(!xpReturnPath.getLast().nextTitle().equals(tableTitle)||!xpPath.getLast().nextTitle().equals(xpReturnPath.getFirst().title()))
                throw new IllegalArgumentException("XP route must return from purchase screen to table");
        }
        if(renewals) {
            Objects.requireNonNull(renewButton,"Renewal button contract");
            if(!renewButton.title().equals(tableTitle)||!renewButton.nextTitle().equals(tableTitle)||maxRenewals<1
                    ||List.of(renewalCostPrefix,renewalCountPrefix,renewalResourcePrefix,renewalReceipt).stream().anyMatch(String::isBlank)
                    ||!renewalReceipt.contains("{cost}")||!renewalReceipt.contains("{renewals}"))
                throw new IllegalArgumentException("Renewal needs bounded exact table, cost/counter/resource and receipt contracts");
        }
    }
    private static void chain(List<Button> path) {
        if(path.size()>8)throw new IllegalArgumentException("Experiment navigation exceeds eight steps");
        for(int i=1;i<path.size();i++)if(!path.get(i-1).nextTitle().equals(path.get(i).title()))throw new IllegalArgumentException("Experiment navigation chain breaks");
    }
    private static String clean(String s){return MenuSnapshot.clean(s);}
    public boolean allowsScreen(String title) {
        String t=clean(title);return t.equals(tableTitle)||stages.stream().anyMatch(s->t.equals(s.select().title())||t.equals(s.tier().title())||t.equals(s.tier().nextTitle())||(s.rewardThreshold()!=null&&t.equals(s.rewardThreshold().rewardMenuTitle())))
                ||xpPath.stream().anyMatch(b->t.equals(b.title())||t.equals(b.nextTitle()))||xpReturnPath.stream().anyMatch(b->t.equals(b.title())||t.equals(b.nextTitle()));
    }
}
