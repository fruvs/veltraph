package dev.veltraph.client.mining.vein;
/** Existing operation deadline is exclusive for new physical collection actions. */
public final class VeinCollectionWindow {
 private VeinCollectionWindow(){}
 public static boolean current(long actualNow,long floor,long originalDeadline){return actualNow>=floor&&actualNow<originalDeadline;}
}
