package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.Optional;

/** Pure observation sequence. Its facts are not native permissions; only the native observer can mint a receipt. */
public final class SlayerQuestTierSequence {
    public enum Phase { TIER_CHOICE,TIER_SUBMITTED,TIER_APPLIED,CONFIRM_CHOICE,CONFIRM_SUBMITTED,ACTIVE,UNKNOWN }
    public record Fact(String declaredBoss,String nativeChoiceMenuTitle,String nativeTierLabel,int actualTier,long tierActionSequence,long confirmationActionSequence,
                       long tierSend,long menuReceive,long confirmSend,long startReceive) {}
    private final SlayerQuestController.ReturnedAction tier;
    private final Object initialMenu;
    private final long born,deadline;
    private Phase phase=Phase.TIER_CHOICE;
    private long lastSerial,tierSend,menuReceive,confirmSend,lastObservedTime;
    private Object appliedMenu;private int appliedId,appliedRevision;
    private SlayerQuestController.ReturnedAction confirm;
    private Fact fact;
    public SlayerQuestTierSequence(SlayerQuestController.ReturnedAction tier,Object nativeMenu,long now) {
        this.tier=tier;initialMenu=nativeMenu;born=now;lastObservedTime=now;
        long end;
        try {end=Math.addExact(now,Math.multiplyExact(tier.settings().timeoutMs(),1_000_000L));}
        catch(RuntimeException invalid){end=now;phase=Phase.UNKNOWN;}
        deadline=end;
        if(tier==null||nativeMenu==null||tier.role()!=SlayerQuestController.ReturnedAction.Role.TIER
                ||tier.actualTier()<1||tier.actualTier()>5||!tier.current())phase=Phase.UNKNOWN;
    }
    public Phase phase(){return phase;}
    public Optional<Fact> fact(){return phase==Phase.ACTIVE?Optional.ofNullable(fact):Optional.empty();}
    public Object appliedMenu(){return appliedMenu;}public int appliedId(){return appliedId;}public int appliedRevision(){return appliedRevision;}
    public SlayerQuestController.ReturnedAction tierChoice(){return tier;}
    public SlayerQuestController.ReturnedAction confirmationChoice(){return confirm;}
    public void invalidate(){phase=Phase.UNKNOWN;fact=null;}
    private boolean time(long now){if(now<born||now<lastObservedTime||now>deadline){invalidate();return false;}lastObservedTime=now;return phase!=Phase.UNKNOWN;}
    private boolean event(long serial,long now){if(!time(now)||serial<=lastSerial){invalidate();return false;}lastSerial=serial;return true;}
    public void tierSubmitted(SlayerQuestController.ReturnedAction action,long serial,boolean actualClickReturned,long now) {
        if(phase!=Phase.TIER_CHOICE||action!=tier||!actualClickReturned||!tier.current()||!event(serial,now)){invalidate();return;}
        tierSend=serial;phase=Phase.TIER_SUBMITTED;
    }
    public void menuApplied(Object nativeMenu,int id,int revision,String title,long serial,boolean completeNativeApply,long now) {
        if(phase!=Phase.TIER_SUBMITTED||nativeMenu==null||!completeNativeApply||!tier.current()
                ||!tier.settings().confirmTitle().equals(MenuSnapshot.clean(title))
                ||nativeMenu==initialMenu&&id==tier.menu().id()&&revision==tier.menu().revision()
                ||!event(serial,now)){invalidate();return;}
        appliedMenu=nativeMenu;appliedId=id;appliedRevision=revision;menuReceive=serial;phase=Phase.TIER_APPLIED;
    }
    public boolean confirmChoice(SlayerQuestController.ReturnedAction action,Object nativeMenu,long now) {
        if(phase!=Phase.TIER_APPLIED||!time(now)||action==null||!action.current()
                ||action.role()!=SlayerQuestController.ReturnedAction.Role.CONFIRM||action.tierChoice()!=tier
                ||nativeMenu!=appliedMenu||action.menu().id()!=appliedId||action.menu().revision()!=appliedRevision
                ||!tier.settings().confirmTitle().equals(action.menu().title())) {invalidate();return false;}
        confirm=action;phase=Phase.CONFIRM_CHOICE;return true;
    }
    public void confirmSubmitted(SlayerQuestController.ReturnedAction action,long serial,boolean actualClickReturned,long now) {
        if(phase!=Phase.CONFIRM_CHOICE||action!=confirm||!actualClickReturned||!confirm.current()||!event(serial,now)){invalidate();return;}
        confirmSend=serial;phase=Phase.CONFIRM_SUBMITTED;
    }
    public void receivedStarted(String raw,long serial,SlayerQuestController.State before,
            SlayerQuestController.State after,boolean normallyApplied,long now) {
        if(phase!=Phase.CONFIRM_SUBMITTED||before!=SlayerQuestController.State.WAIT_START
                ||after!=SlayerQuestController.State.ACTIVE||!normallyApplied||!uniqueStartedHeader(raw)
                ||confirm==null||!confirm.current()||!event(serial,now)){invalidate();return;}
        fact=new Fact(tier.settings().bossLabel(),tier.menu().title(),tier.action().expectedName(),tier.actualTier(),tier.sequence(),confirm.sequence(),
                tierSend,menuReceive,confirmSend,serial);phase=Phase.ACTIVE;
    }
    public static boolean uniqueStartedHeader(String raw) {
        if(raw==null||raw.length()>8192)return false;
        var lines=MenuSnapshot.clean(raw).lines().map(MenuSnapshot::clean).toList();
        return lines.stream().filter("SLAYER QUEST STARTED!"::equals).count()==1
                &&lines.stream().noneMatch(line->line.equals("SLAYER QUEST COMPLETE!")
                    ||line.equals("Your Slayer Quest has been cancelled!")||line.startsWith("You already have an active")
                    ||line.equals("You cannot afford to start this quest!"));
    }
    /** Received confirmation content must uniquely echo the actually clicked tier, not merely a price/header. */
    public static boolean confirmationEcho(java.util.List<String> lines,SlayerQuestController.ReturnedAction choice) {
        if(lines==null||choice==null||choice.role()!=SlayerQuestController.ReturnedAction.Role.TIER||lines.size()>4096
                ||choice.settings().tierPattern().length()>512)return false;
        var seen=new java.util.HashSet<Integer>();
        var patterns=new java.util.ArrayList<java.util.regex.Pattern>();
        for(int tier=1;tier<=5;tier++) {
            String label=choice.settings().tierPattern().replace("{tier}",SlayerSettings.roman(tier));
            patterns.add(java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])"+java.util.regex.Pattern.quote(label)+"(?![\\p{L}\\p{N}_])"));
        }
        for(String raw:lines) {
            if(raw==null||raw.length()>8192)return false;
            String text=MenuSnapshot.clean(raw);
            for(int tier=1;tier<=5;tier++) {
                if(patterns.get(tier-1).matcher(text).find())seen.add(tier);
            }
        }
        return seen.size()==1&&seen.contains(choice.actualTier());
    }
    public static int actualChoiceTier(SlayerQuestController.Action action,MenuSnapshot menu,SlayerSettings settings) {
        if(action==null||menu==null||settings==null||action.kind()!=SlayerQuestController.Kind.CLICK
                ||action.menuId()!=menu.id()||!settings.bossTitle().equals(menu.title())
                ||!settings.tierPattern().contains("{tier}")
                ||settings.tierPattern().indexOf("{tier}")!=settings.tierPattern().lastIndexOf("{tier}"))return 0;
        var slots=menu.slots().stream().filter(slot->slot.id()==action.slotId()&&!slot.playerInventory()&&!slot.locked()
                &&slot.count()>0&&MenuSnapshot.clean(slot.name()).equals(action.expectedName())).toList();
        if(slots.size()!=1||!action.expectedName().equals(settings.availableTier(menu)))return 0;
        int found=0;
        for(int tier=1;tier<=settings.requestedTier();tier++) {
            if(settings.tierPattern().replace("{tier}",SlayerSettings.roman(tier)).equals(action.expectedName())) {
                if(found!=0)return 0;found=tier;
            }
        }
        return found;
    }
}
