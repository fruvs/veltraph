package dev.veltraph.client.scheduling;

import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Travel;
import java.util.Objects;
import java.util.UUID;

/** One exact Root-authorized update of an existing TIMER during the Bottle owner's issued travel. */
public final class BottleTimerContinuation {
    private final Object owner,pin;private final Travel travel;private boolean granted,revoked;private long lastNow=Long.MIN_VALUE;
    public BottleTimerContinuation(Object owner,Object pin,Travel travel){this.owner=Objects.requireNonNull(owner);this.pin=Objects.requireNonNull(pin);this.travel=Objects.requireNonNull(travel);}
    private boolean valid(Object owner,Object pin,Travel travel,Object connection,UUID uuid,boolean metadata,long now){return !revoked&&this.owner==owner&&this.pin==pin&&this.travel==travel&&travel.connection()==connection&&(uuid==null||travel.uuid().equals(uuid))&&metadata&&now>=lastNow&&now>=travel.issuedMillis()&&now<=travel.expiresMillis();}
    public boolean grant(Object owner,Object pin,Travel travel,Object connection,UUID uuid,boolean metadata,long now){granted=false;if(!valid(owner,pin,travel,connection,uuid,metadata,now)){revoke();return false;}lastNow=now;granted=true;return true;}
    public boolean consume(Object owner,Object pin,Travel travel,Object connection,UUID uuid,boolean metadata,long now){boolean once=granted;granted=false;if(!once||!valid(owner,pin,travel,connection,uuid,metadata,now)){revoke();return false;}lastNow=now;return true;}
    public boolean owns(Object owner,Object pin,Travel travel){return !revoked&&this.owner==owner&&this.pin==pin&&this.travel==travel;}
    public void revoke(){revoked=true;granted=false;}
}
