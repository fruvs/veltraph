package dev.veltraph.client.fishing.bait;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;

/** Recovered BaitBuyer routes, with bounded requests and server inventory/receipt acknowledgments. */
public final class BaitRuntime {
    public enum State { IDLE, REEL, WAIT_REEL, OPEN_INITIAL_ROOT, INITIAL_ROOT, INITIAL_BAGS, INITIAL_BAG,
        OPEN_BAZAAR, BAZAAR, FISHING, BAITS, PRODUCT, BUY, WAIT_PURCHASE, OPEN_FINAL_ROOT,
        FINAL_ROOT, FINAL_BAGS, ENABLE, WAIT_ENABLE, MOVE, WAIT_MOVE, WAIT_MENU, CLOSE, WAIT_CLOSE,
        RESTORE, COMPLETE, FAILED, CANCELLED }
    public enum Kind { REEL, USE_MENU, COMMAND, CLICK, PURCHASE, MOVE, CLOSE, RESTORE }
    public record Stock(int index,String itemId,String name,int count,String components) {}
    public record Snapshot(Object player,Object world,Object menuToken,MenuSnapshot menu,List<Stock> inventory,
                           Map<Integer,Stock> storage,boolean cursorEmpty,boolean ownPredictedCursor,boolean hook) {
        public Snapshot { inventory=List.copyOf(inventory);storage=Map.copyOf(storage); }
    }
    public record Settings(BaitLocation location,int maxStacks,long maxUnitPrice,long maxCost) {
        public Settings { if(maxStacks<1||maxStacks>36||maxUnitPrice<1||maxCost<1)throw new IllegalArgumentException("Positive bounded bait quantity and price caps are required"); }
    }
    public record Action(Kind kind,Snapshot expected,int slot,int quantity) {}
    private State state=State.IDLE,next;
    private BaitProfile profile;private Settings settings;
    private Object player,world,ownedMenu,previousMenu;
    private String ownedTitle="",wantedTitle="",reason="Idle";
    private long started,requestAt,lastRequest=-300;
    private int previousRevision,remaining,purchased,transferred,transferQuantity;
    private Snapshot before;
    private BaitProfile.Quote quote;
    private boolean receipt,fill;
    private BigDecimal confirmedCost=BigDecimal.ZERO;
    public State state(){return state;}
    public String reason(){return reason;}
    public boolean active(){return state!=State.IDLE&&!terminal();}
    public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public int purchased(){return purchased;}
    public int transferred(){return transferred;}
    public BigDecimal cost(){return confirmedCost;}
    public boolean allowsTitle(String title){String clean=MenuSnapshot.clean(title);return active()&&(clean.equals(ownedTitle)||state==State.WAIT_MENU&&clean.equals(wantedTitle));}
    public Object ownedMenu(){return ownedMenu;}
    public void start(Snapshot s,BaitProfile p,Settings options,long now){
        reset();profile=p;settings=options;player=s.player();world=s.world();started=now;lastRequest=now-300;
        if(player==null||world==null||s.menu()!=null||!s.cursorEmpty()||emptyInventory(s)<4){fail("Restocking needs a closed menu, empty cursor and at least four empty inventory slots");return;}
        remaining=options.location()==BaitLocation.INVENTORY?Math.min(2,options.maxStacks()):0;
        state=State.REEL;reason="Reel before menu ownership";
    }
    public Optional<Action> tick(Snapshot s,long now){
        if(!active())return Optional.empty();
        if(s.player()!=player||s.world()!=world){cancel("Player/world changed during bait restocking");return Optional.empty();}
        if(now<started||now-started>60000){fail("Bait workflow timed out");return Optional.empty();}
        if(!s.cursorEmpty()&&!s.ownPredictedCursor()){cancel("Cursor ownership changed during bait restocking");return Optional.empty();}
        if(state==State.WAIT_REEL){if(!s.hook())state=settings.location()==BaitLocation.BAG?State.OPEN_INITIAL_ROOT:State.OPEN_BAZAAR;else timeout(now,"Initial bait reel was not acknowledged");return Optional.empty();}
        if(state==State.WAIT_MENU){
            if(s.menu()!=null&&s.menu().title().equals(wantedTitle)&&(s.menuToken()!=previousMenu||s.menu().revision()>previousRevision)&&s.cursorEmpty()){
                ownedMenu=s.menuToken();ownedTitle=s.menu().title();state=next;return Optional.empty();
            }
            if(s.menu()!=null&&(s.menuToken()!=ownedMenu||!s.menu().title().equals(ownedTitle))){cancel("Unexpected menu replaced the bait request");return Optional.empty();}
            timeout(now,"Bait menu request was not acknowledged");return Optional.empty();
        }
        if(state==State.WAIT_CLOSE){
            if(s.menu()==null&&s.cursorEmpty()){ownedMenu=null;ownedTitle="";state=next;return Optional.empty();}
            if(s.menuToken()!=ownedMenu){cancel("Closing bait menu was replaced");return Optional.empty();}
            timeout(now,"Bait menu did not close");return Optional.empty();
        }
        if(state==State.WAIT_PURCHASE){
            if(s.menu()!=null&&(s.menuToken()!=ownedMenu||!s.menu().title().equals(ownedTitle))){cancel("Purchase menu was replaced");return Optional.empty();}
            boolean fresh=s.menu()==null||s.menu().revision()>before.menu().revision();
            if(receipt&&fresh&&s.cursorEmpty()&&inventoryBait(s)==inventoryBait(before)+quote.quantity()&&otherInventory(s).equals(otherInventory(before))){
                purchased=Math.addExact(purchased,quote.quantity());remaining-=fill?remaining:1;confirmedCost=confirmedCost.add(quote.cost());
                if(remaining==0){state=s.menu()==null?(settings.location()==BaitLocation.BAG?State.OPEN_FINAL_ROOT:State.RESTORE):State.CLOSE;next=settings.location()==BaitLocation.BAG?State.OPEN_FINAL_ROOT:State.RESTORE;}
                else state=State.BUY;
                return Optional.empty();
            }
            timeout(now,"Quoted bait purchase lacks exact inventory and receipt acknowledgment");return Optional.empty();
        }
        if(state==State.WAIT_ENABLE){
            if(!sameOwned(s)){cancel("Bait enable menu was replaced");return Optional.empty();}
            if(s.cursorEmpty()&&s.menu().revision()>before.menu().revision()){
                try{
                    var toggle=unique(s,profile.buttons().get("enable"),false);
                    if(toggle.lore().contains(profile.enabledLore())&&!toggle.lore().contains(profile.disabledLore()))state=State.MOVE;
                }catch(IllegalArgumentException e){fail(e.getMessage());}
            }
            timeout(now,"Bait enable request was not acknowledged");return Optional.empty();
        }
        if(state==State.WAIT_MOVE){
            if(!sameOwned(s)){cancel("Bait bag was replaced during transfer");return Optional.empty();}
            if(s.cursorEmpty()&&s.menu().revision()>before.menu().revision()&&inventoryBait(s)==inventoryBait(before)-transferQuantity
                    &&bagBait(s)==bagBait(before)+transferQuantity&&otherInventory(s).equals(otherInventory(before))&&otherStorage(s).equals(otherStorage(before))){transferred=Math.addExact(transferred,transferQuantity);state=State.MOVE;return Optional.empty();}
            timeout(now,"Bait transfer lacks exact inventory and bag acknowledgment");return Optional.empty();
        }
        if(EnumSet.of(State.INITIAL_ROOT,State.INITIAL_BAGS,State.INITIAL_BAG,State.BAZAAR,State.FISHING,State.BAITS,State.PRODUCT,State.BUY,State.FINAL_ROOT,State.FINAL_BAGS,State.ENABLE,State.MOVE,State.CLOSE).contains(state)&&!sameOwned(s)){
            cancel("Bait menu identity changed before request");return Optional.empty();
        }
        if(now-lastRequest<300||!s.cursorEmpty())return Optional.empty();
        try{
            switch(state){
                case REEL -> {
                    if(s.hook()){state=State.WAIT_REEL;return issue(Kind.REEL,s,-1,0,now);}
                    state=settings.location()==BaitLocation.BAG?State.OPEN_INITIAL_ROOT:State.OPEN_BAZAAR;
                }
                case OPEN_INITIAL_ROOT -> {waitMenu(s,"root",State.INITIAL_ROOT,now);return issue(Kind.USE_MENU,s,-1,0,now);}
                case INITIAL_ROOT -> {return navigation(s,"bags","bags",State.INITIAL_BAGS,now);}
                case INITIAL_BAGS -> {return navigation(s,"bag","bag",State.INITIAL_BAG,now);}
                case INITIAL_BAG -> {
                    requireOwned(s);int free=emptyBag(s);long stacks=s.storage().values().stream().filter(this::bait).count();
                    int capUnits=settings.maxStacks()*profile.stackSize()-transferred;
                    int held=inventoryBait(s);
                    if(held>0&&capUnits>0){state=State.ENABLE;break;}
                    int capLeft=capUnits/profile.stackSize();
                    if(free<=5||stacks>5||capLeft<=0){state=State.CLOSE;next=State.RESTORE;break;}
                    remaining=Math.min(capLeft,Math.min(free,emptyInventory(s)));
                    if(remaining<1||inventoryBait(s)+(long)remaining*profile.stackSize()>settings.maxStacks()*(long)profile.stackSize())throw new IllegalArgumentException("Bait quantity exceeds transfer cap");
                    fill=remaining==emptyInventory(s)&&inventoryBait(s)==0;
                    state=State.CLOSE;next=State.OPEN_BAZAAR;
                }
                case OPEN_BAZAAR -> {
                    if(s.menu()!=null)throw new IllegalArgumentException("Bazaar opening requires a closed owned menu");
                    waitMenu(s,"bazaar",State.BAZAAR,now);return issue(Kind.COMMAND,s,-1,0,now);
                }
                case BAZAAR -> {return navigation(s,"fishing","fishing",State.FISHING,now);}
                case FISHING -> {return navigation(s,"baits","baits",State.BAITS,now);}
                case BAITS -> {return navigation(s,profile.baitName(),"product",State.PRODUCT,now);}
                case PRODUCT -> {return navigation(s,"buy","buy",State.BUY,now);}
                case BUY -> {
                    requireOwned(s);var button=unique(s,profile.buttons().get(fill?"fill":"stack"),false);
                    quote=profile.quote(button);int wanted=(fill?remaining:1)*profile.stackSize();
                    if(quote.quantity()!=wanted||quote.quantity()>emptyInventory(s)*profile.stackSize())throw new IllegalArgumentException("Quoted bait quantity/capacity mismatch");
                    if(quote.cost().compareTo(BigDecimal.valueOf(settings.maxUnitPrice()).multiply(BigDecimal.valueOf(quote.quantity())))>0||confirmedCost.add(quote.cost()).compareTo(BigDecimal.valueOf(settings.maxCost()))>0)throw new IllegalArgumentException("Bait quote exceeds unit/session cost cap");
                    before=s;receipt=false;state=State.WAIT_PURCHASE;return issue(Kind.PURCHASE,s,button.id(),quote.quantity(),now);
                }
                case OPEN_FINAL_ROOT -> {waitMenu(s,"root",State.FINAL_ROOT,now);return issue(Kind.USE_MENU,s,-1,0,now);}
                case FINAL_ROOT -> {return navigation(s,"bags","bags",State.FINAL_BAGS,now);}
                case FINAL_BAGS -> {return navigation(s,"bag","bag",State.ENABLE,now);}
                case ENABLE -> {
                    requireOwned(s);var toggle=unique(s,profile.buttons().get("enable"),false);
                    boolean enabled=toggle.lore().contains(profile.enabledLore()),disabled=toggle.lore().contains(profile.disabledLore());
                    if(enabled==disabled)throw new IllegalArgumentException("Bait toggle state is ambiguous");
                    if(enabled){state=State.MOVE;break;}
                    before=s;state=State.WAIT_ENABLE;return issue(Kind.CLICK,s,toggle.id(),0,now);
                }
                case MOVE -> {
                    requireOwned(s);var matches=s.menu().slots().stream().filter(slot->slot.playerInventory()&&slot.count()>0&&profile.bait(slot.itemId(),slot.name())).sorted(Comparator.comparingInt(MenuSnapshot.Slot::id)).toList();
                    if(matches.isEmpty()){state=State.INITIAL_BAG;break;}
                    var slot=matches.getFirst();if(slot.count()>profile.stackSize())throw new IllegalArgumentException("Unexpected bait stack quantity");
                    if(transferred+slot.count()>settings.maxStacks()*profile.stackSize()){state=State.CLOSE;next=State.RESTORE;break;}
                    before=s;transferQuantity=slot.count();state=State.WAIT_MOVE;return issue(Kind.MOVE,s,slot.id(),slot.count(),now);
                }
                case CLOSE -> {requireOwned(s);state=State.WAIT_CLOSE;return issue(Kind.CLOSE,s,-1,0,now);}
                case RESTORE -> {if(s.menu()!=null)throw new IllegalArgumentException("Restore requires closed bait menu");state=State.COMPLETE;lastRequest=requestAt=now;reason="Bait restocking acknowledged; restore fishing slot";return Optional.of(new Action(Kind.RESTORE,s,-1,0));}
                default -> {}
            }
        }catch(IllegalArgumentException|ArithmeticException e){fail(e.getMessage());}
        return Optional.empty();
    }
    private Optional<Action> navigation(Snapshot s,String button,String title,State ready,long now){
        requireOwned(s);String label=profile.buttons().getOrDefault(button,button);var slot=unique(s,label,false);
        waitMenu(s,title,ready,now);return issue(Kind.CLICK,s,slot.id(),0,now);
    }
    private void waitMenu(Snapshot s,String role,State ready,long now){previousMenu=s.menuToken();previousRevision=s.menu()==null?-1:s.menu().revision();wantedTitle=profile.titles().get(role);next=ready;state=State.WAIT_MENU;requestAt=now;}
    private Optional<Action> issue(Kind kind,Snapshot s,int slot,int quantity,long now){lastRequest=requestAt=now;reason="Awaiting "+kind+" acknowledgment";return Optional.of(new Action(kind,s,slot,quantity));}
    private boolean sameOwned(Snapshot s){return s.menu()!=null&&s.menuToken()==ownedMenu&&s.menu().title().equals(ownedTitle);}
    private void requireOwned(Snapshot s){if(!sameOwned(s))throw new IllegalArgumentException("Bait menu identity changed");}
    private MenuSnapshot.Slot unique(Snapshot s,String name,boolean playerSlot){var slots=s.menu().slots().stream().filter(slot->slot.playerInventory()==playerSlot&&slot.count()>0&&!slot.locked()&&slot.name().equals(name)).toList();if(slots.size()!=1)throw new IllegalArgumentException("Missing/ambiguous bait button: "+name);return slots.getFirst();}
    private boolean bait(Stock stock){return stock.count()>0&&profile.bait(stock.itemId(),stock.name());}
    private int inventoryBait(Snapshot s){return s.inventory().stream().filter(this::bait).mapToInt(Stock::count).sum();}
    private int bagBait(Snapshot s){return s.storage().values().stream().filter(this::bait).mapToInt(Stock::count).sum();}
    private int emptyInventory(Snapshot s){return (int)s.inventory().stream().filter(stock->stock.count()==0).count();}
    private int emptyBag(Snapshot s){if(s.storage().size()!=profile.bagSlots().size())throw new IllegalArgumentException("Declared bait storage slots are unavailable");return (int)s.storage().values().stream().filter(stock->stock.count()==0).count();}
    private Map<Integer,Stock> otherInventory(Snapshot s){var result=new HashMap<Integer,Stock>();for(var stock:s.inventory())if(stock.count()>0&&!bait(stock))result.put(stock.index(),stock);return result;}
    private Map<Integer,Stock> otherStorage(Snapshot s){var result=new HashMap<Integer,Stock>();for(var stock:s.storage().values())if(stock.count()>0&&!bait(stock))result.put(stock.index(),stock);return result;}
    public void message(String text){
        if(state!=State.WAIT_PURCHASE||text==null||text.length()>4096)return;
        var parsed=profile.receipt(text);if(parsed.isEmpty())return;
        if(parsed.get().quantity()!=quote.quantity()||parsed.get().cost().compareTo(quote.cost())!=0){fail("Server bait receipt differs from requested quantity/cost");return;}receipt=true;
    }
    private void timeout(long now,String error){if(now-requestAt>4000)fail(error);}
    public void fail(String error){state=State.FAILED;reason=error;}
    public void cancel(String error){state=State.CANCELLED;reason=error;}
    public void releaseContext(){player=world=ownedMenu=previousMenu=null;before=null;}
    public void reset(){state=State.IDLE;profile=null;settings=null;player=world=ownedMenu=previousMenu=null;ownedTitle=wantedTitle="";reason="Idle";remaining=purchased=transferred=transferQuantity=0;confirmedCost=BigDecimal.ZERO;before=null;quote=null;receipt=fill=false;}
}
