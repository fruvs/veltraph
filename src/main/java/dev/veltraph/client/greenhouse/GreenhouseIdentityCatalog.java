package dev.veltraph.client.greenhouse;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned official item identities only. Never interpreted as recipes, timings or observations. */
public record GreenhouseIdentityCatalog(String source,String capturedAt,long lastUpdated,List<Entry> entries) {
    public record Entry(String id,String name,String tier,String itemModel,String category) {}
    public GreenhouseIdentityCatalog { entries=List.copyOf(entries); }
    public static GreenhouseIdentityCatalog bundled(){
        try(var stream=GreenhouseIdentityCatalog.class.getResourceAsStream("/greenhouse/identities-2026-09-30.json")){
            if(stream==null)return new GreenhouseIdentityCatalog("","",0,List.of());
            var catalog=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),GreenhouseIdentityCatalog.class);
            if(catalog==null||catalog.entries().size()>100||catalog.entries().stream().anyMatch(e->e.id()==null||e.id().isBlank()||e.name()==null||e.name().isBlank())||catalog.entries().stream().map(Entry::id).distinct().count()!=catalog.entries().size())throw new IllegalArgumentException("Invalid versioned identity catalog");
            return catalog;
        }catch(IOException|RuntimeException invalid){return new GreenhouseIdentityCatalog("","",0,List.of());}
    }
    public String diagnostic(){return entries.size()+" documented item identities; "+entries.stream().filter(e->"MUTATION".equals(e.category())).count()+" mutations; current recipes and care intervals unknown without Guide/Diagnostics";}
}
