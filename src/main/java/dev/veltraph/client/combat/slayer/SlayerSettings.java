package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.config.*;
import java.util.Set;

public record SlayerSettings(String rootTitle,String bossLabel,String bossTitle,String tierLabel,
                             String confirmTitle,String confirmLabel,String claimLabel,String cancelLabel,
                             String cancelTitle,String cancelConfirmLabel,long maxCost,long timeoutMs,long clickDelayMs,boolean repeat,boolean cancelExisting,int phoneAttempts,String tierPattern,int requestedTier,QuestCurrency currency) {
    public SlayerSettings {
        for(String s:new String[]{rootTitle,bossLabel,bossTitle,tierLabel,confirmTitle,confirmLabel,claimLabel,cancelLabel,cancelTitle,cancelConfirmLabel})
            if(s==null||s.isBlank()) throw new IllegalArgumentException("Slayer menu labels cannot be blank");
        if(currency==null||maxCost<0||timeoutMs<500||clickDelayMs<0||phoneAttempts<1||phoneAttempts>5||requestedTier<1||requestedTier>5) throw new IllegalArgumentException("Invalid Slayer limits (quest tier must be 1-5)");
    }
    public static String roman(int tier) { return switch(tier) { case 1->"I";case 2->"II";case 3->"III";case 4->"IV";case 5->"V";default->throw new IllegalArgumentException("Quest tier must be 1-5"); }; }
    public String availableTier(dev.veltraph.client.gui.MenuSnapshot menu) {
        if(!tierPattern.contains("{tier}")) return tierLabel;
        for(int tier=requestedTier;tier>=1;tier--) {
            String label=tierPattern.replace("{tier}",roman(tier));
            if(menu.matching(label).stream().anyMatch(slot->!slot.locked())) return label;
        }
        return tierLabel;
    }
    public boolean allowed(String title) { return rootTitle.equals(title)||bossTitle.equals(title)||confirmTitle.equals(title)||cancelTitle.equals(title); }
    public static SlayerSettings from(ConfigService c) {
        return new SlayerSettings(c.get(SlayerConfig.SLAYER_ROOT_TITLE),c.get(SlayerConfig.SLAYER_BOSS_LABEL),c.get(SlayerConfig.SLAYER_BOSS_TITLE),
                c.get(SlayerConfig.SLAYER_TIER_LABEL).replace("{tier}",roman(c.get(SlayerConfig.SLAYER_QUEST_TIER))),c.get(SlayerConfig.SLAYER_CONFIRM_TITLE),c.get(SlayerConfig.SLAYER_CONFIRM_LABEL),
                c.get(SlayerConfig.SLAYER_CLAIM_LABEL),c.get(SlayerConfig.SLAYER_CANCEL_LABEL),c.get(SlayerConfig.SLAYER_CANCEL_TITLE),
                c.get(SlayerConfig.SLAYER_CANCEL_CONFIRM_LABEL),c.get(SlayerConfig.SLAYER_MAX_QUEST_COST),c.get(SlayerConfig.SLAYER_MENU_TIMEOUT_MS),
                c.get(SlayerConfig.SLAYER_CLICK_DELAY_MS),c.get(SlayerConfig.SLAYER_REPEAT),c.get(SlayerConfig.SLAYER_CANCEL_EXISTING),c.get(SlayerConfig.SLAYER_PHONE_ATTEMPTS),c.get(SlayerConfig.SLAYER_TIER_LABEL),c.get(SlayerConfig.SLAYER_QUEST_TIER),c.get(SlayerConfig.SLAYER_QUEST_CURRENCY));
    }
}
