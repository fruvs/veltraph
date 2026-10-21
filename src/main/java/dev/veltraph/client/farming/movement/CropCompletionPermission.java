package dev.veltraph.client.farming.movement;
/** Exact observation permission used by the native adapter, independently regression-tested. */
public record CropCompletionPermission(boolean clientThread,boolean live,boolean notPaused,boolean notFrozen,boolean playerCamera,boolean enabled,boolean notSuspended,boolean grounded,boolean closedScreen,boolean inputOwner,boolean cameraOwner,boolean chosenTool,boolean cropGuard) {
 public boolean allows(){return clientThread&&live&&notPaused&&notFrozen&&playerCamera&&enabled&&notSuspended&&grounded&&closedScreen&&inputOwner&&cameraOwner&&chosenTool&&cropGuard;}
}
