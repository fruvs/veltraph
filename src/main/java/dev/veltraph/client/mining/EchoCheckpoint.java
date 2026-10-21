package dev.veltraph.client.mining;

import dev.veltraph.client.world.BlockPos;
import java.util.List;

/** Local readable subset of Echo recorded actions: supported walking followed by one block break. */
public record EchoCheckpoint(BlockPos standing,BlockPos target) {
    public static List<EchoCheckpoint> parse(String text) {
        if(text==null || text.isBlank()) return List.of();
        if(text.length()>16000)throw new IllegalArgumentException("Echo route is too long");
        return java.util.Arrays.stream(text.split(";",-1)).map(entry->{
            var pair=entry.trim().split(">",-1);
            if(pair.length!=2)throw new IllegalArgumentException("Use standing x,y,z>target x,y,z");
            return new EchoCheckpoint(pos(pair[0]),pos(pair[1]));
        }).toList();
    }
    private static BlockPos pos(String text) {
        var xyz=text.trim().split(",",-1);
        if(xyz.length!=3)throw new IllegalArgumentException("Echo coordinate needs x,y,z");
        var p=new BlockPos(Integer.parseInt(xyz[0].trim()),Integer.parseInt(xyz[1].trim()),Integer.parseInt(xyz[2].trim()));
        if(Math.abs((long)p.x())>30000000 || Math.abs((long)p.z())>30000000 || p.y() < -2048 || p.y()>2048)
            throw new IllegalArgumentException("Echo coordinate exceeds local bounds");
        return p;
    }
}
