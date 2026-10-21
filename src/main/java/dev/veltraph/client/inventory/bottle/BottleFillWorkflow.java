package dev.veltraph.client.inventory.bottle;

import java.util.*;

/** Prepared glass -> actual source-water conversion -> fresh-server chest storage, repeated finitely. */
public final class BottleFillWorkflow {
    public enum State { IDLE, MOVE, WAIT_MOVE, WATER, FILL, WAIT_FILL, CHEST, WAIT_CHEST, STORE, WAIT_STORE,
        REOPEN, VERIFY_STORE, NEXT, CLEANUP, WAIT_RESTORE, COMPLETE, FAILED, CANCELLED, RECOVERY_REQUIRED }
    public record Identity(String id,String name,String components) {
        public Identity {if(id==null||name==null||components==null)throw new IllegalArgumentException("Complete identity required");}
        public boolean matches(Stack s){return s.count()>0&&equals(s.identity());}
    }
    public record Stack(Identity identity,int count,boolean tool) {
        public static final Stack EMPTY=new Stack(new Identity("","",""),0,false);
        public Stack {Objects.requireNonNull(identity);if(count<0)throw new IllegalArgumentException("Negative count");}
        public boolean empty(){return count==0;}
        public Stack withCount(int n){return n==0?EMPTY:new Stack(identity,n,tool);}
    }
    public record Position(int x,int y,int z) {
        public Position {if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||y< -2048||y>2048)throw new IllegalArgumentException("Bounded position required");}
    }
    public record Request(Identity glass,Identity water,int quantity,String dimension,Position source,Position sourceStand,
                          Position chest,Position chestStand,String chestTitle,int delayTicks,int timeoutTicks) {
        public Request {
            for(var identity:List.of(glass,water))if(identity.name().isBlank()||identity.components().isBlank())throw new IllegalArgumentException("Observed full bottle identities required");
            if(!glass.id().equals("minecraft:glass_bottle")||!water.id().equals("minecraft:potion"))throw new IllegalArgumentException("Normal glass/potion water filling required; XP identity is not assumed");
            if(quantity<1||quantity>64||dimension.isBlank()||chestTitle.isBlank()||delayTicks<1||timeoutTicks<=delayTicks||timeoutTicks>2400||source.equals(chest))throw new IllegalArgumentException("Explicit positions, title, bounded quantity/timing required");
        }
    }
    public record Row(int slot,int inventoryIndex,Stack stack) {}
    public record Observation(Object player,Object world,Object menu,String title,int revision,String dimension,List<Stack> inventory,List<Row> rows,
                              int selected,boolean cursorEmpty,boolean correctionTick,boolean waterSource,boolean chestValid) {
        public Observation {inventory=List.copyOf(inventory);rows=List.copyOf(rows);}
        public boolean valid(){return player!=null&&world!=null&&inventory.size()==36&&selected>=0&&selected<9;}
        public List<Stack> chest(){return rows.stream().filter(r->r.inventoryIndex()<0).map(Row::stack).toList();}
    }
    public enum Approach { MOVING, READY, BLOCKED }
    public interface Port {
        Observation read(Request request);
        default boolean fillAcknowledged(Observation observed,List<Stack> before,List<Stack> after){return false;}
        default boolean storageAcknowledged(Observation observed,List<Stack> after,List<Stack> chestBefore,int issuedOutput){return false;}
        boolean swap(Observation expected,int main,int hotbar);
        boolean select(Observation expected,int hotbar);
        Approach approach(Observation expected,Position target,Position stand);
        boolean fill(Observation expected,Position source);
        boolean open(Observation expected,Position chest);
        boolean store(Observation expected,int inventoryIndex);
        boolean close(Observation expected);
        void release();
    }
    private final Port port;private Request request;private State state=State.IDLE,goal=State.COMPLETE;private String reason="Idle",failureReason="";
    private Object player,world,ownedMenu,oldMenu;private int originalSelected,originalIndex=-1,hotbar=-1,output=-1,completed,initialGlassCount;
    private List<Stack> expected=List.of(),beforeFill=List.of(),filled=List.of(),stored=List.of(),chestBefore=List.of(),restored=List.of();
    private long next,deadline,lastTick;private boolean openIssued,restorationIssued,storagePending,batchMode;private int reserveSlots,converted;private boolean lastStoreFresh,verifyBatchOnly,fillOutcomePending;private List<Stack> finalChest=List.of();private final LinkedHashSet<Integer> pendingWater=new LinkedHashSet<>();
    public BottleFillWorkflow(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public String reason(){return reason;}public Request request(){return request;}public int completed(){return completed;}
    public boolean active(){return switch(state){case IDLE,COMPLETE,FAILED,CANCELLED,RECOVERY_REQUIRED->false;default->true;};}
    public boolean recoveryReservesCurrentContext(){return state==State.RECOVERY_REQUIRED&&context(port.read(request));}
    public void start(Request request,long tick){start(request,tick,-1);}
    /** Parent may bind one full acquired stack; every other stack remains protected by the exact ledger. */
    public void start(Request request,long tick,int acquiredIndex){startMode(request,tick,acquiredIndex,false,0);}
    public void startBatched(Request request,long tick,int acquiredIndex,int reserveSlots){if(reserveSlots<2||reserveSlots>3)throw new IllegalArgumentException("Explicit source reserve2..3 required");startMode(request,tick,acquiredIndex,true,reserveSlots);}
    private void startMode(Request request,long tick,int acquiredIndex,boolean batch,int reserve){
        if(active()||state==State.RECOVERY_REQUIRED)throw new IllegalStateException("Bottle work/recovery already owned");this.request=request;batchMode=batch;reserveSlots=reserve;converted=0;lastStoreFresh=verifyBatchOnly=fillOutcomePending=false;finalChest=List.of();pendingWater.clear();state=State.IDLE;goal=State.COMPLETE;failureReason="";completed=0;
        ownedMenu=null;oldMenu=null;originalIndex=-1;hotbar=-1;output=-1;openIssued=false;restorationIssued=false;storagePending=false;beforeFill=List.of();filled=List.of();stored=List.of();restored=List.of();lastTick=tick;
        var o=port.read(request);if(!o.valid()||o.menu()!=null||!o.cursorEmpty()||!o.dimension().equals(request.dimension())||!o.waterSource()||!o.chestValid()){finish(State.FAILED,"Living loaded player, source water, exact chest, closed menus and empty cursor required");return;}
        player=o.player();world=o.world();originalSelected=o.selected();expected=o.inventory();var matches=new ArrayList<Integer>();
        for(int i=0;i<36;i++){var s=expected.get(i);if(request.water().matches(s)){finish(State.FAILED,"Start with no matching filled bottles");return;}if(request.glass().matches(s))matches.add(i);}
        if(acquiredIndex<0&&matches.size()!=1||acquiredIndex>=0&&(!matches.contains(acquiredIndex)||expected.get(acquiredIndex).count()<request.quantity()||expected.get(acquiredIndex).count()>64)){finish(State.FAILED,"One exact prepared or parent-bound full acquired glass stack required");return;}originalIndex=acquiredIndex<0?matches.getFirst():acquiredIndex;var bottles=expected.get(originalIndex);
        if(bottles.tool()||bottles.count()<request.quantity()||bottles.count()>64){finish(State.FAILED,"Prepared glass quantity/tool contract refused");return;}
        initialGlassCount=bottles.count();
        hotbar=originalIndex<9?originalIndex:-1;if(hotbar<0)for(int i=0;i<9;i++)if(expected.get(i).empty()){hotbar=i;break;}
        if(hotbar<0||expected.stream().filter(Stack::empty).count()<Math.max(originalIndex<9?1:2,batchMode?reserveSlots:0)){finish(State.FAILED,"Free hotbar and output capacity required; tools are preserved");return;}
        deadline=tick+request.timeoutTicks();next=tick;state=originalIndex<9?State.WATER:State.MOVE;port.release();
    }
    public void cancel(){if(active()&&goal!=State.CANCELLED){goal=State.CANCELLED;failureReason="Cancelled";reason="Cancelled; draining owned bottle placement";deadline=lastTick+request.timeoutTicks();
        if(state!=State.WAIT_MOVE&&state!=State.WAIT_FILL&&state!=State.WAIT_RESTORE&&!(storagePending&&(state==State.WAIT_STORE||state==State.REOPEN||state==State.VERIFY_STORE)))state=State.CLEANUP;port.release();}}
    public boolean acknowledgeManualRecovery(){if(state!=State.RECOVERY_REQUIRED)return false;var o=port.read(request);
        if(!o.valid()||o.menu()!=null||!o.cursorEmpty()||o.selected()!=originalSelected||originalIndex<0||hotbar<0)return false;
        var atHome=o.inventory().get(originalIndex);if(originalIndex>=9&&!o.inventory().get(hotbar).empty()||atHome.tool()
                ||!atHome.empty()&&!(request.glass().matches(atHome)&&atHome.count()<=initialGlassCount||request.water().matches(atHome)&&atHome.count()==1))return false;
        // Explicit operator acknowledgment owns any other manual inventory changes. This verifies
        // the known temporary pair only; it never counts an uncertain fill/store as completed.
        finish(goal==State.CANCELLED?State.CANCELLED:State.FAILED,"Original bottle placement manually verified; no conversion/storage retry");return true;}
    public void tick(long tick){
        if(!active())return;lastTick=tick;var o=port.read(request);
        if(!context(o)||!o.dimension().equals(request.dimension())){finish(State.RECOVERY_REQUIRED,"Player/world/dimension changed; no stale cleanup");return;}
        if(!o.cursorEmpty()){finish(State.RECOVERY_REQUIRED,"Foreign cursor preserved; no bottle/menu operation");return;}
        if(tick>deadline){if(state==State.CLEANUP||state==State.WAIT_RESTORE||fillOutcomePending){finish(State.RECOVERY_REQUIRED,"Exact bottle cleanup acknowledgment timed out");return;}if(batchMode&&state==State.WAIT_FILL&&partialConversion(beforeFill,o.inventory())){if(goal!=State.CANCELLED){goal=State.FAILED;failureReason="Native fill receipt expired; no retry or conversion credit";}fillOutcomePending=true;expected=o.inventory();filled=expected;state=State.CHEST;deadline=tick+request.timeoutTicks();next=tick;port.release();}else fail("Bottle action timed out; no uncertain retry");}
        if(tick<next)return;
        switch(state){
            case MOVE->{if(!o.inventory().equals(expected)||o.menu()!=null||!port.swap(o,originalIndex,hotbar)){finish(State.FAILED,"Prepared bottle transfer refused");return;}expected=swapped(expected,originalIndex,hotbar);waitFor(State.WAIT_MOVE,tick,"Waiting for bottle hotbar placement");}
            case WAIT_MOVE->{if(o.inventory().equals(expected)&&o.correctionTick())step(goal==State.COMPLETE?State.WATER:State.CLEANUP,tick);}
            case WATER->{if(batchMode&&(converted>=request.quantity()||expected.stream().filter(Stack::empty).count()<reserveSlots)){step(State.CHEST,tick);return;}if(!guardClosed(o)||!o.waterSource()){fail("Source water/inventory changed");return;}var a=port.approach(o,request.source(),request.sourceStand());if(a==Approach.BLOCKED)fail("No safe normal water approach");else if(a==Approach.READY)step(State.FILL,tick);}
            case FILL->{if(!guardClosed(o)||!o.waterSource()||!request.glass().matches(o.inventory().get(hotbar))||o.inventory().get(hotbar).tool()) {fail("Exact glass/source unavailable");return;}
                if(!port.select(o,hotbar)||!port.fill(port.read(request),request.source())){fail("Native source-water use refused");return;}beforeFill=expected;filled=List.of();waitFor(State.WAIT_FILL,tick,"Awaiting one native bottle conversion");}
            case WAIT_FILL->{var result=conversion(beforeFill,o.inventory());if(result>=0){if(batchMode&&!port.fillAcknowledged(o,beforeFill,o.inventory()))return;output=result;pendingWater.add(result);converted++;filled=o.inventory();expected=filled;step(goal!=State.COMPLETE?State.CLEANUP:batchMode&&converted<request.quantity()&&expected.stream().filter(Stack::empty).count()>=reserveSlots?State.WATER:State.CHEST,tick);}
                else if(!o.inventory().equals(beforeFill)&&!(batchMode&&partialConversion(beforeFill,o.inventory()))){finish(State.RECOVERY_REQUIRED,"Unexpected fill identity/quantity/other inventory delta");}}
            case CHEST->openChest(o,tick,State.WAIT_CHEST);
            case WAIT_CHEST->{if(o.menu()!=null&&ownedMenu!=null&&o.menu()!=ownedMenu){fail("Received bottle chest was replaced");return;}if(receivedChest(o)){
                if(fillOutcomePending){if(!o.inventory().equals(beforeFill)&&conversion(beforeFill,o.inventory())<0){finish(State.RECOVERY_REQUIRED,"Fresh cleanup ledger rejects issued fill projection");return;}ownedMenu=o.menu();expected=o.inventory();fillOutcomePending=false;step(State.CLEANUP,tick);return;}
                if(!o.inventory().equals(filled)){fail("Fresh server chest/player contents reject bottle conversion");return;}ownedMenu=o.menu();expected=filled;chestBefore=o.chest();step(State.STORE,tick);}}
            case STORE->{if(!guardOwned(o)||!o.inventory().equals(expected)||output<0||!request.water().matches(o.inventory().get(output))||o.inventory().get(output).count()!=1||o.inventory().get(output).tool()
                        ||o.chest().stream().noneMatch(Stack::empty)||!o.chest().equals(chestBefore)){fail("Exact water bottle/storage capacity changed");return;}
                if(!port.store(o,output)){fail("Native exact water storage refused");return;}var after=new ArrayList<>(expected);after.set(output,Stack.EMPTY);stored=List.copyOf(after);storagePending=true;waitFor(State.WAIT_STORE,tick,"Awaiting fresh server storage verification");}
            case WAIT_STORE->{if(batchMode&&guardOwned(o)&&o.inventory().equals(stored)&&storage(chestBefore,o.chest())&&port.storageAcknowledged(o,stored,chestBefore,output)){lastStoreFresh=false;acceptStore(o,tick);return;}if(!guardOwned(o)){fail("Storage menu replaced");return;}oldMenu=o.menu();if(!port.close(o)){fail("Owned storage close refused");return;}ownedMenu=null;openIssued=false;step(State.REOPEN,tick);}
            case REOPEN->openChest(o,tick,State.VERIFY_STORE);
            case VERIFY_STORE->{if(o.menu()!=null&&ownedMenu!=null&&o.menu()!=ownedMenu){fail("Verification chest was replaced");return;}if(receivedChest(o)&&o.menu()!=oldMenu){ownedMenu=o.menu();if(verifyBatchOnly){if(!o.inventory().equals(expected)||!o.chest().equals(finalChest)){fail("Fresh final batch inventory/chest ledger rejected");return;}verifyBatchOnly=false;lastStoreFresh=true;step(State.NEXT,tick);return;}if(!o.inventory().equals(stored)||!storage(chestBefore,o.chest())){fail("Fresh server chest/player contents reject storage; no retry");return;}
                lastStoreFresh=true;acceptStore(o,tick);}}
            case NEXT->{if(batchMode&&pendingWater.isEmpty()&&!lastStoreFresh){if(!guardOwned(o)||!o.inventory().equals(expected)){fail("Final batch verification owner changed");return;}finalChest=o.chest();oldMenu=o.menu();if(!port.close(o)){fail("Final batch verification close refused");return;}ownedMenu=null;openIssued=false;verifyBatchOnly=true;step(State.REOPEN,tick);return;}if(batchMode&&goal==State.COMPLETE&&!pendingWater.isEmpty()){if(!guardOwned(o)||!o.inventory().equals(expected)){fail("Batched storage owner/ledger changed");return;}output=pendingWater.getFirst();chestBefore=o.chest();filled=expected;deadline=tick+request.timeoutTicks();step(State.STORE,tick);return;}if(!guardOwned(o)||!o.inventory().equals(expected)||!port.close(o)){fail("Owned stored-bottle menu close refused");return;}ownedMenu=null;openIssued=false;oldMenu=null;beforeFill=List.of();filled=List.of();stored=List.of();
                deadline=tick+request.timeoutTicks();step(goal==State.COMPLETE&&completed<request.quantity()?State.WATER:State.CLEANUP,tick);}
            case CLEANUP->cleanup(o,tick);
            case WAIT_RESTORE->{if(o.menu()!=null){if(receivedChest(o)&&o.menu()!=oldMenu&&o.inventory().equals(restored)){ownedMenu=o.menu();expected=restored;step(State.CLEANUP,tick);}else if(o.menu()!=ownedMenu){finish(State.RECOVERY_REQUIRED,"Cleanup replacement menu preserved");}}
                else if(!openIssued&&o.inventory().equals(restored)&&o.correctionTick()){var a=port.approach(o,request.chest(),request.chestStand());if(a==Approach.BLOCKED){finish(State.RECOVERY_REQUIRED,"Restoration verification approach blocked");return;}if(a==Approach.READY){if(!selectSafe(o)||!port.open(port.read(request),request.chest())){finish(State.RECOVERY_REQUIRED,"Restoration verification open refused");return;}openIssued=true;}}
            }
            default->{}
        }
    }
    private boolean context(Observation o){return o.valid()&&o.player()==player&&o.world()==world;}
    private boolean guardClosed(Observation o){return o.menu()==null&&o.inventory().equals(expected);}
    private boolean guardOwned(Observation o){return o.menu()==ownedMenu&&o.title().equals(request.chestTitle())&&o.chestValid();}
    private boolean receivedChest(Observation o){return openIssued&&o.menu()!=null&&o.title().equals(request.chestTitle())&&o.revision()>0&&o.chestValid();}
    private void openChest(Observation o,long tick,State after){
        if(o.menu()!=null){if(!openIssued||!o.title().equals(request.chestTitle())||!o.chestValid()||ownedMenu!=null&&o.menu()!=ownedMenu){fail("Foreign chest/menu response preserved");return;}ownedMenu=o.menu();if(o.revision()>0)step(after,tick);return;}
        if(!o.chestValid()){fail("Configured storage chest unloaded/replaced");return;}if(openIssued)return;
        if(fillOutcomePending){if(!partialConversion(beforeFill,o.inventory())){finish(State.RECOVERY_REQUIRED,"Foreign ledger during read-only fill reconciliation");return;}expected=o.inventory();filled=expected;}else if(!o.inventory().equals(expected)&&!o.inventory().equals(stored)){fail("Inventory changed during chest approach");return;}
        var a=port.approach(o,request.chest(),request.chestStand());if(a==Approach.BLOCKED){fail("Safe storage approach blocked");return;}
        if(a==Approach.READY){if(!selectSafe(o)||!port.open(port.read(request),request.chest())){fail("Native exact chest interaction refused");return;}openIssued=true;reason="Waiting for server-opened exact chest";}
    }
    private boolean selectSafe(Observation o){for(int i=0;i<9;i++){var s=o.inventory().get(i);if(s.empty()||!s.tool()&&(request.glass().matches(s)||request.water().matches(s)))return port.select(o,i);}return false;}
    private int conversion(List<Stack> before,List<Stack> after){
        if(before.size()!=36||after.size()!=36)return -1;var input=before.get(hotbar);if(!request.glass().matches(input))return -1;
        for(int i=0;i<36;i++)if(request.water().matches(after.get(i))&&after.get(i).count()==1&&!after.get(i).tool()){
            var result=new ArrayList<>(before);if(input.count()==1){if(i!=hotbar)continue;result.set(hotbar,after.get(i));}
            else{if(i==hotbar||!before.get(i).empty())continue;result.set(hotbar,input.withCount(input.count()-1));result.set(i,after.get(i));}
            if(result.equals(after))return i;
        }return -1;
    }
    private void acceptStore(Observation o,long tick){expected=stored;pendingWater.remove(output);completed++;storagePending=false;step(State.NEXT,tick);}
    private boolean partialConversion(List<Stack> before,List<Stack> after){if(before.size()!=36||after.size()!=36)return false;int water=-1;for(int i=0;i<36;i++){if(before.get(i).equals(after.get(i)))continue;if(i==hotbar){var s=before.get(i);if(after.get(i).equals(s.withCount(s.count()-1))||s.count()==1&&request.water().matches(after.get(i))&&after.get(i).count()==1)continue;return false;}if(water>=0||!before.get(i).empty()||!request.water().matches(after.get(i))||after.get(i).count()!=1||after.get(i).tool())return false;water=i;}return true;}
    private boolean storage(List<Stack> before,List<Stack> after){if(before.size()!=after.size())return false;int changed=0;
        for(int i=0;i<before.size();i++)if(!before.get(i).equals(after.get(i))){if(!before.get(i).empty()||!request.water().matches(after.get(i))||after.get(i).count()!=1||after.get(i).tool())return false;changed++;}return changed==1;}
    private void cleanup(Observation o,long tick){
        // During an issued store, only the exact pre/post ledger is acceptable. Cancellation never resends it.
        if(!o.inventory().equals(expected)){if(!stored.isEmpty()&&o.inventory().equals(stored))expected=stored;
            else if(!beforeFill.isEmpty()&&o.inventory().equals(beforeFill))expected=beforeFill;
            else {finish(State.RECOVERY_REQUIRED,"Unknown bottle ledger; no cleanup overwrite");return;}}
        if(o.menu()!=null){if(ownedMenu==null&&receivedChest(o))ownedMenu=o.menu();if(!guardOwned(o)){finish(State.RECOVERY_REQUIRED,"Foreign/replacement menu preserved");return;}
            oldMenu=o.menu();if(!port.close(o)){finish(State.RECOVERY_REQUIRED,"Owned empty-cursor menu close refused");return;}ownedMenu=null;openIssued=false;next=tick+request.delayTicks();return;}
        if(originalIndex>=9&&!restorationIssued&&!expected.get(hotbar).empty()){
            if(!expected.get(originalIndex).empty()){finish(State.RECOVERY_REQUIRED,"Original bottle slot occupied; no overwrite");return;}
            if(!port.swap(o,originalIndex,hotbar)){finish(State.RECOVERY_REQUIRED,"Exact remaining bottle restitution refused");return;}
            restored=swapped(expected,originalIndex,hotbar);restorationIssued=true;openIssued=false;waitFor(State.WAIT_RESTORE,tick,"Waiting for fresh server original bottle placement");return;
        }
        if(!restored.isEmpty()&&!o.inventory().equals(restored)){finish(State.RECOVERY_REQUIRED,"Restored bottle pair changed");return;}
        if(!port.select(o,originalSelected)){finish(State.RECOVERY_REQUIRED,"Original selected tool slot restoration refused");return;}
        finish(goal,goal==State.COMPLETE?"Fresh server contents confirm "+completed+" water bottles stored; remaining bottles and selection restored":failureReason+"; remaining bottles and selection restored");
    }
    private static List<Stack> swapped(List<Stack> in,int a,int b){var result=new ArrayList<>(in);Collections.swap(result,a,b);return List.copyOf(result);}
    private void fail(String reason){goal=goal==State.CANCELLED?State.CANCELLED:State.FAILED;if(goal!=State.CANCELLED)failureReason=reason;this.reason=reason;state=State.CLEANUP;deadline=lastTick+request.timeoutTicks();next=lastTick;port.release();}
    private void step(State state,long tick){this.state=state;next=tick+request.delayTicks();}
    private void waitFor(State state,long tick,String reason){step(state,tick);this.reason=reason;}
    private void finish(State state,String reason){this.state=state;this.reason=reason;port.release();}
}
