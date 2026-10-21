package dev.veltraph.client.observation.nativeinput;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerBirthInstalledContextTest {
    private final Object client=new Object(),world=new Object(),mode=new Object();
    private NativePlayerBirthInstalledContext installed(){
        var source=new NativePlayerBirthInstalledContext(client);
        Object modeAttempt=source.beginMode(client,mode);source.modeReturned(modeAttempt,client,mode,true);
        Object worldAttempt=source.beginWorld(client,world,mode);source.worldReturned(worldAttempt,client,world,mode,true);
        return source;
    }
    @Test void genuineOrderedInstallationRetainsOriginalWorldAndMode(){var s=installed();assertSame(world,s.world());assertSame(mode,s.mode());assertTrue(s.current(client,world,world,mode,true));}
    @Test void samePlayerWorldPairCannotMoveTogetherToForeignWorld(){var s=installed();Object foreign=new Object();assertFalse(s.current(client,foreign,foreign,mode,true));assertFalse(s.current(client,world,world,mode,true));}
    @Test void samePlayerAndWorldCannotBorrowForeignMode(){var s=installed();assertFalse(s.current(client,world,world,new Object(),true));}
    @Test void setterCallbackReplacementCannotBecomeOriginalInstallation(){var s=new NativePlayerBirthInstalledContext(client);Object t=s.beginMode(client,mode);s.modeReturned(t,client,new Object(),true);assertNull(s.beginWorld(client,world,mode));}
    @Test void originalWorldBodyThrowOrReplacementCannotMintContext(){var s=new NativePlayerBirthInstalledContext(client);Object m=s.beginMode(client,mode);s.modeReturned(m,client,mode,true);Object w=s.beginWorld(client,world,mode);s.worldReturned(w,client,new Object(),mode,true);assertFalse(s.current(client,world,world,mode,true));}
    @Test void worldCannotInstallBeforeOriginalModeNormalReturn(){var s=new NativePlayerBirthInstalledContext(client);assertNull(s.beginWorld(client,world,mode));}
    @Test void originalModeNormalReturnCannotStandInForWorldReturn(){var s=new NativePlayerBirthInstalledContext(client);Object m=s.beginMode(client,mode);s.modeReturned(m,client,mode,true);s.beginWorld(client,world,mode);assertFalse(s.current(client,world,world,mode,true));}
    @Test void differentPlayerWorldAndRepeatedInstallationRefuse(){var s=installed();assertFalse(s.current(client,world,new Object(),mode,true));assertNull(s.beginMode(client,mode));}
}
