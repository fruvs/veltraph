package dev.veltraph.client.builder;
/** Exact readable source purchase chooser at1894–2010; facts come from source flags and current native menu/item. */
public final class Source95PurchaseChooser {
 private Source95PurchaseChooser(){}
 public enum Kind { NONE, ONE, FILL, CUSTOM, STACK }
 public record Choice(Kind kind,int quantity){}
 public static Choice choose(int remaining,boolean oneAtMost16,boolean specialResource,int nativeDefaultMaxStack,int nativeEmptyPlayerSlots){
  if(remaining<0||nativeDefaultMaxStack<1||nativeDefaultMaxStack>999||nativeEmptyPlayerSlots<0||nativeEmptyPlayerSlots>36)throw new IllegalArgumentException("Actual bounded source remaining/stack/menu capacity facts required");
  if(remaining==0)return new Choice(Kind.NONE,0);
  if(remaining<=16&&oneAtMost16)return new Choice(Kind.ONE,1);
  int emptyCapacity=Math.multiplyExact(nativeEmptyPlayerSlots,nativeDefaultMaxStack);
  if(emptyCapacity<=remaining&&!specialResource)return new Choice(Kind.FILL,emptyCapacity);
  if(remaining>nativeDefaultMaxStack*3&&!specialResource)return new Choice(Kind.CUSTOM,remaining);
  return new Choice(Kind.STACK,nativeDefaultMaxStack);
 }
}
