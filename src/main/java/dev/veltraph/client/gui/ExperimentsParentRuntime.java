package dev.veltraph.client.gui;

import java.util.*;

/** Table/tier/XP/renewal parent under the solver's owner. Never retries an uncertain side effect. */
public final class ExperimentsParentRuntime {
    public enum State { TABLE, SELECT_TIER, XP_ENTRY, XP_NAVIGATION, BUY_BOTTLE, WAIT_FOR_PURCHASE, XP_RETURN,
        WAITING_ACK, PLAYING, GAME_RETURN, RENEW, WAIT_FOR_RENEWAL, COMPLETE, FAILED, CANCELLED }
    public enum Kind { NONE, CLICK, CLOSE, COMMAND }
    public record Action(Kind kind,MenuSnapshot menu,int slot,String command) {
        static Action none(){return new Action(Kind.NONE,null,-1,"");}
    }
    /** Full main/offhand/armor inventory identity includes components; XP is read from native player state. */
    public record Observation(MenuSnapshot menu,int level,List<String> inventory,List<String> resourceRows,boolean cursorEmpty,boolean ownPrediction,boolean serverRevision) {
        public Observation(MenuSnapshot menu,int level,List<String> inventory,List<String> resourceRows,boolean cursorEmpty){this(menu,level,inventory,resourceRows,cursorEmpty,false,true);}
        public Observation { inventory=List.copyOf(inventory);resourceRows=List.copyOf(resourceRows); }
    }
    private record Pending(Observation before,long issued,State destination,String title,int expectedLevel,boolean routeStep) {}
    private record Quote(MenuSnapshot.Slot slot,int levels,long cost) {}
    private final ExperimentsRuntime solver=new ExperimentsRuntime();
    private ExperimentsParentSettings settings;
    private ExperimentSettings.RewardThreshold stageThreshold;
    private State state=State.CANCELLED;
    private Pending pending;
    private Observation economicBefore;
    private Quote purchase;
    private Observation issuedBefore;
    private long issuedAt;
    private boolean receipt,tableCommandIssued;
    private long started,deadline,nextClick,spent,renewalSpent,renewalCost,resourceBefore;
    private int stage,renewals,purchases,route,requiredLevel,levelCost,renewalCounter,purchaseExpectedLevel;
    private String reason="Parent disabled";
    public void start(ExperimentsParentSettings p,long now) {
        settings=Objects.requireNonNull(p);solver.reset();state=State.TABLE;pending=null;economicBefore=null;purchase=null;
        issuedBefore=null;issuedAt=0;started=now;deadline=now+p.timeoutMillis();nextClick=now;spent=0;renewalSpent=0;renewalCost=0;
        stageThreshold=null;stage=0;renewals=0;purchases=0;route=0;requiredLevel=0;levelCost=0;receipt=false;tableCommandIssued=false;reason="Waiting for exact table menu";
    }
    public void cancel(){if(state!=State.COMPLETE&&state!=State.FAILED){state=State.CANCELLED;reason="Parent cancelled";}release();}
    public void release(){pending=null;economicBefore=null;purchase=null;issuedBefore=null;solver.releaseSession();}
    public State state(){return state;}
    public long spent(){return spent;}
    public int renewals(){return renewals;}
    public String diagnostics(){return state+": "+reason+"; stage="+(stage+1)+"; renewals="+renewals+"; XP spend="+spent+"; renewal spend="+renewalSpent+"; purchases="+purchases;}
    public void rejectedAction(){fail("Native dispatch rejected changed observation; action is not retried");}
    public void onServerMessage(String line) {
        if(settings==null)return;
        String clean=MenuSnapshot.clean(line);
        if(state==State.WAIT_FOR_PURCHASE&&purchase!=null&&clean.equals(format(settings.purchaseReceipt(),purchase.cost(),purchase.levels(),0)))receipt=true;
        if(state==State.WAIT_FOR_RENEWAL&&clean.equals(format(settings.renewalReceipt(),renewalCost,0,renewalCounter+1)))receipt=true;
    }
    public Action tick(Observation o,ExperimentSettings gameSettings,long now) {
        if(terminal())return Action.none();
        if(now<started||now-started>=settings.maxSessionMillis())return fail("Experiment session bound expired");
        if(o.level()<0)return fail("Missing native XP observation");
        if(o.menu()!=null&&!settings.allowsScreen(o.menu().title()))return fail("Unexpected experiment parent screen");
        if(state!=State.PLAYING&&now>=deadline)return fail("No confirmed server outcome; uncertain action is not retried");
        if(!o.cursorEmpty()) {
            boolean waiting=pending!=null||state==State.WAIT_FOR_PURCHASE||state==State.WAIT_FOR_RENEWAL
                    ||(state==State.PLAYING&&(solver.state()==ExperimentsRuntime.State.WAITING_ACK||solver.state()==ExperimentsRuntime.State.CLAIMING));
            if(!waiting||issuedBefore==null||!o.ownPrediction()||o.serverRevision()||o.menu()==null||issuedBefore.menu()==null
                    ||o.menu().id()!=issuedBefore.menu().id()||!o.menu().title().equals(issuedBefore.menu().title()))
                return fail("Foreign cursor while "+state+"; ownedPrediction="+o.ownPrediction()+"; serverRevision="+o.serverRevision());
            if(!o.inventory().equals(issuedBefore.inventory()))return fail("Protected inventory changed during owned PICKUP prediction");
            long timeout=state==State.PLAYING?gameSettings.acknowledgmentTimeoutMillis():settings.timeoutMillis();
            if(now-issuedAt>=timeout)return fail("Owned PICKUP prediction has no server correction; uncertain click is not retried");
            reason="Waiting for owned PICKUP prediction correction; prediction is not a server acknowledgment";
            return Action.none();
        }
        if(state==State.PLAYING)return play(o,gameSettings,now);
        if(pending!=null)return acknowledge(o,now);
        if(state==State.WAIT_FOR_PURCHASE)return purchased(o,now);
        if(state==State.WAIT_FOR_RENEWAL)return renewed(o,now);
        if(now<nextClick)return Action.none();
        var s=settings.stages().get(stage);
        switch(state) {
            case TABLE -> {
                if(o.menu()==null)return openTable(now);
                if(!title(o,settings.tableTitle()))return Action.none();
                tableCommandIssued=false;
                return navigate(o,s.select(),State.SELECT_TIER,now,-1);
            }
            case SELECT_TIER -> {
                if(!title(o,s.tier().title()))return Action.none();
                var button=unique(o.menu(),s.tier());if(button==null)return fail("Missing or ambiguous tier identity/state");
                Long required=number(button.lore(),s.requiredLevelPrefix(),""),cost=number(button.lore(),s.levelCostPrefix(),"");
                if(required==null||cost==null||required>Integer.MAX_VALUE||cost>Integer.MAX_VALUE||cost>required)return fail("Tier XP requirement/cost quote missing or invalid");
                requiredLevel=required.intValue();levelCost=cost.intValue();
                stageThreshold=null;
                if(ExperimentsRuntime.identify(s.tier().nextTitle())!=ExperimentsRuntime.Game.SUPERPAIRS) {
                    if(s.rewardThreshold()==null)return fail("Minigame stage needs an explicit reward threshold lore/title contract");
                    Integer rounds=rewardThreshold(button.lore(),s.rewardThreshold().tierLoreMarker());
                    if(rounds==null)return fail("Minigame reward threshold missing or malformed");
                    stageThreshold=new ExperimentSettings.RewardThreshold(rounds,s.rewardThreshold().rewardMenuTitle());
                }
                if(o.level()<requiredLevel) {
                    if(settings.maxPurchases()==0||purchases>=settings.maxPurchases())return fail("Tier needs XP; purchase disabled or bounded count exhausted");
                    route=0;enter(State.XP_ENTRY,now,"Opening XP level purchase flow");return Action.none();
                }
                solver.reset();return navigate(o,s.tier(),State.PLAYING,now,o.level()-levelCost);
            }
            case XP_ENTRY -> {return navigate(o,s.xpEntry(),State.XP_NAVIGATION,now,-1);}
            case XP_NAVIGATION -> {return route(o,settings.xpPath(),State.BUY_BOTTLE,now);}
            case BUY_BOTTLE -> {return buy(o,now);}
            case XP_RETURN -> {return route(o,settings.xpReturnPath(),State.TABLE,now);}
            case GAME_RETURN -> {
                if(o.menu()==null)return openTable(now);
                if(!title(o,settings.tableTitle()))return Action.none();
                if(stage+1<settings.stages().size()){stage++;enter(State.TABLE,now,"Server completed game; starting next stage");return Action.none();}
                if(!settings.renewals()||renewals>=settings.maxRenewals()){state=State.COMPLETE;reason="Server confirmed all configured games and renewal bound";return new Action(Kind.CLOSE,o.menu(),-1,"");}
                enter(State.RENEW,now,"Checking server renewal quote");return Action.none();
            }
            case RENEW -> {return renew(o,now);}
            default -> {return Action.none();}
        }
    }
    private Action play(Observation o,ExperimentSettings p,long now) {
        if(!o.serverRevision()) {
            solver.waitForServerOutcome(p,now);
            return solver.state()==ExperimentsRuntime.State.FAILED?fail(solver.diagnostics()):Action.none();
        }
        if(!solver.acceptsExitObservation()&&(o.menu()==null||!title(o,settings.stages().get(stage).tier().nextTitle())))return fail("Game changed before server completion");
        var action=solver.tick(o.menu(),p.withRewardThreshold(stageThreshold),now);
        if(solver.state()==ExperimentsRuntime.State.FAILED)return fail(solver.diagnostics());
        if(solver.state()==ExperimentsRuntime.State.COMPLETE){enter(State.GAME_RETURN,now,"Server confirmed game reward completion");tableCommandIssued=false;}
        return switch(action.kind()) {
            case CLICK -> issued(o,now,action.slot());
            case CLOSE -> new Action(Kind.CLOSE,action.menu(),-1,"");
            case NONE -> Action.none();
        };
    }
    private Action issued(Observation before,long now,int slot) {
        issuedBefore=before;issuedAt=now;return new Action(Kind.CLICK,before.menu(),slot,"");
    }
    private Action openTable(long now) {
        if(settings.tableCommand().isBlank()||tableCommandIssued)return Action.none();
        tableCommandIssued=true;deadline=now+settings.timeoutMillis();reason="Waiting for explicitly configured table command result";
        return new Action(Kind.COMMAND,null,-1,settings.tableCommand());
    }
    private Action navigate(Observation o,ExperimentsParentSettings.Button b,State destination,long now,int expectedLevel) {
        if(!title(o,b.title()))return Action.none();
        var target=unique(o.menu(),b);if(target==null)return fail("Missing or ambiguous navigation button/state");
        pending=new Pending(o,now,destination,b.nextTitle(),expectedLevel,destination==state&&(state==State.XP_NAVIGATION||state==State.XP_RETURN));state=State.WAITING_ACK;deadline=now+settings.timeoutMillis();reason="Waiting for exact server menu and resource outcome";
        return issued(o,now,target.id());
    }
    private Action acknowledge(Observation o,long now) {
        var p=pending;
        if(!o.inventory().equals(p.before().inventory()))return fail("Protected inventory changed during parent navigation");
        if(o.menu()==null)return Action.none();
        if(!o.menu().title().equals(p.title())) {
            if(o.menu().id()!=p.before().menu().id()||!o.menu().title().equals(p.before().menu().title()))return fail("Unrelated menu replaced pending parent action");
            return Action.none();
        }
        if(!o.serverRevision()||(o.menu().id()==p.before().menu().id()&&o.menu().revision()==p.before().menu().revision()))return Action.none();
        if(p.expectedLevel()>=0&&o.level()!=p.expectedLevel())return Action.none();
        State dest=p.destination();pending=null;enter(dest,now,"Observed exact parent action outcome");
        if(p.routeStep())route++;
        return Action.none();
    }
    private Action route(Observation o,List<ExperimentsParentSettings.Button> path,State destination,long now) {
        if(route>=path.size()){route=0;enter(destination,now,"Observed navigation chain complete");return Action.none();}
        return navigate(o,path.get(route),state,now,-1);
    }
    private Action buy(Observation o,long now) {
        if(!title(o,settings.xpPath().getLast().nextTitle()))return Action.none();
        var quotes=new ArrayList<Quote>();
        for(var slot:o.menu().slots()) {
            if(slot.playerInventory()||slot.locked()||slot.count()<1||!slot.itemId().equals("minecraft:experience_bottle")||!MenuSnapshot.clean(slot.name()).equals(settings.xpBottleName())||slot.lore().stream().map(MenuSnapshot::clean).noneMatch(settings.xpAvailableLore()::equals))continue;
            Long levels=number(slot.lore(),settings.xpLevelsPrefix(),"");Long cost=costAfterHeader(slot.lore());
            if(levels==null||cost==null||levels>Integer.MAX_VALUE)return fail("Malformed XP bottle quote");
            if(levels>requiredLevel)quotes.add(new Quote(slot,levels.intValue(),cost));
        }
        if(quotes.isEmpty())return fail("No source-shaped bottle quote above requested XP threshold");
        // Source picks first eligible server slot; duplicates require explicit disambiguation instead.
        quotes.sort(Comparator.comparingInt(q->q.slot().id()));purchase=quotes.getFirst();
        if(quotes.stream().filter(q->q.levels()==purchase.levels()).count()>1)return fail("Ambiguous XP level quote");
        Long funds=number(o.resourceRows(),settings.currencyPrefix(),"");
        if(funds==null||purchase.cost()>funds||purchase.cost()>settings.maxXpSpend()-spent)return fail("XP currency unavailable or cumulative spend bound exceeded");
        long credit=settings.xpCreditMode()==ExperimentsParentSettings.XpCreditMode.FINAL_LEVEL?purchase.levels():(long)o.level()+purchase.levels();
        if(credit>Integer.MAX_VALUE||credit<=o.level())return fail("XP quote credit outcome invalid");
        purchaseExpectedLevel=(int)credit;
        economicBefore=o;resourceBefore=funds;receipt=false;purchases++;enter(State.WAIT_FOR_PURCHASE,now,"Waiting for exact purchase receipt, XP and currency outcome");
        return issued(o,now,purchase.slot().id());
    }
    private Action purchased(Observation o,long now) {
        if(!o.inventory().equals(economicBefore.inventory()))return fail("Protected inventory changed during direct XP purchase");
        Long funds=number(o.resourceRows(),settings.currencyPrefix(),"");
        if(funds!=null&&funds<resourceBefore-purchase.cost())return fail("Unexpected XP purchase resource deduction");
        if(!o.serverRevision()||!receipt||o.level()!=purchaseExpectedLevel||funds==null||funds!=resourceBefore-purchase.cost())return Action.none();
        spent+=purchase.cost();economicBefore=null;purchase=null;receipt=false;route=0;enter(State.XP_RETURN,now,"Purchase receipt, server XP and currency agree");return Action.none();
    }
    private Action renew(Observation o,long now) {
        if(!title(o,settings.tableTitle()))return Action.none();
        var b=unique(o.menu(),settings.renewButton());if(b==null)return fail("Missing or ambiguous renewal state/button");
        Long cost=number(b.lore(),settings.renewalCostPrefix(),""),counter=number(allLore(o.menu()),settings.renewalCountPrefix(),"");
        Long resource=number(o.resourceRows(),settings.renewalResourcePrefix(),"");
        if(cost==null||counter==null||counter>Integer.MAX_VALUE||resource==null||cost>resource||cost>settings.maxRenewalSpend()-renewalSpent)
            return fail("Renewal quote/resource/counter unavailable or budget exceeded");
        renewalCost=cost;renewalCounter=counter.intValue();resourceBefore=resource;economicBefore=o;receipt=false;
        enter(State.WAIT_FOR_RENEWAL,now,"Waiting for exact renewal receipt, counter and resource outcome");
        return issued(o,now,b.id());
    }
    private Action renewed(Observation o,long now) {
        if(!o.inventory().equals(economicBefore.inventory())||o.level()!=economicBefore.level())return fail("Protected inventory/XP changed during renewal");
        if(!title(o,settings.tableTitle()))return Action.none();
        Long counter=number(allLore(o.menu()),settings.renewalCountPrefix(),""),resource=number(o.resourceRows(),settings.renewalResourcePrefix(),"");
        if(!o.serverRevision()||!receipt||counter==null||counter!=renewalCounter+1L||resource==null||resource!=resourceBefore-renewalCost
                ||o.menu().id()!=economicBefore.menu().id()||o.menu().revision()==economicBefore.menu().revision())return Action.none();
        renewals++;renewalSpent+=renewalCost;stage=0;economicBefore=null;receipt=false;enter(State.TABLE,now,"Server renewal confirmed; repeat state retained for next actual game");return Action.none();
    }
    private Long costAfterHeader(List<String> lore) {
        int index=-1;for(int i=0;i<lore.size();i++)if(MenuSnapshot.clean(lore.get(i)).equals(settings.xpCostHeader())){if(index>=0)return null;index=i;}
        if(index<0||index+1>=lore.size())return null;return number(List.of(lore.get(index+1)),"",settings.xpCostSuffix());
    }
    /** Source uses the last matching tier row and the integer before the following colon. */
    public static Integer rewardThreshold(List<String> lore,String marker) {
        String row=null;
        for(String line:lore)if(MenuSnapshot.clean(line).contains(marker))row=MenuSnapshot.clean(line);
        if(row==null)return null;
        String suffix=row.substring(row.indexOf(marker)+marker.length());
        int colon=suffix.indexOf(':');if(colon<0)return null;
        String count=suffix.substring(0,colon).strip();
        if(!count.matches("[0-9]+"))return null;
        try{return Integer.parseInt(count);}catch(NumberFormatException invalid){return null;}
    }
    /** Exact complete integer row, comma grouping permitted; duplicate/malformed quotes never coerce. */
    public static Long number(List<String> rows,String prefix,String suffix) {
        Long result=null;int count=0;
        for(String row:rows) {
            String s=MenuSnapshot.clean(row);if(!s.startsWith(prefix)||!s.endsWith(suffix))continue;
            if(s.length()<prefix.length()+suffix.length())return null;
            s=s.substring(prefix.length(),s.length()-suffix.length()).strip();
            if(!s.matches("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)"))return null;
            try{result=Long.parseLong(s.replace(",",""));}catch(NumberFormatException invalid){return null;}
            if(++count>1)return null;
        }
        return result;
    }
    private static List<String> allLore(MenuSnapshot m){return m.slots().stream().filter(s->!s.playerInventory()).flatMap(s->s.lore().stream()).toList();}
    private static MenuSnapshot.Slot unique(MenuSnapshot m,ExperimentsParentSettings.Button b){var matches=m.slots().stream().filter(b::matches).toList();return matches.size()==1?matches.getFirst():null;}
    private static String format(String template,long cost,int levels,int renewals){return template.replace("{cost}",Long.toString(cost)).replace("{levels}",Integer.toString(levels)).replace("{renewals}",Integer.toString(renewals));}
    private static boolean title(Observation o,String title){return o.menu()!=null&&o.menu().title().equals(title);}
    private void enter(State s,long now,String message){state=s;deadline=now+settings.timeoutMillis();nextClick=now+settings.clickDelayMillis();reason=message;}
    private boolean terminal(){return state==State.FAILED||state==State.CANCELLED||state==State.COMPLETE;}
    private Action fail(String message){state=State.FAILED;reason=message;release();return Action.none();}
}
