package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReturnLegTerminalRadiusTest {
 private static SharedMovementPolicy.Candidate device(){return new SharedMovementPolicy.Candidate("nativeAote",SharedMovementPolicy.Mode.AOTE,new Vec3(8,72,0),4,1,new Object(),new Object(),new Object());}
 @Test void intermediateReceiptEnvelopeDoesNotMutateCanonicalFinalTolerance(){var options=new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.AOTE),SharedMovementPolicy.Purpose.EXACT_RETURN,2,45000,.18,1.25);assertEquals(.30,SharedMovementPolicy.legTerminalRadius(device(),options));assertEquals(.18,options.terminalRadius());}
 @Test void exactGroundEntryAndGeneralDeviceKeepOriginalTolerance(){var returnOptions=new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK),SharedMovementPolicy.Purpose.EXACT_RETURN,0,45000,.18,1.25);var walk=new SharedMovementPolicy.Candidate("entry",SharedMovementPolicy.Mode.WALK,new Vec3(30,70,0),30,0,new Object(),new Object(),null);assertEquals(.18,SharedMovementPolicy.legTerminalRadius(walk,returnOptions));var general=new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.AOTE),SharedMovementPolicy.Purpose.GENERAL_TRAVEL,2,45000,.35,1.25);assertEquals(.35,SharedMovementPolicy.legTerminalRadius(device(),general));}
}
