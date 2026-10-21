package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.GroundStationaryNeutralProof;
/** Private producer boundary only. This mathematical fact issues no native proof or input. */
final class StationaryNeutralHorizon {
    private StationaryNeutralHorizon() {}
    static boolean covered(GroundStationaryNeutralProof.Descriptor d){return d!=null
            &&d.traceFrames()>=d.brakeFrames()&&d.maxHorizon()>0&&d.maxHorizon()<=64
            &&(d.maxHorizon()==1||d.traceFrames()>=2);}
}
