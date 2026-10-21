package dev.veltraph.client.combat.ghost;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;
/** Actual relevant chunk availability/identity; viewcenter/storage reallocation alone is not a mutation. */
public final class McGhostSourceRelevantChunks {
    private final Object player,world,connection;private final Map<Long,Object> chunks;
    private McGhostSourceRelevantChunks(Minecraft client,Map<Long,Object> chunks){player=client.player;world=client.level;connection=client.getConnection();this.chunks=Map.copyOf(chunks);}
    static McGhostSourceRelevantChunks capture(Minecraft client,GhostSourceWalkTerrainEpochs.Footprint footprint){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)throw new IllegalStateException("Actual native relevant chunks required");
        var values=new HashMap<Long,Object>();for(long key:footprint.chunks()){int x=(int)(key>>32),z=(int)key;var chunk=client.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
            if(chunk==null)throw new IllegalStateException("Relevant captured chunk unavailable");values.put(key,chunk);}
        return new McGhostSourceRelevantChunks(client,values);
    }
    boolean current(Minecraft client){if(!client.isSameThread()||client.player!=player||client.level!=world||client.getConnection()!=connection)return false;
        for(var e:chunks.entrySet()){int x=(int)(e.getKey()>>32),z=(int)(long)e.getKey();if(client.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)!=e.getValue())return false;}return true;}
}
