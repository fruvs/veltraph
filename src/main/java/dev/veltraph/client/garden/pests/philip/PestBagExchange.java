package dev.veltraph.client.garden.pests.philip;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import java.util.*;

/** Philip's recovered CLICK_EMPTY_BAG: one dynamic exact row click, received message and native bag zero. */
public final class PestBagExchange {
    public enum State { IDLE, WAIT_DELAY, WAIT_OUTCOME, CLOSE, COMPLETE, FAILED, CANCELLED }
    public record Settings(String title,AbiphoneCall.Item marker,AbiphoneCall.Item emptyBagButton,String receipt,
                           List<String> rejections,int maximumPests,int delayTicks,int timeoutTicks) {
        public Settings {rejections=List.copyOf(rejections);Objects.requireNonNull(marker);Objects.requireNonNull(emptyBagButton);
            if(title==null||title.isBlank()||receipt==null||receipt.isBlank()||marker.empty()||emptyBagButton.empty()||maximumPests<1||maximumPests>1000||delayTicks<1||timeoutTicks<=delayTicks||timeoutTicks>1200
                ||rejections.stream().anyMatch(String::isBlank)||rejections.contains(receipt))throw new IllegalArgumentException("Observed exact Philip menu/marker/button/receipt and bounded bag/timing required");}}
    public record Observation(Object player,Object world,Object connection,Object menu,String title,int revision,
                              List<AbiphoneCall.Row> rows,boolean cursorEmpty,boolean ownPrediction,int bag,int vacuumCount) {
        public Observation {rows=List.copyOf(rows);}public boolean valid(){return player!=null&&world!=null&&connection!=null&&vacuumCount==1&&bag>=0;}}
    public interface Port {Observation read();boolean click(Observation expected,int slot);boolean close(Observation expected);}
    private final Port port;private State state=State.IDLE;private Settings settings;private Observation initial;
    private long next,deadline,last;private boolean ack;private String reason="Idle";private int clicks;
    public PestBagExchange(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(Settings settings,long tick){if(active())throw new IllegalStateException("Philip exchange already owned");this.settings=Objects.requireNonNull(settings);var o=port.read();
        if(!o.valid()||o.menu()==null||!o.cursorEmpty()||!o.title().equals(settings.title())||unique(o,settings.marker()).size()!=1||unique(o,settings.emptyBagButton()).size()!=1||o.bag()<1||o.bag()>settings.maximumPests()){
            state=State.FAILED;reason="One exact native vacuum, positive bounded bag and unique observed Philip menu rows required";return false;}
        initial=o;ack=false;clicks=0;last=tick;next=tick+settings.delayTicks();deadline=tick+settings.timeoutTicks();state=State.WAIT_DELAY;reason="Waiting for observed Philip button";return true;}
    private static List<AbiphoneCall.Row> unique(Observation o,AbiphoneCall.Item item){return o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(item)).toList();}
    private boolean scoped(Observation o){return o.valid()&&o.player()==initial.player()&&o.world()==initial.world()&&o.connection()==initial.connection()&&o.menu()==initial.menu()&&o.title().equals(settings.title())&&unique(o,settings.marker()).size()==1;}
    public void message(String message){if(state!=State.WAIT_OUTCOME)return;String text=MenuSnapshot.clean(message);if(settings.receipt().equals(text))ack=true;else if(settings.rejections().contains(text))fail("Server rejected Philip exchange");}
    public void tick(long tick){if(!active())return;var o=port.read();if(tick<last||tick>deadline){fail("Philip exchange timed out; request was not repeated");return;}last=tick;
        if(!scoped(o)){fail("Philip owner/menu/vacuum identity replaced");return;}if(!o.cursorEmpty()){if(state==State.WAIT_OUTCOME&&o.ownPrediction())return;fail("Philip cursor ownership changed");return;}
        if(tick<next)return;switch(state){
            case WAIT_DELAY->{if(o.bag()!=initial.bag()){fail("Bag changed before the owned Philip request");return;}var rows=unique(o,settings.emptyBagButton());if(rows.size()!=1||!port.click(o,rows.getFirst().slot())){fail("Exact current Philip row click refused");return;}clicks++;ack=false;state=State.WAIT_OUTCOME;next=tick+settings.delayTicks();reason="One empty-bag click; awaiting native bag/revision and received receipt";}
            case WAIT_OUTCOME->{if(o.bag()!=initial.bag()&&o.bag()!=0){fail("Unexpected partial or increased vacuum bag");return;}if(ack&&o.bag()==0&&o.revision()!=initial.revision()){state=State.CLOSE;next=tick+settings.delayTicks();reason="Received empty-bag exchange and native zero/revision";}}
            case CLOSE->{if(o.bag()!=0||!ack||!port.close(o)){fail("Owned Philip closure refused");return;}state=State.COMPLETE;reason="Observed native Philip exchange completed; original inventory pair remains owned by ingress";}
            default->{}
        }}
    private void fail(String reason){state=State.FAILED;this.reason=reason;}
    public void cancel(){if(active()){state=State.CANCELLED;reason="Philip exchange stopped; no retry";}}
    public boolean active(){return state==State.WAIT_DELAY||state==State.WAIT_OUTCOME||state==State.CLOSE;}
    public State state(){return state;}public String reason(){return reason;}public int clicks(){return clicks;}public int exchanged(){return state==State.COMPLETE?initial.bag():0;}
}
