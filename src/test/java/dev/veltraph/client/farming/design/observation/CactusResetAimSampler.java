package dev.veltraph.client.farming.design.observation;

/** Test-only native observation ledger. Never plans or applies a camera pose. */
public final class CactusResetAimSampler {
    public record Scope(Object owner,Object player,Object world,Object connection,int generation) {
        boolean same(Scope other){return other!=null&&owner==other.owner&&player==other.player&&world==other.world
                &&connection==other.connection&&generation==other.generation;}
    }
    private Scope boundScope,previousScope;private String failure;private int previousTick=-1;private float previousYaw;
    private boolean previousAligning;private int steps,packetMarkers;private float maximum;
    /** Only the declared reset's actually applied server packet rebases observation continuity. */
    public void appliedDeclaredResetPacket(Scope scope){
        if(scope==null||scope.generation()<=0)throw new IllegalArgumentException("Exact reset generation required");
        bind(scope);previousScope=null;previousTick=-1;previousAligning=false;packetMarkers++;
    }
    public void observe(Scope scope,int tick,float yaw,boolean aligning,boolean suspended,boolean eligible){
        if(scope==null||scope.generation()<=0||!eligible||!Float.isFinite(yaw)){
            if(boundScope!=null)failure="Lost exact native aim observation eligibility";
            previousScope=null;previousTick=-1;previousAligning=false;return;
        }
        bind(scope);
        if(previousScope!=null&&previousScope.same(scope)&&tick!=previousTick+1)failure="Nonconsecutive native aim observations";
        if(previousScope!=null&&previousScope.same(scope)&&tick==previousTick+1&&previousAligning&&suspended){
            float difference=(yaw-previousYaw)%360;if(difference>180)difference-=360;if(difference< -180)difference+=360;
            float step=Math.abs(difference);
            // No size-based jump exclusion: unexpected native/manual/server turning violates the same bound.
            if(step>.001){steps++;maximum=Math.max(maximum,step);}
        }
        previousScope=scope;previousTick=tick;previousYaw=yaw;previousAligning=aligning;
    }
    private void bind(Scope scope){
        if(boundScope==null)boundScope=scope;
        else if(!boundScope.same(scope)){failure="Changed exact aim scope/reset generation";steps=0;maximum=0;previousScope=null;boundScope=scope;}
    }
    public String failure(){return failure;}
    public int steps(){return steps;}
    public float maximum(){return maximum;}
    public int packetMarkers(){return packetMarkers;}
}
