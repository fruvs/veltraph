package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.*;
import dev.veltraph.client.mining.module.MiningModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordedEchoModuleTest {
    @Test void optionalConstructorRetainsLegacyEmptyProfileAndRecordedUsesSameEchoIdentity(){
        var services=new RecordedEchoControllerTest.Port();var config=new MapConfigService();config.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,64,2");
        var legacy=new MiningModule("echo","Echo",MiningMode.ECHO,services,config,services);assertTrue(legacy.preStartCheck().isEmpty());legacy.setEnabled(true);legacy.onTick();assertFalse(legacy.recordedPlayback());assertTrue(services.breaking.isBreaking());legacy.setEnabled(false);
        config.set(RecordedEchoConfig.PROFILE,RecordedEchoControllerTest.profile("\"FORWARD\"",""));var recorded=new MiningModule("echo","Echo",MiningMode.ECHO,services,config,services);assertTrue(recorded.preStartCheck().isEmpty());recorded.setEnabled(true);recorded.onTick();assertEquals("echo",recorded.id());assertTrue(recorded.recordedPlayback());assertTrue(services.input.forward);assertEquals(1,recorded.enableRuns());assertTrue(recorded.scheduledRestReadiness().isPresent());assertFalse(recorded.pauseForScheduledRest(new Object()));recorded.setEnabled(false);assertFalse(services.input.forward);
    }
    @Test void profileMutationStopsSameFacadeWithoutImplicitRestart(){var s=new RecordedEchoControllerTest.Port();var cfg=new MapConfigService();cfg.set(RecordedEchoConfig.PROFILE,RecordedEchoControllerTest.profile("\"FORWARD\"",""));var module=new MiningModule("echo","Echo",MiningMode.ECHO,s,cfg,s);module.setEnabled(true);module.onTick();assertTrue(s.input.forward);cfg.set(RecordedEchoConfig.PROFILE,RecordedEchoControllerTest.profile("\"BACK\"",""));module.onTick();assertFalse(module.isEnabled());assertFalse(s.input.forward);assertEquals(1,module.enableRuns());}
    @Test void missingAdapterAndInvalidProfileCannotSilentlyStartLegacy(){var s=new RecordedEchoControllerTest.Port();var cfg=new MapConfigService();cfg.set(RecordedEchoConfig.PROFILE,RecordedEchoControllerTest.profile("\"FORWARD\"",""));var legacy=new MiningModule("echo","Echo",MiningMode.ECHO,s,cfg);assertTrue(legacy.preStartCheck().isPresent());legacy.setEnabled(true);assertFalse(legacy.isEnabled());var recorded=new MiningModule("echo","Echo",MiningMode.ECHO,s,cfg,s);cfg.set(RecordedEchoConfig.PROFILE,"{bad");recorded.setEnabled(true);assertFalse(recorded.isEnabled());assertFalse(s.input.forward);}
}
