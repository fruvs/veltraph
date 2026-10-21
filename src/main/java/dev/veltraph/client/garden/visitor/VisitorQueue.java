package dev.veltraph.client.garden.visitor;

import java.util.*;
import dev.veltraph.client.world.Vec3;

/** Immutable configured identities; source 4.0.30 order is three-dimensional reference-point distance. */
public final class VisitorQueue {
    public record Entry(UUID identity,String name,VisitorRarity rarity,double distance){
        public Entry(UUID identity,String name,VisitorRarity rarity){this(identity,name,rarity,0);}
        public Entry{Objects.requireNonNull(identity);if(name==null||name.isBlank()||!Double.isFinite(distance)||distance<0)throw new IllegalArgumentException("Invalid visitor label/distance");name=name.strip();rarity=rarity==null?VisitorRarity.UNKNOWN:rarity;}
    }
    private VisitorQueue(){}
    public static List<String> names(String list,String fallback,int maximum){
        if(maximum<1||maximum>32)throw new IllegalArgumentException("Visitor count cap must be between 1 and 32");
        String labels=list==null||list.isBlank()?fallback:list;var result=new ArrayList<String>();var unique=new HashSet<String>();
        if(labels!=null)for(String raw:labels.split(",",-1)){
            String name=raw.strip();if(name.isBlank()||!name.matches("[\\p{L}\\p{N} '\\-]{1,80}")||!unique.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Visitor labels must be distinct readable exact names");
            result.add(name);
        }
        if(result.isEmpty()||result.size()>maximum)throw new IllegalArgumentException("Configure one to "+maximum+" visitor names");return List.copyOf(result);
    }
    public static List<Entry> ordered(List<Entry> entries,SortOrder order){
        return ordered(entries,order,new Random());
    }
    public static List<Entry> ordered(List<Entry> entries,SortOrder order,Random random){
        var identities=new HashSet<UUID>();var names=new HashSet<String>();
        for(var e:entries)if(!identities.add(e.identity())||!names.add(e.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate visitor identity/label");
        if(order==SortOrder.RANDOM){var shuffled=new ArrayList<>(entries);Collections.shuffle(shuffled,random);return List.copyOf(shuffled);}
        var sorted=new ArrayList<>(entries.stream().sorted(Comparator.comparingDouble(Entry::distance).reversed()).toList());
        if(order==SortOrder.ASCENDING)Collections.reverse(sorted); // Source reverses the complete sorted list, including ties.
        return List.copyOf(sorted);
    }
    public static Vec3 origin(String value,Vec3 fallback){
        if(value==null||value.isBlank())return Objects.requireNonNull(fallback);
        String[] parts=value.strip().split("\\s*,\\s*|\\s+",-1);if(parts.length!=3)throw new IllegalArgumentException("Visitor order origin requires x,y,z");
        try{double x=Double.parseDouble(parts[0]),y=Double.parseDouble(parts[1]),z=Double.parseDouble(parts[2]);
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||y< -2048||y>2048)throw new NumberFormatException();
            return new Vec3(x,y,z);
        }catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid finite visitor order origin");}
    }
}
