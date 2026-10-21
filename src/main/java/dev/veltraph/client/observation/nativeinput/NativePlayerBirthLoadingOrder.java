package dev.veltraph.client.observation.nativeinput;
/** Exact typed original loading transition identity; no GUI or current-key permission. */
final class NativePlayerBirthLoadingOrder {
 private final Object opening;private Object screen;private boolean opened,closed,refused;
 NativePlayerBirthLoadingOrder(Object opening){this.opening=opening;if(opening==null)refused=true;}
 boolean opened(Object call,Object screen,boolean typedOldNineAdvanced,boolean bindingUnchanged){if(opened||call!=opening||screen==null||!typedOldNineAdvanced||!bindingUnchanged)refused=true;if(!refused){opened=true;this.screen=screen;}return !refused;}
 boolean retainOpening(NativePlayerBirthProof.LoadingUpdateCompletion completion){if(opened||closed||refused||completion==null||!completion.consumeFor(opening)){refused=true;return false;}Object retained=completion.screenIdentity();if(retained==null){refused=true;return false;}opened=true;screen=retained;return true;}
 boolean close(Object old,Object next,boolean typedOldNineAdvanced,boolean bindingUnchanged,boolean heldUnchanged){if(!opened||closed||old!=screen||next!=null||!typedOldNineAdvanced||!bindingUnchanged||!heldUnchanged)refused=true;if(!refused)closed=true;return !refused;}
 boolean complete(){return !refused&&opened&&closed;}
 boolean readyToFinalize(boolean originalLoadingBodyOpen,Object retainedLoadingScreen){return complete()&&!originalLoadingBodyOpen&&retainedLoadingScreen==null;}
}
