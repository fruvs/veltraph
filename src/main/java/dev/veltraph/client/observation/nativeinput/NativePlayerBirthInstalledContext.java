package dev.veltraph.client.observation.nativeinput;

/** Original login installation identities; never refreshed from a later matching player/world pair. */
final class NativePlayerBirthInstalledContext {
    private final Object client;
    private Object mode, world;
    private Object modeAttempt, worldAttempt;
    private boolean modeReturned, worldReturned, refused;

    NativePlayerBirthInstalledContext(Object client) { this.client=client; if(client==null)refused=true; }

    Object beginMode(Object actualClient,Object originalMode) {
        if(refused||actualClient!=client||originalMode==null||modeAttempt!=null||modeReturned){refused=true;return null;}
        mode=originalMode;modeAttempt=new Object();return modeAttempt;
    }

    void modeReturned(Object attempt,Object actualClient,Object actualInstalledMode,boolean normal) {
        if(refused||attempt==null||attempt!=modeAttempt||actualClient!=client||actualInstalledMode!=mode||!normal||modeReturned){refused=true;return;}
        modeReturned=true;
    }

    Object beginWorld(Object actualClient,Object originalWorld,Object actualMode) {
        if(refused||!modeReturned||actualClient!=client||originalWorld==null||actualMode!=mode||worldAttempt!=null||worldReturned){refused=true;return null;}
        world=originalWorld;worldAttempt=new Object();return worldAttempt;
    }

    void worldReturned(Object attempt,Object actualClient,Object actualInstalledWorld,Object actualMode,boolean normal) {
        if(refused||attempt==null||attempt!=worldAttempt||actualClient!=client||actualInstalledWorld!=world||actualMode!=mode||!normal||worldReturned){refused=true;return;}
        worldReturned=true;
    }

    boolean current(Object actualClient,Object clientWorld,Object playerWorld,Object actualMode,boolean playerRequired) {
        if(refused||!modeReturned||!worldReturned||actualClient!=client||clientWorld!=world||actualMode!=mode||playerRequired&&playerWorld!=world){refused=true;return false;}
        return true;
    }

    Object world(){return worldReturned&&!refused?world:null;}
    Object mode(){return modeReturned&&!refused?mode:null;}
    void refuse(){refused=true;}
}
