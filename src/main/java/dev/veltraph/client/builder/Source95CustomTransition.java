package dev.veltraph.client.builder;
/** Actual adapter pre-advance boundary: pin the received CUSTOM confirmation before leg.tick changes phase to ACK. */
final class Source95CustomTransition {
 private Source95CustomTransition(){}
 static boolean bindBeforeAdvance(Source95AcquisitionAuthority authority,Source95BazaarLeg leg,Source95AcquisitionAuthority.Scope current,Source95MenuTransitions.Event sign,Source95MenuTransitions.Event confirmation,long clickFloor,long submitFloor){
  if(leg==null||!leg.customStrategy()||leg.state()!=Source95BazaarLeg.State.CUSTOM_CONFIRM||sign==null||confirmation==null||submitFloor<sign.sequence()||confirmation.sequence()<=sign.sequence()){
   authority.revoke("CUSTOM confirmation is not owned by the received issued sign phase");return false;
  }
  return authority.authorizeCustomConfirmation(current,sign,confirmation,clickFloor,submitFloor);
 }
}
