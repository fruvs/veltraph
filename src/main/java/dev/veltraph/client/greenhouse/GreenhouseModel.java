package dev.veltraph.client.greenhouse;

import java.util.*;

/** Local server observations. Time passing alone never creates plant facts. */
public final class GreenhouseModel {
    private GreenhouseModel() {}
    public enum Knowledge { KNOWN, STALE, UNKNOWN }
    public enum PlantState { EMPTY, GROWING, HARVEST_READY, DEAD, UNKNOWN }
    public enum WaterState { WATERED, DRY, UNKNOWN }
    public enum ClockKind { SERVER_EPOCH, GROWTH_CYCLE, UNKNOWN }
    public record Context(String profile, String plot, String world) {
        public boolean identified() { return nonblank(profile) && nonblank(plot) && nonblank(world); }
    }
    public record Cell(int x,int y,int z) implements Comparable<Cell> {
        public int compareTo(Cell other) { int n=Integer.compare(x,other.x);if(n==0)n=Integer.compare(y,other.y);return n==0?Integer.compare(z,other.z):n; }
    }
    public record Position(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public double distanceSquared(Position p){return Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2);}
    }
    /** Clock values are copied from diagnostics; missing or ambiguous values stay UNKNOWN. */
    public record Clock(ClockKind kind,long value,String source) {
        public Clock { Objects.requireNonNull(kind); source=source==null?"":source; }
        public boolean known(){return kind!=ClockKind.UNKNOWN&&value>=0&&!source.isBlank();}
        public static Clock unknown(){return new Clock(ClockKind.UNKNOWN,0,"");}
    }
    public record Plant(String crop,PlantState state,WaterState water,int growthStage,
                        Clock nextGrowth,Clock waterExpiry,Clock decayExpiry,String fingerprint,String source) {
        public Plant { crop=crop==null?"":crop;Objects.requireNonNull(state);Objects.requireNonNull(water);
            Objects.requireNonNull(nextGrowth);Objects.requireNonNull(waterExpiry);Objects.requireNonNull(decayExpiry);
            fingerprint=fingerprint==null?"":fingerprint;source=source==null?"":source; }
        public boolean identified(){return !fingerprint.isBlank()&&!source.isBlank()&&state!=PlantState.UNKNOWN&&(state==PlantState.EMPTY||!crop.isBlank());}
    }
    /** A revision must advance only on a new server observation, never on client ticks. */
    public record Snapshot(Context context,long observedAt,long revision,Map<Cell,Plant> plants,
                           Map<String,Integer> inventory,String toolIdentity,int toolSlot,int selectedSlot,
                           Position position,boolean screenFree,boolean alive,boolean loaded) {
        public Snapshot { Objects.requireNonNull(context);plants=Map.copyOf(plants);inventory=Map.copyOf(inventory);
            toolIdentity=toolIdentity==null?"":toolIdentity;Objects.requireNonNull(position); }
        public Knowledge knowledge(long now,long maxAge){
            if(!context.identified()||observedAt<0||revision<0||!loaded||!alive||!position.finite())return Knowledge.UNKNOWN;
            if(now<observedAt||maxAge<1||now-observedAt>maxAge)return Knowledge.STALE;
            return Knowledge.KNOWN;
        }
        public boolean inventoryValid(){return inventory.entrySet().stream().allMatch(e->nonblank(e.getKey())&&e.getValue()!=null&&e.getValue()>=0);}
    }
    public record ObservationKey(Context context,Cell cell) {}
    public static final class Store {
        private final Map<ObservationKey,Entry> entries=new HashMap<>();
        public record Entry(Plant plant,long observedAt,long revision) {}
        public void observe(Snapshot s){for(var e:s.plants().entrySet()){
            var key=new ObservationKey(s.context(),e.getKey());var previous=entries.get(key);
            if(previous==null||(s.revision()>previous.revision()&&s.observedAt()>=previous.observedAt()))entries.put(key,new Entry(e.getValue(),s.observedAt(),s.revision()));
        }}
        public Optional<Entry> get(Context c,Cell cell){return Optional.ofNullable(entries.get(new ObservationKey(c,cell)));}
        public Knowledge knowledge(Context c,Cell cell,long now,long maxAge){var e=get(c,cell);if(e.isEmpty()||!e.get().plant().identified())return Knowledge.UNKNOWN;
            return now<e.get().observedAt()||now-e.get().observedAt()>maxAge?Knowledge.STALE:Knowledge.KNOWN;}
        public Map<ObservationKey,Entry> entries(){return Map.copyOf(entries);}
        public void restore(Map<ObservationKey,Entry> saved){entries.clear();entries.putAll(saved);}
    }
    private static boolean nonblank(String s){return s!=null&&!s.isBlank();}
}
