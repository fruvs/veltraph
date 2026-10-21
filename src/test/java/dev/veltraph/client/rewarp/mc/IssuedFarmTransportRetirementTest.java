package dev.veltraph.client.rewarp.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IssuedFarmTransportRetirementTest {
 @Test void exactOriginalConnectorCanMatchButAnyReplacementCannot(){var source=new Object();var screen=new Object();var future=new Object();var connection=new Object();assertTrue(IssuedFarmTransportRetirement.nativeMatch(source,source,screen,screen,future,future,connection,connection,true,true,true));assertFalse(IssuedFarmTransportRetirement.nativeMatch(source,new Object(),screen,screen,future,future,connection,connection,true,true,true));assertFalse(IssuedFarmTransportRetirement.nativeMatch(source,source,screen,new Object(),future,future,connection,connection,true,true,true));assertFalse(IssuedFarmTransportRetirement.nativeMatch(source,source,screen,screen,future,new Object(),connection,connection,true,true,true));assertFalse(IssuedFarmTransportRetirement.nativeMatch(source,source,screen,screen,future,future,connection,new Object(),true,true,true));}
 @Test void joinedContextOrMissingActualCloseCauseCannotAbort(){var x=new Object();assertFalse(IssuedFarmTransportRetirement.nativeMatch(x,x,x,x,x,x,x,x,false,true,true));assertFalse(IssuedFarmTransportRetirement.nativeMatch(x,x,x,x,x,x,x,x,true,true,false));assertFalse(IssuedFarmTransportRetirement.nativeMatch(x,x,x,x,x,x,x,x,true,false,true));}
}
