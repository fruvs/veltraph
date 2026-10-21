package dev.veltraph.client.garden.pests.philip;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import java.util.*;

/** Recovered current Abiphone helper: close-before-start policy, one prefix+contact command, fresh exact dialogue. */
public final class SourceContactCommand {
    public enum State { IDLE, WAIT_MENU, HANDOFF, FAILED, CANCELLED }
    public record Settings(String prefix,String contact,String title,AbiphoneCall.Item marker,String receipt,List<String> rejections,int timeoutTicks) {
        public Settings {Objects.requireNonNull(marker);rejections=List.copyOf(rejections);
            if(prefix==null||!prefix.matches("[a-z][a-z0-9_]{0,31} ")||contact==null||!contact.matches("[A-Za-z0-9][A-Za-z0-9 _'-]{0,63}")||title==null||title.isBlank()||marker.empty()||receipt==null||receipt.isBlank()
                ||rejections.stream().anyMatch(String::isBlank)||rejections.contains(receipt)||timeoutTicks<1||timeoutTicks>1200)throw new IllegalArgumentException("Explicit bounded observed command prefix/contact/title/marker/receipt required");}
        public String command(){return prefix+contact;}}
    public record Observation(boolean valid,Object player,Object world,Object connection,Object menu,String title,int revision,List<AbiphoneCall.Row> rows,boolean cursorEmpty,List<AbiphoneCall.Item> inventory,int selectedSlot) {
        public Observation {rows=List.copyOf(rows);inventory=List.copyOf(inventory);}public boolean live(){return valid&&player!=null&&world!=null&&connection!=null&&inventory.size()==36&&selectedSlot>=0&&selectedSlot<9;}}
    public interface Port {Observation read();boolean send(Observation expected,String command);}
    private final Port port;private Settings settings;private Observation initial,handoff;private Object ownedMenu;private State state=State.IDLE;
    private long deadline,last;private boolean ack;private String reason="Idle";private int requests;
    public SourceContactCommand(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(Settings settings,long tick){if(state==State.WAIT_MENU||state==State.HANDOFF)throw new IllegalStateException("Source contact request/handoff already owned");this.settings=Objects.requireNonNull(settings);initial=port.read();requests=0;ack=false;ownedMenu=null;handoff=null;last=tick;deadline=tick+settings.timeoutTicks();
        if(!initial.live()||initial.menu()!=null||!initial.cursorEmpty()){fail("Closed native menus, healthy owner and full native inventory required before contact command");return false;}
        if(!port.send(initial,settings.command())){fail("Immediate native contact command binding refused");return false;}requests=1;state=State.WAIT_MENU;reason="One observed source contact command; awaiting fresh native dialogue and receipt";return true;}
    public void message(String line){if(state!=State.WAIT_MENU)return;String text=MenuSnapshot.clean(line);if(settings.receipt().equals(text))ack=true;else if(settings.rejections().contains(text))fail("Received source contact rejection");}
    public void tick(long tick){if(state!=State.WAIT_MENU)return;if(tick<last||tick>deadline){fail("Source contact response timed out; no command retry");return;}last=tick;var o=port.read();
        if(!o.live()||o.player()!=initial.player()||o.world()!=initial.world()||o.connection()!=initial.connection()||!o.inventory().equals(initial.inventory())||o.selectedSlot()!=initial.selectedSlot()){fail("Native source contact owner/inventory changed before dialogue");return;}
        if(!o.cursorEmpty()){fail("Source contact cursor changed");return;}if(o.menu()==null)return;
        if(!o.title().equals(settings.title())||o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(settings.marker())).count()!=1){fail("Unexpected source contact menu preserved");return;}
        if(ownedMenu!=null&&ownedMenu!=o.menu()){fail("Source contact dialogue object replaced");return;}ownedMenu=o.menu();
        if(ack){handoff=o;state=State.HANDOFF;reason="Received configured source contact receipt and fresh exact native dialogue";}}
    private void fail(String reason){state=State.FAILED;this.reason=reason;}
    public void cancel(){if(state==State.WAIT_MENU||state==State.HANDOFF){state=State.CANCELLED;reason="Source contact stopped; no retry";}}
    public boolean release(){if(state!=State.HANDOFF)return false;state=State.IDLE;ownedMenu=null;handoff=null;return true;}
    public boolean handoffValid(){if(state!=State.HANDOFF||handoff==null)return false;var o=port.read();return o.live()&&o.player()==initial.player()&&o.world()==initial.world()&&o.connection()==initial.connection()&&o.menu()==handoff.menu()&&o.title().equals(settings.title())&&o.cursorEmpty()&&o.inventory().equals(initial.inventory())&&o.selectedSlot()==initial.selectedSlot()&&o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(settings.marker())).count()==1;}
    public State state(){return state;}public String reason(){return reason;}public int requests(){return requests;}public Object ownedMenu(){return ownedMenu;}public Optional<Observation> handoff(){return state==State.HANDOFF?Optional.ofNullable(handoff):Optional.empty();}
}
