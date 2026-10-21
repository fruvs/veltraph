package dev.veltraph.client.garden.loadout;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Stable4.0.30 named OPEN/SWAP/CLOSE; marker+fresh menu and actual complete configured gear witness. */
public final class NamedLoadoutFlow {
    public enum State { IDLE, OPEN_LOADOUT, WAIT_MENU, SWAP_LOADOUT, WAIT_ACK, CLOSE_LOADOUT, APPLIED, RESTORED, FAILED, CANCELLED }
    public record Gear(String registryId,int count,String components) {public Gear {Objects.requireNonNull(registryId);Objects.requireNonNull(components);if(count<0)throw new IllegalArgumentException("Invalid gear count");} }
    public record Profile(String name,String actionItemId,String activeItemId,Map<String,Gear> gear) {
        public Profile {name=MenuSnapshot.clean(name);if(name.isBlank()||actionItemId==null||actionItemId.isBlank()||activeItemId==null||activeItemId.isBlank())throw new IllegalArgumentException("Exact named row/item identities required");gear=Map.copyOf(gear);
            if(!gear.keySet().equals(Set.of("HEAD","CHEST","LEGS","FEET","OFFHAND")))throw new IllegalArgumentException("All four armor and offhand fingerprints required");}
    }
    public record Settings(String command,String menuTitle,String activeMarker,String inactiveMarker,List<Profile> profiles,int delayTicks,int timeoutTicks) {
        public Settings {command=MenuSnapshot.clean(command);menuTitle=MenuSnapshot.clean(menuTitle);activeMarker=MenuSnapshot.clean(activeMarker);inactiveMarker=MenuSnapshot.clean(inactiveMarker);profiles=List.copyOf(profiles);
            if(!command.equals("loadout")||menuTitle.isBlank()||activeMarker.isBlank()||inactiveMarker.isBlank()||activeMarker.equals(inactiveMarker)||profiles.isEmpty()||delayTicks<1||timeoutTicks<=delayTicks)
                throw new IllegalArgumentException("Source-supported menu opener, exact markers/profiles and bounded timing required");
            var names=new HashSet<String>();for(var p:profiles)if(!names.add(p.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Ambiguous named loadout profile");}
    }
    public record Observation(Object player,Object world,Object connection,Object menu,MenuSnapshot snapshot,Map<String,Gear> gear,boolean cursorEmpty,boolean ownPrediction,boolean serverRevision) {
        public Observation {gear=Map.copyOf(gear);}
    }
    public record Selection(String name,Map<String,Gear> gear) {public Selection {gear=Map.copyOf(gear);} }
    public record Receipt(Object player,Object world,Object connection,String name,Map<String,Gear> gear,Object acknowledgedMenu,int revision,long tick,boolean restoring) {public Receipt {gear=Map.copyOf(gear);} }
    public interface Port {Observation read();boolean command(Observation expected,String command);boolean click(Observation expected,int slot);boolean close(Observation expected);}
    private final Port port;private State state=State.IDLE;private Settings settings;private Profile target;private Selection original;private Receipt receipt;
    private Object player,world,connection,ownedMenu,ackMenu;private int issuedRevision,ackRevision;private Map<String,Gear> before;private long next,deadline,last;
    private boolean restoring,cancelRequested,clicked;private String reason="Idle";
    public NamedLoadoutFlow(Port port){this.port=Objects.requireNonNull(port);}
    public void start(String name,Settings settings,long tick){
        if(active())throw new IllegalStateException("Named loadout flow already owns an operation");
        this.settings=Objects.requireNonNull(settings);target=find(name);original=null;receipt=null;restoring=cancelRequested=clicked=false;ownedMenu=null;ackMenu=null;
        var o=port.read();player=o.player();world=o.world();connection=o.connection();before=o.gear();last=tick;next=tick;deadline=tick+settings.timeoutTicks();
        if(target==null||player==null||world==null||connection==null||o.snapshot()!=null||!o.cursorEmpty()){fail("Closed menus, complete live context and configured unique loadout required");return;}state=State.OPEN_LOADOUT;reason="Opening named loadout menu";
    }
    public void restore(long tick){
        if(active())throw new IllegalStateException("Finish current named loadout request before restore");
        if(original==null||!same(port.read())){fail("Original loadout or same player/world/connection unavailable");return;}
        target=find(original.name());if(target==null||!target.gear().equals(original.gear())){fail("Original gear differs from configured restoration profile");return;}
        restoring=true;clicked=false;receipt=null;ownedMenu=null;ackMenu=null;last=next=tick;deadline=tick+settings.timeoutTicks();before=port.read().gear();state=State.OPEN_LOADOUT;reason="Restoring captured original named loadout";
    }
    private Profile find(String name){return settings==null||name==null?null:settings.profiles().stream().filter(p->p.name().equalsIgnoreCase(MenuSnapshot.clean(name))).findFirst().orElse(null);}
    private boolean same(Observation o){return o.player()==player&&o.world()==world&&o.connection()==connection&&player!=null&&world!=null&&connection!=null;}
    private boolean marker(MenuSnapshot.Slot slot,String marker){return slot.lore().stream().anyMatch(line->MenuSnapshot.clean(line).equals(marker));}
    private List<MenuSnapshot.Slot> rows(MenuSnapshot m,String name){return m.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()==1&&MenuSnapshot.clean(s.name()).equalsIgnoreCase(name)).toList();}
    public void tick(long tick){
        if(!active())return;var o=port.read();if(tick<last||tick>deadline){fail("Named loadout acknowledgment timed out; no retry");return;}last=tick;
        if(!same(o)){fail("Loadout player/world/connection changed; no stale restore");return;}
        if(!o.cursorEmpty()){if(o.ownPrediction()&&(state==State.WAIT_ACK||state==State.WAIT_MENU))return;fail("Foreign cursor interrupted named loadout");return;}
        if(ownedMenu!=null&&o.menu()!=ownedMenu&&state!=State.CLOSE_LOADOUT){fail("Named loadout container replaced");return;}
        if(tick<next)return;
        if(state==State.OPEN_LOADOUT){if(o.snapshot()!=null||!port.command(o,settings.command())){fail("Native named loadout opening refused");return;}state=State.WAIT_MENU;next=tick+settings.delayTicks();return;}
        if(state==State.WAIT_MENU){
            if(o.snapshot()==null)return;if(!titleAllowed(o.snapshot().title())){fail("Unexpected named loadout menu");return;}ownedMenu=o.menu();
            if(original==null){var active=o.snapshot().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()==1&&marker(s,settings.activeMarker())&&!marker(s,settings.inactiveMarker())).toList();
                if(active.size()!=1){fail("Original named loadout marker missing/ambiguous");return;}var p=find(active.getFirst().name());
                if(p==null||!active.getFirst().itemId().equals(p.activeItemId())||!o.gear().equals(before)||!o.gear().equals(p.gear())){fail("Original named row does not agree with actual captured gear");return;}
                original=new Selection(p.name(),o.gear());}
            state=State.SWAP_LOADOUT;next=tick+settings.delayTicks();return;
        }
        if(state==State.SWAP_LOADOUT){
            if(o.snapshot()==null||!titleAllowed(o.snapshot().title())){fail("Named loadout menu unavailable");return;}var matched=rows(o.snapshot(),target.name());
            if(matched.size()!=1){fail("Requested named row missing/ambiguous");return;}var row=matched.getFirst();boolean active=marker(row,settings.activeMarker()),inactive=marker(row,settings.inactiveMarker());
            if(active&&!inactive&&row.itemId().equals(target.activeItemId())&&o.gear().equals(target.gear())){ackMenu=o.menu();ackRevision=o.snapshot().revision();state=State.CLOSE_LOADOUT;return;}
            if(!inactive||active||!row.itemId().equals(target.actionItemId())){fail("Unknown named row state/item identity");return;}
            issuedRevision=o.snapshot().revision();state=State.WAIT_ACK;clicked=true;
            if(!port.click(o,row.id())){fail("Bound named loadout click refused");return;}next=tick+settings.delayTicks();return;
        }
        if(state==State.WAIT_ACK){
            if(o.snapshot()==null||!titleAllowed(o.snapshot().title())){fail("Loadout menu closed before acknowledgment");return;}var matched=rows(o.snapshot(),target.name());
            if(matched.size()==1){var row=matched.getFirst();if(marker(row,settings.activeMarker())&&!marker(row,settings.inactiveMarker())&&row.itemId().equals(target.activeItemId())
                    &&o.snapshot().revision()>issuedRevision&&o.serverRevision()&&o.gear().equals(target.gear())){ackMenu=o.menu();ackRevision=o.snapshot().revision();state=State.CLOSE_LOADOUT;next=tick+settings.delayTicks();}}return;
        }
        if(state==State.CLOSE_LOADOUT){
            if(o.snapshot()!=null){if(o.menu()!=ownedMenu||!titleAllowed(o.snapshot().title())||!port.close(o)){fail("Owned loadout menu close refused");return;}return;}
            if(!o.gear().equals(target.gear())){fail("Gear changed before named loadout completion");return;}
            receipt=new Receipt(player,world,connection,target.name(),o.gear(),ackMenu,ackRevision,tick,restoring);state=restoring?State.RESTORED:State.APPLIED;reason="Named loadout and actual gear verified; owned menu closed";
            if(cancelRequested&&!restoring){restore(tick);cancelRequested=true;}else if(cancelRequested){state=State.CANCELLED;reason="Cancelled after original named loadout restoration";}
        }
    }
    public void cancel(long tick){
        cancelRequested=true;var o=port.read();
        if(original!=null&&same(o)&&target!=null&&o.gear().equals(target.gear())&&(state==State.APPLIED||state==State.FAILED||(active()&&clicked))){
            if(o.snapshot()!=null){if(ownedMenu!=o.menu()||!o.cursorEmpty()||!port.close(o)){fail("Cannot close owned menu for compensating original loadout restore");return;}}
            state=State.FAILED;restore(tick);cancelRequested=true;return;
        }
        if(active()&&clicked)return;
if(same(o)&&ownedMenu==o.menu()&&o.snapshot()!=null&&o.cursorEmpty())port.close(o);
        state=State.CANCELLED;reason="Cancelled before named loadout request";
    }
    private void fail(String text){state=State.FAILED;reason=text;var o=port.read();if(same(o)&&ownedMenu!=null&&o.menu()==ownedMenu&&o.snapshot()!=null&&o.cursorEmpty())port.close(o);}
    /** Identity-only affiliation. This does not authenticate any public Receipt or grant effects. */
    public boolean usesExactPort(Port exact){return port==exact;}
    public boolean cleanupActive(){return cancelRequested&&active();}
    public boolean active(){return state==State.OPEN_LOADOUT||state==State.WAIT_MENU||state==State.SWAP_LOADOUT||state==State.WAIT_ACK||state==State.CLOSE_LOADOUT;}
    public boolean titleAllowed(String title){return settings!=null&&settings.menuTitle().equals(MenuSnapshot.clean(title));}
    public State state(){return state;}public String reason(){return reason;}public Selection original(){return original;}public Receipt receipt(){return receipt;}public String requestName(){return target==null?null:target.name();}
}
