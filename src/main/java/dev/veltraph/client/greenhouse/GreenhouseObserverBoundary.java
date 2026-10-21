package dev.veltraph.client.greenhouse;

/** Only observer work enters this boundary. Original native operations always stay in their callers. */
public final class GreenhouseObserverBoundary {
    private GreenhouseObserverBoundary() {}
    public static boolean run(Runnable observation){
        try{observation.run();return true;}
        catch(Throwable failure){return false;}
    }
}
