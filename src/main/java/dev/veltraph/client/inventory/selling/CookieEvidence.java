package dev.veltraph.client.inventory.selling;

import com.google.gson.JsonParser;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Fresh source footer-region/tab-neighbor evidence with explicit unrecovered labels. */
public final class CookieEvidence {
    private CookieEvidence() {}
    public record Grammar(String footerStart,String footerEnd,String footerInactive,String tabMarker,
                          String tabInlineInactive,String tabNextInactivePrefix) {
        public Grammar {
            for(String value:List.of(footerStart,footerEnd,footerInactive,tabMarker,tabInlineInactive,tabNextInactivePrefix))
                if(value==null||MenuSnapshot.clean(value).isBlank()||value.length()>256)throw new IllegalArgumentException("All six observed cookie labels required");
            footerStart=MenuSnapshot.clean(footerStart);footerEnd=MenuSnapshot.clean(footerEnd);footerInactive=MenuSnapshot.clean(footerInactive);
            tabMarker=MenuSnapshot.clean(tabMarker);tabInlineInactive=MenuSnapshot.clean(tabInlineInactive);tabNextInactivePrefix=MenuSnapshot.clean(tabNextInactivePrefix);
            if(footerStart.equals(footerEnd))throw new IllegalArgumentException("Cookie region bounds must differ");
        }
        public static Grammar parse(String json){
            var object=JsonParser.parseString(json).getAsJsonObject();
            var names=List.of("footerStart","footerEnd","footerInactive","tabMarker","tabInlineInactive","tabNextInactivePrefix");
            if(!object.keySet().equals(Set.copyOf(names)))throw new IllegalArgumentException("Exact six-field cookie grammar required");
            for(String name:names)if(!object.get(name).isJsonPrimitive()||!object.get(name).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Cookie labels must be literal strings");
            return new Grammar(object.get(names.get(0)).getAsString(),object.get(names.get(1)).getAsString(),object.get(names.get(2)).getAsString(),
                object.get(names.get(3)).getAsString(),object.get(names.get(4)).getAsString(),object.get(names.get(5)).getAsString());
        }
    }
    private static int occurrences(String body,String token){return (body.length()-body.replace(token,"").length())/token.length();}
    public static boolean active(String footer,List<String> orderedTab,String exactLine,String grammarJson){
        String current=footer==null?"":MenuSnapshot.clean(footer);
        if(grammarJson==null||grammarJson.isBlank()){
            String expected=MenuSnapshot.clean(exactLine==null?"":exactLine);
            return !expected.isBlank()&&Arrays.stream(current.split("\\R")).map(MenuSnapshot::clean).filter(expected::equals).count()==1;
        }
        final Grammar grammar;try{grammar=Grammar.parse(grammarJson);}catch(RuntimeException invalid){return false;}
        if(occurrences(current,grammar.footerStart)>1||occurrences(current,grammar.footerEnd)>1)return false;
        int start=current.indexOf(grammar.footerStart),end=start<0?-1:current.indexOf(grammar.footerEnd,start+grammar.footerStart.length());
        if(start>=0&&end>start&&!current.substring(start,end).contains(grammar.footerInactive))return true;
        var lines=orderedTab==null?List.<String>of():orderedTab.stream().map(MenuSnapshot::clean).toList();
        var matches=new ArrayList<Integer>();for(int i=0;i<lines.size();i++)if(lines.get(i).contains(grammar.tabMarker))matches.add(i);
        if(matches.size()!=1)return false;int row=matches.getFirst();String line=lines.get(row);
        if(!line.contains(grammar.tabInlineInactive))return true;
        // Source fallback accepts a marker carrying the inline inactive label unless its next
        // displayed line begins with the second inactive label. Never cache that decision.
        return row+1<lines.size()&&!lines.get(row+1).strip().startsWith(grammar.tabNextInactivePrefix);
    }
    /** The source's exceptional zero-empty-slot capability is FISHING (BAF3196–3208). */
    public static boolean pressureReady(int empty,int configuredMaximum,boolean fishing){
        return empty>=0&&configuredMaximum>=0&&configuredMaximum<=2&&empty<=(fishing?0:configuredMaximum);
    }
}
