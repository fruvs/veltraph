package dev.veltraph.client.skills;

import dev.veltraph.client.navigation.LocalRoute;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** One per-soul owner. No action retries; a failed return never removes a received claim. */
public final class FairySoulTraversal {
    public enum Phase { OUTBOUND, COLLECTING, RETURNING, COMPLETE, FAILED, CANCELLED }
    public enum Motion { ACTIVE, ARRIVED, FAILED }
    public interface Port {
        boolean current();
        boolean start(FairySoulRouteCompiler.Hop hop);
        Motion tick();
        boolean useDispatched();
        long receiptSerial();
        Vec3 receiptPosition();
        Vec3 position();
        void stop();
        default String diagnostics(){return "";}
    }
    private final Port port;private final FairySoulRouteCompiler.Plan plan;
    private Phase phase=Phase.OUTBOUND;private int index;private boolean started,used,received,requested,acknowledged;private long began,serial;private String reason="Outbound";
    public FairySoulTraversal(Port port,FairySoulRouteCompiler.Plan plan){this.port=Objects.requireNonNull(port);this.plan=Objects.requireNonNull(plan);}
    public void tick(long now){
        if(terminal())return;if(!port.current()){fail("Fairy native scope or custody changed");return;}
        if(phase==Phase.COLLECTING)return;
        var hops=phase==Phase.OUTBOUND?plan.outbound():plan.returning();
        if(index==hops.size()){port.stop();started=false;index=0;phase=phase==Phase.OUTBOUND?Phase.COLLECTING:Phase.COMPLETE;return;}
        var hop=hops.get(index);if(!started){serial=port.receiptSerial();began=now;used=received=false;if(!port.start(hop)){fail("Fairy hop could not start: "+port.diagnostics());return;}started=true;}
        if(now<began||now-began>hop.timeoutMillis()){fail("Fairy hop exceeded its source travel bound");return;}
        long beforeTickSerial=port.receiptSerial();var motion=port.tick();boolean ether=hop.node().kind()==LocalRoute.Kind.ETHERWARP;
        if(ether){
            // The receipt observed before the actual dispatch is never eligible.
            if(!used){if(port.useDispatched()){used=true;serial=beforeTickSerial;}else serial=port.receiptSerial();}
            if(used&&port.receiptSerial()>serial&&port.receiptPosition()!=null){
                if(port.receiptPosition().distanceTo(hop.node().position())>=.9){fail("Fairy etherwarp received the wrong destination");return;}received=true;
            }
        }
        if(motion==Motion.FAILED){fail("Fairy owned travel failed: "+port.diagnostics());return;}
        if(motion==Motion.ARRIVED){
            if(ether&&(!used||!received))return;
            if(port.position()==null||port.position().distanceTo(hop.node().position())> (ether?.89:.35)){fail("Fairy hop has no current arrival pose");return;}
            port.stop();started=false;index++;
        }
    }
    public boolean interactionRequested(){if(phase!=Phase.COLLECTING||requested||!port.current())return false;requested=true;return true;}
    public boolean acknowledge(){if(phase!=Phase.COLLECTING||!requested||acknowledged||!port.current())return false;acknowledged=true;port.stop();phase=Phase.RETURNING;index=0;started=false;return true;}
    public void cancel(){if(terminal())return;port.stop();phase=Phase.CANCELLED;reason="Stopped";}
    private void fail(String why){port.stop();phase=Phase.FAILED;reason=why;}
    public boolean terminal(){return phase==Phase.COMPLETE||phase==Phase.FAILED||phase==Phase.CANCELLED;}
    public Phase phase(){return phase;}public int index(){return index;}public boolean acknowledged(){return acknowledged;}public String reason(){return reason;}
}
