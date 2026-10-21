package dev.veltraph.client.galatea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.ToDoubleFunction;
/** Readable central chooser. Null-feet base callback is supplied independently of normal STEM score. */
public final class FigScaffoldCentralSeed {
    private FigScaffoldCentralSeed(){}
    /** Ordinary descriptor observation removes non-LOG entries without changing original identity/order. */
    public static List<BlockPos> liveModeList(List<BlockPos> original,java.util.function.Predicate<BlockPos> nativeLog){return original.stream().filter(nativeLog).toList();}
    public static Optional<BlockPos> select(List<BlockPos> modeList,Vec3 descriptorBase,float yaw,ToDoubleFunction<BlockPos> nullFeetBaseScore){
        if(modeList.isEmpty())return Optional.empty();
        if(modeList.size()==1)return Optional.of(modeList.getFirst());
        BlockPos best=null;double score=0;
        for(BlockPos next:modeList){
            var to=new Vec3(next.x(),next.y(),next.z());double angle=Math.toDegrees(Math.atan2(to.z()-descriptorBase.z(),to.x()-descriptorBase.x()))-90-yaw;
            double delta=((angle+180)%360+360)%360-180;
            double candidate=nullFeetBaseScore.applyAsDouble(next)+Math.abs(delta)/15;
            if(best!=null&&Double.compare(score,candidate)<=0)continue;
            best=next;score=candidate;
        }
        return Optional.ofNullable(best);
    }
}
