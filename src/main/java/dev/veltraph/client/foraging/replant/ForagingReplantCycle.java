package dev.veltraph.client.foraging.replant;

import java.util.*;

/** Source seven stages, with explicit consumption/world acknowledgements and bounded failure cleanup. */
public final class ForagingReplantCycle {
    public enum Stage { SWITCH_TO_SAPLINGS, PLANTING_SAPLINGS, SWITCH_TO_BONEMEAL, USE_BONEMEAL, GO_TO_AXE, USE_AXE, RESETTING, RETURNING, COMPLETE, FAILED, CANCELLED }
    public enum Cell { EMPTY, SAPLING, LOG, WRONG }
    public enum Resource { SAPLING, BONEMEAL }
    public record Observation(boolean available,boolean loaded,boolean soilValid,boolean cursorEmpty,boolean wrongSpecies,boolean unexpectedScreen,
                              List<Cell> cells,int saplings,int bonemeal,boolean grown,boolean harvestDone,boolean harvestFailed,boolean home,long now) {
        public Observation { cells=List.copyOf(cells);if(cells.size()!=4)throw new IllegalArgumentException("Four source planting positions required"); }
    }
    public record Settings(long timeout,long ack,long interval,int boneLimit,int cycles,boolean reset) {}
    public interface Port {
        boolean equip(Resource resource);
        boolean plant(int index);
        boolean bonemeal(int index);
        boolean axe();
        boolean beginHarvest();
        void harvestTick();
        void clearSapling(int index);
        void release();
        boolean returnHome();
    }
    private Stage stage=Stage.SWITCH_TO_SAPLINGS;
    private long started=-1,sent=-1,stable=-1,nextBone;
    private int index,before,boneUses,cycles;
    private final BitSet owned=new BitSet(4);
    private String reason="",failureAfterReset="";
    private Resource supply;
    /** A shortage suspends this exact stage only between acknowledged actions. */
    public Optional<Resource> supplyRequest(){return Optional.ofNullable(supply);}
    public boolean supplyBoundary(){return supply!=null&&sent<0&&!terminal();}
    public boolean resumeAfterSupply(Observation o,long elapsed){
        if(!supplyBoundary()||elapsed<0||(supply==Resource.SAPLING?o.saplings()<4:o.bonemeal()<1))return false;
        if(started>=0)started=Math.addExact(started,elapsed);nextBone=Math.addExact(nextBone,elapsed);supply=null;reason="Supply acknowledged; same original stage resumed";return true;
    }
    public void rejectSupply(String message,Port p){p.release();supply=null;reason=message;stage=Stage.FAILED;}
    private void shortage(Resource resource,Port p){p.release();supply=resource;reason="Awaiting owned "+resource+" supply";}
    public Stage stage(){return stage;}public String reason(){return reason;}public int cycles(){return cycles;}public int bonemealUses(){return boneUses;}
    public String progress(){return "position="+index+" | bonemeal="+boneUses+" | awaitingAck="+(sent>=0);}
    public boolean atScheduledRestBoundary(){return supply==null&&stage==Stage.SWITCH_TO_SAPLINGS&&sent<0&&owned.isEmpty();}
    public void rebaseAfterScheduledRest(long elapsed){if(elapsed<0||!atScheduledRestBoundary())throw new IllegalStateException("Not an owned idle replant boundary");if(started>=0)started+=elapsed;nextBone+=elapsed;}
    public boolean terminal(){return stage==Stage.COMPLETE||stage==Stage.FAILED||stage==Stage.CANCELLED;}
    private void transition(Stage next,long now){stage=next;started=now;sent=stable=-1;}
    private void fail(String message,Observation o,Settings s,Port p){p.release();reason=message;
        if(s.reset()&&!owned.isEmpty()&&o.loaded()&&o.soilValid()&&!o.wrongSpecies()&&o.cursorEmpty()&&!o.unexpectedScreen()){
            failureAfterReset=message;index=3;p.axe();transition(Stage.RESETTING,o.now());
        }else transition(Stage.FAILED,o.now());
    }
    public void cancel(Port port){port.release();stage=Stage.CANCELLED;supply=null;reason="Cancelled";}
    public void tick(Observation o,Settings s,Port p){
        if(terminal()||supply!=null)return;if(started<0)started=o.now();
        if(!o.available()||!o.loaded()||!o.soilValid()||o.wrongSpecies()||!o.cursorEmpty()||o.unexpectedScreen()){
            p.release();reason=!o.available()?"World/player unavailable":!o.loaded()?"Plant/growth volume unloaded":!o.soilValid()?"Wrong soil":o.wrongSpecies()?"Wrong species or obstructed planting cell":!o.cursorEmpty()?"Occupied inventory cursor":"Unexpected screen";transition(Stage.FAILED,o.now());return;
        }
        if(o.now()-started>s.timeout()&&stage!=Stage.USE_AXE&&stage!=Stage.RESETTING){fail("Stage timed out: "+stage,o,s,p);return;}
        switch(stage){
            case SWITCH_TO_SAPLINGS -> {if(o.saplings()<4){shortage(Resource.SAPLING,p);return;}if(p.equip(Resource.SAPLING)){index=0;owned.clear();transition(Stage.PLANTING_SAPLINGS,o.now());}}
            case PLANTING_SAPLINGS -> {
                if(index==4){p.release();transition(Stage.SWITCH_TO_BONEMEAL,o.now());return;}
                if(sent>=0){
                    boolean ack=o.cells().get(index)==Cell.SAPLING&&o.saplings()==before-1;
                    if(ack){if(stable<0)stable=o.now();if(o.now()-stable>=s.ack()){owned.set(index);index++;sent=stable=-1;started=o.now();}}
                    else {stable=-1;if(o.now()-sent>s.timeout())fail("Placement/consumption not acknowledged",o,s,p);}
                }else if(o.cells().get(index)!=Cell.EMPTY){fail("Planting position is not empty",o,s,p);}
                else if(p.plant(index)){before=o.saplings();sent=o.now();owned.set(index);}
            }
            case SWITCH_TO_BONEMEAL -> {if(o.bonemeal()<1){shortage(Resource.BONEMEAL,p);return;}if(p.equip(Resource.BONEMEAL)){boneUses=0;nextBone=o.now();transition(Stage.USE_BONEMEAL,o.now());}}
            case USE_BONEMEAL -> {
                if(sent>=0){
                    if(o.bonemeal()==before-1){if(stable<0)stable=o.now();if(o.now()-stable>=s.ack()){boneUses++;sent=stable=-1;nextBone=o.now()+s.interval();}}
                    else if(o.now()-sent>s.timeout()){fail("Bonemeal consumption not acknowledged",o,s,p);return;}
                    else stable=-1;
                }
                if(o.grown()&&sent<0&&boneUses>0){p.release();transition(Stage.GO_TO_AXE,o.now());return;}
                if(sent<0&&o.now()>=nextBone){
                    if(boneUses>=s.boneLimit()){fail("Bonemeal consumed without tree growth",o,s,p);return;}
                    if(o.bonemeal()<1){shortage(Resource.BONEMEAL,p);return;}
                    int target=o.cells().indexOf(Cell.SAPLING);if(target<0){fail("Saplings disappeared without matching growth",o,s,p);return;}
                    if(p.bonemeal(target)){before=o.bonemeal();sent=o.now();}
                }
            }
            case GO_TO_AXE -> {if(p.axe()&&p.beginHarvest())transition(Stage.USE_AXE,o.now());}
            case USE_AXE -> {p.harvestTick();if(o.harvestFailed()){fail("Normal harvest failed",o,s,p);return;}if(o.harvestDone()){p.release();transition(Stage.RETURNING,o.now());}}
            case RETURNING -> {if(o.home()||p.returnHome()){p.release();cycles++;owned.clear();if(s.cycles()>0&&cycles>=s.cycles())transition(Stage.COMPLETE,o.now());else transition(Stage.SWITCH_TO_SAPLINGS,o.now());}}
            case RESETTING -> {
                if(o.now()-started>s.timeout()){p.release();reason="Reset timed out after: "+failureAfterReset;transition(Stage.FAILED,o.now());return;}
                while(index>=0&&(!owned.get(index)||o.cells().get(index)==Cell.EMPTY))index--;
                if(index<0){p.release();reason=failureAfterReset;transition(Stage.FAILED,o.now());return;}
                if(o.cells().get(index)!=Cell.SAPLING){p.release();reason="Reset refuses non-sapling replacement";transition(Stage.FAILED,o.now());return;}
                if(p.axe())p.clearSapling(index);
            }
            default -> {}
        }
    }
}
