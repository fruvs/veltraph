package dev.veltraph.client.hunting;

/** Readable stable fish ranking/Cod suppression; no synthetic entity health, motion or capture credit. */
public final class HuntingTactics {
    private HuntingTactics() {}
    /** Stable aim provider275-304: clamp native eye-relative Y, then20% toward midpoint.
     * Its upper/lower fractions are initialized in provider471-472. */
    public static float nativeAimHeight(float height,float ageScale,boolean singlePassenger){float scaled=height*ageScale;return singlePassenger?scaled*.5f:scaled;}
    public static double aimHeight(double height,double eyeRelative,double upper,double lower){
        double min=height*lower,max=height*upper,at=Math.clamp(eyeRelative,min,max);
        return at+((min+max)/2-at)*.2;
    }
    public static double score(double distance,double angular,double routeCost,double y,boolean visible,double minimumY){
        return distance*.5+angular*.5+routeCost/10+Math.abs(88-y)*.5+(visible?0:15)+(y<minimumY-3?200:0);
    }
    public static boolean codBlocked(double pull,double progress){
        if(!Double.isFinite(pull)||!Double.isFinite(progress)||pull<0||progress<0)return false;
        return progress==0||progress+pull>100&&progress+pull<110;
    }
    public static final class CodSpin {
        private int blocked;private boolean bypass,progressKnown;
        public boolean block(boolean cod,double pull,double progress,boolean firstHit,long sinceUse,long graceMs){
            if(!cod||bypass)return false;
            boolean grace=firstHit&&!progressKnown&&sinceUse<graceMs;
            boolean spinning=grace||codBlocked(pull,progress);
            if(!grace&&Double.isFinite(pull)&&Double.isFinite(progress)&&pull>=0&&progress>=0)progressKnown=true;
            // The 42nd callback sets bypass after its blocked result was already computed.
            if(spinning&&blocked++>40)bypass=true;return spinning;
        }
        public int blockedCallbacks(){return blocked;}
        public boolean bypass(){return bypass;}
        public void reset(){blocked=0;bypass=progressKnown=false;}
    }
}
