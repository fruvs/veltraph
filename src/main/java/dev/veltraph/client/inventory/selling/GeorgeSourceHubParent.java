package dev.veltraph.client.inventory.selling;

import java.util.*;

/** Source Hub command → finite NPC batch → Garden command → captured physical return, under one Root lease. */
public final class GeorgeSourceHubParent {
    public enum State { IDLE, HUB_WARP, NPC_BATCH, GARDEN_WARP, RESTORE, COMPLETE, FAILED, CANCELLED }
    public interface Port {
        GeorgeWarpPermit.Observation read();boolean valid();boolean closed();boolean send(String command);
        /** Root validates typed arrival then replaces only the exact lease's admitted native references. */
        boolean commitWarp(GeorgeWarpPermit permit,GeorgeWarpPermit.Observation arrival);
        boolean startNpcBatch();FarmAutoSellRuntime.Progress npcBatch();void cancelBatch();
        boolean beginPhysicalReturn();FarmAutoSellRuntime.Progress physicalReturn();
    }
    private final Port port;private State state=State.IDLE;private GeorgeSourceHubProfile profile;private GeorgeWarpPermit permit;
    private GeorgeWarpPermit.Destination garden;private boolean receipt;private long deadline,last;private String reason="Idle";
    public GeorgeSourceHubParent(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(GeorgeSourceHubProfile profile,GeorgeWarpPermit.Destination garden,long tick,int sessionTimeout){
        if(active())throw new IllegalStateException("George Hub parent already active");if(garden.area()!=dev.veltraph.client.game.GameArea.GARDEN||sessionTimeout<1||sessionTimeout>24000)throw new IllegalArgumentException("Captured Garden return and cumulative deadline required");
        this.profile=profile;this.garden=garden;last=tick;deadline=Math.addExact(tick,sessionTimeout);if(!port.valid()||!port.closed())return fail("Committed closed source Hub owner required");return issue(profile.hubCommand(),profile.hub(),State.HUB_WARP);
    }
    private boolean issue(String command,GeorgeWarpPermit.Destination destination,State next){receipt=false;permit=GeorgeWarpPermit.issue(port.read(),destination,profile.timeoutTicks());state=next;if(!port.send(command))return fail("Source warp dispatch refused or uncertain; no retry");reason="One source warp; waiting for exact command receipt and native landing";return true;}
    public void message(String raw){String line=dev.veltraph.client.gui.MenuSnapshot.clean(raw);if(state!=State.HUB_WARP&&state!=State.GARDEN_WARP)return;if(profile.rejections().contains(line)){fail("Source warp rejected");return;}if(line.equals(state==State.HUB_WARP?profile.hubReceipt():profile.returnReceipt()))receipt=true;}
    public void tick(long tick){
        if(!active())return;if(tick<last||tick>=deadline||!port.valid()){fail("Source Hub owner/contract/cumulative deadline changed");return;}last=tick;
        switch(state){
            case HUB_WARP,GARDEN_WARP->{var observed=port.read();permit.observe(observed);if(permit.state()==GeorgeWarpPermit.State.CANCELLED){fail("Unadmitted source native warp or timeout");return;}
                if(receipt&&permit.state()==GeorgeWarpPermit.State.OBSERVED){var destination=permit.commit(observed);if(destination.isEmpty()||!port.commitWarp(permit,destination.orElseThrow())){fail("Root exact native world admission refused");return;}
                    if(state==State.HUB_WARP){if(!port.startNpcBatch()){fail("Source NPC batch refused");return;}state=State.NPC_BATCH;reason="Same parent owns frozen NPC pet queue";}
                    else{if(!port.beginPhysicalReturn()){fail("Captured physical Garden return refused");return;}state=State.RESTORE;reason="Waiting for actual saved position/camera/gear restoration";}}
            }
            case NPC_BATCH->{var p=port.npcBatch();if(p==FarmAutoSellRuntime.Progress.FAILED)fail("Source NPC batch failed; preserve prior receipts");else if(p==FarmAutoSellRuntime.Progress.COMPLETE){if(!port.closed())fail("Source NPC cleanup not closed");else issue(profile.returnCommand(),garden,State.GARDEN_WARP);}}
            case RESTORE->{var p=port.physicalReturn();if(p==FarmAutoSellRuntime.Progress.FAILED)fail("Captured physical return failed; no resume");else if(p==FarmAutoSellRuntime.Progress.COMPLETE){state=State.COMPLETE;reason="Exact source Hub batch and Garden physical return acknowledged";}}
            default->{}
        }
    }
    /** Root may cache packet-applied signal before its general unexpected-teleport observation. */
    public boolean observeNativeWarp(){return permit!=null&&(state==State.HUB_WARP||state==State.GARDEN_WARP)&&permit.observe(port.read());}
    public Optional<GeorgeWarpPermit> pendingWarpPermit(){return permit!=null&&(state==State.HUB_WARP||state==State.GARDEN_WARP)?Optional.of(permit):Optional.empty();}
    public Optional<GeorgeWarpPermit.Pending> cachedPendingNativeWarp(){return permit!=null&&(state==State.HUB_WARP||state==State.GARDEN_WARP)?permit.cachedPendingNativeWarp():Optional.empty();}
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 p,long tick){return permit!=null&&permit.consumeExpectedTeleport(p,tick);}
    private boolean fail(String why){reason=why;state=State.FAILED;if(permit!=null)permit.cancel();port.cancelBatch();return false;}
    public void cancel(){if(!active())return;state=State.CANCELLED;reason="Source Hub parent cancelled; no queued sale, retry or resume";if(permit!=null)permit.cancel();port.cancelBatch();}
    public boolean active(){return state==State.HUB_WARP||state==State.NPC_BATCH||state==State.GARDEN_WARP||state==State.RESTORE;}public State state(){return state;}public String reason(){return reason;}
}
