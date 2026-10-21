package dev.veltraph.client.hunting;
import java.util.*;
import java.util.regex.Pattern;

/** Exact FishingShardCatch patterns, full-line matching and catalog-known identities only. */
public final class ShardCaptureTracker {
    private static final Pattern CAUGHT=Pattern.compile("^You caught (?:a|an|x(\\d+)) (.+?) Shards?!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARM=Pattern.compile("^(?:CHARM|NAGA) You charmed a (.+?) and captured (?:(?:its|a) Shard|(\\d+) Shards(?: from it)?)\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANCE=Pattern.compile("^(.+?)\\s+Shard\\s*\\(\\s*([0-9]*\\.?[0-9]+)%\\s*\\)\\s*$",Pattern.CASE_INSENSITIVE);
    private final Map<String,Integer> counts=new LinkedHashMap<>();
    public boolean observe(String text,ShardCatalog catalog){
        String line=ShardCatalog.clean(text),name=null;int quantity=1;
        var charm=CHARM.matcher(line);var caught=CAUGHT.matcher(line);var chance=CHANCE.matcher(line);
        try{
            if(charm.matches()){name=charm.group(1);if(charm.group(2)!=null)quantity=Integer.parseInt(charm.group(2));}
            else if(caught.matches()){name=caught.group(2);if(caught.group(1)!=null)quantity=Integer.parseInt(caught.group(1));}
            else if(chance.matches())name=chance.group(1);
            if(name==null||quantity<1)return false;
            var identity=catalog.identity(name);if(identity.isEmpty())return false;
            counts.put(identity.get(),Math.addExact(counts.getOrDefault(identity.get(),0),quantity));return true;
        }catch(ArithmeticException|NumberFormatException invalid){return false;}
    }
    public Map<String,Integer> counts(){return Map.copyOf(counts);}
    public void reset(){counts.clear();}
}
