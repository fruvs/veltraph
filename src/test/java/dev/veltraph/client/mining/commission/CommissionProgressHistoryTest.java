package dev.veltraph.client.mining.commission;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;
import static org.junit.jupiter.api.Assertions.*;
class CommissionProgressHistoryTest {
    private CommissionObservation task(long serial,String name,Work kind,double percent){return new CommissionObservation("native-context",serial,100,List.of(new Task(name,kind,"","",percent,true,false,-1)),true);}
    private CommissionObservation mithril(long serial,double percent){return task(serial,"Mithril Miner",Work.MITHRIL,percent);}
    private CommissionProgressHistory history(){var baseline=mithril(5,20);return new CommissionProgressHistory(CommissionWorkBinding.choose(baseline).orElseThrow(),baseline);}
    @Test void taskAbaCannotRevive(){var h=history();assertFalse(h.observe(task(6,"Titanium Miner",Work.TITANIUM,0)));assertFalse(h.observe(mithril(7,20)));}
    @Test void completedThenActiveCannotRevive(){var h=history();assertFalse(h.observe(mithril(6,100)));assertFalse(h.observe(mithril(7,20)));}
    @Test void progressResetAndRestorationCannotRevive(){var h=history();assertTrue(h.observe(mithril(6,80)));assertFalse(h.observe(mithril(7,0)));assertFalse(h.observe(mithril(8,80)));}
    @Test void genuineMonotonicProgressRemainsCompatible(){var h=history();assertTrue(h.observe(mithril(6,40)));assertTrue(h.observe(mithril(6,40)));assertTrue(h.observe(mithril(7,99.9)));}
    @Test void lostSectionCannotRevive(){var h=history();assertFalse(h.observe(new CommissionObservation("native-context",6,100,List.of(),false)));assertFalse(h.observe(mithril(7,20)));}
    @Test void changedAndRestoredContextCannotRevive(){var h=history();assertFalse(h.observe(new CommissionObservation("other-native-context",6,100,mithril(6,20).tasks(),true)));assertFalse(h.observe(mithril(7,20)));}
    @Test void earlierOrInconsistentSerialRefuses(){var h=history();assertFalse(h.observe(mithril(4,20)));var other=history();assertFalse(other.observe(mithril(5,30)));}
    @Test void unknownProgressAndDuplicateRowsRefuse(){var h=history();var task=new Task("Mithril Miner",Work.MITHRIL,"","",20,false,false,-1);assertFalse(h.observe(new CommissionObservation("native-context",6,100,List.of(task),true)));var other=history();assertFalse(other.observe(new CommissionObservation("native-context",6,100,List.of(mithril(6,20).tasks().getFirst(),mithril(6,20).tasks().getFirst()),true)));}
}
