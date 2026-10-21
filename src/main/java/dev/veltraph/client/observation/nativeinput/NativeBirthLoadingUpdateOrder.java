package dev.veltraph.client.observation.nativeinput;
/** Data-only original tracker/screen/update one-use ordering; native authority remains in private authenticated source capsules. */
final class NativeBirthLoadingUpdateOrder {
 private final Object owner,tracker,player,world;private Object screen,reason;private boolean started,screened,attempted,returned,consumed,refused;
 NativeBirthLoadingUpdateOrder(Object owner,Object tracker,Object player,Object world){this.owner=owner;this.tracker=tracker;this.player=player;this.world=world;refused=owner==null||tracker==null||player==null||world==null;}
 boolean startReturned(Object tracker,Object player,Object world,boolean normal){if(refused||started||this.tracker!=tracker||this.player!=player||this.world!=world||!normal){refused=true;return false;}started=true;return true;}
 boolean screenReturned(Object screen){if(refused||!started||screened||screen==null){refused=true;return false;}this.screen=screen;screened=true;return true;}
 boolean updateEntered(Object screen,Object tracker,Object reason){if(attempted){refused=true;return false;}attempted=true;if(refused||!started||!screened||this.screen!=screen||this.tracker!=tracker||reason==null){refused=true;return false;}this.reason=reason;return true;}
 boolean updateReturned(Object screen,Object tracker,boolean normal){if(refused||!attempted||returned||this.screen!=screen||this.tracker!=tracker||reason==null||!normal){refused=true;return false;}returned=true;return true;}
 boolean consume(Object screen,Object tracker){if(refused||consumed||!returned||this.screen!=screen||this.tracker!=tracker){refused=true;return false;}consumed=true;return true;}
 void refuse(){refused=true;}
}
