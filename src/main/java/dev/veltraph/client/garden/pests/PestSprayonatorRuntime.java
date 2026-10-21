package dev.veltraph.client.garden.pests;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Complete source spray parent: selection receipt, supply, selected plots, one use each, farm return. */
public final class PestSprayonatorRuntime {
    public enum State { IDLE, SELECT_TOOL, OPEN_MENU, ESCAPE_PEST_TRAPS, SELECT_MATERIAL, WAIT_SELECTION, CLOSE_MENU, SUPPLY, TRAVEL, READY, WAIT_ACK, RESTORE, RETURN, COMPLETE, FAILED, CANCELLED }
    public enum Progress { ACTIVE, COMPLETE, FAILED }
    public record Settings(PestSprayonatorBindings bindings,int units,long unitValue,long maxValue,long timeoutMs,long ackTimeoutMs) {
        public Settings {
            Objects.requireNonNull(bindings);
            if(!Set.of(1,3,5).contains(units)||unitValue<1||maxValue<1||timeoutMs<1||ackTimeoutMs<1||ackTimeoutMs>timeoutMs
                    ||Math.multiplyExact(Math.multiplyExact(unitValue,units),bindings.selected().size())>maxValue)
                throw new IllegalArgumentException("Selected-plot aggregate spray value exceeds positive bounded budget");
        }
        public int required(){return Math.multiplyExact(units,bindings.selected().size());}
    }
    public record Observation(boolean valid,boolean screenFree,boolean cursorEmpty,boolean toolReady,String toolIdentity,
            String materialIdentity,int materialCount,boolean selected,MenuSnapshot menu,boolean inSelectedPlot,boolean trapNearby) {}
    public record Receipt(int plotId,boolean applied,int before,int after,String serverLine) {}
    public interface Actions {
        boolean valid();boolean select();boolean escape();Progress escaped();boolean openMenu();boolean click(MenuSnapshot menu,int slot);boolean close(MenuSnapshot menu);
        /** Default-deny: only an issued native click with exact captured custody may wait. */
        default boolean waitingForOwnSelectionPrediction(Observation observed,MenuSnapshot issuedMenu,int issuedSlot){return false;}
        default void selectionAcknowledged() {}
        boolean acquire(int deficit);Progress acquired();boolean travel(PestSprayonatorBindings.Plot plot);Progress travelled(PestSprayonatorBindings.Plot plot);
        boolean use(Observation expected);boolean restore();Progress restored();boolean beginReturn();Progress returned();boolean resume();void cancel();
    }
    private final Actions actions;private Settings settings;private State state=State.IDLE;private long started,last,phaseStarted;
    private String toolIdentity,materialIdentity,ack,reason="Idle";private int initialSupply,remainingSupply,index,requestRevision,requestMenuId;
    private MenuSnapshot ownedMenu,selectionRequest;private int selectionRequestSlot=-1;private final List<Receipt> receipts=new ArrayList<>();
    public PestSprayonatorRuntime(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public boolean start(Settings requested,Observation o,long now){
        if(active())throw new IllegalStateException("Sprayonator parent already active");
        settings=Objects.requireNonNull(requested);receipts.clear();index=0;ack=null;ownedMenu=selectionRequest=null;selectionRequestSlot=-1;
        if(!healthy(o)||!o.screenFree()||!actions.valid())return fail("Exact farm lease, closed cursor and identified spray resources required");
        toolIdentity=o.toolIdentity();materialIdentity=o.materialIdentity();initialSupply=remainingSupply=o.materialCount();started=last=phaseStarted=now;
        state=State.SELECT_TOOL;reason="Source selected plots and aggregate value checked";return true;
    }
    private static boolean healthy(Observation o){return o!=null&&o.valid()&&o.cursorEmpty()&&o.materialCount()>=0&&o.toolIdentity()!=null&&!o.toolIdentity().isBlank()&&o.materialIdentity()!=null&&!o.materialIdentity().isBlank();}
    private boolean same(Observation o){return healthy(o)&&Objects.equals(toolIdentity,o.toolIdentity())&&Objects.equals(materialIdentity,o.materialIdentity());}
    private void phase(State next,long now){state=next;phaseStarted=now;}
    public void tick(Settings current,Observation o,long now){
        if(!active())return;
        if(now<last||now-started>=settings.timeoutMs()) {fail("Sprayonator parent deadline expired; no retry");return;}last=now;
        if(!settings.equals(current)||!actions.valid()){cancel("Sprayonator lease, tool/material identity, cursor or configuration changed");return;}
        // PICKUP predicts the exact owned menu button on the cursor before server correction.
        // This permits only observation of that frozen intent, never treating prediction as ACK.
        if(state==State.WAIT_SELECTION&&o!=null&&!o.cursorEmpty()){
            boolean sameExceptCursor=o.valid()&&o.materialCount()>=0&&o.toolIdentity()!=null&&!o.toolIdentity().isBlank()
                    &&o.materialIdentity()!=null&&!o.materialIdentity().isBlank()&&Objects.equals(toolIdentity,o.toolIdentity())&&Objects.equals(materialIdentity,o.materialIdentity())
                    &&o.materialCount()==remainingSupply&&o.menu()!=null&&o.menu().id()==requestMenuId&&o.menu().revision()==requestRevision;
            boolean own=false;try{own=sameExceptCursor&&selectionRequest!=null&&selectionRequestSlot>=0
                    &&actions.waitingForOwnSelectionPrediction(o,selectionRequest,selectionRequestSlot);}catch(RuntimeException unknown){own=false;}
            if(!own){cancel("Unrecognized cursor or changed custody during spray selection");return;}
            if(now-phaseStarted>=settings.ackTimeoutMs())fail("Changed server menu and tool lore selection receipt withheld");
            return;
        }
        if(!same(o)){cancel("Sprayonator lease, tool/material identity, cursor or configuration changed");return;}
        boolean menuPhase=state==State.OPEN_MENU||state==State.SELECT_MATERIAL||state==State.WAIT_SELECTION||state==State.CLOSE_MENU;
        if(state!=State.SUPPLY&&!menuPhase&&!o.screenFree()){cancel("Unowned screen interrupted spray workflow");return;}
        if(state!=State.SUPPLY&&state!=State.WAIT_ACK&&o.materialCount()!=remainingSupply){cancel("Spray supply changed outside its acknowledged action");return;}
        try {
            switch(state){
                case SELECT_TOOL->{if(!actions.select()){fail("Sprayonator relocation refused");return;}phase(State.OPEN_MENU,now);}
                case OPEN_MENU->{
                    if(!o.toolReady())return;
                    if(o.trapNearby()){phase(State.ESCAPE_PEST_TRAPS,now);if(!actions.escape())fail("Source trap escape contract or loaded path refused");return;}
                    if(o.selected()&&o.screenFree()){supplyOrTravel(o,now);return;}
                    if(o.menu()==null){if(!actions.openMenu()){fail("Spray menu opening refused");return;}phase(State.SELECT_MATERIAL,now);return;}
                    acceptMenu(o.menu());phase(State.SELECT_MATERIAL,now);
                }
                case ESCAPE_PEST_TRAPS->{
                    if(!o.toolReady()){fail("Spray tool changed during source trap escape");return;}var p=actions.escaped();
                    if(p==Progress.FAILED)fail("Source trap escape path or landing failed");else if(p==Progress.COMPLETE){
                        if(o.trapNearby()){fail("Verified escape landing still contains a nearby trap");return;}phase(State.OPEN_MENU,now);}
                }
                case SELECT_MATERIAL->{
                    if(o.menu()==null){if(now-phaseStarted>=settings.ackTimeoutMs())fail("Spray menu did not arrive");return;}
                    acceptMenu(o.menu());var matches=o.menu().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0
                            &&s.name().equals(settings.bindings().material().name())).toList();
                    if(matches.size()!=1){fail("Spray material menu target absent or ambiguous");return;}
                    var slot=matches.getFirst();
                    if(slot.lore().contains(settings.bindings().selectedMenuLore())&&o.selected()){phase(State.CLOSE_MENU,now);return;}
                    if(!slot.lore().contains(settings.bindings().availableLore())||slot.lore().contains(settings.bindings().selectedMenuLore())){fail("Spray material button not available");return;}
                    requestRevision=o.menu().revision();requestMenuId=o.menu().id();selectionRequest=o.menu();selectionRequestSlot=slot.id();phase(State.WAIT_SELECTION,now);
                    if(!actions.click(o.menu(),slot.id()))fail("Fresh exact spray menu click refused");
                }
                case WAIT_SELECTION->{
                    acceptMenu(o.menu());
                    if(o.menu()==null||o.menu().id()!=requestMenuId){fail("Selection menu closed or replaced before receipt");return;}
                    var slots=o.menu().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(settings.bindings().material().name())).toList();
                    if(slots.size()!=1){fail("Selection receipt became ambiguous");return;}
                    if(o.menu().revision()!=requestRevision&&slots.getFirst().lore().contains(settings.bindings().selectedMenuLore())
                            &&!slots.getFirst().lore().contains(settings.bindings().availableLore())&&o.selected()){
                        actions.selectionAcknowledged();selectionRequest=null;selectionRequestSlot=-1;phase(State.CLOSE_MENU,now);}
                    else if(now-phaseStarted>=settings.ackTimeoutMs())fail("Changed server menu and tool lore selection receipt withheld");
                }
                case CLOSE_MENU->{
                    if(!o.selected()){fail("Tool selection lore changed before closing");return;}
                    if(o.menu()!=null){acceptMenu(o.menu());if(!actions.close(o.menu()))fail("Owned spray menu close refused");return;}
                    if(o.screenFree()){ownedMenu=null;supplyOrTravel(o,now);}
                }
                case SUPPLY->{var p=actions.acquired();if(p==Progress.FAILED){fail("Guarded source spray supply acquisition failed");return;}
                    if(p==Progress.COMPLETE){int expected=Math.max(initialSupply,settings.required());if(!o.screenFree()||o.materialCount()!=expected||!o.selected()){
                        fail("Spray acquisition receipt/resource delta does not match exact deficit");return;}remainingSupply=o.materialCount();nextPlot(now);}}
                case TRAVEL->{if(!o.selected()||!o.toolReady()){fail("Selected spray tool changed during plot travel");return;}
                    var p=actions.travelled(plot());if(p==Progress.FAILED)fail("Selected plot arrival rejected");else if(p==Progress.COMPLETE){
                        if(!o.inSelectedPlot()){fail("Native arrival is outside selected source plot");return;}phase(State.READY,now);}}
                case READY->{if(!o.selected()||!o.toolReady()||!o.inSelectedPlot()||o.materialCount()<settings.units()){fail("Selected plot/tool/supply use guard failed");return;}
                    ack=null;phase(State.WAIT_ACK,now);if(!actions.use(o))fail("One spray use refused");}
                case WAIT_ACK->{if(!o.selected()||!o.toolReady()||!o.inSelectedPlot()){cancel("Spray tool or selected plot changed before server acknowledgment");return;}
                    int expected=remainingSupply-settings.units();if(o.materialCount()<expected||o.materialCount()>remainingSupply){fail("Unexpected spray consumption or supply increase");return;}
                    if(ack!=null){boolean applied=ack.equals(plot().successAck());if(o.materialCount()==(applied?expected:remainingSupply)){
                        receipts.add(new Receipt(plot().id(),applied,remainingSupply,o.materialCount(),ack));remainingSupply=o.materialCount();index++;ack=null;nextPlot(now);return;}}
                    if(now-phaseStarted>=settings.ackTimeoutMs())fail("Exact selected-plot success and material consumption withheld; no retry");}
                case RESTORE->{var p=actions.restored();if(p==Progress.FAILED)fail("Spray inventory/tool restoration failed");else if(p==Progress.COMPLETE){
                    if(!actions.beginReturn())fail("Exact original farm return refused");else phase(State.RETURN,now);}}
                case RETURN->{var p=actions.returned();if(p==Progress.FAILED)fail("Exact original farm return failed");else if(p==Progress.COMPLETE){
                    if(!actions.resume())fail("Original farm engine resume refused");else {state=State.COMPLETE;reason="Selected plots acknowledged; original farm tool/gear/pose and engine resumed";}}}
                default->{}
            }
        }catch(RuntimeException failure){fail("Sprayonator guarded action failed: "+failure.getMessage());}
    }
    private void acceptMenu(MenuSnapshot m){if(m==null)return;if(!m.title().equals(settings.bindings().menuTitle())||(ownedMenu!=null&&m.id()!=ownedMenu.id()))throw new IllegalStateException("Unowned spray menu");ownedMenu=m;}
    private void supplyOrTravel(Observation o,long now){
        if(!o.selected()){fail("Selected material tool lore unavailable");return;}
        int deficit=Math.max(0,settings.required()-remainingSupply);
        if(deficit>0){phase(State.SUPPLY,now);if(!actions.acquire(deficit))fail("Exact guarded spray deficit acquisition refused");}
        else nextPlot(now);
    }
    private void nextPlot(long now){
        if(index>=settings.bindings().selected().size()){phase(State.RESTORE,now);if(!actions.restore())fail("Sprayonator inventory restoration refused");return;}
        phase(State.TRAVEL,now);if(!actions.travel(plot()))fail("Selected plot travel refused");
    }
    public void message(String line){
        if(!active()||line==null)return;
        if(line.equals("The server is shutting down!")||line.toLowerCase(Locale.ROOT).contains("please slow down")){fail("Server rejected spray context: "+line);return;}
        if(state!=State.WAIT_ACK)return;
        if(line.equals(plot().successAck())||line.equals("This plot was sprayed with that item recently! Try again soon!")){if(ack==null)ack=line;return;}
        if(line.startsWith("This ability is on cooldown")||line.startsWith("You don't have any")||line.equals("Invalid spray."))fail("Server rejected one spray; no retry: "+line);
    }
    private boolean fail(String why){state=State.FAILED;reason=why;actions.cancel();return false;}
    public void cancel(){if(active())cancel("Manual spray parent stop; farm remains stopped");}
    private void cancel(String why){state=State.CANCELLED;reason=why;actions.cancel();}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED&&state!=State.CANCELLED;}
    public boolean allowsMenu(String title){return active()&&(state==State.OPEN_MENU||state==State.SELECT_MATERIAL||state==State.WAIT_SELECTION||state==State.CLOSE_MENU)&&settings.bindings().menuTitle().equals(MenuSnapshot.clean(title));}
    public PestSprayonatorBindings.Plot plot(){var selected=settings==null?List.<PestSprayonatorBindings.Plot>of():settings.bindings().selected();return index<selected.size()?selected.get(index):null;}
    public List<Receipt> receipts(){return List.copyOf(receipts);}public State state(){return state;}public String status(){return state+": "+reason;}
}
