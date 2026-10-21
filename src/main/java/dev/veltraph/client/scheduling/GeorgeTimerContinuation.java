package dev.veltraph.client.scheduling;
import dev.veltraph.client.inventory.selling.GeorgeWarpPermit;
import java.util.UUID;
/** One exact Root-authorized update of the original farm TIMER during its George command. */
public final class GeorgeTimerContinuation {
    private final Object owner,helper,token,pin;private final GeorgeWarpPermit permit;private boolean granted,revoked;
    public GeorgeTimerContinuation(Object owner,Object helper,Object token,Object pin,GeorgeWarpPermit permit){this.owner=java.util.Objects.requireNonNull(owner);this.helper=java.util.Objects.requireNonNull(helper);this.token=java.util.Objects.requireNonNull(token);this.pin=java.util.Objects.requireNonNull(pin);this.permit=java.util.Objects.requireNonNull(permit);}
    private boolean valid(Object owner,Object helper,Object token,Object pin,GeorgeWarpPermit permit,Object connection,UUID uuid,boolean metadata,long tick){var p=this.permit.pending();return !revoked&&this.owner==owner&&this.helper==helper&&this.token==token&&this.pin==pin&&this.permit==permit&&p.connection()==connection&&(uuid==null||p.uuid().equals(uuid))&&metadata&&tick<p.deadline()&&this.permit.state()!=GeorgeWarpPermit.State.CANCELLED;}
    public boolean grant(Object owner,Object helper,Object token,Object pin,GeorgeWarpPermit permit,Object connection,UUID uuid,boolean metadata,long tick){granted=false;if(!valid(owner,helper,token,pin,permit,connection,uuid,metadata,tick)){revoked=true;return false;}granted=true;return true;}
    public boolean consume(Object owner,Object helper,Object token,Object pin,GeorgeWarpPermit permit,Object connection,UUID uuid,boolean metadata,long tick){boolean once=granted;granted=false;if(!once||!valid(owner,helper,token,pin,permit,connection,uuid,metadata,tick)){revoked=true;return false;}return true;}
    public boolean owns(Object owner,Object helper,Object token,Object pin,GeorgeWarpPermit permit){return !revoked&&this.owner==owner&&this.helper==helper&&this.token==token&&this.pin==pin&&this.permit==permit;}
    public void revoke(){revoked=true;granted=false;}
}
