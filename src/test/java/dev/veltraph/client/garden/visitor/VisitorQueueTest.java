package dev.veltraph.client.garden.visitor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisitorQueueTest {
    static VisitorQueue.Entry entry(String name,VisitorRarity rarity){return new VisitorQueue.Entry(UUID.randomUUID(),name,rarity);}
    @Test void exactLabelsPreserveConfiguredOrderAndSingleFallback(){assertEquals(List.of("Jacob","Beth"),VisitorQueue.names("Jacob, Beth","Seymour",5));assertEquals(List.of("Seymour"),VisitorQueue.names("","Seymour",5));}
    @Test void duplicateUnknownBlankAndExcessNamesFail(){for(String names:List.of("","Jacob,","Jacob,jacob","Jacob,Beth,Seymour","/warp garden"))assertThrows(IllegalArgumentException.class,()->VisitorQueue.names(names,"",2));}
    @Test void distanceOrderIgnoresRarityAndAscendingReversesSortedTies(){var near=new VisitorQueue.Entry(UUID.randomUUID(),"A",VisitorRarity.LEGENDARY,2);var far=new VisitorQueue.Entry(UUID.randomUUID(),"B",VisitorRarity.COMMON,5);var far2=new VisitorQueue.Entry(UUID.randomUUID(),"C",VisitorRarity.RARE,5);assertEquals(List.of(far,far2,near),VisitorQueue.ordered(List.of(near,far,far2),SortOrder.DESCENDING));assertEquals(List.of(near,far2,far),VisitorQueue.ordered(List.of(near,far,far2),SortOrder.ASCENDING));}
    @Test void duplicateEntityCannotBeScheduledTwice(){var e=entry("Jacob",VisitorRarity.RARE);assertThrows(IllegalArgumentException.class,()->VisitorQueue.ordered(List.of(e,e),SortOrder.DESCENDING));}
    @Test void randomOrderKeepsEveryCapturedIdentityExactlyOnce(){var entries=List.of(entry("A",VisitorRarity.RARE),entry("B",VisitorRarity.COMMON),entry("C",VisitorRarity.EPIC));assertEquals(new HashSet<>(entries),new HashSet<>(VisitorQueue.ordered(entries,SortOrder.RANDOM,new Random(7))));assertEquals(SortOrder.RANDOM,SortOrder.parse("random"));}
    @Test void explicitOriginIsFiniteAndEmptyUsesCapturedPose(){var pose=new dev.veltraph.client.world.Vec3(.79,70,.12);assertEquals(pose,VisitorQueue.origin("",pose));assertEquals(new dev.veltraph.client.world.Vec3(1,70,-3),VisitorQueue.origin("1,70,-3",pose));assertThrows(IllegalArgumentException.class,()->VisitorQueue.origin("NaN,70,0",pose));}
}
