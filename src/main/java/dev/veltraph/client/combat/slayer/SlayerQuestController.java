package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Batphone/quest menu state machine. Source: BatphoneQuestState + CryptQuestPick + recovered events. */
public final class SlayerQuestController {
    public enum State { IDLE, OPENING, SELECT_BOSS, SELECT_TIER, CONFIRM_START, WAIT_START, ACTIVE,
        RESTART, SELECT_CANCEL, CONFIRM_CANCEL, WAIT_CANCEL, COMPLETE, FAILED }
    public enum Kind { NONE, USE_PHONE, CLICK, CLOSE_MENU }
    public record Action(Kind kind,int menuId,int slotId,String expectedName) {
        public static Action none() { return new Action(Kind.NONE,-1,-1,""); }
    }
    private State state=State.IDLE;private boolean cancelling,claimSent,cancelThenStart;private long since,nextAction,quote=-1;private int calls;
    private String reason="Idle";private int completed;private MenuSnapshot lastClicked;
    /** Read-only identity of a real returned action. It is not a native submission/acknowledgment receipt. */
    public static final class ReturnedAction {
        public enum Role { TIER, CONFIRM, OTHER }
        private final SlayerQuestController owner;private final long sequence,stateVersion;
        private final Action action;private final MenuSnapshot menu;private final SlayerSettings settings;
        private final Role role;private final int actualTier;private final ReturnedAction tierChoice;
        private ReturnedAction(SlayerQuestController owner,long sequence,Action action,MenuSnapshot menu,
                SlayerSettings settings,Role role,int tier,ReturnedAction tierChoice){
            this.owner=owner;this.sequence=sequence;stateVersion=owner.observedStateVersion();this.action=action;this.menu=menu;this.settings=settings;
            this.role=role;actualTier=tier;this.tierChoice=tierChoice;
        }
        public Action action(){return action;}public MenuSnapshot menu(){return menu;}
        public SlayerSettings settings(){return settings;}public Role role(){return role;}
        public int actualTier(){return actualTier;}public long sequence(){return sequence;}
        public long stateVersion(){return stateVersion;}
        public ReturnedAction tierChoice(){return tierChoice;}
        public boolean current(){return owner.returnedActionCurrent(this);}
    }
    private long returnedSequence;private boolean returnedSequenceExhausted;
    private long observedStateVersion;private boolean stateVersionExhausted;
    public long observedStateVersion(){return stateVersionExhausted?-1:observedStateVersion;}
    private void observeStateMutation(){if(observedStateVersion==Long.MAX_VALUE)stateVersionExhausted=true;else observedStateVersion++;}
    private ReturnedAction lastReturned,tierReturned;
    public Optional<ReturnedAction> returnedAction(Action actual){return lastReturned!=null&&lastReturned.action==actual
            &&returnedActionCurrent(lastReturned)?Optional.of(lastReturned):Optional.empty();}
    public boolean returnedActionCurrent(ReturnedAction actual){return actual!=null&&!returnedSequenceExhausted
            &&actual.owner==this&&actual==lastReturned&&actual.sequence==returnedSequence;}
    private void clearReturned(){lastReturned=tierReturned=null;if(returnedSequence==Long.MAX_VALUE)returnedSequenceExhausted=true;else returnedSequence++;}
    private void observeReturned(Action actual,MenuSnapshot menu,SlayerSettings settings,State next){
        try {
            if(returnedSequence==Long.MAX_VALUE){returnedSequenceExhausted=true;lastReturned=tierReturned=null;return;}
            returnedSequence++;
            int tier=next==State.CONFIRM_START?SlayerQuestTierSequence.actualChoiceTier(actual,menu,settings):0;
            var role=tier>0?ReturnedAction.Role.TIER:next==State.WAIT_START?ReturnedAction.Role.CONFIRM:ReturnedAction.Role.OTHER;
            var observed=new ReturnedAction(this,returnedSequence,actual,menu,settings,role,tier,
                    role==ReturnedAction.Role.CONFIRM?tierReturned:null);
            lastReturned=observed;if(role==ReturnedAction.Role.TIER)tierReturned=observed;
        }catch(Throwable observationFailure){lastReturned=tierReturned=null;}
    }
    private static final Pattern COST=Pattern.compile("(?i)^\\s*(?:cost|price|quest cost)\\s*:\\s*((?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]+)?)([kmb]?)\\s*(coins|motes)\\s*$");
    public record Quote(long amount,QuestCurrency currency){}
    public record QuoteRead(boolean present,Optional<Quote> quote){}
    public void start(boolean cancel,long now) {
        clearReturned();
        cancelling=cancel;cancelThenStart=false;claimSent=false;quote=-1;completed=0;calls=0;lastClicked=null;nextAction=now;transition(State.OPENING,now);
    }
    public Action tick(MenuSnapshot menu,SlayerSettings s,long now) {
        if(state==State.IDLE||state==State.COMPLETE||state==State.FAILED) return Action.none();
        if(state==State.ACTIVE) return menu==null?Action.none():new Action(Kind.CLOSE_MENU,menu.id(),-1,"");
        if(now-since>s.timeoutMs()) {
            if(menu==null&&(state==State.SELECT_BOSS||state==State.SELECT_CANCEL)&&calls<s.phoneAttempts()) transition(State.OPENING,now);
            else return fail("Quest step timed out: "+state);
        }
        if(now<nextAction) return Action.none();
        if(menu!=null&&!s.allowed(MenuSnapshot.clean(menu.title()))) return fail("Unexpected menu: "+menu.title());
        if(state==State.RESTART) { claimSent=false;quote=-1;lastClicked=null;calls=0;transition(State.OPENING,now); }
        if(state==State.OPENING) {
            if(menu!=null) return new Action(Kind.CLOSE_MENU,menu.id(),-1,"");
            calls++;
            transition(cancelling?State.SELECT_CANCEL:State.SELECT_BOSS,now);
            nextAction=now+s.clickDelayMs();return new Action(Kind.USE_PHONE,-1,-1,"");
        }
        if(menu==null) return Action.none();
        String title=MenuSnapshot.clean(menu.title());
        if(state==State.SELECT_BOSS&&title.equals(s.rootTitle())) {
            var claims=menu.matching(s.claimLabel());
            if(!claims.isEmpty()&&!claimSent) { claimSent=true;return click(menu,s.claimLabel(),State.SELECT_BOSS,s,now,false); }
            if(claimSent&&(!claims.isEmpty()||!acknowledged(menu))) return Action.none();
            if(title.equals(s.bossTitle())&&!menu.matching(s.availableTier(menu)).isEmpty()) return click(menu,s.availableTier(menu),State.CONFIRM_START,s,now,true);
            return click(menu,s.bossLabel(),State.SELECT_TIER,s,now,false);
        }
        if(state==State.SELECT_BOSS&&title.equals(s.bossTitle())) return click(menu,s.availableTier(menu),State.CONFIRM_START,s,now,true);
        if(state==State.SELECT_TIER&&title.equals(s.bossTitle())&&acknowledged(menu)) return click(menu,s.availableTier(menu),State.CONFIRM_START,s,now,true);
        if(state==State.CONFIRM_START&&title.equals(s.confirmTitle())&&acknowledged(menu))
            return click(menu,s.confirmLabel(),State.WAIT_START,s,now,true);
        if(state==State.SELECT_CANCEL&&(title.equals(s.rootTitle())||title.equals(s.bossTitle()))) return click(menu,s.cancelLabel(),State.CONFIRM_CANCEL,s,now,false);
        if(state==State.CONFIRM_CANCEL&&title.equals(s.cancelTitle())&&acknowledged(menu))
            return click(menu,s.cancelConfirmLabel(),State.WAIT_CANCEL,s,now,false);
        return Action.none();
    }
    private Action click(MenuSnapshot menu,String name,State next,SlayerSettings settings,long now,boolean paid) {
        var slots=menu.matching(name);
        if(slots.isEmpty()) return Action.none();
        if(slots.size()!=1) return fail("Ambiguous menu item: "+name);
        var slot=slots.getFirst();
        if(slot.locked()) return fail("Menu option is locked: "+name);
        if(paid) {
            var price=quote(slot.lore(),settings.currency());
            if(price.present()&&price.quote().isEmpty())return fail("Quest quote is malformed, ambiguous or in a different currency");
            if(price.quote().isPresent()) quote=price.quote().get().amount();
            if(quote<0) return fail("Quest price is unreadable");
            if(quote>settings.maxCost()) return fail("Quest price exceeds configured limit");
        }
        lastClicked=menu;transition(next,now);nextAction=now+settings.clickDelayMs();
        var actual=new Action(Kind.CLICK,menu.id(),slot.id(),MenuSnapshot.clean(slot.name()));
        observeReturned(actual,menu,settings,next);
        return actual;
    }
    private boolean acknowledged(MenuSnapshot menu) {
        return lastClicked==null||menu.id()!=lastClicked.id()||!menu.title().equals(lastClicked.title())||menu.revision()!=lastClicked.revision();
    }
    public void message(String text,SlayerSettings settings,long now) {
        String line=MenuSnapshot.clean(text);
        // Hypixel may combine a standalone header and explanatory lines in one component.
        line=line.lines().map(MenuSnapshot::clean).filter(l->l.equals("SLAYER QUEST STARTED!")||l.equals("SLAYER QUEST COMPLETE!")
                ||l.equals("Your Slayer Quest has been cancelled!")||l.equals("You cannot afford to start this quest!")||l.startsWith("You already have an active"))
                .findFirst().orElse(line);
        if(state==State.IDLE||state==State.COMPLETE||state==State.FAILED) return;
        boolean selecting=state==State.SELECT_BOSS||state==State.SELECT_TIER||state==State.CONFIRM_START||state==State.WAIT_START;
        if(line.equals("SLAYER QUEST STARTED!")&&!cancelling&&(state==State.CONFIRM_START||state==State.WAIT_START||state==State.RESTART)) {
            transition(State.ACTIVE,now);reason="Quest active";
        } else if(line.equals("SLAYER QUEST COMPLETE!")&&state==State.ACTIVE) {
            completed++;transition(settings.repeat()?State.RESTART:State.COMPLETE,now);nextAction=now+settings.clickDelayMs();
        } else if(line.equals("Your Slayer Quest has been cancelled!")) {
            if(cancelThenStart) { cancelThenStart=false;cancelling=false;transition(State.RESTART,now);nextAction=now+settings.clickDelayMs(); }
            else { transition(cancelling?State.COMPLETE:State.FAILED,now);reason=cancelling?"Quest cancelled":"Quest cancelled externally"; }
        } else if(selecting&&line.startsWith("You already have an active")&&settings.cancelExisting()&&!cancelling) {
            cancelling=true;cancelThenStart=true;calls=0;transition(State.OPENING,now);
        } else if((state==State.SELECT_TIER||state==State.CONFIRM_START)&&line.startsWith("You already have an active")&&!cancelling) {
            // CryptQuestPick treats this response during tier selection as successful handoff.
            transition(State.ACTIVE,now);reason="Existing quest active";
        } else if(selecting&&(line.equals("You cannot afford to start this quest!")||line.startsWith("You already have an active"))) {
            fail(line);
        }
    }
    public static OptionalLong cost(List<String> lore) {
        var result=quote(lore,QuestCurrency.COINS);return result.quote().isPresent()?OptionalLong.of(result.quote().get().amount()):OptionalLong.empty();
    }
    public static QuoteRead quote(List<String> lore,QuestCurrency expected) {
        boolean awaitingAmount=false,present=false;Quote found=null;
        for(String raw:lore.stream().flatMap(String::lines).toList()) {
            String line=MenuSnapshot.clean(raw);
            if(line.matches("(?i)(cost|price|quest cost):?")) {if(awaitingAmount)return new QuoteRead(true,Optional.empty());present=true;awaitingAmount=true;continue;}
            boolean free=line.equalsIgnoreCase("Cost: Free")||(awaitingAmount&&line.equalsIgnoreCase("Free"));
            boolean amountLine=awaitingAmount||line.matches("(?i)^(cost|price|quest cost)\\s*:.*");
            if(!amountLine&&!free)continue;
            present=true;
            var m=COST.matcher(awaitingAmount?"Cost: "+line:line);
            if(line.isBlank()&&awaitingAmount)continue;
            awaitingAmount=false;
            if(found!=null||!free&&(!m.matches()||QuestCurrency.parse(m.group(3))!=expected))return new QuoteRead(true,Optional.empty());
            try {
                if(free){found=new Quote(0,expected);continue;}
                BigDecimal value=new BigDecimal(m.group(1).replace(",",""));
                long factor=switch(m.group(2).toLowerCase(Locale.ROOT)) { case "k"->1000;case "m"->1000000;case "b"->1000000000;default->1; };
                found=new Quote(value.multiply(BigDecimal.valueOf(factor)).setScale(0,java.math.RoundingMode.CEILING).longValueExact(),expected);
            } catch(ArithmeticException|NumberFormatException e) { return new QuoteRead(true,Optional.empty()); }
        }
        return new QuoteRead(present,awaitingAmount?Optional.empty():Optional.ofNullable(found));
    }
    private void transition(State next,long now) { if(state!=next)observeStateMutation();state=next;since=now;reason=next.name(); }
    private Action fail(String why) { clearReturned();if(state!=State.FAILED)observeStateMutation();state=State.FAILED;reason=why;return Action.none(); }
    public void abort(String why) { fail(why); }
    /** The menu wait starts after the access action, not while walking to its NPC. */
    public void accessReady(long now) { if(state==State.SELECT_BOSS||state==State.SELECT_CANCEL) since=now; }
    public void stop() { clearReturned();if(state!=State.IDLE)observeStateMutation();state=State.IDLE;reason="Stopped"; }
    public State state() { return state; }
    public int completed(){return completed;}
    public String status() { return reason+" | completed="+completed; }
    public boolean inMenus() { return state!=State.IDLE&&state!=State.ACTIVE&&state!=State.COMPLETE&&state!=State.FAILED; }
}
