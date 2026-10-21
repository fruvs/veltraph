package dev.veltraph.client.inventory.selling;

import java.util.*;

/** Readable source opener callback order and <=5 dispatch attempts. A lost acknowledgment never retries itself. */
public final class NpcCookieMenuRuntime {
    public enum State { IDLE, OPEN, WAIT_MENU, COOKIE, WAIT_COOKIE, SELLER, WAIT_SELLER, HANDOFF, FAILED, CANCELLED }
    public record Row(int slot,boolean playerInventory,boolean locked,NpcCookieBindings.Item item){}
    public record Observation(boolean live,Object player,Object world,Object connection,Object menu,String title,int revision,
                              List<String> inventory,List<Row> rows,int selected,boolean cursorEmpty){
        public Observation {inventory=List.copyOf(inventory);rows=List.copyOf(rows);}
    }
    public interface Port {Observation read();boolean open(Observation expected,NpcCookieBindings bindings,boolean viaCommand);boolean click(Observation expected,int slot);boolean restoreSelection(Observation expected,int slot);}
    private final Port port;private NpcCookieBindings bindings;private boolean command;private Observation initial;private State state=State.IDLE;private Object ownedMenu;private long deadline,last,next;private int attempts,delay;private String reason="Idle";
    public NpcCookieMenuRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(NpcCookieBindings bindings,boolean command,long tick,int delay,int timeout){
        if(active())throw new IllegalStateException("NPC opener active");this.bindings=bindings;this.command=command;this.delay=delay;initial=port.read();last=tick;next=tick;deadline=Math.addExact(tick,timeout);ownedMenu=null;attempts=0;
        if(delay<1||timeout<=delay||!initial.live()||initial.menu()!=null||!initial.cursorEmpty()||initial.selected()<0||initial.selected()>8)return fail("Closed live native inventory required");
        state=State.OPEN;reason="Prepared observed source NPC menu opener";return true;
    }
    public void tick(long tick){
        if(!active())return;var o=port.read();if(tick<last||tick>deadline||!context(o)||!o.cursorEmpty()||!o.inventory().equals(initial.inventory())){fail("NPC opener deadline/context/cursor/inventory changed; no retry");return;}last=tick;
        int expectedSelected=command?initial.selected():bindings.menuItemSlot();
        if(state!=State.OPEN&&o.selected()!=expectedSelected){fail("Selected tool changed during NPC menu opening");return;}if(tick<next)return;
        switch(state){
            case OPEN -> {if(++attempts>5){fail("Source opener attempt counter exceeded five");return;}if(o.menu()!=null||!port.open(o,bindings,command)){fail("Observed source opener refused");return;}state=State.WAIT_MENU;}
            case WAIT_MENU -> {if(o.menu()!=null){if(!o.title().equals(bindings.skyBlockTitle())||unique(o,bindings.cookieButton()).size()!=1){fail("Unexpected or ambiguous SkyBlock menu");return;}ownedMenu=o.menu();state=State.COOKIE;next=tick+delay;}}
            case COOKIE -> click(o,bindings.cookieButton(),bindings.skyBlockTitle(),State.WAIT_COOKIE,tick);
            case WAIT_COOKIE -> {if(o.menu()!=ownedMenu&&o.menu()!=null){if(!o.title().equals(bindings.cookieTitle())||unique(o,bindings.sellerButton()).size()!=1){fail("Unexpected cookie menu");return;}ownedMenu=o.menu();state=State.SELLER;next=tick+delay;}}
            case SELLER -> click(o,bindings.sellerButton(),bindings.cookieTitle(),State.WAIT_SELLER,tick);
            case WAIT_SELLER -> {if(o.menu()!=ownedMenu&&o.menu()!=null){if(!o.title().equals(bindings.sellerTitle())||unique(o,bindings.sellerMarker()).size()!=1){fail("Unexpected NPC seller menu");return;}ownedMenu=o.menu();if(!command&&!port.restoreSelection(o,initial.selected())){fail("Owned opener selection restoration refused");return;}state=State.HANDOFF;reason="Observed cookie seller callback acknowledged";}}
            default -> {}
        }
    }
    private void click(Observation o,NpcCookieBindings.Item item,String title,State nextState,long tick){var rows=unique(o,item);if(o.menu()!=ownedMenu||!o.title().equals(title)||rows.size()!=1||!port.click(o,rows.getFirst().slot())){fail("Native cookie button changed or refused");return;}state=nextState;next=tick+delay;}
    private boolean context(Observation o){return o.live()&&o.player()==initial.player()&&o.world()==initial.world()&&o.connection()==initial.connection();}
    private static List<Row> unique(Observation o,NpcCookieBindings.Item item){return o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(item)).toList();}
    public boolean handoffValid(){var o=port.read();return state==State.HANDOFF&&context(o)&&o.menu()==ownedMenu&&o.title().equals(bindings.sellerTitle())&&o.cursorEmpty()&&o.inventory().equals(initial.inventory())&&o.selected()==initial.selected()&&unique(o,bindings.sellerMarker()).size()==1;}
    private void restoreOwnedSelection(){if(initial==null||bindings==null||command)return;var o=port.read();if(context(o)&&o.cursorEmpty()&&o.inventory().equals(initial.inventory())&&o.selected()==bindings.menuItemSlot()&&(o.menu()==null||o.menu()==ownedMenu))port.restoreSelection(o,initial.selected());}
    private boolean fail(String why){restoreOwnedSelection();state=State.FAILED;reason=why;return false;}
    public void cancel(){if(active()||state==State.HANDOFF){restoreOwnedSelection();state=State.CANCELLED;reason="Cancelled; replacement state preserved";}}
    public boolean active(){return switch(state){case OPEN,WAIT_MENU,COOKIE,WAIT_COOKIE,SELLER,WAIT_SELLER->true;default->false;};}
    public State state(){return state;}public String reason(){return reason;}public int attempts(){return attempts;}
    public Object ownedMenu(){return ownedMenu;}
}
