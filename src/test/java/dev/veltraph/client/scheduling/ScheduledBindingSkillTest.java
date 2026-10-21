package dev.veltraph.client.scheduling;
import dev.veltraph.client.scheduling.mc.McScriptScheduler;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
final class ScheduledBindingSkillTest {
    private final ScheduledPausePort pause=new ScheduledPausePort(){public boolean pause(Object t){return false;}public boolean owns(Object t){return false;}public boolean rebindAfterOwnedArrival(Object t){return false;}public boolean resume(Object t){return false;}public void stop(Object t){}};
    private final McScriptScheduler.ArrivalRecovery arrival=new McScriptScheduler.ArrivalRecovery(){
        public boolean begin(dev.veltraph.client.rewarp.FarmReturnPoint p,dev.veltraph.client.game.GameArea a){return false;}
        public McScriptScheduler.ArrivalStatus tick(dev.veltraph.client.game.GameArea a){return McScriptScheduler.ArrivalStatus.FAILED;}
        public boolean arrived(dev.veltraph.client.game.GameArea a){return false;}public void cancel(){}
    };
    @Test void fishingVariantsKeepActualSkillAndStaticCallersKeepTheirContract(){
        ScheduledSkill[] current={ScheduledSkill.FISHING};
        var dynamic=new McScriptScheduler.Binding(()->current[0],pause,()->"fishing",()->"settings",Optional::empty,false,()->false,arrival);
        assertEquals(ScheduledSkill.FISHING,dynamic.skill());
        current[0]=ScheduledSkill.HUNTING;assertEquals(ScheduledSkill.HUNTING,dynamic.skill());
        var fixed=new McScriptScheduler.Binding(ScheduledSkill.FARMING,pause,()->"farm",()->"settings",Optional::empty,true,()->false,arrival);
        assertEquals(ScheduledSkill.FARMING,fixed.skill());assertEquals(ScheduledSkill.FARMING,fixed.skillSupplier().get());
        assertThrows(NullPointerException.class,()->new McScriptScheduler.Binding((ScheduledSkill)null,pause,()->"farm",()->"settings",Optional::empty,true,()->false,arrival));
    }
}
