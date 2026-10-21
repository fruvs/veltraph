package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.random.RandomGenerator;

/** Readable4.0.30 resource-helper dispatch. Source values are supplied; facts are observed by the native port. */
public final class SourceResourcePolicy {
    public enum Mode { BAZAAR, BUILDER, WOOL_WEAVER, INFINIDIRT, MAD_REDSTONE_ENGINEER, BAZAAR_AND_DISOLVE, EMPTY }
    public enum Order { FIRST, LAST, RANDOM }
    public enum State { START, SWITCH_TO_TOOL, LOOK_AWAY, OPEN_TOOL, MOVE_FROM_INV_TO_TOOL,
        CHECK_ALREADY_HAS_REQUIRED_IN_TOOL, BUY_RESOURCE, REMOVE_FROM_TOOL, SELL_OTHER_RESOURCES,
        CLOSE_GUI, OPEN_INV_TO_DISSOLVE, PICK_UP_BLOCK, PUT_IN_CRAFTING_TABLE, CRAFT_ITEM,
        CLOSE_GUI_TO_LOOP, WIGGLE, COMPLETE, BLOCKED }
    public enum Action { NONE, SELECT_TOOL, LOOK_AWAY, USE_TOOL, QUICK_MOVE_IN, QUICK_MOVE_OUT,
        BUY, SELL_OTHER, CLOSE, OPEN_INVENTORY, PICK_PACKED, PUT_RANDOM_INPUT, QUICK_MOVE_RESULT,
        RECOVER, COMPLETE, STOP }
    public record Slot(int id,int count) {public Slot{if(id<0||count<1)throw new IllegalArgumentException("Invalid observed resource slot");}}
    public record Facts(boolean context,boolean gui,boolean toolMenu,boolean inventoryMenu,boolean selected,
                        boolean blockLook,boolean lagBlocked,boolean clickBlocked,int stored,
                        List<Slot> inventoryResource,List<Slot> toolForeign,List<Slot> packed,
                        int otherEligibleStacks,List<Integer> emptyCraftInputs,int resultCount,
                        boolean cursorPacked,boolean cursorEmpty,boolean ownedCraft,long now) {
        public Facts{inventoryResource=List.copyOf(inventoryResource);toolForeign=List.copyOf(toolForeign);packed=List.copyOf(packed);emptyCraftInputs=List.copyOf(emptyCraftInputs);}
    }
    public record Decision(Action action,int slot,int quantity,long generation,String reason) {}
    public record Candidate(BlockPos pos,double centerDistance,boolean standing) {}
    private final Mode mode;private final Order order;private final int required;
    private final long moveCooldown,removeCooldown,clickMin,clickMax,timeout;
    private final RandomGenerator random;
    private final Map<Integer,Long> recent=new HashMap<>();
    private State state=State.START;private long generation=1,next=-1,started=-1;
    private int openAttempts;private boolean recovering,closeProved;
    private String reason="Source helper start";
    public SourceResourcePolicy(Mode mode,Order order,int required,long moveCooldown,long removeCooldown,
                                long clickMin,long clickMax,long timeout,RandomGenerator random) {
        this.mode=Objects.requireNonNull(mode);this.order=Objects.requireNonNull(order);this.random=Objects.requireNonNull(random);
        if(required<0||required>50000||moveCooldown<0||removeCooldown<0||clickMin<0||clickMax<=clickMin||clickMax>30000||timeout<1000||timeout>300000)throw new IllegalArgumentException("Explicit source timing/count bounds required");
        this.required=required;this.moveCooldown=moveCooldown;this.removeCooldown=removeCooldown;this.clickMin=clickMin;this.clickMax=clickMax;this.timeout=timeout;
    }
    public Decision tick(Facts f) {
        if(!f.context())return fail("Source resource context/callback owner changed");
        if(started<0)started=f.now();if(f.now()-started>timeout)return fail("Source resource helper timed out");
        if(state==State.BLOCKED)return d(Action.STOP,-1,0,reason);
        if(state==State.COMPLETE)return d(Action.COMPLETE,-1,0,reason);
        if(recovering)return d(Action.NONE,-1,0,"Await owned WIGGLE path callback");
        if(next>=0&&f.now()<next||f.lagBlocked()||f.clickBlocked())return d(Action.NONE,-1,0,"Source shared click/lag gate");
        if(state==State.START) {
            openAttempts=0;closeProved=false;
            if(f.otherEligibleStacks()>5)state=State.SELL_OTHER_RESOURCES;
            else if(!f.selected())state=State.SWITCH_TO_TOOL;
            else if(f.blockLook())state=State.LOOK_AWAY;
            else if(mode==Mode.BAZAAR_AND_DISOLVE&&!f.packed().isEmpty())state=State.OPEN_INV_TO_DISSOLVE;
            else state=State.OPEN_TOOL;
        }
        switch(state) {
            case SWITCH_TO_TOOL -> {if(!f.selected())return issue(Action.SELECT_TOOL,-1,0,f.now());state=State.START;}
            case LOOK_AWAY -> {if(f.blockLook())return issue(Action.LOOK_AWAY,-1,0,f.now());state=State.OPEN_TOOL;}
            case OPEN_TOOL -> {
                if(f.toolMenu()){state=State.CHECK_ALREADY_HAS_REQUIRED_IN_TOOL;break;}
                if(f.gui())return fail("Foreign GUI while opening source tool");
                if(f.blockLook()){state=State.LOOK_AWAY;break;}
                // Exact source trigger: increment, then compare against a freshly drawn3or4.
                if(++openAttempts>random.nextInt(3,5)){state=State.WIGGLE;recovering=true;return d(Action.RECOVER,-1,2000,"Source7x7x7 nearest valid standing-point recovery");}
                return issue(Action.USE_TOOL,-1,0,f.now());
            }
            case CHECK_ALREADY_HAS_REQUIRED_IN_TOOL -> {
                if(!f.toolMenu())return fail("Source tool menu replaced");
                if(f.stored()<0)return fail("Source tool storage count unavailable");
                if(!f.toolForeign().isEmpty())state=State.REMOVE_FROM_TOOL;
                else if(f.otherEligibleStacks()>5)state=State.SELL_OTHER_RESOURCES;
                else if(!f.inventoryResource().isEmpty())state=State.MOVE_FROM_INV_TO_TOOL;
                else if(mode==Mode.EMPTY||f.stored()>=required){closeProved=true;state=State.CLOSE_GUI;}
                else state=State.BUY_RESOURCE;
            }
            case MOVE_FROM_INV_TO_TOOL -> {
                if(!f.toolMenu())return fail("Source tool menu replaced during insertion");
                int missing=required-f.stored();if(missing<=0){closeProved=true;state=State.CLOSE_GUI;break;}
                if(f.inventoryResource().isEmpty()){state=State.BUY_RESOURCE;break;}
                int slot=choose(f.inventoryResource());if(cooled(slot,moveCooldown,f.now()))return issue(Action.QUICK_MOVE_IN,slot,missing,f.now());
            }
            case REMOVE_FROM_TOOL -> {
                if(!f.toolMenu())return fail("Source tool menu replaced during removal");
                if(f.toolForeign().isEmpty()){state=State.CHECK_ALREADY_HAS_REQUIRED_IN_TOOL;break;}
                int slot=choose(f.toolForeign());if(cooled(slot,removeCooldown,f.now()))return issue(Action.QUICK_MOVE_OUT,slot,0,f.now());
            }
            case BUY_RESOURCE -> {
                if(mode==Mode.EMPTY){closeProved=true;state=State.CLOSE_GUI;break;}
                if(mode==Mode.INFINIDIRT||mode==Mode.MAD_REDSTONE_ENGINEER)return fail("Source acquisition mode not supported by helper: "+mode);
                if(f.stored()<0)return fail("Observed source tool count unavailable before buying");
                int deficit=required-f.stored();if(deficit<=0){closeProved=true;state=State.CLOSE_GUI;break;}
                return issue(Action.BUY,-1,deficit,f.now());
            }
            case SELL_OTHER_RESOURCES -> {return issue(Action.SELL_OTHER,-1,f.otherEligibleStacks(),f.now());}
            case CLOSE_GUI -> {
                if(!closeProved)return fail("Source helper close lacks material/EMPTY-resource proof");
                if(!f.cursorEmpty())return fail("Source helper close has carried item");
                if(f.gui())return issue(Action.CLOSE,-1,0,f.now());state=State.COMPLETE;return d(Action.COMPLETE,-1,0,"Source material outcome and close observed");
            }
            case OPEN_INV_TO_DISSOLVE -> {
                if(f.inventoryMenu()){if(!f.ownedCraft())return fail("Preexisting unowned crafting input/result");state=State.PICK_UP_BLOCK;break;}
                if(f.gui())return issue(Action.CLOSE,-1,0,f.now());return issue(Action.OPEN_INVENTORY,-1,0,f.now());
            }
            case PICK_UP_BLOCK -> {
                if(!f.inventoryMenu()){state=State.CLOSE_GUI_TO_LOOP;break;}
                if(f.packed().isEmpty()){state=State.CLOSE_GUI_TO_LOOP;break;}
                if(!f.cursorEmpty())return fail("Dissolution source cursor is not empty");
                return issue(Action.PICK_PACKED,f.packed().getFirst().id(),f.packed().getFirst().count(),f.now());
            }
            case PUT_IN_CRAFTING_TABLE -> {
                if(!f.inventoryMenu()){state=State.CLOSE_GUI_TO_LOOP;break;}
                if(f.resultCount()>0){state=State.CRAFT_ITEM;break;}
                if(!f.cursorPacked())return fail("Dissolution carried item differs from owned packed source");
                // Exact source random1..4; native port proves the chosen cell is empty before input.
                int slot=random.nextInt(1,5);if(!f.emptyCraftInputs().contains(slot))return fail("Chosen source crafting cell occupied; preserved");
                return issue(Action.PUT_RANDOM_INPUT,slot,0,f.now());
            }
            case CRAFT_ITEM -> {
                if(!f.inventoryMenu()){state=State.CLOSE_GUI_TO_LOOP;break;}
                if(f.resultCount()==0){state=State.PICK_UP_BLOCK;break;}
                if(!f.ownedCraft()||!f.cursorEmpty())return fail("Unowned crafting result/cursor");
                return issue(Action.QUICK_MOVE_RESULT,0,f.resultCount(),f.now());
            }
            case CLOSE_GUI_TO_LOOP -> {
                if(!f.cursorEmpty())return fail("Dissolution loop close has carried item");
                if(f.gui())return issue(Action.CLOSE,-1,0,f.now());restart();
            }
            case WIGGLE -> {return fail("WIGGLE requires owned path callback");}
            default -> {}
        }
        return d(Action.NONE,-1,0,"Source dispatch "+state);
    }
    public void acknowledged(Action action) {
        if(state==State.BLOCKED||state==State.COMPLETE)return;
        if(action==Action.PICK_PACKED)state=State.PUT_IN_CRAFTING_TABLE;
        if(action==Action.PUT_RANDOM_INPUT)state=State.CRAFT_ITEM;
        // Source stays in CRAFT_ITEM while a partial shift craft still has a result.
        if(action==Action.BUY||action==Action.SELL_OTHER)restart();
    }
    public boolean recovered(long ticket,boolean success){if(!recovering||ticket!=generation)return false;recovering=false;if(success)restart();else fail("Source WIGGLE path failed");return true;}
    public void cancel(){generation++;recovering=false;state=State.BLOCKED;reason="Source resource helper cancelled";}
    private void restart(){generation++;recovering=false;state=State.START;openAttempts=0;next=-1;closeProved=false;reason="Source start callback";}
    private boolean cooled(int slot,long gap,long now){Long last=recent.get(slot);if(last!=null&&now-last<gap)return false;recent.put(slot,now);return true;}
    private int choose(List<Slot> slots){return switch(order){case FIRST->slots.getFirst().id();case LAST->slots.getLast().id();case RANDOM->slots.get(random.nextInt(slots.size())).id();};}
    private Decision issue(Action action,int slot,int qty,long now){next=now+random.nextLong(clickMin,clickMax);return d(action,slot,qty,"Source "+state);}
    private Decision d(Action a,int s,int q,String text){reason=text;return new Decision(a,s,q,generation,text);}
    private Decision fail(String text){state=State.BLOCKED;return d(Action.STOP,-1,0,text);}
    public State state(){return state;}public long generation(){return generation;}public String status(){return state+": "+reason;}
    /** Source vector center helper adds .5 on ALL axes before the 1.5 exclusion test. */
    public static double recoveryCenterDistance(BlockPos candidate,dev.veltraph.client.world.Vec3 actual) {
        return new dev.veltraph.client.world.Vec3(candidate.x()+.5,candidate.y()+.5,candidate.z()+.5).distanceTo(actual);
    }
    public static List<BlockPos> recoveryCandidates(BlockPos actor,List<Candidate> facts) {
        return facts.stream().filter(c->c.standing()&&c.centerDistance()>=1.5&&Math.abs(c.pos().x()-actor.x())<=3&&Math.abs(c.pos().y()-actor.y())<=3&&Math.abs(c.pos().z()-actor.z())<=3)
                .sorted(Comparator.comparingLong(c->{long dx=c.pos().x()-actor.x(),dy=c.pos().y()-actor.y(),dz=c.pos().z()-actor.z();return dx*dx+dy*dy+dz*dz;})).map(Candidate::pos).toList();
    }
}
