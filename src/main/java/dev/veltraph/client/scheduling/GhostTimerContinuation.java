package dev.veltraph.client.scheduling;
import dev.veltraph.client.combat.ghost.*;
import java.util.UUID;
/** One Root-validated timer update; newer exact attempts preserve the original activation/pin/deadline. */
public final class GhostTimerContinuation {
    private final Object owner,pin;private final GhostWarpPermit origin;
    private GhostWarpPermit permit;private GhostTransportLoop.Attempt attempt;private long serialFloor;
    private boolean updateGranted,revoked;
    public GhostTimerContinuation(Object owner,Object pin,GhostWarpPermit permit){
        this.owner=java.util.Objects.requireNonNull(owner);this.pin=java.util.Objects.requireNonNull(pin);origin=java.util.Objects.requireNonNull(permit);
        this.permit=permit;attempt=permit.attempt();serialFloor=permit.receiptSerial();
    }
    private boolean context(Object owner,Object pin,Object connection,UUID id,boolean enabled,boolean metadata,long now){
        return !revoked&&this.owner==owner&&this.pin==pin&&origin.connection()==connection&&(id==null||origin.playerId().equals(id))&&enabled&&metadata&&origin.live(now);
    }
    private boolean accept(GhostWarpPermit next,GhostTransportLoop.Attempt current,long now){
        if(current==null)return false;
        if(current==attempt)return next==permit;
        if(current.activation()!=origin.activation()||current.ordinal()<=attempt.ordinal()||current.issuedAt()<=attempt.issuedAt()
                ||current.deadline()!=origin.expiresAt()||now<current.issuedAt()||now>current.deadline())return false;
        if(next!=null&&(next.attempt()!=current||next.previousPlayer()!=origin.previousPlayer()||next.previousWorld()!=origin.previousWorld()
                ||next.connection()!=origin.connection()||!next.playerId().equals(origin.playerId())||next.profile()!=origin.profile()
                ||!next.dimension().equals(origin.dimension())||next.expiresAt()!=origin.expiresAt()||next.receiptSerial()<serialFloor||!next.live(now)))return false;
        attempt=current;permit=next;if(next!=null)serialFloor=next.receiptSerial();return true;
    }
    public boolean grant(Object owner,Object pin,GhostWarpPermit permit,Object connection,UUID id,boolean enabled,boolean metadata,long now){return grant(owner,pin,permit,permit==null?null:permit.attempt(),connection,id,enabled,metadata,now);}
    public boolean grant(Object owner,Object pin,GhostWarpPermit next,GhostTransportLoop.Attempt current,Object connection,UUID id,boolean enabled,boolean metadata,long now){
        updateGranted=false;if(!context(owner,pin,connection,id,enabled,metadata,now)||!accept(next,current,now)){revoked=true;return false;}
        updateGranted=true;return true;
    }
    public boolean consume(Object owner,Object pin,GhostWarpPermit permit,Object connection,UUID id,boolean enabled,boolean metadata,long now){return consume(owner,pin,permit,permit==null?null:permit.attempt(),connection,id,enabled,metadata,now);}
    public boolean consume(Object owner,Object pin,GhostWarpPermit current,GhostTransportLoop.Attempt currentAttempt,Object connection,UUID id,boolean enabled,boolean metadata,long now){
        boolean granted=updateGranted;updateGranted=false;
        if(!granted||!context(owner,pin,connection,id,enabled,metadata,now)||currentAttempt!=attempt||current!=permit){revoked=true;return false;}return true;
    }
    public boolean owns(Object owner,Object pin,GhostWarpPermit permit){return !revoked&&this.owner==owner&&this.pin==pin&&permit!=null&&this.permit==permit;}
    public void revoke(){updateGranted=false;revoked=true;}
}
