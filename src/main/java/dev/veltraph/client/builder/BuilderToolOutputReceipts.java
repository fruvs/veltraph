package dev.veltraph.client.builder;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
/** Passive server block-packet receipts scoped to a connection, owner and explicitly declared cells. */
public final class BuilderToolOutputReceipts {
 private record Receipt(long serial,String block) {}
 private record Watch(Object owner,long floor,Set<BlockPos> cells,Map<BlockPos,Receipt> received) {}
 private static long serial;
 public record ObservedPacket(long serial,String block) {}
 private record DiagnosticWatch(Object connection,Set<BlockPos> cells,Map<BlockPos,ObservedPacket> seen) {}
 private static final Map<Object,DiagnosticWatch> diagnosticWatches=new IdentityHashMap<>();
 public static synchronized void observe(Object connection,Object token,Set<BlockPos> cells){if(cells.size()>128)throw new IllegalArgumentException("Bounded passive diagnostic cells required");diagnosticWatches.put(token,new DiagnosticWatch(connection,Set.copyOf(cells),new LinkedHashMap<>()));}
 public static synchronized Map<BlockPos,ObservedPacket> observed(Object token){var w=diagnosticWatches.get(token);return w==null?Map.of():Map.copyOf(w.seen());}
 public static synchronized void stopObserving(Object token){diagnosticWatches.remove(token);}
 private static final Map<Object,Watch> watches=new IdentityHashMap<>();private BuilderToolOutputReceipts(){}
 public static synchronized void attach(Object connection,Object owner,Set<BlockPos> cells){if(watches.containsKey(connection))throw new IllegalStateException("Tool receipts already owned");watches.put(connection,new Watch(owner,serial,Set.copyOf(cells),new HashMap<>()));}
 public static synchronized void received(Object connection,BlockPos pos,String block){long received=++serial;for(var diagnostic:diagnosticWatches.values())if(diagnostic.connection()==connection&&diagnostic.cells().contains(pos))diagnostic.seen().put(pos,new ObservedPacket(received,block));var w=watches.get(connection);if(w!=null&&received>w.floor()&&w.cells().contains(pos))w.received().put(pos,new Receipt(received,block));}
 public static synchronized boolean matches(Object connection,Object owner,List<Source95ToolBinding.Output> outputs){var w=watches.get(connection);return w!=null&&w.owner()==owner&&outputs.stream().allMatch(o->{var r=w.received().get(o.offset());return r!=null&&r.serial()>w.floor()&&o.after().equals(r.block());});}
 public record Snapshot(long serial,long floor,boolean owned,Map<BlockPos,String> received) {}
 public static synchronized Snapshot snapshot(Object connection,Object owner){var w=watches.get(connection);var states=new LinkedHashMap<BlockPos,String>();if(w!=null&&w.owner()==owner)w.received().forEach((p,r)->states.put(p,r.block()));return new Snapshot(serial,w!=null&&w.owner()==owner?w.floor():-1,w!=null&&w.owner()==owner,Map.copyOf(states));}
 public static synchronized void detach(Object connection,Object owner){var w=watches.get(connection);if(w!=null&&w.owner()==owner)watches.remove(connection);}
}
