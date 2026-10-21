package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.regex.Pattern;
/** Exact readable stable4.0.30 local cookie HUD grammar. Cached positive is process-lived; no age/PWC reset is invented. */
public final class GhostSourceCookiePredicate {
    public static final String FOOTER_PATTERN="Cookie Buff(?:§.)*(?:\\n(§.)*§7.+)*";
    private static final Pattern COOKIE=Pattern.compile(FOOTER_PATTERN),FORMATTING=Pattern.compile("(?i)§[0-9A-FK-OR]");
    private boolean cached;
    public boolean observe(String rawFooter,List<String> rawOrderedRows){
        String footer=normalize(rawFooter);
        if(footer!=null&&COOKIE.matcher(footer).find()){
            try{String tail=footer.substring(footer.indexOf("Cookie Buff"));tail=tail.substring(0,tail.indexOf("§s"));cached=!tail.contains("Not active!");}
            catch(RuntimeException malformed){cached=false;}
        }
        if(!cached){var rows=rawOrderedRows==null?List.<String>of():rawOrderedRows.stream().filter(java.util.Objects::nonNull).map(GhostSourceCookiePredicate::normalize).filter(s->!s.isEmpty()).toList();
            for(int i=0;i<rows.size();i++){String row=rows.get(i);if(!row.contains("Cookie Buff"))continue;
                if(!row.contains("Not active!")){cached=true;break;}
                String next=i+1<rows.size()?rows.get(i+1):null;if(next!=null&&next.trim().startsWith("Not active"))break;
                cached=true;break;
            }
        }
        return cached;
    }
    static String normalize(String value){return value==null?null:FORMATTING.matcher(value).replaceAll("").trim();}
    public boolean cachedValue(){return cached;}
}
