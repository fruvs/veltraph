package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
/** SOURCE-ONLY, UNRUN native fixture. Caller must supply the actual registered Farm helper already at its actual approved goal. */
public final class LegacyKeyOnlyNativeFixture {
 private LegacyKeyOnlyNativeFixture(){}
 public static LegacyTravelRetirement.RetirementOutcome run(Minecraft client,FarmServices services,ConfigService config,LongSupplier revision,
        Module actualHelper,long actualJourney,SharedMovementPolicy.Purpose actualPurpose,Vec3 actualGoal){
  if(client.player==null||!new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(actualGoal))throw new AssertionError("Fixture requires actual already-at-goal player");
  long epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
  var authority=new LegacyTravelPublicationLease(client,services,config,revision);var leg=authority.beginExpected(actualJourney,actualHelper,actualPurpose,actualGoal);
  if(leg==null||leg.cameraAuthority()==leg.parent()||leg.actualTicket()!=0)throw new AssertionError("Actual typed Farm unpublished Leg required");
  if(!leg.setForwardHeld(false)||!leg.setBackHeld(false)||!leg.setLeftHeld(false)||!leg.setRightHeld(false)||!leg.setJumpHeld(false)||!leg.setSneakHeld(false)||!leg.setSprintHeld(false))throw new AssertionError("Neutral ground key publication refused");
  if(leg.actualTicket()!=0||McOwnedRotation.epoch(client)!=epoch||McOwnedRotation.publicationSerial(client)!=serial)throw new AssertionError("Neutral arrival must publish no camera");
  var outcome=leg.retireOwned();
  if(!outcome.cleanSuccess()||!outcome.allNineReleased()||outcome.cameraRequired()||outcome.camera()!=LegacyTravelRetirement.Result.NOT_OWNED)throw new AssertionError("Actual keys-only retirement must complete without camera cancellation");
  if(McOwnedRotation.epoch(client)!=epoch||McOwnedRotation.publicationSerial(client)!=serial||!new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(actualGoal))throw new AssertionError("Fixture must cause no camera/pose displacement");
  return outcome;
 }
}
