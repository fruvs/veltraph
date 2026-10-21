package dev.veltraph.client.greenhouse;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Current counts and offsets come only from visible native guide lore and explicit exact prefixes. */
public final class GreenhouseGuide {
    public record Binding(String title,String slotName,String itemId,String cropPrefix,String profileLine,String plotLine,
                          Map<String,String> ingredientCountPrefixes,String offsetPrefix) {
        public Binding { ingredientCountPrefixes=Map.copyOf(ingredientCountPrefixes); }
    }
    public record Recipe(String crop,Map<String,Integer> ingredients,Set<Cell> offsets,Context context,long observedAt,String source) {
        public Recipe { ingredients=Map.copyOf(ingredients);offsets=Set.copyOf(offsets); }
    }
    public static Optional<Recipe> read(MenuSnapshot m,Binding b,Context context,long now){
        if(context==null||!context.identified()||m==null||b==null||!m.title().equals(b.title())||b.cropPrefix()==null||b.cropPrefix().isBlank()||b.ingredientCountPrefixes().isEmpty()||b.ingredientCountPrefixes().size()>16)return Optional.empty();
        var slots=m.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(b.slotName())&&s.itemId().equals(b.itemId())&&s.lore().contains(b.profileLine())&&s.lore().contains(b.plotLine())).toList();
        if(slots.size()!=1)return Optional.empty();var lines=slots.getFirst().lore();
        var crop=unique(lines,b.cropPrefix());if(crop.isEmpty()||crop.get().isBlank())return Optional.empty();var counts=new HashMap<String,Integer>();
        try{for(var entry:b.ingredientCountPrefixes().entrySet()){if(entry.getKey().isBlank()||entry.getValue()==null||entry.getValue().isBlank())return Optional.empty();var v=unique(lines,entry.getValue());if(v.isEmpty())return Optional.empty();int n=Integer.parseInt(v.get());if(n<1||n>100)return Optional.empty();counts.put(entry.getKey(),n);}}
        catch(NumberFormatException invalid){return Optional.empty();}
        var offsets=new HashSet<Cell>();
        if(b.offsetPrefix()!=null&&!b.offsetPrefix().isBlank())try{for(var line:lines)if(line.startsWith(b.offsetPrefix())){var xyz=line.substring(b.offsetPrefix().length()).trim().split("\\s+");if(xyz.length!=3)return Optional.empty();var cell=new Cell(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));if(Math.abs((long)cell.x())>16||Math.abs((long)cell.y())>16||Math.abs((long)cell.z())>16||cell.equals(new Cell(0,0,0))||!offsets.add(cell))return Optional.empty();}}
        catch(NumberFormatException invalid){return Optional.empty();}
        return Optional.of(new Recipe(crop.get(),counts,offsets,context,now,"Observed native Guide: "+m.title()+" container="+m.id()+" revision="+m.revision()));
    }
    private static Optional<String> unique(List<String> lines,String prefix){var matches=lines.stream().filter(s->s.startsWith(prefix)).map(s->s.substring(prefix.length()).trim()).toList();return matches.size()==1?Optional.of(matches.getFirst()):Optional.empty();}
}
