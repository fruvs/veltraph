package dev.veltraph.client.hunting;

import com.google.gson.*;
import dev.veltraph.client.game.GameArea;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit local replacement for encrypted Moonglade routes/tool/name/outcome constants. */
public record HuntingProfile(GameArea area,String dimension,Point min,Point max,List<Node> route,List<Target> targets,
    double radius,double minimumY,double reach,long actionDelayMs,long actionAckMs,long targetMs,long runMs,
    int maxActions,int maxOutcomes,int maxFailures,long blacklistMs,double airReserve) {
    public enum Medium { WALK,SWIM }
    public enum Outcome { DEATH_DROP,ATTRIBUTED_RECEIPT }
    public record Point(double x,double y,double z){public Point{if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>2048)throw new IllegalArgumentException("Invalid hunting coordinate");}}
    public record Node(Point position,Medium medium) {}
    public record Item(String id,String name){public Item{if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||name==null||name.isBlank())throw new IllegalArgumentException("Exact hunting item ID/name required");}}
    public record Target(String id,String entity,Pattern name,Item tool,int slot,Pattern pull,Pattern progress,long singleHitGraceMs,
        Outcome outcome,Item drop,int quantity,Pattern receipt,Item cost,int costPerUse,int maxCost,HuntingFlight.Contract flight) {
        /** Profiles predating explicit flight retain unknown metadata, never guessed Snowball physics. */
        public Target(String id,String entity,Pattern name,Item tool,int slot,Pattern pull,Pattern progress,long singleHitGraceMs,
            Outcome outcome,Item drop,int quantity,Pattern receipt,Item cost,int costPerUse,int maxCost){
            this(id,entity,name,tool,slot,pull,progress,singleHitGraceMs,outcome,drop,quantity,receipt,cost,costPerUse,maxCost,null);
        }
        public Target {
            if(id==null||id.isBlank()||!Set.of("minecraft:cod","minecraft:salmon","minecraft:tropical_fish").contains(entity)||slot<0||slot>8||quantity<1||quantity>64
                ||singleHitGraceMs<0||singleHitGraceMs>10_000||costPerUse<0||maxCost<0||costPerUse>maxCost||costPerUse>0&&cost==null)throw new IllegalArgumentException("Invalid source hunting target/tool/cost limits");
            if(pull!=null&&pull.matcher("").groupCount()!=1||progress!=null&&progress.matcher("").groupCount()!=1)throw new IllegalArgumentException("Pull/progress must have one numeric group");
            if(outcome==Outcome.ATTRIBUTED_RECEIPT&&(receipt==null||receipt.matcher("").groupCount()!=1))throw new IllegalArgumentException("Receipt must capture exact target ID/name attribution");
        }
    }
    public HuntingProfile {
        route=List.copyOf(route);targets=List.copyOf(targets);
        if(area==null||!area.isSkyBlock()||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||min.x()>max.x()||min.y()>max.y()||min.z()>max.z()
            ||max.x()-min.x()>256||max.y()-min.y()>128||max.z()-min.z()>256||route.size()>64||targets.isEmpty()||targets.size()>32
            ||!Double.isFinite(radius)||!Double.isFinite(reach)||!Double.isFinite(airReserve)||radius<1||radius>64||reach<1||reach>3||!Double.isFinite(minimumY)||actionDelayMs<50||actionDelayMs>10_000||actionAckMs<100||targetMs<actionAckMs||runMs<targetMs
            ||maxActions<1||maxActions>1000||maxOutcomes<1||maxOutcomes>128||maxFailures<1||maxFailures>3||blacklistMs<100||airReserve<0||airReserve>10)throw new IllegalArgumentException("Invalid hunting bounds/route/timing");
        if(targets.stream().map(Target::id).distinct().count()!=targets.size())throw new IllegalArgumentException("Duplicate hunting target ID");
        for(var node:route){var p=node.position();if(p.x()<min.x()||p.x()>max.x()||p.y()<min.y()||p.y()>max.y()||p.z()<min.z()||p.z()>max.z())throw new IllegalArgumentException("Route leaves hunting region");}
    }
    public boolean inside(Point p){return p.x()>=min.x()&&p.x()<=max.x()&&p.y()>=min.y()&&p.y()<=max.y()&&p.z()>=min.z()&&p.z()<=max.z();}
    public static HuntingProfile parse(String json){
        if(json==null||json.isBlank())throw new IllegalArgumentException("Explicit hunting JSON profile required");
        var parsed=JsonParser.parseString(json);if(!parsed.isJsonObject())throw new IllegalArgumentException("Hunting profile must be an object");
        var r=parsed.getAsJsonObject();List<Node> route=new ArrayList<>();
        if(r.has("route"))for(var e:r.getAsJsonArray("route")){var n=e.getAsJsonObject();route.add(new Node(point(n.getAsJsonArray("position")),Medium.valueOf(text(n,"medium").toUpperCase(Locale.ROOT))));}
        List<Target> targets=new ArrayList<>();for(var e:r.getAsJsonArray("targets")){
            var t=e.getAsJsonObject();targets.add(new Target(text(t,"id"),text(t,"entity"),Pattern.compile(text(t,"nameRegex")),item(t.getAsJsonObject("tool")),integer(t,"slot")-1,
                regex(t,"pullRegex"),regex(t,"progressRegex"),number(t,"singleHitGraceMs"),Outcome.valueOf(text(t,"outcome")),item(t.getAsJsonObject("drop")),integer(t,"quantity"),regex(t,"receiptRegex"),t.has("cost")?item(t.getAsJsonObject("cost")):null,integer(t,"costPerUse"),integer(t,"maxCost"),flight(t)));
        }
        return new HuntingProfile(GameArea.valueOf(text(r,"area")),text(r,"dimension"),point(r.getAsJsonArray("min")),point(r.getAsJsonArray("max")),route,targets,
            decimal(r,"radius"),decimal(r,"minimumY"),decimal(r,"reach"),number(r,"actionDelayMs"),number(r,"actionAckMs"),number(r,"targetMs"),number(r,"runMs"),integer(r,"maxActions"),integer(r,"maxOutcomes"),integer(r,"maxFailures"),number(r,"blacklistMs"),decimal(r,"airReserve"));
    }
    private static Point point(JsonArray a){if(a==null||a.size()!=3)throw new IllegalArgumentException("Three coordinates required");return new Point(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static Item item(JsonObject o){return new Item(text(o,"id"),text(o,"name"));}
    private static String text(JsonObject o,String key){if(o==null||!o.has(key))throw new IllegalArgumentException("Configure hunting "+key);return o.get(key).getAsString().strip();}
    private static long number(JsonObject o,String key){return o.get(key).getAsLong();}
    private static int integer(JsonObject o,String key){return o.get(key).getAsInt();}
    private static double decimal(JsonObject o,String key){return o.get(key).getAsDouble();}
    private static Pattern regex(JsonObject o,String key){return o.has(key)&&!o.get(key).getAsString().isBlank()?Pattern.compile(o.get(key).getAsString()):null;}
    private static HuntingFlight.Contract flight(JsonObject target){
        if(!target.has("flight"))return null;var value=target.getAsJsonObject("flight");
        return new HuntingFlight.Contract(HuntingFlight.Model.valueOf(text(value,"model")),integer(value,"maxTicks"),decimal(value,"maxTravel"),value.has("maxObservationLagTicks")?integer(value,"maxObservationLagTicks"):-1);
    }
}
