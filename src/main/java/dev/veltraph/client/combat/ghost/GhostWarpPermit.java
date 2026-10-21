package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;

/** One issued Ghost command, exact native UUID/connection, and a newer applied teleport receipt. */
public record GhostWarpPermit(Object previousPlayer,Object previousWorld,Object connection,UUID playerId,
        GhostPreflightProfile profile,String dimension,long receiptSerial,long issuedAt,long expiresAt,GhostTransportLoop.Attempt attempt) {
    public GhostWarpPermit(Object player,Object world,Object connection,UUID uuid,GhostPreflightProfile profile,String dimension,long serial,long issued,long expires){this(player,world,connection,uuid,profile,dimension,serial,issued,expires,new GhostTransportLoop.Attempt(new Object(),1,issued,expires));}
    public GhostWarpPermit {
        if(previousPlayer==null||previousWorld==null||connection==null||playerId==null||profile==null||dimension==null||dimension.isBlank()
                ||expiresAt<issuedAt||expiresAt-issuedAt>60000||attempt==null||attempt.issuedAt()!=issuedAt||attempt.deadline()!=expiresAt)throw new IllegalArgumentException("Bound issued Ghost native warp required");
    }
    public Object activation(){return attempt.activation();}
    public int attemptOrdinal(){return attempt.ordinal();}
    public boolean live(long now){return now>=issuedAt&&now<=expiresAt;}
    public boolean previousContext(Object player,Object world,Object listener){return previousPlayer==player&&previousWorld==world&&connection==listener;}
    public boolean arrival(Object player,Object world,Object listener,UUID id,boolean alive,boolean attached,boolean loaded,
            GameArea area,String dimension,Vec3 feet,long serial,Vec3 received,long now){
        return live(now)&&player!=null&&world!=null&&connection==listener&&playerId.equals(id)&&alive&&attached&&loaded
                &&area==GameArea.DWARVEN_MINES&&this.dimension.equals(dimension)&&finite(feet)&&finite(received)
                &&serial>receiptSerial&&received.distanceTo(profile.anchor())<1&&feet.distanceTo(profile.anchor())<1&&feet.distanceTo(received)<.25;
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
