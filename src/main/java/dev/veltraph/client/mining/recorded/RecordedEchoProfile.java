package dev.veltraph.client.mining.recorded;

import com.google.gson.*;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import java.util.*;

/** Explicit local bindings supplement the recovered design without inventing world coordinates. */
public record RecordedEchoProfile(float yaw,float pitch,List<Checkpoint> checkpoints) {
    public enum Key { LEFT_CLICK, RIGHT_CLICK, JUMP, SPRINT, SNEAK, FORWARD, LEFT, BACK, RIGHT }
    public record Placement(String item,BlockPos support,Direction face,BlockPos result,String block) {}
    public record Checkpoint(BlockPos position,Set<Key> keys,BlockPos leftTarget,Placement placement) {
        public Checkpoint { keys=Set.copyOf(keys); }
    }
    public RecordedEchoProfile { checkpoints=List.copyOf(checkpoints); }
    public static RecordedEchoProfile parse(String text) {
        if(text==null||text.isBlank()||text.length()>65536)throw new IllegalArgumentException("Configure a bounded recorded Echo design");
        var root=JsonParser.parseString(text).getAsJsonObject();
        only(root,"yaw","pitch","checkpoints");
        float yaw=root.get("yaw").getAsFloat(),pitch=root.get("pitch").getAsFloat();
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(yaw)>36000||Math.abs(pitch)>90)throw new IllegalArgumentException("Finite yaw and pitch within -90..90 required");
        var result=new ArrayList<Checkpoint>();
        for(var raw:root.getAsJsonArray("checkpoints")) {
            if(result.size()>=256)throw new IllegalArgumentException("At most 256 checkpoints");
            var c=raw.getAsJsonObject();only(c,"position","keys","leftTarget","placement");
            var keys=EnumSet.noneOf(Key.class);
            for(var k:c.getAsJsonArray("keys"))if(!keys.add(Key.valueOf(k.getAsString())))throw new IllegalArgumentException("Duplicate recorded key");
            BlockPos left=c.has("leftTarget")?pos(c.get("leftTarget")):null;
            Placement placement=null;
            if(c.has("placement")) {
                var p=c.getAsJsonObject("placement");only(p,"item","support","face","result","block");
                var face=Direction.valueOf(p.get("face").getAsString());var support=pos(p.get("support"));var target=pos(p.get("result"));
                if(!target.equals(support.offset(face)))throw new IllegalArgumentException("Placement result must adjoin the explicit support face");
                placement=new Placement(id(p.get("item")),support,face,target,id(p.get("block")));
            }
            if(keys.contains(Key.LEFT_CLICK)!=(left!=null)||keys.contains(Key.RIGHT_CLICK)!=(placement!=null))throw new IllegalArgumentException("Every click requires its exact local target binding");
            if(keys.contains(Key.LEFT_CLICK)&&keys.contains(Key.RIGHT_CLICK))throw new IllegalArgumentException("Concurrent attack and placement needs distinct item custody");
            if(keys.contains(Key.FORWARD)&&keys.contains(Key.BACK)||keys.contains(Key.LEFT)&&keys.contains(Key.RIGHT))throw new IllegalArgumentException("Opposing movement keys");
            result.add(new Checkpoint(pos(c.get("position")),keys,left,placement));
        }
        if(result.size()<2)throw new IllegalArgumentException("Recording needs an initial checkpoint and a final arrival checkpoint");
        if(!result.getLast().keys().isEmpty())throw new IllegalArgumentException("Final arrival checkpoint must release all keys");
        for(int i=1;i<result.size();i++)if(result.get(i).position().equals(result.get(i-1).position()))throw new IllegalArgumentException("Adjacent checkpoints need distinct positions");
        return new RecordedEchoProfile(yaw,pitch,result);
    }
    private static String id(JsonElement value){String s=value.getAsString();if(!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||s.length()>160)throw new IllegalArgumentException("Namespaced native item/block id required");return s;}
    private static BlockPos pos(JsonElement value) {
        var a=value.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Position requires three integers");
        int[] v=new int[3];for(int i=0;i<3;i++){var number=a.get(i).getAsBigDecimal();v[i]=number.intValueExact();}
        if(Math.abs((long)v[0])>30000000||Math.abs((long)v[2])>30000000||v[1]<-2048||v[1]>2048)throw new IllegalArgumentException("Position exceeds local bounds");
        return new BlockPos(v[0],v[1],v[2]);
    }
    private static void only(JsonObject object,String... names){var allowed=Set.of(names);for(var name:object.keySet())if(!allowed.contains(name))throw new IllegalArgumentException("Unknown recorded Echo field: "+name);}
}
