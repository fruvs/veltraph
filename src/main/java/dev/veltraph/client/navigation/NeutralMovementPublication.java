package dev.veltraph.client.navigation;
/** An ARRIVED output is an explicit ordinary neutral publication, separate from receipt-owned teardown. */
public final class NeutralMovementPublication {
 private NeutralMovementPublication(){}
 public enum Key { FORWARD,BACK,LEFT,RIGHT,JUMP,SNEAK,SPRINT }
 @FunctionalInterface public interface Publisher {boolean publishFalse(Key key);}
 public static boolean publish(Publisher publisher){for(var key:Key.values())if(!publisher.publishFalse(key))return false;return true;}
}
