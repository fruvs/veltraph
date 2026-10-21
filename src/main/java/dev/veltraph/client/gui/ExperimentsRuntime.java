package dev.veltraph.client.gui;

import java.util.*;

/** Deterministic game-only FSM. Every issued click waits for a fresh server menu observation. */
public final class ExperimentsRuntime {
    public enum Game { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }
    public enum State { IDLE, OBSERVING, PLAYING, WAITING_ACK, EXITING, CLAIMING, COMPLETE, FAILED }
    public enum Kind { NONE, CLICK, CLOSE }
    public record Action(Kind kind,MenuSnapshot menu,int slot) {
        public static Action none() { return new Action(Kind.NONE,null,-1); }
    }
    private record Pending(MenuSnapshot before,int slot,long started,boolean reward) {}
    private int menuId=-1,roundClicks;
    private Game game;
    private State state=State.IDLE;
    private String reason="Open a supported experiment game";
    private long nextClick;
    private Pending pending;
    private boolean showing;
    private final List<Integer> sequence=new ArrayList<>();
    private final Set<Integer> glowing=new HashSet<>();
    private final Map<Integer,Integer> numbers=new TreeMap<>();
    private final Set<Integer> board=new TreeSet<>();
    private final Map<Integer,String> memory=new HashMap<>();
    private final Map<Integer,SuperpairsRewardBindings.Card> rewardCards=new HashMap<>();
    private String preferenceDiagnostic="";
    private final Set<Integer> revealed=new HashSet<>();
    private final Set<Integer> matched=new HashSet<>();
    private boolean rewardIssued;
    private long rewardDeadline;
    private MenuSnapshot rewardBefore;
    private MenuSnapshot exitBefore;
    private long exitDeadline;
    private boolean exitRewardMenu;
    public boolean acceptsExitObservation() { return state==State.EXITING||exitRewardMenu; }
    /** Prediction/stale native frames may expire an outstanding action but cannot acknowledge it. */
    public void waitForServerOutcome(ExperimentSettings settings,long now) {
        if(pending!=null&&now-pending.started()>=settings.acknowledgmentTimeoutMillis())fail("No server acknowledgment; uncertain click is not retried");
        else if(rewardIssued&&now>=rewardDeadline)fail("Reward claim has no confirmed server outcome");
        else if(!rewardIssued&&(state==State.EXITING||exitRewardMenu)&&now>=exitDeadline)fail("Exit has no fresh exact reward outcome");
    }
    public State state() { return state; }
    public String diagnostics() { return state+": "+reason+preferenceDiagnostic+"; acknowledged moves="+roundClicks; }
    public static Game identify(String title) {
        String text=MenuSnapshot.clean(title).toLowerCase(Locale.ROOT);
        if(text.startsWith("chronomatron")||text.startsWith("chain")) return Game.CHRONOMATRON;
        if(text.startsWith("ultrasequencer")||text.startsWith("series")) return Game.ULTRASEQUENCER;
        if(text.startsWith("superpairs")) return Game.SUPERPAIRS;
        return null;
    }
    public void reset() {
        menuId=-1;game=null;state=State.IDLE;reason="Open a supported experiment game";nextClick=0;pending=null;
        showing=false;roundClicks=0;sequence.clear();glowing.clear();numbers.clear();board.clear();memory.clear();rewardCards.clear();preferenceDiagnostic="";revealed.clear();matched.clear();rewardIssued=false;rewardDeadline=0;rewardBefore=null;exitBefore=null;exitDeadline=0;exitRewardMenu=false;
    }
    public void rejectedClick() { fail("Menu changed before click dispatch"); }
    /** Drop all retained menu/card data on disable while preserving the terminal diagnostic. */
    public void releaseSession() {
        pending=null;rewardBefore=null;exitBefore=null;exitRewardMenu=false;sequence.clear();glowing.clear();numbers.clear();board.clear();memory.clear();rewardCards.clear();preferenceDiagnostic="";revealed.clear();matched.clear();
        menuId=-1;game=null;showing=false;
    }
    public Action tick(MenuSnapshot menu,ExperimentSettings settings,long now) {
        if(state==State.FAILED||state==State.COMPLETE) return Action.none();
        if(state==State.EXITING) {
            if(now>=exitDeadline)return fail("Exit has no fresh exact reward menu; close is not a reward outcome");
            if(menu==null)return Action.none();
            if(menu.id()==exitBefore.id()&&menu.title().equals(exitBefore.title()))return Action.none();
            if(settings.rewardThreshold()==null||!menu.title().equals(settings.rewardThreshold().rewardMenuTitle())
                    ||(menu.id()==exitBefore.id()&&menu.revision()==exitBefore.revision()))return fail("Unrelated or stale menu replaced threshold exit");
            menuId=menu.id();exitRewardMenu=true;state=State.CLAIMING;reason="Observed exact reward menu after threshold exit";
        }
        if(menu==null) {
            if(menuId!=-1) fail("Game menu closed before an observed reward outcome");
            return Action.none();
        }
        Game observed=identify(menu.title());
        if(menuId==-1) {
            if(observed==null) return Action.none();
            menuId=menu.id();game=observed;state=State.OBSERVING;
        } else if(menu.id()!=menuId||(exitRewardMenu?!menu.title().equals(settings.rewardThreshold().rewardMenuTitle()):observed!=game)) {
            // Never adopt another menu or carry a puzzle across a reopened container.
            return fail("Experiment menu/session changed");
        }
        if(has(menu,settings.failedLabel())) return fail("Server reports experiment failure");
        if(rewardIssued&&rewardBefore!=null&&menu.revision()!=rewardBefore.revision()
                &&!has(rewardBefore,settings.completedLabel())&&has(menu,settings.completedLabel())) {
            pending=null;state=State.COMPLETE;reason="Server confirmed rewards claimed";
            return new Action(Kind.CLOSE,menu,-1);
        }
        if(pending!=null) {
            if(now-pending.started()>=settings.acknowledgmentTimeoutMillis()) return fail("No server acknowledgment; uncertain click is not retried");
            if(!acknowledged(menu,pending,settings)) return Action.none();
            boolean claim=pending.reward();pending=null;nextClick=now+settings.clickDelayMillis();
            if(claim) { state=State.CLAIMING;reason="Waiting for server reward outcome";return Action.none(); }
            roundClicks++;state=State.PLAYING;
        }
        if(rewardIssued) {
            if(now>=rewardDeadline) return fail("Reward claim has no confirmed server outcome");
            return Action.none();
        }
        var rewards=menu.matching(settings.rewardLabel()).stream().filter(s->!s.locked()).toList();
        if(rewards.size()>1) return fail("Ambiguous reward buttons");
        if(rewards.size()==1&&now>=nextClick) {
            rewardIssued=true;rewardBefore=menu;rewardDeadline=now+settings.acknowledgmentTimeoutMillis();return click(menu,rewards.getFirst().id(),now,true);
        }
        if(exitRewardMenu) { reason="Waiting for exact reward claim control";if(now>=exitDeadline)return fail("Reward menu has no claim control after threshold exit");return Action.none(); }
        if(game==Game.SUPERPAIRS) return pairs(menu,settings,now);
        if(settings.rewardThreshold()!=null&&game==Game.CHRONOMATRON) {
            // Source uses the first occupied server bookshelf, not the largest stack or move count.
            var round=menu.slots().stream().filter(s->!s.playerInventory()&&s.count()>0&&s.itemId().equals("minecraft:bookshelf")).findFirst().orElse(null);
            if(round==null)return fail("Missing server bookshelf round counter");
            if(round.count()>threshold(settings))return exit(menu,settings,now,round.count());
        }
        boolean show=has(menu,settings.showLabel()),input=has(menu,settings.inputLabel());
        if(show&&input) return fail("Ambiguous show/input indicators");
        if(show) {
            if(!showing) { sequence.clear();numbers.clear();glowing.clear();board.clear();roundClicks=0;showing=true; }
            state=State.OBSERVING;reason="Watching server show phase";
            if(game==Game.CHRONOMATRON) observeChain(menu);
            else observeNumbers(menu);
            if(state==State.FAILED)return Action.none();
            if(settings.rewardThreshold()!=null&&game==Game.ULTRASEQUENCER&&numbers.size()-1>threshold(settings))return exit(menu,settings,now,numbers.size()-1);
            return Action.none();
        }
        if(!input) { reason="Waiting for an explicit game phase or reward";return Action.none(); }
        if(showing) {
            showing=false;glowing.clear();
            if(game==Game.ULTRASEQUENCER) {
                if(numbers.isEmpty()) return fail("No numbered tiles observed");
                int expected=1;for(int number:numbers.keySet()) if(number!=expected++) return fail("Number sequence has a gap or duplicate");
                sequence.addAll(numbers.values());
            }
            if(sequence.isEmpty()) return fail("Input phase arrived without a complete show phase");
        }
        if(sequence.isEmpty()) return fail("Enable the helper before the show phase begins");
        if(roundClicks>=sequence.size()||now<nextClick) { reason="Waiting for next round or reward";return Action.none(); }
        int target=sequence.get(roundClicks);
        var slot=slot(menu,target);
        if(slot==null||slot.locked()||"minecraft:air".equals(slot.itemId())) return fail("Recorded game tile is unavailable");
        return click(menu,target,now,false);
    }
    private int threshold(ExperimentSettings settings) {
        return settings.maximizeXp()?(game==Game.ULTRASEQUENCER?20:15):settings.rewardThreshold().rounds();
    }
    private Action exit(MenuSnapshot menu,ExperimentSettings settings,long now,int count) {
        pending=null;exitBefore=menu;exitDeadline=now+settings.acknowledgmentTimeoutMillis();state=State.EXITING;
        reason="Observed round count "+count+" exceeds reward threshold "+threshold(settings)+"; waiting for reward screen after exit";
        return new Action(Kind.CLOSE,menu,-1);
    }
    private void observeChain(MenuSnapshot menu) {
        Set<Integer> active=new HashSet<>();
        for(var s:menu.slots()) if(gameTile(s,10,44)&&s.foil()) active.add(s.id());
        if(active.size()>1) { fail("Multiple highlighted sequence tiles");return; }
        if(active.size()==1) {
            int id=active.iterator().next();
            if(!glowing.contains(id)) sequence.add(id);
            board.add(id);
        }
        glowing.clear();glowing.addAll(active);
    }
    private void observeNumbers(MenuSnapshot menu) {
        // Original scans 9..44 and uses stack count; reject unrelated decorative glass and controls.
        Map<Integer,Integer> observed=new TreeMap<>();
        for(var s:menu.slots()) if(gameTile(s,9,45)&&numberItem(s)) {
            if(observed.put(s.count(),s.id())!=null) { fail("Duplicate numbered tiles");return; }
        }
        if(!observed.isEmpty()) { numbers.clear();numbers.putAll(observed);board.addAll(observed.values()); }
    }
    private static boolean numberItem(MenuSnapshot.Slot s) {
        return s.itemId().endsWith("_dye")||s.itemId().equals("minecraft:bone_meal")||s.itemId().equals("minecraft:lapis_lazuli");
    }
    private Action pairs(MenuSnapshot menu,ExperimentSettings settings,long now) {
        preferenceDiagnostic=settings.avoidXpSuperpairs()?(settings.superpairsRewards().xpCards().isEmpty()?"; XP preference unbound: no exact observed reward classification":"; exact observed XP preference enabled"):"";
        for(var s:menu.slots()) if(gameTile(s,9,45)&&MenuSnapshot.clean(s.name()).equals(settings.hiddenLabel())) board.add(s.id());
        if(board.isEmpty()) { reason="Waiting for face-down game cards";return Action.none(); }
        Set<Integer> visible=new HashSet<>();
        for(int id:board) {
            var s=slot(menu,id);
            if(s==null) return fail("Card grid changed");
            if(s.locked()||s.itemId().equals("minecraft:air")) { matched.add(id);continue; }
            if(!MenuSnapshot.clean(s.name()).equals(settings.hiddenLabel())) {
                visible.add(id);memory.put(id,identity(s));
                var card=SuperpairsRewardBindings.Card.from(s);if(card==null)rewardCards.remove(id);else rewardCards.put(id,card);
            }
        }
        // Only server-marked removed/locked cards count as matches; two client clicks do not.
        revealed.clear();revealed.addAll(visible);
        if(visible.size()>=2||now<nextClick) { reason="Waiting for server pair resolution";return Action.none(); }
        var hidden=board.stream().filter(id->!matched.contains(id)&&!visible.contains(id))
                .filter(id->MenuSnapshot.clean(slot(menu,id).name()).equals(settings.hiddenLabel()))
                .filter(id->!settings.avoidXpSuperpairs()||!settings.superpairsRewards().isXp(rewardCards.get(id))).toList();
        if(hidden.isEmpty()) { reason="Waiting for eligible cards or final reward";return Action.none(); }
        int target=-1;
        if(visible.size()==1) {
            String face=memory.get(visible.iterator().next());
            target=hidden.stream().filter(id->face.equals(memory.get(id))).findFirst().orElse(-1);
        } else {
            outer:for(int a:hidden) for(int b:hidden) if(a!=b&&memory.containsKey(a)&&memory.get(a).equals(memory.get(b))) { target=a;break outer; }
        }
        if(target<0) target=hidden.stream().filter(id->!memory.containsKey(id)).findFirst().orElse(hidden.getFirst());
        return click(menu,target,now,false);
    }
    private Action click(MenuSnapshot menu,int id,long now,boolean reward) {
        if(slot(menu,id)==null) return fail("Target is not a server-owned game slot");
        pending=new Pending(menu,id,now,reward);state=State.WAITING_ACK;reason=reward?"Claiming reward":"Waiting for observed move result";
        return new Action(Kind.CLICK,menu,id);
    }
    private boolean acknowledged(MenuSnapshot after,Pending p,ExperimentSettings settings) {
        if(after.revision()==p.before().revision()) return false;
        if(p.reward()) return !after.slots().equals(p.before().slots());
        if(has(after,settings.rewardLabel())||has(after,settings.showLabel())!=has(p.before(),settings.showLabel())) return true;
        var before=slot(p.before(),p.slot());
        var current=slot(after,p.slot());
        if(!Objects.equals(before,current)) return true;
        // A timer/control update alone cannot acknowledge a move. Board observations may do so.
        for(int id:board) if(!Objects.equals(slot(p.before(),id),slot(after,id))) return true;
        return false;
    }
    private static String identity(MenuSnapshot.Slot s) { return s.itemId()+"|"+s.count()+"|"+MenuSnapshot.clean(s.name())+"|"+String.join("\n",s.lore())+"|"+s.foil()+"|"+s.componentIdentity(); }
    private static boolean gameTile(MenuSnapshot.Slot s,int start,int end) { return !s.playerInventory()&&s.id()>=start&&s.id()<end&&!s.locked()&&!s.itemId().equals("minecraft:air"); }
    private static MenuSnapshot.Slot slot(MenuSnapshot menu,int id) { return menu.slots().stream().filter(s->s.id()==id&&!s.playerInventory()).findFirst().orElse(null); }
    private static boolean has(MenuSnapshot menu,String text) {
        String wanted=MenuSnapshot.clean(text).toLowerCase(Locale.ROOT);
        return menu.slots().stream().filter(s->!s.playerInventory()).anyMatch(s->MenuSnapshot.clean(s.name()).toLowerCase(Locale.ROOT).contains(wanted)
                ||s.lore().stream().anyMatch(l->MenuSnapshot.clean(l).toLowerCase(Locale.ROOT).contains(wanted)));
    }
    private Action fail(String message) { pending=null;state=State.FAILED;reason=message;return Action.none(); }
}
