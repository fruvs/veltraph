package dev.veltraph.client.combat.ghost;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
/** Actual mutation-hook receipt. Absent hooks/history are unavailable; unrelated changes do not revoke a route. */
public final class GhostSourceWalkTerrainEpochs {
    private static final WeakHashMap<Object,Entry> WORLDS=new WeakHashMap<>();private static final int MODERN_HISTORY_LIMIT=10000;
    private static final class Entry {long revision,discardedThrough,allChanged;boolean levelHook,chunkHook;final TreeMap<Long,Change> history=new TreeMap<>();}
    private record Change(Cell cell,Long chunk) {}
    public record Footprint(Set<Cell> cells,Set<Long> chunks){public Footprint {cells=Set.copyOf(cells);chunks=Set.copyOf(chunks);}}
    public record Stamp(Object world,long revision,Footprint footprint) {}
    private GhostSourceWalkTerrainEpochs() {}
    /** Build on the pure worker, outside the client thread and mutation-hook lock. */
    public static Footprint footprint(Set<Cell> cells){var chunks=new java.util.HashSet<Long>();for(var c:cells)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)chunks.add(chunkKey((c.x()+x)>>4,(c.z()+z)>>4));return new Footprint(cells,chunks);}
    public static synchronized void observedLevelHook(Object world){WORLDS.computeIfAbsent(world,k->new Entry()).levelHook=true;}
    public static synchronized void observedChunkHook(Object world){WORLDS.computeIfAbsent(world,k->new Entry()).chunkHook=true;}
    public static synchronized void changedAll(Object world){var e=WORLDS.computeIfAbsent(world,k->new Entry());e.allChanged=++e.revision;}
    public static synchronized void changedCell(Object world,Cell cell){append(world,new Change(cell,null));}
    public static synchronized void changedChunk(Object world,int x,int z){append(world,new Change(null,chunkKey(x,z)));}
    private static void append(Object world,Change change){var e=WORLDS.computeIfAbsent(world,k->new Entry());e.history.put(++e.revision,change);if(e.history.size()>MODERN_HISTORY_LIMIT)e.discardedThrough=e.history.pollFirstEntry().getKey();}
    public static synchronized Stamp capture(Object world,Footprint footprint){var e=WORLDS.get(world);if(e==null||!e.levelHook||!e.chunkHook)throw new IllegalStateException("Actual terrain mutation hooks unavailable");return new Stamp(world,e.revision,footprint);}
    public static synchronized boolean current(Stamp s){var e=WORLDS.get(s.world);if(e==null||!e.levelHook||!e.chunkHook||s.revision<e.discardedThrough||s.revision<e.allChanged)return false;
        for(var change:e.history.tailMap(s.revision,false).values()){
            if(change.chunk!=null&&s.footprint.chunks.contains(change.chunk))return false;
            if(change.cell!=null)for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(s.footprint.cells.contains(new Cell(change.cell.x()+x,change.cell.y()+y,change.cell.z()+z)))return false;
        }return true;
    }
    private static long chunkKey(int x,int z){return (long)x<<32|(z&0xffffffffL);}
}
