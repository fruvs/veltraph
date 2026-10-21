package dev.veltraph.client.navigation;

import java.util.function.BooleanSupplier;

/** Exact generation evidence, never permission to cancel a newer same-owner trajectory. */
public record DynamicRouteCustody(Object player,Object world,Object connection,Object authority,long epoch) {
    public boolean matches(Object actualPlayer,Object actualWorld,Object actualConnection,Object actualAuthority,long actualEpoch){
        return player!=null&&world!=null&&connection!=null&&player==actualPlayer&&world==actualWorld
                &&connection==actualConnection&&authority==actualAuthority&&epoch==actualEpoch;
    }
    public boolean cancelIfMatching(Object actualPlayer,Object actualWorld,Object actualConnection,Object actualAuthority,long actualEpoch,Runnable cancel){
        if(!matches(actualPlayer,actualWorld,actualConnection,actualAuthority,actualEpoch))return false;
        cancel.run();return true;
    }
    /** Recheck after geometry and before any effect; rejection runs no camera or key publication. */
    public static boolean publish(BooleanSupplier current,Runnable camera,Runnable keys){
        if(!current.getAsBoolean())return false;
        camera.run();if(!current.getAsBoolean())return false;keys.run();return true;
    }
}
