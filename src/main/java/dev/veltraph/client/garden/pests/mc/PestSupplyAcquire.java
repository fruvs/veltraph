package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.garden.visitor.VisitorBazaarFlow;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.mc.McServerCommandService;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** Source sacks-first/Bazaar custom amount flow. Exact component identity, quantity, chat cost and purse debit. */
final class PestSupplyAcquire {
    enum State { IDLE, SACKS, BAZAAR, COMPLETE, FAILED }
    private final GardenActionModule m;private final VisitorBazaarFlow flow=new VisitorBazaarFlow();private State state=State.IDLE;
    private PestBindings.Supply spec;private int before;private long max,unit;private BigDecimal balanceBefore,confirmedCost,actualCost;
    private boolean sacksRejected;private String reason="Idle";private long sacksDeadline,next;
    PestSupplyAcquire(GardenActionModule m){this.m=m;}
    boolean begin(PestBindings.Supply spec,boolean sacks,long max,long unit){
        this.spec=spec;this.max=max;this.unit=unit;confirmedCost=actualCost=null;sacksRejected=false;
        if(max<1||unit<1||!spec.item().name().matches("[\\p{L}\\p{N} '\\-]+")){fail("Explicit positive total/unit purchase caps and plain product required");return false;}
        before=PestItems.count(m.client,spec.item(),false);balanceBefore=m.balance(spec.balancePrefix()).orElse(null);
        if(balanceBefore==null||balanceBefore.signum()<0||m.client.gui.screen()!=null){fail("Closed menus and readable configured purse required before acquisition");return false;}
        if(sacks){state=State.SACKS;sacksDeadline=m.now()+2500;return command("gfs "+spec.item().name()+" "+spec.quantity());}
        return bazaar();
    }
    private boolean command(String command){if(!new McServerCommandService(m.client).sendCommand(command)){fail("Supply command unavailable");return false;}return true;}
    private boolean bazaar(){state=State.BAZAAR;flow.start(spec.item().name(),spec.quantity(),before,true,max,m.now(),15000);next=m.now()+250;return command("bz "+spec.item().name());}
    void tick(){
        if(state==State.COMPLETE||state==State.FAILED)return;int have=PestItems.count(m.client,spec.item(),false);var currentBalance=m.balance(spec.balancePrefix()).orElse(null);
        if(currentBalance==null){fail("Purse became unreadable");return;}
        if(state==State.SACKS){
            if(have==before+spec.quantity()&&currentBalance.compareTo(balanceBefore)==0){state=State.COMPLETE;reason="Exact sacks supply increase observed; zero purse debit";return;}
            if(have!=before||currentBalance.compareTo(balanceBefore)!=0){fail("Sacks quantity or purse changed unexpectedly");return;}
            if(sacksRejected){bazaar();return;}if(m.now()>=sacksDeadline)fail("Sacks acknowledgment withheld; stopped before duplicate acquisition");return;
        }
        if(m.now()<next)return;
        var menu=m.menu();if(!m.client.player.containerMenu.getCarried().isEmpty()){fail("Cursor occupied during supply purchase");return;}
        if(m.client.gui.screen()!=null&&menu==null&&!(m.client.gui.screen() instanceof AbstractSignEditScreen)){fail("Unexpected supply screen");return;}
        if(flow.state()!=VisitorBazaarFlow.State.ACK&&(currentBalance.compareTo(balanceBefore)!=0||have!=before)){fail("Purse changed before guarded purchase");return;}
        if(menu!=null&&(menu.title().equals(spec.item().name())||menu.title().equals("Confirm Instant Buy")))m.own(menu);
        var action=flow.tick(menu,m.client.gui.screen() instanceof AbstractSignEditScreen,have,m.now());
        if(action.kind()==VisitorBazaarFlow.Kind.CLICK){
            if(flow.state()==VisitorBazaarFlow.State.ACK){
                var q=flow.quote();confirmedCost=PestBindings.amount(action.slot().lore(),"Price: ","Coins").orElse(null);
                if(q==null||q.unitCeiling()>unit||confirmedCost==null||confirmedCost.compareTo(BigDecimal.valueOf(max))>0||balanceBefore.compareTo(confirmedCost)<0){fail("Supply quantity/unit/total quote exceeds guard or purse");return;}
            }
            m.own(action.menu());
            if(!Objects.equals(m.menus.read(),action.menu())||!m.menus.click(action.menu(),action.slot().id())){fail("Supply menu replaced before request");return;}
        }else if(action.kind()==VisitorBazaarFlow.Kind.SIGN){if(!submit(action.text())){fail("Amount sign unavailable");return;}}
        else if(action.kind()==VisitorBazaarFlow.Kind.CLOSE)m.closeOwned();
        if(action.kind()!=VisitorBazaarFlow.Kind.NONE)next=m.now()+250;
        if(flow.state()==VisitorBazaarFlow.State.FAILED){fail(flow.status());return;}
        if(flow.state()==VisitorBazaarFlow.State.COMPLETE){
            if(actualCost==null||confirmedCost==null||actualCost.compareTo(confirmedCost)>0||currentBalance.compareTo(balanceBefore.subtract(actualCost))!=0
                    ||have!=before+spec.quantity()){fail("Purchase outcome requires exact material increase and actual purse debit");return;}
            state=State.COMPLETE;reason="Supply product, exact quantity, server cost and purse debit verified";
        }
    }
    private boolean submit(String text){if(!(m.client.gui.screen() instanceof AbstractSignEditScreen sign)||!text.matches("[0-9]{1,10}"))return false;
        if(!sign.keyPressed(new KeyEvent(0,1073741898,0))||!sign.keyPressed(new KeyEvent(0,1073741901,1))||!sign.keyPressed(new KeyEvent(0,8,0)))return false;
        for(int cp:text.codePoints().toArray())if(!sign.charTyped(new CharacterEvent(cp)))return false;sign.onClose();return true;}
    void message(String line){
        if(state==State.SACKS&&!spec.sacksRejectAck().isBlank()&&line.equals(spec.sacksRejectAck()))sacksRejected=true;
        if(state==State.BAZAAR){flow.message(line);if(flow.state()==VisitorBazaarFlow.State.ACK){
            var match=Pattern.compile("^\\[Bazaar] Bought ([0-9]+(?:,[0-9]{3})*)x (.+) for ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?) coins!$",Pattern.CASE_INSENSITIVE).matcher(line);
            if(match.matches()&&Integer.parseInt(match.group(1).replace(",",""))==spec.quantity()&&match.group(2).equals(spec.item().name()))actualCost=new BigDecimal(match.group(3).replace(",",""));}
        }
    }
    boolean allows(String title){return state==State.BAZAAR&&(m.client.gui.screen() instanceof AbstractSignEditScreen||title.toLowerCase(java.util.Locale.ROOT).contains("bazaar")||title.equals(spec.item().name())||title.contains("Instant Buy")||title.equals("How many do you want?"));}
    void fail(String why){state=State.FAILED;reason=why;}
    State state(){return state;}String status(){return state+": "+reason;}
}
