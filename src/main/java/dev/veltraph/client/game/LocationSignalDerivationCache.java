package dev.veltraph.client.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One immutable text derivation only. Callers must still collect every native signal on every read. */
public final class LocationSignalDerivationCache {
    private Entry latest;
    private static final class Raw {
        final String brand,address,title,footer;
        final List<String> rows,tab;
        Raw(String brand,String address,String title,List<String> rows,String footer,List<String> tab){
            this.brand=brand;this.address=address;this.title=title;this.rows=copy(rows);this.footer=footer;this.tab=copy(tab);
        }
        private static List<String> copy(List<String> source){return source==null?null:Collections.unmodifiableList(new ArrayList<>(source));}
        boolean same(Raw other){return other!=null&&Objects.equals(brand,other.brand)&&Objects.equals(address,other.address)
            &&Objects.equals(title,other.title)&&Objects.equals(rows,other.rows)&&Objects.equals(footer,other.footer)&&Objects.equals(tab,other.tab);}
    }
    private static final class Entry {
        final Raw raw;final LocationSignals signals;LocationResolution resolution;
        Entry(Raw raw){this.raw=raw;signals=LocationSignals.of(raw.brand,raw.address,raw.title,raw.rows,raw.footer,raw.tab);}
    }
    /** Entire freshly read raw tuple, including row ordering/nulls/case/formatting, is copied before reuse. */
    public LocationSignals signals(String brand,String address,String title,List<String> rows,String footer,List<String> tab){
        var raw=new Raw(brand,address,title,rows,footer,tab);var current=latest;
        if(current!=null&&current.raw.same(raw))return current.signals;
        var created=new Entry(raw);latest=created;return created.signals;
    }
    /** Pure resolver result only; an unrelated/stale input never borrows the latest input's result. */
    public LocationResolution resolve(LocationSignals actual){
        var current=latest;
        if(current==null||current.signals!=actual)return GameContextResolver.resolve(actual);
        var result=current.resolution;
        if(result==null){result=GameContextResolver.resolve(actual);current.resolution=result;}
        return result;
    }
}
