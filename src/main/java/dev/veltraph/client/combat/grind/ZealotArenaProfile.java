package dev.veltraph.client.combat.grind;

import com.google.gson.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Local contracts replace encrypted ingress/spot/helmet strings, never guessed original coordinates. */
public record ZealotArenaProfile(Mode mode,String dimension,String gate,List<String> spots,String retreat,
        List<String> preferredBows,String cooldownPrefix,Set<GameArea> startAreas,String warpCommand,
        String warpReceipt,String warpRefusal,Vec3 warpLanding,long timeoutMs,long dragonQuietMs,
        String dragonMarker,boolean checkPlayers,double range,Beam beam) {
    public enum Mode { BOW, PRECURSOR }
    public record Beam(JsonObject helmet,String abilityLine,String offLore,String onLore,String offReceipt,
                       String onReceipt,int manaLow,int manaHigh,long manaFreshMs,long ackMs) {
        public Beam {helmet=helmet.deepCopy();}
        @Override public JsonObject helmet(){return helmet.deepCopy();}
    }
    public ZealotArenaProfile {spots=List.copyOf(spots);preferredBows=List.copyOf(preferredBows);startAreas=Set.copyOf(startAreas);}
    public static ZealotArenaProfile parse(String json){
        if(json==null||json.isBlank()||json.length()>262144)throw new IllegalArgumentException("Explicit bounded Zealot arena profile required");
        try {
            var o=JsonParser.parseString(json).getAsJsonObject();Mode mode=Mode.valueOf(text(o,"mode"));String dim=text(o,"dimension");
            if(!dim.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Explicit arena dimension required");
            List<String> spots=strings(o,"spots",1,11),bows=strings(o,"preferredBows",mode==Mode.BOW?1:0,16);
            var areas=new HashSet<GameArea>();for(String s:strings(o,"startAreas",1,2)){var a=GameArea.valueOf(s);if(a!=GameArea.HUB&&a!=GameArea.THE_END)throw new IllegalArgumentException("Only observed Hub/End ingress supported");areas.add(a);}
            String command=text(o,"warpCommand"),receipt=text(o,"warpReceipt"),refusal=text(o,"warpRefusal");Vec3 landing=null;
            if(!command.equals(command.strip())||!receipt.equals(receipt.strip())||!refusal.equals(refusal.strip()))throw new IllegalArgumentException("Exact unpadded warp contracts required");
            if(!command.isBlank()){
                if(!(command.startsWith("warp ")||command.startsWith("veltraph_private_zealot_"))||command.startsWith("/")||command.contains("\n")||command.contains("\r")||receipt.isBlank()||refusal.isBlank()||receipt.equals(refusal))throw new IllegalArgumentException("Exact single warp command/received/refusal contracts required");
                var p=o.getAsJsonObject("warpLanding");landing=new Vec3(number(p,"x"),number(p,"y"),number(p,"z"));new LocalRoute.Node(landing,0,0);
            }else if(areas.stream().anyMatch(a->a!=GameArea.THE_END))throw new IllegalArgumentException("Hub ingress requires explicit received warp contract");
            long timeout=bounded(o,"timeoutMs",1000,60000),quiet=bounded(o,"dragonQuietMs",0,60000);double range=number(o,"range");if(range<3||range>300)throw new IllegalArgumentException("Arena range must be3..300");
            Beam beam=null;if(mode==Mode.PRECURSOR){var b=o.getAsJsonObject("beam");if(b==null||!b.has("helmet")||!b.get("helmet").isJsonObject())throw new IllegalArgumentException("Exact native helmet stack required");
                int low=Math.toIntExact(bounded(b,"manaLow",0,Integer.MAX_VALUE)),high=Math.toIntExact(bounded(b,"manaHigh",1,Integer.MAX_VALUE));
                if(low>=high)throw new IllegalArgumentException("Explicit lower<upper absolute mana range required");
                beam=new Beam(b.getAsJsonObject("helmet"),required(b,"abilityLine"),required(b,"offLore"),required(b,"onLore"),required(b,"offReceipt"),required(b,"onReceipt"),low,high,bounded(b,"manaFreshMs",100,5000),bounded(b,"ackMs",100,15000));
                if(beam.offLore().equals(beam.onLore())||beam.offReceipt().equals(beam.onReceipt())||beam.abilityLine().equals(beam.offLore())||beam.abilityLine().equals(beam.onLore()))throw new IllegalArgumentException("Distinct beam states required");
            }
            return new ZealotArenaProfile(mode,dim,required(o,"gate"),spots,required(o,"retreat"),bows,text(o,"cooldownPrefix"),areas,command,receipt,refusal,landing,timeout,quiet,text(o,"dragonMarker"),bool(o,"checkPlayers"),range,beam);
        }catch(IllegalArgumentException e){throw e;}catch(RuntimeException e){throw new IllegalArgumentException("Invalid explicit arena profile",e);}
    }
    /** Hub is the ingress world; only an already-arrived End start requires the destination dimension. */
    public boolean permitsStart(GameArea area,String currentDimension){return startAreas.contains(area)&&(area==GameArea.HUB||dimension.equals(currentDimension));}
    public List<LocalRoute> load(LocalRouteStore store){
        List<LocalRoute> all=new ArrayList<>();var names=new ArrayList<String>();names.add(gate);names.addAll(spots);names.add(retreat);
        for(String name:names)try{var route=store.load(name);if(route.area()!=GameArea.THE_END||!route.dimension().equals(dimension)||route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.FLY))throw new IllegalArgumentException("Arena routes require same End dimension and WALK/ETHER nodes");all.add(route);}catch(java.io.IOException e){throw new IllegalArgumentException("Arena route unavailable: "+name,e);}
        return List.copyOf(all);
    }
    private static boolean bool(JsonObject o,String k){if(!o.has(k)||!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Explicit "+k+" boolean required");return o.get(k).getAsBoolean();}
    private static String required(JsonObject o,String k){String s=text(o,k);if(s.isBlank())throw new IllegalArgumentException("Explicit "+k+" required");return s;}
    private static String text(JsonObject o,String k){if(o==null||!o.has(k)||!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Explicit "+k+" string required");return o.get(k).getAsString();}
    private static List<String> strings(JsonObject o,String k,int min,int max){if(!o.has(k)||!o.get(k).isJsonArray())throw new IllegalArgumentException("Explicit "+k+" list required");var a=o.getAsJsonArray(k);if(a.size()<min||a.size()>max)throw new IllegalArgumentException("Invalid "+k+" size");List<String> out=new ArrayList<>();for(var v:a){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isBlank()||out.contains(v.getAsString()))throw new IllegalArgumentException("Distinct nonempty "+k+" entries required");out.add(v.getAsString());}return out;}
    private static double number(JsonObject o,String k){if(o==null||!o.has(k)||!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Explicit "+k+" number required");double v=o.get(k).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException("Finite "+k+" required");return v;}
    private static long bounded(JsonObject o,String k,long min,long max){double v=number(o,k);if(v!=Math.rint(v)||v<min||v>max)throw new IllegalArgumentException("Invalid explicit "+k);return (long)v;}
}
