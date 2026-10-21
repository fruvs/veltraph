package dev.veltraph.client.mining.vein;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
/** Client-thread packet AFTER observations, not predicted client edits. Bounded and world scoped. */
public final class MiningBlockReceipts {
 public record Receipt(Object player,Object world,Object connection,BlockPos cell,BlockState state,long serial,long at){}
 private static final LinkedHashMap<BlockPos,Receipt> receipts=new LinkedHashMap<>();
 private static Object world,connection,player;private static long serial;
 private MiningBlockReceipts(){}
 public static void observeSafely(Runnable observation){var c=Minecraft.getInstance();if(!c.isSameThread())return;try{observation.run();}catch(Throwable unavailable){receipts.clear();world=connection=player=null;}}
 public static void applied(Object listener,BlockPos cell,BlockState state){
  var c=Minecraft.getInstance();if(!c.isSameThread()||c.player==null||c.level==null||c.getConnection()!=listener)return;
  if(c.level!=world||c.getConnection()!=connection||c.player!=player){receipts.clear();world=c.level;connection=c.getConnection();player=c.player;}
  if(!c.level.getBlockState(cell).equals(state))return;
  receipts.put(cell.immutable(),new Receipt(player,world,connection,cell.immutable(),state,++serial,System.nanoTime()/1_000_000));
  while(receipts.size()>256)receipts.remove(receipts.keySet().iterator().next());
 }
 public static Optional<Receipt> latest(Minecraft c,BlockPos cell){
  if(!c.isSameThread()||c.player!=player||c.level!=world||c.getConnection()!=connection)return Optional.empty();return Optional.ofNullable(receipts.get(cell));
 }
 public static long serial(){return serial;}
}
