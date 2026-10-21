package dev.veltraph.client.combat.sven;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;

/** Issued native warp intent for root lifecycle handoff, never a general world-change exemption. */
public record SvenWarpPermit(SvenIngressPort.Ticket request,SvenIngressPort.Operation operation,
        Object previousPlayer,Object previousWorld,Object connection,String command,long issuedAt,long expiresAt) {
    public SvenWarpPermit {
        if(request==null||previousPlayer==null||previousWorld==null||connection==null||command==null||command.isBlank()
                ||expiresAt<issuedAt||expiresAt-issuedAt>60000
                ||operation!=SvenIngressPort.Operation.PARENT_WARP&&operation!=SvenIngressPort.Operation.WARP_ROUTE_START)
            throw new IllegalArgumentException("Bound issued Sven parent/route warp required");
    }
    public boolean live(long now){return now>=issuedAt&&now<=expiresAt;}
    public boolean previousContext(Object player,Object world,Object connection){return previousPlayer==player&&previousWorld==world&&this.connection==connection;}
    public boolean arrival(Object player,Object world,Object connection,boolean alive,GameArea area,Vec3 feet,long now){
        return live(now)&&player!=null&&world!=null&&connection==this.connection&&alive&&area==GameArea.THE_PARK&&feet!=null
                &&Double.isFinite(feet.x())&&Double.isFinite(feet.y())&&Double.isFinite(feet.z())&&feet.distanceTo(SvenCaveProfile.PARK_SPAWN)<15.0;
    }
}
