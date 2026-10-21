package dev.veltraph.client.builder;

import com.google.gson.JsonParser;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Bound source acquisition/selling menu transitions. Every click needs a changed menu or item-count receipt. */
public final class GeneratedMenuFlow {
    public enum Action { NONE, CLICK, OPEN_INVENTORY, OPEN_BAZAAR, CLOSE, COMPLETE, STOP }
    public record Step(String sourceState,String title,String label,String nextTitle,String receiptItem,int receiptDelta,int slot,boolean playerSlot,String expectedItem,String cursorAfter) {
        public Step(String state,String title,String label,String nextTitle,String item,int delta){this(state,title,label,nextTitle,item,delta,-1,false,"","");}
        public Step {
            if(!Set.of("BUY_RESOURCE","REMOVE_FROM_TOOL","SELL_OTHER_RESOURCES","OPEN_INV_TO_DISSOLVE","PICK_UP_BLOCK","PUT_IN_CRAFTING_TABLE","CRAFT_ITEM","CLOSE_GUI_TO_LOOP","WIGGLE","OPEN_BAZAAR","SELL_INVENTORY","CONFIRM_SELL_INVENTORY").contains(sourceState))throw new IllegalArgumentException("Unknown source menu state");
            boolean nativeScreen=sourceState.equals("OPEN_INV_TO_DISSOLVE")||sourceState.equals("CLOSE_GUI_TO_LOOP");
            if(title.isBlank()||label.isBlank()&&slot<0&&!nativeScreen||title.length()>256||label.length()>256||nextTitle.length()>256||slot<-1||slot>200)throw new IllegalArgumentException("Exact source menu/slot contract required");
            if(!expectedItem.isBlank()&&!expectedItem.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Exact source/craft slot item id required");
            if(playerSlot&&expectedItem.isBlank()||!cursorAfter.isBlank()&&!Set.of("picked","empty").contains(cursorAfter)||cursorAfter.equals("picked")&&expectedItem.isBlank())throw new IllegalArgumentException("Player/cursor action needs explicit item/receipt contract");
            if(receiptItem.isBlank()&&nextTitle.isBlank()&&cursorAfter.isBlank()||receiptItem.isBlank()&&receiptDelta!=0||!receiptItem.isBlank()&&(!receiptItem.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||receiptDelta==0))throw new IllegalArgumentException("Observed next menu, cursor or signed item-count receipt required");
            if(sourceState.equals("PICK_UP_BLOCK")&&(!playerSlot||!cursorAfter.equals("picked"))||sourceState.equals("PUT_IN_CRAFTING_TABLE")&&(playerSlot||slot<1||slot>4||!expectedItem.equals("minecraft:air")||!cursorAfter.equals("empty"))||sourceState.equals("CRAFT_ITEM")&&(playerSlot||slot!=0||!cursorAfter.equals("picked")))throw new IllegalArgumentException("Inventory dissolution must bind player source, empty native crafting input, and native result cursor receipts");
            if(sourceState.equals("OPEN_BAZAAR")&&(!title.equals("@closed")||!label.equals("bazaar")||nextTitle.isBlank()))throw new IllegalArgumentException("Source market opening requires explicit bazaar command and exact observed next menu title");
        }
    }
    public record Decision(Action action,int slot,String reason) {}
    private final List<Step> steps;
    private int index,menuId,revision,beforeCount;
    private long issued=-1,started=-1;
    private String title="";
    private int pickedCount;
    public GeneratedMenuFlow(List<Step> steps){this.steps=List.copyOf(steps);if(steps.isEmpty()||steps.size()>32)throw new IllegalArgumentException("Menu flow requires 1..32 bound source steps");if(steps.getLast().cursorAfter().equals("picked"))throw new IllegalArgumentException("Source menu flow must return its carried item before completion");}
    public static GeneratedMenuFlow decode(String json) {
        if(json==null||json.isBlank())throw new IllegalArgumentException("Blank source acquisition/selling menu flow");
        if(json.length()>16384)throw new IllegalArgumentException("Oversized source menu flow");
        var steps=new ArrayList<Step>();
        for(var e:JsonParser.parseString(json).getAsJsonArray()) {var o=e.getAsJsonObject();steps.add(new Step(o.get("state").getAsString(),o.get("title").getAsString(),o.has("label")?o.get("label").getAsString():"",o.has("nextTitle")?o.get("nextTitle").getAsString():"",o.has("receiptItem")?o.get("receiptItem").getAsString():"",o.has("receiptDelta")?o.get("receiptDelta").getAsInt():0,o.has("slot")?o.get("slot").getAsInt():-1,o.has("playerSlot")&&o.get("playerSlot").getAsBoolean(),o.has("expectedItem")?o.get("expectedItem").getAsString():"",o.has("cursorAfter")?o.get("cursorAfter").getAsString():""));}
        return new GeneratedMenuFlow(steps);
    }
    public String receiptItem(){return index>=steps.size()?"":steps.get(index).receiptItem();}
    public Step current(){return index>=steps.size()?null:steps.get(index);}
    public boolean allowsTitle(String title){var p=current();return p!=null&&(title.equals(p.title())||title.equals(p.nextTitle()));}
    public Decision tick(MenuSnapshot menu,int itemCount,long now) {
        return tick(menu,itemCount,"",0,now);
    }
    public Decision tick(MenuSnapshot menu,int itemCount,String cursorItem,int cursorCount,long now) {
        if(index>=steps.size())return new Decision(Action.COMPLETE,-1,"Observed all source menu transitions");
        if(started<0)started=now;
        if(now-started>30000)return stop("Source menu flow timed out");
        var step=steps.get(index);
        if(issued>=0) {
            boolean menuChanged=step.nextTitle().equals("@closed")&&menu==null||menu!=null&&(!step.nextTitle().isBlank()&&menu.title().equals(step.nextTitle()))&&(menu.id()!=menuId||!menu.title().equals(title)||menu.revision()!=revision);
            boolean delta=step.receiptDelta()>0?itemCount-beforeCount>=step.receiptDelta():beforeCount-itemCount>=-step.receiptDelta();
            boolean receipt=!step.receiptItem().isBlank()&&delta&&menu!=null&&menu.revision()!=revision;
            boolean cursor=menu!=null&&menu.id()==menuId&&menu.revision()!=revision&&(step.cursorAfter().equals("empty")&&cursorCount==0||step.cursorAfter().equals("picked")&&cursorItem.equals(step.expectedItem())&&cursorCount==pickedCount);
            if(menuChanged||receipt||cursor){index++;issued=-1;started=now;return index==steps.size()?new Decision(Action.COMPLETE,-1,"Observed all source menu transitions"):new Decision(Action.NONE,-1,"Observed "+step.sourceState());}
            return now-issued>3000?stop("No changed menu/item receipt for "+step.sourceState()):new Decision(Action.NONE,-1,"Await source menu receipt");
        }
        if(step.sourceState().equals("OPEN_INV_TO_DISSOLVE")) {
            if(menu!=null||cursorCount!=0)return stop("Inventory dissolution requires closed menu and empty cursor");
            menuId=-1;revision=-1;title="";issued=now;return new Decision(Action.OPEN_INVENTORY,-1,step.sourceState());
        }
        if(step.sourceState().equals("OPEN_BAZAAR")) {
            if(menu!=null||cursorCount!=0)return stop("Bound source Bazaar route requires closed menu and empty cursor");
            menuId=-1;revision=-1;title="";issued=now;return new Decision(Action.OPEN_BAZAAR,-1,step.sourceState());
        }
        if(menu==null||!menu.title().equals(step.title()))return stop("Unbound menu in "+step.sourceState());
        if(step.sourceState().equals("CLOSE_GUI_TO_LOOP")){if(cursorCount!=0)return stop("Refuse to close source workflow with a carried item");menuId=menu.id();revision=menu.revision();title=menu.title();issued=now;return new Decision(Action.CLOSE,-1,step.sourceState());}
        var slots=menu.slots().stream().filter(s->s.playerInventory()==step.playerSlot()&&!s.locked()&&(s.count()>0||step.expectedItem().equals("minecraft:air"))&&(step.slot()<0||s.id()==step.slot())&&(step.label().isBlank()||s.name().equals(step.label()))&&(step.expectedItem().isBlank()||s.itemId().equals(step.expectedItem()))).toList();
        if(slots.size()!=1)return stop("Ambiguous/missing source slot label in "+step.sourceState());
        menuId=menu.id();revision=menu.revision();title=menu.title();beforeCount=itemCount;pickedCount=slots.getFirst().count();issued=now;
        return new Decision(Action.CLICK,slots.getFirst().id(),step.sourceState());
    }
    private Decision stop(String reason){return new Decision(Action.STOP,-1,reason);}
}
