package dev.veltraph.client.greenhouse;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import java.util.regex.*;

/** Automatic read-only profile from the completed October1 native menus. No target/location inference. */
public final class GreenhouseCapturedMenus {
    private GreenhouseCapturedMenus() {}
    public enum Kind { CARPENTER, CROP_GUIDE, MUTATIONS, LAYOUT_PREVIEW, DIAGNOSTICS }
    public enum PlantKind { BASE, MUTATION }
    public enum Disclosure { VISIBLE_COUNTS, ZERO_ADJACENT_CROPS, HIDDEN, SECRET, ABSENT }
    public enum Status { GROWING, DEAD, UNKNOWN }
    public record Source(String title,int containerId,int revision,long observedAtMillis,String provenance) {}
    public record Effect(String name,List<String> description) { public Effect {description=List.copyOf(description);} }
    public record Definition(String name,String nativeMenuItem,PlantKind kind,String size,List<String> surfaces,int growthStages,
                             List<Effect> effects,Source source,int slot) {
        public Definition {surfaces=List.copyOf(surfaces);effects=List.copyOf(effects);}
    }
    public record Mutation(Definition definition,Disclosure disclosure,Map<String,Integer> requirements,
                           boolean discovered,boolean discoveryKnown,List<String> conditionLines) {
        public Mutation {requirements=Map.copyOf(requirements);conditionLines=List.copyOf(conditionLines);}
        public boolean countsKnown(){return disclosure==Disclosure.VISIBLE_COUNTS||disclosure==Disclosure.ZERO_ADJACENT_CROPS;}
    }
    /** Relative remaining duration copied from lore. Never a server epoch or universal care interval. */
    public record Duration(String text,long seconds,Source source,int slot,String prefix) {}
    public record Stage(int current,int total) {}
    public record Diagnostics(Definition definition,Status status,String rawStatus,String causeOfDeath,Optional<Stage> stage,
                              Optional<Duration> age,Optional<Duration> nextStage,Optional<Duration> decay,
                              OptionalInt water,Optional<Duration> waterRemaining,boolean enoughWaterExplicit,
                              boolean deadNeedsNoWaterExplicit,List<Effect> applies,List<Effect> receives,
                              boolean receivedEffectsLockedExplicit,Source source) {
        public Diagnostics {applies=List.copyOf(applies);receives=List.copyOf(receives);}
        public boolean actionableCellKnown(){return false;}
        public String summary(){return definition.name()+": "+rawStatus+(causeOfDeath.isBlank()?"":" ("+causeOfDeath+")")+
            " | stage="+stage.map(s->s.current()+"/"+s.total()).orElse("unknown")+" | water="+(water.isPresent()?water.getAsInt()+"/100":"unknown")+
            " | next stage="+nextStage.map(Duration::text).orElse("unknown")+" | water lasts="+waterRemaining.map(Duration::text).orElse("unknown")+
            " | decay="+decay.map(Duration::text).orElse("unknown")+" | target/profile/dimension unbound";}
    }
    /** These are menu-grid positions, not world coordinates or a universal adjacency metric. */
    public record LayoutCell(int slot,int row,int column,String crop,boolean target,boolean anyOrderExplicit) {}
    public record Layout(String mutation,List<LayoutCell> cells,Source source) {public Layout {cells=List.copyOf(cells);} }
    public record Carpenter(OptionalInt analyzed,OptionalInt total,List<String> uniqueBonusLore,List<String> slotRows,Source source) {
        public Carpenter {uniqueBonusLore=List.copyOf(uniqueBonusLore);slotRows=List.copyOf(slotRows);}
    }
    public record Observation(Kind kind,Source source,List<Definition> crops,List<Mutation> mutations,
                              Optional<Diagnostics> diagnostics,Optional<Layout> layout,Optional<Carpenter> carpenter) {
        public Observation {crops=List.copyOf(crops);mutations=List.copyOf(mutations);}
    }
    private static final Pattern COUNT=Pattern.compile("([1-9][0-9]*)x (.+)");
    private static final Pattern STAGE=Pattern.compile("([0-9]+)/([0-9]+)");
    private static final Pattern WATER=Pattern.compile("(-?[0-9]+)/100");
    private static final Pattern DURATION=Pattern.compile("([0-9]+)([dhms])");
    public static Optional<Observation> read(MenuSnapshot m,long observedAt,String provenance){
        if(m==null||m.slots().size()>150||observedAt<0||provenance==null||provenance.isBlank())return Optional.empty();
        var source=new Source(m.title(),m.id(),m.revision(),observedAt,provenance);
        if(m.title().equals("Crop Diagnostics"))return diagnostics(m,source).map(d->new Observation(Kind.DIAGNOSTICS,source,List.of(),List.of(),Optional.of(d),Optional.empty(),Optional.empty()));
        if(m.title().equals("Mutation Layout Preview"))return layout(m,source).map(l->new Observation(Kind.LAYOUT_PREVIEW,source,List.of(),List.of(),Optional.empty(),Optional.of(l),Optional.empty()));
        if(m.title().equals("Carpenter"))return carpenter(m,source).map(c->new Observation(Kind.CARPENTER,source,List.of(),List.of(),Optional.empty(),Optional.empty(),Optional.of(c)));
        boolean guide=m.title().equals("Crop Guide"),mutations=Set.of("(1/2) All Mutations","(2/2) All Mutations").contains(m.title());
        if(!guide&&!mutations)return Optional.empty();
        var definitions=new ArrayList<Definition>();var recipes=new ArrayList<Mutation>();var names=new HashSet<String>();
        for(var row:m.slots())if(nativeRow(row)){
            var definition=definition(row,source);if(definition.isEmpty())continue;var d=definition.get();
            if(!names.add(d.name()))return Optional.empty();
            if(guide&&d.kind()==PlantKind.BASE)definitions.add(d);
            if(mutations&&d.kind()==PlantKind.MUTATION){var recipe=mutation(d,row.lore());if(recipe.isEmpty())return Optional.empty();recipes.add(recipe.get());}
        }
        if(definitions.isEmpty()&&recipes.isEmpty())return Optional.empty();
        return Optional.of(new Observation(guide?Kind.CROP_GUIDE:Kind.MUTATIONS,source,definitions,recipes,Optional.empty(),Optional.empty(),Optional.empty()));
    }
    private static Optional<Definition> definition(MenuSnapshot.Slot row,Source source){
        if(!nativeRow(row)||row.name().isBlank())return Optional.empty();boolean base=row.lore().contains("Base Crop"),mutation=row.lore().contains("Mutation Crop");
        if(base==mutation)return Optional.empty();
        Optional<String> size=field(row.lore(),"Size: "),surface=field(row.lore(),"Growth Surface: "),stages=field(row.lore(),"Growth Stages: ");
        if(size.isEmpty()||!size.get().matches("[1-9][0-9]*x[1-9][0-9]*")||surface.isEmpty()||stages.isEmpty())return Optional.empty();
        try{int count=Integer.parseInt(stages.get());if(count<0||count>10000)return Optional.empty();return Optional.of(new Definition(row.name(),row.itemId(),base?PlantKind.BASE:PlantKind.MUTATION,size.get(),Arrays.asList(surface.get().split(", ")),count,effects(row.lore()),source,row.id()));}
        catch(NumberFormatException invalid){return Optional.empty();}
    }
    private static Optional<Mutation> mutation(Definition d,List<String> lore){
        int index=lore.indexOf("Spreading Conditions");if(index<0||lore.lastIndexOf("Spreading Conditions")!=index)return Optional.empty();var lines=new ArrayList<String>();
        for(int i=index+1;i<lore.size()&&!lore.get(i).isBlank();i++)lines.add(lore.get(i));
        var counts=new LinkedHashMap<String,Integer>();Disclosure disclosure;
        if(lines.equals(List.of("???")))disclosure=Disclosure.HIDDEN;
        else if(lines.equals(List.of("Secret")))disclosure=Disclosure.SECRET;
        else if(lines.equals(List.of("0 adjacent crops")))disclosure=Disclosure.ZERO_ADJACENT_CROPS;
        else if(lines.isEmpty())disclosure=Disclosure.ABSENT;
        else{disclosure=Disclosure.VISIBLE_COUNTS;for(String line:lines){var matcher=COUNT.matcher(line);if(!matcher.matches())return Optional.empty();try{int n=Integer.parseInt(matcher.group(1));if(n>100||counts.putIfAbsent(matcher.group(2),n)!=null)return Optional.empty();}catch(NumberFormatException invalid){return Optional.empty();}}}
        boolean discovered=lore.contains("DISCOVERED"),unknown=lore.contains("UNKNOWN");if(discovered&&unknown)return Optional.empty();
        return Optional.of(new Mutation(d,disclosure,counts,discovered,discovered||unknown,lines));
    }
    private static Optional<Diagnostics> diagnostics(MenuSnapshot m,Source s){
        Optional<MenuSnapshot.Slot> crop=row(m,4,null,null),growth=row(m,20,"Growth Stage","minecraft:jungle_sapling"),water=row(m,21,"Water Level","minecraft:water_bucket"),applies=row(m,22,"Applies Effects","minecraft:golden_apple"),receives=row(m,23,"Has Effects","minecraft:nether_star"),status=row(m,24,"Growth Status","minecraft:beacon");
        if(crop.isEmpty()||growth.isEmpty()||water.isEmpty()||applies.isEmpty()||receives.isEmpty()||status.isEmpty())return Optional.empty();
        var definition=definition(crop.get(),s);if(definition.isEmpty())return Optional.empty();Optional<String> rawStatus=field(status.get().lore(),"Status: "),rawStage=field(growth.get().lore(),"Stage: "),rawWater=field(water.get().lore(),"Water: ");
        if(rawStatus.isEmpty()||rawStage.isEmpty()||rawWater.isEmpty())return Optional.empty();
        Status parsedStatus=switch(rawStatus.get()){case "Growing"->Status.GROWING;case "DEAD"->Status.DEAD;default->Status.UNKNOWN;};
        Optional<Stage> stage=Optional.empty();var ratio=STAGE.matcher(rawStage.get());
        if(ratio.matches())try{int current=Integer.parseInt(ratio.group(1)),total=Integer.parseInt(ratio.group(2));if(current>total||total!=definition.get().growthStages())return Optional.empty();stage=Optional.of(new Stage(current,total));}catch(NumberFormatException invalid){return Optional.empty();}
        else if(!rawStage.get().equals("DEAD"))return Optional.empty();
        if((parsedStatus==Status.DEAD)!=rawStage.get().equals("DEAD")&&parsedStatus!=Status.UNKNOWN)return Optional.empty();
        var level=WATER.matcher(rawWater.get());if(!level.matches())return Optional.empty();int amount;try{amount=Integer.parseInt(level.group(1));if(amount< -100||amount>100)return Optional.empty();}catch(NumberFormatException invalid){return Optional.empty();}
        String joined=String.join(" ",water.get().lore()),received=String.join(" ",receives.get().lore());
        return Optional.of(new Diagnostics(definition.get(),parsedStatus,rawStatus.get(),field(status.get().lore(),"Cause of Death: ").orElse(""),stage,
            duration(growth.get(),"Age: ",s),duration(growth.get(),"Next Stage: ",s),duration(growth.get(),"Decay: ",s),OptionalInt.of(amount),duration(water.get(),"Lasts for: ",s),
            joined.contains("This crop has enough water to reach full growth!"),water.get().lore().contains("Dead crops don't need water!"),effects(applies.get().lore()),effects(receives.get().lore()),
            received.contains("This crop's effects are locked to what they were when it first became harvestable."),s));
    }
    private static Optional<Layout> layout(MenuSnapshot m,Source s){
        var targets=m.slots().stream().filter(GreenhouseCapturedMenus::nativeRow).filter(r->r.lore().equals(List.of("Will spread here!"))).toList();if(targets.size()!=1)return Optional.empty();
        var target=targets.getFirst();var cells=new ArrayList<LayoutCell>();cells.add(new LayoutCell(target.id(),target.id()/9,target.id()%9,target.name(),true,false));
        for(var row:m.slots())if(nativeRow(row)&&row.lore().equals(List.of("These can be placed in any order!")))cells.add(new LayoutCell(row.id(),row.id()/9,row.id()%9,row.name(),false,true));
        if(cells.size()<2||cells.size()>100||cells.stream().anyMatch(c->c.crop().isBlank()))return Optional.empty();return Optional.of(new Layout(target.name(),cells,s));
    }
    private static Optional<Carpenter> carpenter(MenuSnapshot m,Source s){
        Optional<MenuSnapshot.Slot> guide=row(m,20,"Crop Guide","minecraft:wheat_seeds"),mutations=row(m,22,"All Mutations","minecraft:painting"),slots=row(m,24,"Unlocking Plant Slots","minecraft:dirt");
        if(guide.isEmpty()&&mutations.isEmpty()&&slots.isEmpty())return Optional.empty();OptionalInt analyzed=OptionalInt.empty(),total=OptionalInt.empty();
        if(mutations.isPresent()){var value=field(mutations.get().lore(),"Total Analyzed: ");if(value.isPresent()){var match=STAGE.matcher(value.get());if(match.matches())try{int a=Integer.parseInt(match.group(1)),t=Integer.parseInt(match.group(2));if(a<=t){analyzed=OptionalInt.of(a);total=OptionalInt.of(t);}}catch(NumberFormatException ignored){}}}
        return Optional.of(new Carpenter(analyzed,total,guide.map(MenuSnapshot.Slot::lore).orElse(List.of()),slots.map(r->r.lore().stream().filter(l->l.contains("█")).toList()).orElse(List.of()),s));
    }
    public static Optional<Duration> parseDuration(String text,Source source,int slot,String prefix){
        if(text==null||text.isBlank()||text.length()>100)return Optional.empty();long seconds=0;int lastRank=-1,index=0;var matcher=DURATION.matcher(text);
        try{while(matcher.find()){if(!text.substring(index,matcher.start()).isBlank())return Optional.empty();String unit=matcher.group(2);int rank="dhms".indexOf(unit);if(rank<=lastRank)return Optional.empty();lastRank=rank;long n=Long.parseLong(matcher.group(1));long factor=switch(unit){case "d"->86400;case "h"->3600;case "m"->60;default->1;};seconds=Math.addExact(seconds,Math.multiplyExact(n,factor));index=matcher.end();}
            if(index==0||!text.substring(index).isBlank())return Optional.empty();return Optional.of(new Duration(text,seconds,source,slot,prefix));}
        catch(ArithmeticException|NumberFormatException invalid){return Optional.empty();}
    }
    private static Optional<Duration> duration(MenuSnapshot.Slot row,String prefix,Source source){return field(row.lore(),prefix).flatMap(value->parseDuration(value,source,row.id(),prefix));}
    private static Optional<MenuSnapshot.Slot> row(MenuSnapshot m,int id,String name,String item){var rows=m.slots().stream().filter(r->r.id()==id&&nativeRow(r)&&(name==null||r.name().equals(name))&&(item==null||r.itemId().equals(item))).toList();return rows.size()==1?Optional.of(rows.getFirst()):Optional.empty();}
    private static boolean nativeRow(MenuSnapshot.Slot s){return !s.playerInventory()&&s.count()>0&&!s.itemId().equals("minecraft:barrier")&&!s.itemId().equals("minecraft:bedrock");}
    private static Optional<String> field(List<String> lines,String prefix){var values=lines.stream().filter(line->line.startsWith(prefix)).map(line->line.substring(prefix.length())).toList();return values.size()==1&&!values.getFirst().isBlank()?Optional.of(values.getFirst()):Optional.empty();}
    private static List<Effect> effects(List<String> lines){var result=new ArrayList<Effect>();String name=null;var text=new ArrayList<String>();
        for(var line:lines){if(line.startsWith("➤ ")){if(name!=null)result.add(new Effect(name,text));name=line.substring(2);text=new ArrayList<>();}
            else if(name!=null){if(line.isBlank()||line.startsWith("Shows ")){result.add(new Effect(name,text));name=null;text=new ArrayList<>();}else text.add(line);}}
        if(name!=null)result.add(new Effect(name,text));return List.copyOf(result);}
}
