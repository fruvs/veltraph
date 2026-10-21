package dev.veltraph.client.garden.pests;

import java.util.Optional;

/** One explicit current-plot spray, requiring server chat and exact material consumption. */
public final class PestSprayRuntime {
    public enum State { IDLE, SELECT_TOOL, READY, WAITING_ACK, COMPLETE, FAILED, CANCELLED }
    public record Settings(boolean enabled,int plot,int units,long unitValue,long maxValue,boolean contestOnly,long timeoutMs) {}
    public record Snapshot(boolean valid,boolean screenFree,boolean inPlot,boolean contest,int toolSlot,
                           String toolIdentity,String materialIdentity,int materialCount,boolean correctSelection,int selectedSlot) {}
    public interface Actions { boolean select(int slot); boolean use(Snapshot expected); void release(); }
    private final Actions actions;
    private State state=State.IDLE;
    private Settings originalSettings;
    private Snapshot original;
    private long started,last;
    private boolean chatAck;
    private String reason="Idle";
    public PestSprayRuntime(Actions actions){this.actions=actions;}
    public static Optional<String> validate(Snapshot s,Settings c){
        if(c==null||!c.enabled())return Optional.of("Enable Sprayonator first");
        if(c.plot()<1||c.plot()>24||c.units()<1||c.units()>64||c.unitValue()<1||c.maxValue()<1||c.timeoutMs()<1)
            return Optional.of("Select plot, supply amount and positive inventory value cap");
        try{if(Math.multiplyExact(c.unitValue(),c.units())>c.maxValue())return Optional.of("Spray consumption exceeds inventory value cap");}
        catch(ArithmeticException overflow){return Optional.of("Spray value overflow");}
        if(s==null||!s.valid()||!s.screenFree()||!s.inPlot())return Optional.of("Stand inside the selected Garden plot with menus closed");
        if(c.contestOnly()&&!s.contest())return Optional.of("No active Jacob's Contest sidebar header");
        if(s.toolSlot()<0||s.toolSlot()>8||s.toolIdentity()==null||s.toolIdentity().isBlank())return Optional.of("Put one unambiguous Sprayonator in the hotbar");
        if(s.materialIdentity()==null||s.materialIdentity().isBlank()||s.materialCount()<c.units())return Optional.of("Configured spray material must already be in inventory");
        if(!s.correctSelection())return Optional.of("Sprayonator selection lore does not match the configured observed line");
        return Optional.empty();
    }
    public boolean start(Snapshot s,Settings c,long now){
        cancel();var invalid=validate(s,c);
        if(invalid.isPresent()){state=State.FAILED;reason=invalid.get();return false;}
        original=s;originalSettings=c;started=last=now;chatAck=false;state=State.SELECT_TOOL;reason="Inventory value and plot checked";return true;
    }
    public void tick(Snapshot s,Settings c,long now){
        if(!active())return;
        if(now<last||now-started>=originalSettings.timeoutMs()){finish(State.FAILED,"Server spray acknowledgment timed out; no retry");return;}
        last=now;
        if(!originalSettings.equals(c)||s==null||!s.valid()||!s.screenFree()||!s.inPlot()||(c.contestOnly()&&!s.contest())){
            finish(State.CANCELLED,"Spray context, plot or settings changed");return;
        }
        if(s.toolSlot()!=original.toolSlot()||!s.toolIdentity().equals(original.toolIdentity())
                ||!s.materialIdentity().equals(original.materialIdentity())||!s.correctSelection()){
            finish(State.CANCELLED,"Spray tool, material identity or selection changed");return;
        }
        if(state==State.SELECT_TOOL){
            if(s.materialCount()!=original.materialCount()){finish(State.CANCELLED,"Supply changed before selection");return;}
            state=State.READY;
            try{if(!actions.select(s.toolSlot()))finish(State.FAILED,"Tool selection failed");}
            catch(RuntimeException failure){finish(State.FAILED,"Tool selection failed");}
            return;
        }
        if(s.selectedSlot()!=original.toolSlot()){finish(State.CANCELLED,"Selected spray slot changed");return;}
        if(state==State.READY){
            if(s.materialCount()!=original.materialCount()){finish(State.CANCELLED,"Supply changed before use");return;}
            state=State.WAITING_ACK;reason="Awaiting server success and exact supply consumption";
            try{if(!actions.use(s))finish(State.FAILED,"Spray item-use guard rejected request");}
            catch(RuntimeException failure){finish(State.FAILED,"Spray item-use failed");}
            return;
        }
        int expected=original.materialCount()-originalSettings.units();
        if(s.materialCount()<expected){finish(State.FAILED,"Server consumed more spray than configured");return;}
        if(chatAck&&s.materialCount()==expected)finish(State.COMPLETE,"Server success and exact spray consumption observed");
    }
    public void message(String line){
        if(state!=State.WAITING_ACK||line==null)return;
        if(line.startsWith("SPRAYONATOR! You sprayed ")){chatAck=true;return;}
        if(line.equals("This plot was sprayed with that item recently! Try again soon!")){
            finish(State.FAILED,"Plot already sprayed; request did not apply a new spray");return;
        }
        if(line.startsWith("This ability is on cooldown")||line.startsWith("You don't have any")
                ||line.equals("Invalid spray.")||line.toLowerCase(java.util.Locale.ROOT).contains("please slow down")
                ||line.equals("The server is shutting down!"))finish(State.FAILED,"Server rejected spray: "+line);
    }
    public void cancel(){if(active())finish(State.CANCELLED,"Manual stop");}
    private void finish(State next,String why){state=next;reason=why;original=null;originalSettings=null;chatAck=false;actions.release();}
    public boolean active(){return state==State.SELECT_TOOL||state==State.READY||state==State.WAITING_ACK;}
    public State state(){return state;}
    public String status(){return state+": "+reason;}
}
