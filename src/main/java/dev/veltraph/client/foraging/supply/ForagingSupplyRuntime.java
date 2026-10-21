package dev.veltraph.client.foraging.supply;

import java.util.*;

/** Selected inventory supply -> chest, otherwise one guarded acquisition -> chest. */
public final class ForagingSupplyRuntime {
    public enum Stage { IDLE, BUY_RESOURCE, ACQUIRE_ACK, OPEN_CHEST, STORE, STORE_ACK, WITHDRAW, WITHDRAW_ACK, COMPLETE, UNKNOWN, CANCELLED }
    public record Stack(String id,String name,String components,int count,boolean protectedItem) {
        public static final Stack EMPTY=new Stack("minecraft:air","","",0,false);
        public Stack { if(count<0)throw new IllegalArgumentException("Negative count"); }
        public boolean matches(ForagingSupplyBindings.Resource r){return count>0&&!protectedItem&&id.equals(r.id())&&name.equals(r.name())&&components.equals(r.components());}
    }
    public record Scope(Object player,Object world,Object connection) {
        public boolean same(Scope other){return other!=null&&player==other.player&&world==other.world&&connection==other.connection;}
    }
    public record Observation(Scope scope,long now,List<Stack> inventory,boolean cursorEmpty,boolean alive,
                              long purse,Object menu,String title,List<Stack> chest,boolean chestValid,boolean nativeAck) {
        public Observation { inventory=List.copyOf(inventory);chest=List.copyOf(chest); }
    }
    public interface Port {
        boolean acquire(Observation o,ForagingSupplyBindings.Resource resource);
        boolean open(Observation o);
        default boolean acquisitionProgress(Observation o){return true;}
        default boolean ownedPurchasePrediction(Observation o){return false;}
        default boolean storageProgress(Observation o){return true;}
        default boolean storageMenuOwned(Observation o){return true;}
        default boolean storageMenuAwaiting(Observation o){return false;}
        boolean store(Observation o,int inventorySlot);
        boolean verifyStorage(Observation o);
        default boolean withdraw(Observation o,int chestSlot){return false;}
        void release();
    }
    private boolean replenishing;private int minimum;private Stack withdrawn;
    public List<Stack> acknowledgedInventory(){return List.copyOf(ledger);}
    private Stage stage=Stage.IDLE;private String reason="Idle";private Scope scope;private long deadline,last;
    private ForagingSupplyBindings.Resource resource;private List<Stack> ledger=List.of(),beforeChest=List.of();
    private String chestTitle;private long balance;private int slot=-1,quantity;private Object chestMenu;private long acquiredCost;
    public Optional<String> readiness(Observation o,ForagingSupplyBindings.Resource r) {
        if(o.scope()==null||o.scope().player()==null||o.scope().world()==null||o.scope().connection()==null||!o.alive()||!o.cursorEmpty()||o.menu()!=null||!o.chestValid()||o.inventory().size()!=36||o.purse()<0)return Optional.of("Living original context, empty cursor, closed menus, loaded chest and readable purse required");
        long selected=o.inventory().stream().filter(s->s.matches(r)).mapToLong(Stack::count).sum();
        if(selected==0&&o.inventory().stream().noneMatch(s->s.count()==0))return Optional.of("Acquisition needs an empty inventory slot");
        return Optional.empty();
    }
    public boolean start(Observation o,ForagingSupplyBindings b,ForagingSupplyConfig.Type type) {
        replenishing=false;resource=b.resources().get(type);var invalid=readiness(o,resource);if(invalid.isPresent()){unknown(invalid.get());return false;}
        chestTitle=b.chestTitle();scope=o.scope();last=o.now();deadline=Math.addExact(last,b.timeoutMs());ledger=o.inventory();balance=o.purse();acquiredCost=0;
        stage=findSlot(ledger)>=0?Stage.OPEN_CHEST:Stage.BUY_RESOURCE;reason="Selected "+type+" chest supply";return true;
    }
    /** Uses the same context, ledger, quote and receipt FSM while retaining delivered resources. */
    public boolean startReplenishment(Observation o,ForagingSupplyBindings b,ForagingSupplyConfig.Type type,int required){
        if(required<1||required>64||b.resources().get(type).quantity()<required)return false;
        if(!start(o,b,type))return false;replenishing=true;minimum=required;
        stage=count(ledger)>=minimum?Stage.COMPLETE:Stage.OPEN_CHEST;reason="Parent "+type+" replenishment";return true;
    }
    public void tick(Observation o,Port p) {
        if(terminal()||stage==Stage.IDLE)return;
        if(!scope.same(o.scope())||!o.alive()||!o.chestValid()||o.now()<last||o.now()>=deadline){unknown("Original context/cursor/chest/deadline invalid");p.release();return;}last=o.now();
        if(!o.cursorEmpty()){
            if(stage==Stage.ACQUIRE_ACK&&o.inventory().equals(ledger)&&o.purse()==balance&&p.ownedPurchasePrediction(o))return;
            unknown("Unowned cursor during acquisition/storage");p.release();return;
        }
        switch(stage) {
            case BUY_RESOURCE -> {
                if(!o.inventory().equals(ledger)||o.purse()!=balance||o.menu()!=null){unknown("Inventory/purse/menu changed before acquisition");break;}
                if(p.acquire(o,resource))stage=Stage.ACQUIRE_ACK;else unknown("Acquisition dispatch refused");
            }
            case ACQUIRE_ACK -> {
                if(!p.acquisitionProgress(o)){unknown("Acquisition menu/receipt ownership changed");break;}
                if(o.menu()!=null&&!o.title().equals(resource.offerTitle())){unknown("Foreign acquisition menu");break;}
                int delta=count(o.inventory())-count(ledger);
                if(delta==0&&o.purse()==balance)break;
                if(delta!=resource.quantity()||o.purse()>=balance||balance-o.purse()>resource.maximumCost()||!onlyResourceDelta(ledger,o.inventory(),delta)){unknown("Partial or unrelated acquisition outcome");break;}
                if(!o.nativeAck()||o.menu()!=null)break;
                acquiredCost=balance-o.purse();ledger=o.inventory();balance=o.purse();stage=replenishing?Stage.COMPLETE:Stage.OPEN_CHEST;if(replenishing)reason="Native purchased resources retained for original parent";
            }
            case OPEN_CHEST -> {
                if(!o.inventory().equals(ledger)||o.purse()!=balance){unknown("Supply ledger changed before chest");break;}
                if(o.menu()==null){if(!p.open(o))unknown("Chest approach/open refused");break;}
                if(!o.title().equals(chestTitle)||o.chest().isEmpty()){unknown("Foreign/non-storage menu");break;}
                if(!p.storageMenuOwned(o)){if(!p.storageMenuAwaiting(o))unknown("Unowned native storage menu");break;}
                chestMenu=o.menu();stage=replenishing?Stage.WITHDRAW:Stage.STORE;
            }
            case WITHDRAW -> {
                if(o.menu()!=chestMenu||!o.title().equals(chestTitle)||!o.inventory().equals(ledger)||o.purse()!=balance){unknown("Chest/ledger replaced before withdrawal");break;}
                if(count(ledger)>=minimum){stage=Stage.COMPLETE;reason="Native chest resources retained for original parent";break;}
                slot=findSlot(o.chest());
                if(slot<0){p.release();stage=Stage.BUY_RESOURCE;break;}
                withdrawn=o.chest().get(slot);beforeChest=o.chest();
                // Whole-stack QUICK_MOVE is admitted only with exact empty inventory capacity.
                if(ledger.stream().noneMatch(s->s.count()==0)){unknown("Withdrawal requires empty inventory capacity");break;}
                if(p.withdraw(o,slot))stage=Stage.WITHDRAW_ACK;else unknown("Exact chest withdrawal dispatch refused");
            }
            case WITHDRAW_ACK -> {
                if(o.purse()!=balance){unknown("Purse changed during withdrawal");break;}
                int delta=count(o.inventory())-count(ledger);
                if(!o.inventory().equals(ledger)&&(!onlyResourceDelta(ledger,o.inventory(),withdrawn.count())||delta!=withdrawn.count())){unknown("Partial or foreign withdrawal inventory outcome");break;}
                if(o.menu()==chestMenu){if(!p.verifyStorage(o))unknown("Withdrawal fresh verification refused");break;}
                if(o.menu()==null){if(!p.storageProgress(o))unknown("Withdrawal reopening refused");break;}
                if(!o.nativeAck())break;
                var expected=new ArrayList<>(beforeChest);expected.set(slot,Stack.EMPTY);
                if(!o.title().equals(chestTitle)||!o.chest().equals(expected)||delta!=withdrawn.count()||!onlyResourceDelta(ledger,o.inventory(),delta)){unknown("Fresh native withdrawal contents disagree with issued stack");break;}
                ledger=o.inventory();chestMenu=o.menu();stage=Stage.WITHDRAW;
            }
            case STORE -> {
                if(o.menu()!=chestMenu||!o.title().equals(chestTitle)||!o.inventory().equals(ledger)||o.purse()!=balance){unknown("Chest/ledger replaced before store");break;}
                slot=findSlot(ledger);if(slot<0){stage=Stage.COMPLETE;reason="All selected supply stored";p.release();return;}
                quantity=ledger.get(slot).count();beforeChest=o.chest();
                if(beforeChest.stream().noneMatch(s->s.count()==0)){unknown("No empty chest capacity");break;}
                if(p.store(o,slot))stage=Stage.STORE_ACK;else unknown("Exact selected store dispatch refused");
            }
            case STORE_ACK -> {
                if(o.purse()!=balance){unknown("Purse changed during storage");break;}
                var after=new ArrayList<>(ledger);after.set(slot,Stack.EMPTY);
                if(o.menu()==chestMenu){if(!o.inventory().equals(ledger)&&!o.inventory().equals(after)){unknown("Storage player ledger partial/foreign change");break;}if(!p.verifyStorage(o))unknown("Storage verification refused");break;}
                if(o.menu()==null){if(!p.storageProgress(o))unknown("Storage reopening refused");break;}
                if(!o.nativeAck())break;
                if(!o.title().equals(chestTitle)||!o.inventory().equals(after)||!storedExact(beforeChest,o.chest(),ledger.get(slot))){unknown("Fresh native storage contents disagree with issued slot");break;}
                ledger=List.copyOf(after);chestMenu=o.menu();stage=Stage.STORE;
            }
            default -> unknown("Invalid stage");
        }
        if(terminal())p.release();
    }
    private boolean onlyResourceDelta(List<Stack> before,List<Stack> after,int delta){if(after.size()!=36)return false;for(int i=0;i<36;i++){var a=before.get(i);var b=after.get(i);if(a.equals(b))continue;if(a.count()>0&&(!a.matches(resource)||b.count()<a.count())||!b.matches(resource))return false;}return count(after)-count(before)==delta;}
    private boolean storedExact(List<Stack> before,List<Stack> after,Stack issued){
        if(before.size()!=after.size())return false;int added=0;
        for(int i=0;i<before.size();i++){var a=before.get(i);var b=after.get(i);if(a.equals(b))continue;
            if(!b.matches(resource)||b.count()>64)return false;
            if(a.count()==0){added+=b.count();continue;}
            if(!a.matches(resource)||b.count()<a.count())return false;
            added+=b.count()-a.count();
        }
        return added==issued.count();
    }
    private int count(List<Stack> items){return items.stream().filter(s->s.matches(resource)).mapToInt(Stack::count).sum();}
    private int findSlot(List<Stack> items){for(int i=0;i<items.size();i++)if(items.get(i).matches(resource))return i;return -1;}
    private void unknown(String why){stage=Stage.UNKNOWN;reason=why;}
    public void unattributedAcquisition(Port p){unknown("Native delivery accepted but original container financial witness unavailable; no later store/retry");p.release();}
    public void cancel(Port p){stage=Stage.CANCELLED;reason="Cancelled without retry";p.release();}
    public boolean terminal(){return stage==Stage.COMPLETE||stage==Stage.UNKNOWN||stage==Stage.CANCELLED;}
    public Stage stage(){return stage;}public String reason(){return reason;}public long acquiredCost(){return acquiredCost;}
}
