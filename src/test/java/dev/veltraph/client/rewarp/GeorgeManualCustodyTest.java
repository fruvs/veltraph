package dev.veltraph.client.rewarp;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GeorgeManualCustodyTest {
    static class Driver extends AbstractModule{Driver(String id){super(id,id,ModuleCategory.INTERMEDIARY);}}
    final Object token=new Object(),permit=new Object();
    @Test void oldBranchStopsFreshManualOwnerButCorrectBranchLeavesItRunning(){var manager=new ModuleManager();var old=new Driver("issued_george");var fresh=new Driver("fresh_manual");manager.register(old);manager.register(fresh);manager.enable(old.id());manager.stopAll();manager.enable(fresh.id());if(GeorgeManualCustody.mayContinue(old,token,permit,old.isEnabled(),manager.inputOwner().orElse(null),null,null))manager.stopAll();assertTrue(fresh.isEnabled());assertFalse(old.isEnabled());}
    @Test void exactEnabledOriginalHelperCanContinueFiniteNativeGuardChecks(){var helper=new Object();assertTrue(GeorgeManualCustody.mayContinue(helper,token,permit,true,helper,token,permit));}
    @Test void sameHelperNewTokenOrNewPermitCannotReuseOldDispatch(){var helper=new Object();assertFalse(GeorgeManualCustody.mayContinue(helper,token,permit,true,helper,new Object(),permit));assertFalse(GeorgeManualCustody.mayContinue(helper,token,permit,true,helper,token,new Object()));}
    @Test void disabledOrForeignOwnerYieldsWithoutNewStopOrCommit(){var helper=new Object();assertFalse(GeorgeManualCustody.mayContinue(helper,token,permit,false,helper,token,permit));assertFalse(GeorgeManualCustody.mayContinue(helper,token,permit,true,new Object(),token,permit));}
}
