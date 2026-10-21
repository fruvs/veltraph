package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;

/** Root admission/evaluation seam; only the exact issued same-connection parent may rebind. */
public final class InstalledGhostWarpLifecycle {
    public enum Decision { INACTIVE, WAIT, COMMIT, STOP }
    public record Observation(Object player,Object world,Object connection,UUID playerId,boolean alive,boolean attached,
            boolean loaded,boolean closedMenu,boolean permittedLoadingScreen,GameArea area,String dimension,Vec3 feet,
            long receiptSerial,Vec3 received,boolean higherPriority,long now) {}
    private Object owner;private GhostWarpPermit permit;private boolean replacementSeen;private GameArea initialArea;
    public boolean capture(Object candidate,Object inputOwner,int controllers,boolean enabled,GhostWarpPermit issued,
            Object oldPlayer,Object oldWorld,Object oldConnection,UUID oldPlayerId,GameArea oldArea,long now){
        if(owner!=null)return owner==candidate&&permit==issued;
        if(candidate==null||inputOwner!=candidate||controllers!=1||!enabled||issued==null||!issued.live(now)
                ||!issued.previousContext(oldPlayer,oldWorld,oldConnection)||!issued.playerId().equals(oldPlayerId))return false;
        owner=candidate;permit=issued;initialArea=oldArea;return true;
    }
    public Decision evaluate(Object candidate,Object inputOwner,int controllers,boolean enabled,GhostWarpPermit active,Observation o){
        return evaluate(candidate,inputOwner,controllers,enabled,active,active==null?null:active.attempt(),o);
    }
    /** Failed source dispatches may wait with their exact newer same-activation attempt, never old receipt authority. */
    public Decision evaluate(Object candidate,Object inputOwner,int controllers,boolean enabled,GhostWarpPermit active,GhostTransportLoop.Attempt currentAttempt,Observation o){
        if(owner==null)return Decision.INACTIVE;
        if(owner!=candidate||owner!=inputOwner||controllers!=1||!enabled||o==null||!permit.live(o.now())
                ||o.connection()!=permit.connection()||o.higherPriority()||o.player()!=null&&(!o.alive()||!permit.playerId().equals(o.playerId()))
                ||!o.closedMenu()&&!o.permittedLoadingScreen())return Decision.STOP;
        if(active!=permit){
            if(currentAttempt==null||currentAttempt.activation()!=permit.activation()||currentAttempt.ordinal()<=permit.attemptOrdinal()
                    ||currentAttempt.issuedAt()<=permit.issuedAt()||currentAttempt.deadline()!=permit.expiresAt()||o.now()<currentAttempt.issuedAt())return Decision.STOP;
            if(active==null){
                boolean replaced=o.player()!=null&&o.player()!=permit.previousPlayer()||o.world()!=null&&o.world()!=permit.previousWorld();
                if(o.loaded()&&o.attached()&&o.area()!=null&&o.area()!=GameArea.UNKNOWN
                        &&(replaced&&(o.area()!=GameArea.DWARVEN_MINES||!permit.dimension().equals(o.dimension()))||o.area()!=initialArea&&o.area()!=GameArea.DWARVEN_MINES))return Decision.STOP;
                if(o.receiptSerial()>permit.receiptSerial()&&o.received()!=null&&o.received().distanceTo(permit.profile().anchor())>=1)return Decision.STOP;
                return Decision.WAIT; // No arrival authority from the retired permit, including a matching late packet.
            }
            if(active.attempt()!=currentAttempt||active.activation()!=permit.activation()||active.connection()!=permit.connection()
                    ||active.previousPlayer()!=permit.previousPlayer()||active.previousWorld()!=permit.previousWorld()
                    ||!active.playerId().equals(permit.playerId())||active.profile()!=permit.profile()||!active.dimension().equals(permit.dimension())
                    ||active.receiptSerial()<permit.receiptSerial()||!active.live(o.now()))return Decision.STOP;
            permit=active;
        }
        if(currentAttempt!=permit.attempt())return Decision.STOP;
        replacementSeen|=o.player()!=null&&o.player()!=permit.previousPlayer()||o.world()!=null&&o.world()!=permit.previousWorld();
        if(o.player()==null||o.world()==null||!o.attached()||!o.loaded()||!o.closedMenu()||o.area()==null||o.area()==GameArea.UNKNOWN)return Decision.WAIT;
        if(permit.arrival(o.player(),o.world(),o.connection(),o.playerId(),o.alive(),o.attached(),o.loaded(),o.area(),o.dimension(),o.feet(),o.receiptSerial(),o.received(),o.now()))return Decision.COMMIT;
        if(replacementSeen&&(o.area()!=GameArea.DWARVEN_MINES||!permit.dimension().equals(o.dimension()))||o.area()!=initialArea&&o.area()!=GameArea.DWARVEN_MINES)return Decision.STOP;
        // A loaded destination can precede its position packet; only a fresh wrong receipt rejects.
        if(o.receiptSerial()>permit.receiptSerial()&&o.received()!=null&&o.received().distanceTo(permit.profile().anchor())>=1)return Decision.STOP;
        return Decision.WAIT;
    }
    public boolean denial(Object candidate,GhostWarpPermit active,String line,long now){return owns(candidate)&&active==permit&&permit.live(now)&&line!=null&&line.replaceAll("§.","").strip().equalsIgnoreCase(permit.profile().denial());}
    public GhostWarpPermit permit(){return permit;}public boolean active(){return owner!=null;}public boolean owns(Object candidate){return owner!=null&&owner==candidate;}
    public void clear(){owner=null;permit=null;replacementSeen=false;initialArea=null;}
}
