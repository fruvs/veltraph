package dev.veltraph.client.combat.sven;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.UUID;

/** Root's bounded issued same-connection handoff. No control input or context adoption occurs here. */
public final class InstalledSvenWarpLifecycle {
    public enum Decision { INACTIVE, WAIT, COMMIT, STOP }
    public record Observation(Object player,Object world,Object connection,UUID playerId,
            boolean alive,boolean attached,boolean loaded,boolean closedMenu,boolean permittedLoadingScreen,
            GameArea area,Vec3 feet,boolean higherPriority,long now) {}
    private Object owner;
    private SvenWarpPermit permit;
    private UUID playerId;
    private boolean replacementSeen;
    private GameArea initialArea;

    /** Admission uses the unchanged Root observations, never the current replacement as old context. */
    public boolean capture(Object candidate,Object inputOwner,int controllers,boolean enabled,
            SvenWarpPermit issued,Object oldPlayer,Object oldWorld,Object oldConnection,UUID oldPlayerId,GameArea oldArea,long now) {
        if(owner!=null)return owner==candidate&&permit==issued;
        if(candidate==null||inputOwner!=candidate||controllers!=1||!enabled||issued==null||!issued.live(now)
                ||!issued.previousContext(oldPlayer,oldWorld,oldConnection)||oldPlayerId==null)return false;
        owner=candidate;permit=issued;playerId=oldPlayerId;initialArea=oldArea;return true;
    }
    public Decision evaluate(Object candidate,Object inputOwner,int controllers,boolean enabled,SvenWarpPermit active,Observation o) {
        if(owner==null)return Decision.INACTIVE;
        if(owner!=candidate||owner!=inputOwner||controllers!=1||!enabled||permit!=active||o==null
                ||!permit.live(o.now())||o.connection()!=permit.connection()||o.higherPriority()
                ||(o.player()!=null&&(!o.alive()||!playerId.equals(o.playerId())))
                ||(!o.closedMenu()&&!o.permittedLoadingScreen()))return Decision.STOP;
        replacementSeen|=o.player()!=null&&o.player()!=permit.previousPlayer()||o.world()!=null&&o.world()!=permit.previousWorld();
        if(o.player()==null||o.world()==null||!o.attached()||!o.loaded()||!o.closedMenu()
                ||o.area()==null||o.area()==GameArea.UNKNOWN)return Decision.WAIT;
        if(permit.arrival(o.player(),o.world(),o.connection(),o.alive(),o.area(),o.feet(),o.now()))return Decision.COMMIT;
        // The pre-departure old context can remain visible while the issued command travels.
        // An attached loaded replacement, or a resolved different realm, is an actual wrong arrival.
        if(replacementSeen||o.area()!=initialArea)return Decision.STOP;
        return Decision.WAIT;
    }
    /** Exact configured denial cancels only; it never dispatches the parent's normal receipt callback. */
    public boolean denial(Object candidate,SvenWarpPermit active,String configured,String line,long now){
        if(!owns(candidate)||active!=permit||!permit.live(now)||configured==null||line==null)return false;
        String expected=configured.replaceAll("§.","").strip();
        return !expected.isBlank()&&line.replaceAll("§.","").strip().equals(expected);
    }
    public SvenWarpPermit permit(){return permit;}
    public boolean owns(Object candidate){return owner!=null&&owner==candidate;}
    public boolean active(){return owner!=null;}
    public void clear(){owner=null;permit=null;playerId=null;initialArea=null;replacementSeen=false;}

    /** Loading-screen class alone grants no allowance; the exact listener must supply its tracker. */
    public static boolean loadingScreen(Object issuedConnection,Object currentConnection,boolean remoteScreen,
            Object listenerTracker,Object screenTracker) {
        return issuedConnection!=null&&currentConnection==issuedConnection&&remoteScreen
                &&listenerTracker!=null&&listenerTracker==screenTracker;
    }
}
