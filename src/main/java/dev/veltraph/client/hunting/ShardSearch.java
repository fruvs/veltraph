package dev.veltraph.client.hunting;
import java.util.Locale;

/** PurchaseHandler companion's recovered lowercase, 12..16 UTF-16-character search prefix. */
public final class ShardSearch {
    private ShardSearch() {}
    public static String query(String product,int prefixLength){
        if(prefixLength<12||prefixLength>16)throw new IllegalArgumentException("Source search length is 12..16");
        String value=ShardCatalog.clean(product).toLowerCase(Locale.ROOT);
        return value.substring(0,Math.min(value.length(),prefixLength));
    }
}
