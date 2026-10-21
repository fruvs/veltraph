package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.LongSupplier;
/** Finite local Hub command transaction. A send or chat acknowledgement alone never completes it.
 * The issued ticket can survive native player/world replacement only on its original connection and
 * player UUID. New gameplay input waits for exact teleport plus physical Hub destination receipts. */
public final class HubWarpCycle {
    public enum State { IDLE, WAITING, ARRIVED, FAILED, CANCELLED }
    public record Contract(String command,String acceptedMessage,Set<String> rejectedMessages,String dimension,Vec3 destination,double radius,long timeoutMs) {
        public Contract {rejectedMessages=rejectedMessages==null?Set.of():Set.copyOf(rejectedMessages);}
        public boolean valid(){return command!=null&&!command.isBlank()&&!command.contains("\n")&&!command.contains("\r")
            &&acceptedMessage!=null&&(acceptedMessage.isBlank()||!rejectedMessages.contains(acceptedMessage))
            &&dimension!=null&&!dimension.isBlank()&&destination!=null&&finite(destination)
            &&Double.isFinite(radius)&&radius>0&&radius<=2&&timeoutMs>=1000&&timeoutMs<=60000;}
    }
    public record Observation(Object player,Object connection,Object world,String dimension,Vec3 position,boolean inHub,boolean healthy,UUID playerUuid,boolean bodyReady,long nativeTick) {
        public Observation(Object player,Object connection,Object world,String dimension,Vec3 position,boolean inHub,boolean healthy,UUID playerUuid,boolean bodyReady){this(player,connection,world,dimension,position,inHub,healthy,playerUuid,bodyReady,Long.MIN_VALUE);}
        public Observation(Object player,Object connection,Object world,String dimension,Vec3 position,boolean inHub,boolean healthy,UUID playerUuid){this(player,connection,world,dimension,position,inHub,healthy,playerUuid,healthy,Long.MIN_VALUE);}
        public Observation(Object player,Object connection,Object world,String dimension,Vec3 position,boolean inHub,boolean healthy){this(player,connection,world,dimension,position,inHub,healthy,null,healthy,Long.MIN_VALUE);}
    }
    public record TeleportReceipt(long serial,Vec3 position) {}
    /** Unforgeable local issued identity; callers can only obtain this cycle's live issued ticket. */
    public static final class IssuedTicket {private IssuedTicket(){}}
    private final LongSupplier retryCadence;
    private State state=State.IDLE;private Contract contract;private Observation owner;private long deadline,nextSend,cadence;
    private boolean acknowledged,teleport,expectedConsumed;private long receiptBaseline,acceptedSerial;private String reason="";private int sends,stableTicks;
    private IssuedTicket issued;private Object stablePlayer,stableWorld;private long lastReadyTick=Long.MIN_VALUE;
    public HubWarpCycle(){this(()->java.util.concurrent.ThreadLocalRandom.current().nextLong(1500,2000));}
    public HubWarpCycle(LongSupplier retryCadence){this.retryCadence=retryCadence;}
    public boolean start(Contract contract,Observation owner,long now){return start(contract,owner,now,0);}
    public boolean start(Contract contract,Observation owner,long now,long receiptBaseline){
        if(state==State.WAITING||contract==null||!contract.valid()||!validOwner(owner))return false;
        long cadence=retryCadence.getAsLong();if(cadence<1500||cadence>=2000)return false;
        this.cadence=cadence;this.contract=contract;this.owner=owner;deadline=now+contract.timeoutMs();nextSend=now+cadence;
        acknowledged=teleport=expectedConsumed=false;this.receiptBaseline=receiptBaseline;acceptedSerial=receiptBaseline;issued=null;sends=stableTicks=0;stablePlayer=stableWorld=null;lastReadyTick=Long.MIN_VALUE;reason="awaiting command, teleport and physical Hub destination receipts";state=State.WAITING;return true;
    }
    public void sent(boolean sent){if(state==State.WAITING){if(!sent)fail("Hub warp command was not sent");else {sends++;if(issued==null)issued=new IssuedTicket();}}}
    /** Source retries the same issued area command at the recovered cadence, at most six sends. */
    public Optional<String> nextCommand(Observation observed,long now){
        if(!waiting(observed,now)||issued==null||now<nextSend||!validOwner(observed))return Optional.empty();
        if(sends>=6){fail("Hub warp six-command retry limit");return Optional.empty();}
        nextSend=now+cadence;return Optional.of(contract.command());
    }
    public void feedback(Observation observed,String message,long now){
        if(!waiting(observed,now)||!validOwner(observed)||message==null||issued==null)return;
        if(contract.rejectedMessages().contains(message)){fail("Hub warp server rejection: "+message);return;}
        if(contract.acceptedMessage().equals(message))acknowledged=true;
    }
    /** Actual applied native packet receipt; independent of whether Hydra has enabled jump detection. */
    public boolean observeTeleport(Observation observed,TeleportReceipt receipt,long now){
        if(!waiting(observed,now)||!validOwner(observed)||issued==null||receipt==null||receipt.serial()<=receiptBaseline||receipt.position()==null)return false;
        if(!finite(receipt.position())||receipt.position().distanceTo(contract.destination())>contract.radius()){
            fail("Hub warp fresh native teleport outside configured destination");return false;
        }
        if(receipt.serial()<acceptedSerial)return false;
        teleport=true;acceptedSerial=receipt.serial();return true;
    }
    /** A one-use Hydra exemption for the same actual native packet; it never manufactures a receipt. */
    public boolean consumeExpectedTeleport(Observation observed,Vec3 position,TeleportReceipt receipt,long now){
        if(expectedConsumed||position==null||!finite(position)||contract==null||position.distanceTo(contract.destination())>contract.radius())return false;
        if(!observeTeleport(observed,receipt,now)||receipt.serial()!=acceptedSerial)return false;
        expectedConsumed=true;return true;
    }
    public void tick(Observation observed,long now){
        if(!waiting(observed,now))return;
        if(issued!=null&&(contract.acceptedMessage().isBlank()||acknowledged)&&teleport&&validOwner(observed)&&observed.inHub()&&observed.bodyReady()&&contract.dimension().equals(observed.dimension())
            &&observed.position().distanceTo(contract.destination())<=contract.radius()){
            if(stablePlayer!=observed.player()||stableWorld!=observed.world()){stablePlayer=observed.player();stableWorld=observed.world();stableTicks=0;lastReadyTick=Long.MIN_VALUE;}
            long sample=observed.nativeTick()==Long.MIN_VALUE?now:observed.nativeTick();
            if(sample==lastReadyTick)return;lastReadyTick=sample;
            if(++stableTicks>5){owner=observed;state=State.ARRIVED;reason="native Hub command, teleport and stable destination receipts accepted";}
        }else {stableTicks=0;stablePlayer=stableWorld=null;lastReadyTick=Long.MIN_VALUE;}
    }
    /** Holds only the command's existing lease; it does not grant gameplay input or bind a new owner. */
    public boolean permitsPendingReplacement(IssuedTicket ticket,Observation observed,long now){
        return ticket!=null&&ticket==issued&&state==State.WAITING&&waiting(observed,now);
    }
    public boolean bindsArrivedReplacement(Observation observed){return state==State.ARRIVED&&observed!=null&&observed.player()==owner.player()&&observed.world()==owner.world()&&observed.connection()==owner.connection()
        &&validOwner(observed)&&observed.bodyReady()&&observed.inHub()&&contract.dimension().equals(observed.dimension())
        &&observed.position().distanceTo(contract.destination())<=contract.radius()&&Objects.equals(owner.playerUuid(),observed.playerUuid());}
    public void cancel(){if(state==State.WAITING){state=State.CANCELLED;reason="Hub warp cancelled";}issued=null;}
    public void reset(){cancel();state=State.IDLE;owner=null;contract=null;acknowledged=teleport=expectedConsumed=false;reason="";stableTicks=sends=0;}
    private boolean waiting(Observation observed,long now){
        if(state!=State.WAITING)return false;
        if(now>deadline){fail("Hub warp receipt deadline expired");return false;}
        if(observed==null||observed.connection()!=owner.connection()){
            fail("Hub warp original connection changed");return false;
        }
        // An issued same-connection world replacement can temporarily remove the local entity/world.
        if(observed.player()==null||observed.world()==null){if(issued!=null&&owner.playerUuid()!=null&&observed.healthy())return true;fail("Hub warp native owner/context changed");return false;}
        if(owner.playerUuid()!=null&&!owner.playerUuid().equals(observed.playerUuid())){fail("Hub warp original player UUID changed");return false;}
        boolean original=observed.player()==owner.player()&&observed.world()==owner.world();
        if(!validOwner(observed)||!original&&(issued==null||owner.playerUuid()==null||!owner.playerUuid().equals(observed.playerUuid()))){
            fail("Hub warp native owner/context changed");return false;
        }
        return true;
    }
    private static boolean validOwner(Observation o){return o!=null&&o.player()!=null&&o.connection()!=null&&o.world()!=null&&o.position()!=null&&finite(o.position())&&o.healthy();}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private void fail(String reason){state=State.FAILED;this.reason=reason;issued=null;}
    public State state(){return state;}public String reason(){return reason;}
    public IssuedTicket issuedTicket(){return state==State.WAITING?issued:null;}
    public boolean hasIssuedWarp(){return issued!=null&&(state==State.WAITING||state==State.ARRIVED);}
    /** Read-only lifecycle probe; the normal motion observation owns state/cadence changes. */
    public boolean pendingContextMatches(Observation observed,long now){
        if(state!=State.WAITING||issued==null||now>deadline||observed==null||observed.connection()!=owner.connection())return false;
        if(observed.player()==null||observed.world()==null)return owner.playerUuid()!=null&&observed.healthy();
        return validOwner(observed)&&(owner.playerUuid()==null||owner.playerUuid().equals(observed.playerUuid()))&&(observed.player()==owner.player()&&observed.world()==owner.world()
            ||owner.playerUuid()!=null&&owner.playerUuid().equals(observed.playerUuid()));
    }
    /** Exact tracker provenance, equivalent to the shared installed-warp lifecycle predicate. */
    public boolean permitsLoadingScreen(Object currentConnection,boolean remoteScreen,Object listenerTracker,Object screenTracker,long now){
        return state==State.WAITING&&issued!=null&&now<=deadline&&owner.connection()!=null&&currentConnection==owner.connection()
            &&remoteScreen&&listenerTracker!=null&&listenerTracker==screenTracker;
    }
    public int commandSends(){return sends;}
    public Contract activeContract(){return contract;}
}
