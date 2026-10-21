package dev.veltraph.client.service.mc;
/** Pure shape comparison only. True never issues a source, instruction, native token or key permission. */
final class SavedMaintenanceNativeMethodShape {
 private SavedMaintenanceNativeMethodShape(){}
 static int enterOrdinaryFallback(int depth){if(depth<0||depth==Integer.MAX_VALUE)throw new IllegalStateException("Saved ordinary fallback nesting exhausted");return depth+1;}
 static int leaveOrdinaryFallback(int depth){if(depth<=0)throw new IllegalStateException("Saved ordinary fallback custody missing");return depth-1;}
 static boolean outer(boolean hasContext,boolean noParent,boolean notEntered,Object pending,Object installed,
   Object pendingKey,Object receiver,boolean expectedRequested,boolean requested,boolean expectedRestore,boolean restore,
   boolean base,boolean plainReceiver,boolean sameThread){
  return hasContext&&noParent&&notEntered&&pending!=null&&pending==installed&&pendingKey==receiver
   &&expectedRequested==requested&&expectedRestore==restore&&base==plainReceiver&&(!restore||!base)&&sameThread;
 }
 static boolean child(boolean hasContext,boolean base,boolean selectedOpenParent,boolean unenteredCookie,
   Object cookieParent,Object parent,Object cookieContext,Object context,Object cookiePending,Object pending,Object installed,
   Object cookieReceiver,Object receiver,boolean cookieRequested,boolean requested,Object parentReceiver,
   boolean restore,boolean parentRestore){
  return hasContext&&base&&selectedOpenParent&&unenteredCookie&&cookieParent==parent&&cookieContext==context
   &&pending!=null&&cookiePending==pending&&pending==installed&&cookieReceiver==receiver&&cookieRequested==requested
   &&parentReceiver==receiver&&!restore&&!parentRestore;
 }
}
