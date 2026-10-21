package dev.veltraph.client.greenhouse;

import com.google.gson.Gson;
import java.util.*;

/** Shared pending cells survive stale instance saves; only runtime-minted fresh resolutions retire them. */
final class GreenhouseSharedJournal {
    private GreenhouseSharedJournal(){}
    static Set<String> read(String json){
        if(json==null||json.isBlank())return Set.of();if(json.length()>65536)throw new IllegalArgumentException("Unresolved journal exceeds bounded size");
        String[] rows=new Gson().fromJson(json,String[].class);
        if(rows==null||rows.length>100||Arrays.stream(rows).anyMatch(c->c==null||c.isBlank()))throw new IllegalArgumentException("Invalid shared unresolved journal");
        return Set.copyOf(Arrays.asList(rows));
    }
    static Set<String> merge(Set<String> shared,Set<String> local,Set<String> freshlyResolved){
        var merged=new TreeSet<String>(shared);merged.addAll(local);
        // A pending local issued action always wins over resolution; no unresolved outcome is evicted.
        for(String resolved:freshlyResolved)if(!local.contains(resolved))merged.remove(resolved);
        if(merged.size()>100)throw new IllegalArgumentException("Shared unresolved journal exceeds100cells");return Set.copyOf(merged);
    }
}
