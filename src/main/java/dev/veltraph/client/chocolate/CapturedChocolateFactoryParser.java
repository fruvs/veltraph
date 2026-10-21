package dev.veltraph.client.chocolate;

import java.math.*;
import java.util.*;
import java.util.regex.*;

/** Grammar observed in the completed 2026-10-01 capture. Layout evidence is not an account identity. */
public final class CapturedChocolateFactoryParser {
    private CapturedChocolateFactoryParser() {}
    public static final String VERSION="native-capture-2026-10-01-chocolate-debead98-v1";
    public static final String TITLE="Chocolate Factory";
    private static final String[] EMPLOYEES={"Bro","Cousin","Sis","Daddy","Granny","Uncle","Dog"};
    public record Production(BigDecimal base,BigDecimal multiplier,BigDecimal displayed,boolean breakdownConsistent,String explanation) {}
    public record State(boolean recognized,boolean complete,int factoryLevel,Optional<BigDecimal> balance,Optional<BigDecimal> capacity,Optional<BigDecimal> production,Optional<BigDecimal> allTime,Optional<BigDecimal> prestigeProgress,Optional<BigDecimal> prestigeGoal,Optional<Production> breakdown,OptionalInt rabbitsOwned,OptionalInt rabbitCapacity,OptionalInt collectionTotal,List<String> notes,Map<String,ChocolateFactoryObservation.Quote> quotes,Map<String,ChocolateFactoryObservation.ResultTarget> targets) {}
    public record Tower(int level,String status,OptionalLong activeRemainingSeconds,OptionalLong chargeRemainingSeconds,OptionalInt charges,OptionalInt maxCharges,Optional<BigDecimal> displayedBoost) {}
    public static Optional<Tower> tower(Map<Integer,ChocolateFactoryObservation.Row> rows){
        var r=rows.get(38);if(r==null||r.playerInventory()||!r.itemId().equals("minecraft:clock")||!r.name().startsWith("Time Tower "))return Optional.empty();int level=ChocolateNumbers.roman(r.name().substring("Time Tower ".length()));if(level<1)return Optional.empty();
        String status="UNKNOWN";OptionalLong active=OptionalLong.empty(),charge=OptionalLong.empty();Optional<BigDecimal> boost=Optional.empty();int statuses=0;
        for(String line:r.lore()){if(line.equals("Status: INACTIVE")){status="INACTIVE";statuses++;}else if(line.startsWith("Status: ACTIVE ")){status="ACTIVE";active=duration(line.substring("Status: ACTIVE ".length()));statuses++;}if(line.startsWith("Next Charge: "))charge=duration(line.substring("Next Charge: ".length()));var m=Pattern.compile("^Chocolate Factory by \\+([0-9.]+)x for 1h\\.$").matcher(line);if(m.matches())boost=ChocolateNumbers.read(m.group(1),false);}
        if(statuses!=1){status="UNKNOWN";active=OptionalLong.empty();}var counts=counts(r.lore(),Pattern.compile("^Charges: ([0-9]+)/([0-9]+)$"));
        return Optional.of(new Tower(level,status,active,charge,counts[0],counts[1],boost));
    }
    private static OptionalLong duration(String text){var m=Pattern.compile("^(?:([0-9]+)h)?(?:([0-9]+)m)?(?:([0-9]+)s)?$").matcher(text);if(!m.matches()||text.isBlank())return OptionalLong.empty();try{long h=m.group(1)==null?0:Long.parseLong(m.group(1)),min=m.group(2)==null?0:Long.parseLong(m.group(2)),s=m.group(3)==null?0:Long.parseLong(m.group(3));if(min>59||s>59||h>48)return OptionalLong.empty();return OptionalLong.of(Math.addExact(Math.addExact(Math.multiplyExact(h,3600),min*60),s));}catch(ArithmeticException|NumberFormatException bad){return OptionalLong.empty();}}
    public record Rabbit(String name,String rarity,String status,List<String> requirements) {}
    public record CollectionPage(int page,int totalPages,List<Rabbit> rabbits,String coverage) {}
    private static ChocolateFactoryObservation.Row row(Map<Integer,ChocolateFactoryObservation.Row> rows,int slot,String id,String name){var r=rows.get(slot);return r!=null&&!r.playerInventory()&&r.count()==1&&r.itemId().equals(id)&&r.name().equals(name)&&r.fullIdentity()!=null?r:null;}
    private static Optional<BigDecimal> number(List<String> lines,String prefix,String suffix,boolean compact){BigDecimal value=null;for(var line:lines){var text=line.strip();if(text.startsWith(prefix)&&text.endsWith(suffix)&&text.length()>=prefix.length()+suffix.length()){var n=ChocolateNumbers.read(text.substring(prefix.length(),text.length()-suffix.length()),compact);if(n.isEmpty()||value!=null)return Optional.empty();value=n.get();}}return Optional.ofNullable(value);}
    private static Optional<BigDecimal> price(ChocolateFactoryObservation.Row r){if(r==null)return Optional.empty();int i=r.lore().indexOf("Cost");if(i<0||i!=r.lore().lastIndexOf("Cost")||i+1>=r.lore().size())return Optional.empty();return number(List.of(r.lore().get(i+1)),""," Chocolate",false).filter(n->n.signum()>0);}
    private static OptionalInt[] counts(List<String> lines,Pattern pattern){Integer a=null,b=null;for(var line:lines){var m=pattern.matcher(line.strip());if(m.matches()){if(a!=null)return new OptionalInt[]{OptionalInt.empty(),OptionalInt.empty()};try{a=Integer.valueOf(m.group(1).replace(",",""));b=Integer.valueOf(m.group(2).replace(",",""));}catch(NumberFormatException bad){return new OptionalInt[]{OptionalInt.empty(),OptionalInt.empty()};}}}return new OptionalInt[]{a==null?OptionalInt.empty():OptionalInt.of(a),b==null?OptionalInt.empty():OptionalInt.of(b)};}
    private static State unknown(String note){return new State(false,false,-1,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),List.of(note),Map.of(),Map.of());}
    public static State parse(String title,Map<Integer,ChocolateFactoryObservation.Row> rows){
        if(!TITLE.equals(title))return unknown("Captured Factory parser does not recognize this menu title");
        var factory=rows.get(27);var wallet=rows.get(13);var totals=row(rows,45,"minecraft:cocoa_beans","Chocolate Production");var collection=row(rows,50,"minecraft:player_head","Hoppity's Collection");
        if(factory==null||factory.playerInventory()||!factory.itemId().equals("minecraft:dropper")||!factory.name().startsWith("Chocolate Factory ")||wallet==null||wallet.playerInventory()||!wallet.itemId().equals("minecraft:player_head")||totals==null||collection==null)return unknown("Captured Factory anchors are absent or changed; partial/reordered menu remains unsupported");
        int tier=ChocolateNumbers.roman(factory.name().substring("Chocolate Factory ".length()));if(tier!=3&&tier!=4)return unknown("Only observed Factory III/IV layouts are supported; capture the new tier first");
        var balance=number(List.of(wallet.name()),""," Chocolate",false);var capacity=number(factory.lore(),"Max Chocolate: ","",true);var rate=number(totals.lore(),""," Chocolate per second",false);var walletRate=number(wallet.lore(),""," per second",false);
        var allTime=number(wallet.lore(),"All-time Chocolate: ","",false);var progress=number(factory.lore(),"Chocolate this Prestige: ","",false);var goal=number(factory.lore(),"Requires "," Chocolate this Prestige!",true);
        var notes=new ArrayList<String>();notes.add("Actual captured layout; personal account/profile name is absent. A deliberate native-session scope binding is required for actions.");
        if(goal.isEmpty())notes.add(factory.lore().contains("Click to prestige!")?"Prestige is displayed as available, but its exact threshold is not present; no threshold is inferred from capacity.":"Exact prestige threshold is absent; no threshold is inferred from capacity.");
        var base=number(totals.lore(),"Base Chocolate: "," per second",false);var multiplier=number(totals.lore(),"Total Multiplier: ","x",false);Optional<Production> production=Optional.empty();
        if(base.isPresent()&&multiplier.isPresent()&&rate.isPresent()){
            BigDecimal baseSum=BigDecimal.ZERO,multiplierSum=BigDecimal.ZERO;boolean terms=true;int baseTerms=0,multiplierTerms=0;
            for(String line:totals.lore()){var m=Pattern.compile("^\\s*\\+([0-9,.]+)(x?) \\(([^()]+)\\)$").matcher(line);if(!m.matches())continue;var n=ChocolateNumbers.read(m.group(1),false);if(n.isEmpty()){terms=false;continue;}if(m.group(2).equals("x")){multiplierSum=multiplierSum.add(n.get());multiplierTerms++;}else{baseSum=baseSum.add(n.get());baseTerms++;}}
            boolean consistent=terms&&baseTerms>0&&multiplierTerms>0&&baseSum.compareTo(base.get())==0&&multiplierSum.compareTo(multiplier.get())==0&&base.get().multiply(multiplier.get()).subtract(rate.get()).abs().compareTo(new BigDecimal("0.005"))<=0;
            production=Optional.of(new Production(base.get(),multiplier.get(),rate.get(),consistent,"Visible base and multiplier terms must sum exactly and reproduce the shown rate within its half-cent display precision; prospective estimates hold these modifiers unchanged."));
        }
        boolean complete=java.util.stream.IntStream.range(0,54).allMatch(i->rows.containsKey(i)&&!rows.get(i).playerInventory())&&balance.isPresent()&&capacity.isPresent()&&rate.isPresent()&&walletRate.isPresent()&&rate.get().compareTo(walletRate.get())==0&&balance.get().compareTo(capacity.get())<=0;
        for(int i=0;i<EMPLOYEES.length;i++){var r=rows.get(28+i);if(r==null||r.playerInventory()||r.fullIdentity()==null||r.count()!=1||!(r.name().equals("Rabbit "+EMPLOYEES[i])||r.name().equals("Rabbit "+EMPLOYEES[i]+" - Unemployed")||r.name().matches("Rabbit "+EMPLOYEES[i]+" - \\[([0-9]+)] (Intern|Employee|Manager|Director)"))||!Set.of("minecraft:player_head","minecraft:gray_dye").contains(r.itemId()))complete=false;}
        BigDecimal employeeSum=BigDecimal.ZERO;boolean employeeEffectsKnown=true;
        for(int i=0;i<EMPLOYEES.length;i++){var r=rows.get(28+i);if(r==null)continue;if(r.itemId().equals("minecraft:gray_dye")||r.name().equals("Rabbit "+EMPLOYEES[i]+" - Unemployed"))continue;var effect=currentEmployeeBase(r);if(effect.isEmpty())employeeEffectsKnown=false;else employeeSum=employeeSum.add(effect.get());}
        var reportedEmployees=number(totals.lore(),"+"," (Rabbit Employees)",false).orElse(BigDecimal.ZERO);
        if(!employeeEffectsKnown||reportedEmployees.compareTo(employeeSum)!=0){complete=false;notes.add("Employee effects and aggregate employee contribution disagree; menu refresh is not complete.");}
        var barn=rows.get(35);var barnCounts=barn==null?new OptionalInt[]{OptionalInt.empty(),OptionalInt.empty()}:counts(barn.lore(),Pattern.compile("^Your Barn: ([0-9,]+)/([0-9,]+) Rabbits$"));var collectionCounts=counts(collection.lore(),Pattern.compile("^([0-9,]+)/([0-9,]+)$"));
        if(barn==null||!barn.itemId().equals("minecraft:oak_fence")||!barn.name().startsWith("Rabbit Barn ")||barnCounts[0].isEmpty()||barnCounts[1].isEmpty()||collectionCounts[0].isEmpty()||collectionCounts[1].isEmpty()||barnCounts[0].getAsInt()!=collectionCounts[0].getAsInt())complete=false;
        var quotes=new LinkedHashMap<String,ChocolateFactoryObservation.Quote>();var targets=new LinkedHashMap<String,ChocolateFactoryObservation.ResultTarget>();
        if(complete)for(int i=0;i<EMPLOYEES.length;i++){
            var r=rows.get(28+i);if(!r.itemId().equals("minecraft:player_head"))continue;var level=Pattern.compile("^Rabbit "+EMPLOYEES[i]+" - \\[([0-9]+)] (Intern|Employee|Manager|Director)$").matcher(r.name());int n=-1;
            if(level.matches())try{n=Integer.parseInt(level.group(1));}catch(NumberFormatException ignored){}else if(r.name().equals("Rabbit "+EMPLOYEES[i]+" - Unemployed"))n=0;
            if(n>=0){var id="rabbit_"+EMPLOYEES[i].toLowerCase(Locale.ROOT);targets.put(id,new ChocolateFactoryObservation.ResultTarget(id,ChocolateFactoryProfile.Kind.EMPLOYEE,r.slot(),n,r.fullIdentity()));}
        }
        if(complete&&barn.name().startsWith("Rabbit Barn ")){int n=ChocolateNumbers.roman(barn.name().substring("Rabbit Barn ".length()));if(n>0)targets.put("rabbit_barn",new ChocolateFactoryObservation.ResultTarget("rabbit_barn",ChocolateFactoryProfile.Kind.CAPACITY,35,n,barn.fullIdentity()));}
        if(complete){for(int i=0;i<EMPLOYEES.length;i++){
            var r=rows.get(28+i);if(!r.itemId().equals("minecraft:player_head")||!r.lore().contains("Click to promote!")&&!r.lore().contains("Click to hire!"))continue;
            var level=Pattern.compile("^Rabbit "+EMPLOYEES[i]+" - \\[([0-9]+)] (Intern|Employee|Manager|Director)$").matcher(r.name());boolean hire=r.name().equals("Rabbit "+EMPLOYEES[i]+" - Unemployed")&&r.lore().contains("Click to hire!");if(!hire&&!level.matches())continue;
            int current;try{current=hire?0:Integer.parseInt(level.group(1));}catch(NumberFormatException bad){continue;}
            var next=r.lore().stream().map(line->Pattern.compile("^(?:PROMOTE|HIRE) ➜ \\[([0-9]+)] (Intern|Employee|Manager|Director)$").matcher(line)).filter(Matcher::matches).toList();if(next.size()!=1||Integer.parseInt(next.getFirst().group(1))!=current+1)continue;
            var cost=price(r);if(cost.isEmpty())continue;
            var nextBase=number(r.lore(),"+"," Chocolate per second",false);var currentBase=hire?Optional.of(BigDecimal.ZERO):currentEmployeeBase(r);Optional<BigDecimal> delta=Optional.empty();
            if(nextBase.isPresent()&&currentBase.isPresent()&&nextBase.get().compareTo(currentBase.get())>0&&production.isPresent()&&production.get().breakdownConsistent())delta=Optional.of(nextBase.get().subtract(currentBase.get()).multiply(production.get().multiplier()));
            quotes.put("rabbit_"+EMPLOYEES[i].toLowerCase(Locale.ROOT),new ChocolateFactoryObservation.Quote("rabbit_"+EMPLOYEES[i].toLowerCase(Locale.ROOT),ChocolateFactoryProfile.Kind.EMPLOYEE,r.slot(),current,cost.get(),r.fullIdentity(),delta,"Local estimate: next shown employee TOTAL minus current shown TOTAL, multiplied by the unchanged currently displayed total multiplier. Display rounding, boosts and other changes can alter the result."));
        }
        if(barn.lore().contains("Click to upgrade!")&&barn.lore().stream().map(String::strip).anyMatch("+2 Capacity"::equals)){int level=ChocolateNumbers.roman(barn.name().substring("Rabbit Barn ".length()));var cost=price(barn);if(level>0&&cost.isPresent()&&barn.lore().stream().anyMatch(s->s.startsWith("UPGRADE ➜ Rabbit Barn ")&&ChocolateNumbers.roman(s.substring("UPGRADE ➜ Rabbit Barn ".length()))==level+1))quotes.put("rabbit_barn",new ChocolateFactoryObservation.Quote("rabbit_barn",ChocolateFactoryProfile.Kind.CAPACITY,35,level,cost.get(),barn.fullIdentity(),Optional.empty(),"Rabbit storage capacity +2; no passive Chocolate production delta is inferred."));}
        }
        if(!complete)notes.add("Observed menu is incomplete, inconsistent or in an intermediate refresh; actions are unavailable until a complete stable observation.");
        if(production.isEmpty()||!production.get().breakdownConsistent())notes.add("Visible production composition is incomplete/inconsistent; prospective payback remains unknown.");
        return new State(true,complete,tier,balance,capacity,rate,allTime,progress,goal,production,barnCounts[0],barnCounts[1],collectionCounts[1],List.copyOf(notes),Map.copyOf(quotes),Map.copyOf(targets));
    }
    private static Optional<BigDecimal> currentEmployeeBase(ChocolateFactoryObservation.Row r){
        BigDecimal value=null;
        for(int i=0;i<r.lore().size();i++){
            String text=r.lore().get(i).strip();var m=Pattern.compile("^(?:produce )?\\+([0-9,]+) Chocolate per second!$").matcher(text);
            if(!m.matches()&&text.matches("produce \\+[0-9,]+ Chocolate per")&&i+1<r.lore().size()&&r.lore().get(i+1).strip().equals("second!"))m=Pattern.compile("^produce \\+([0-9,]+) Chocolate per$").matcher(text);
            if(m.matches()){if(value!=null)return Optional.empty();var n=ChocolateNumbers.read(m.group(1),false);if(n.isEmpty())return Optional.empty();value=n.get();}
        }
        return Optional.ofNullable(value);
    }
    public static ChocolateFactoryObservation observation(ChocolateFactoryProfile p,ChocolateFactoryObservation raw,boolean bound){var s=parse(raw.title(),raw.rows());return new ChocolateFactoryObservation(p.scope(),raw.session(),raw.menu(),raw.screen(),raw.revision(),raw.observedMillis(),raw.title(),bound&&s.complete(),raw.cursorEmpty(),raw.rows(),s.balance(),s.production(),s.capacity(),s.prestigeProgress(),s.prestigeGoal(),s.quotes(),s.targets(),bound&&raw.title().equals(p.title()));}
    public static Optional<CollectionPage> collectionPage(String title,Map<Integer,ChocolateFactoryObservation.Row> rows){
        var page=Pattern.compile("^\\(([0-9]+)/([0-9]+)\\) Hoppity's Collection$").matcher(title);if(!page.matches()||row(rows,4,"minecraft:player_head","Hoppity's Collection")==null)return Optional.empty();var rabbits=new ArrayList<Rabbit>();
        for(var r:rows.values())if(!r.playerInventory()&&r.slot()>=10&&r.slot()<=43&&r.count()==1&&!r.name().isBlank()&&Set.of("minecraft:gray_dye","minecraft:player_head").contains(r.itemId())){
            var rarity=r.lore().stream().filter(s->s.matches("(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE) RABBIT")).toList();if(rarity.size()!=1)continue;String status=r.lore().contains("You have not found this rabbit yet!")?"MISSING":r.lore().contains("You cannot find this rabbit until you")?"LOCKED":r.itemId().equals("minecraft:player_head")?"COLLECTED":"UNKNOWN";var requirements=r.lore().stream().filter(s->s.startsWith("✖ Requirement")||s.startsWith("Obtained through")||s.contains("Resident")||s.contains("Requires")).toList();rabbits.add(new Rabbit(r.name(),rarity.getFirst().replace(" RABBIT",""),status,requirements));
        }
        return Optional.of(new CollectionPage(Integer.parseInt(page.group(1)),Integer.parseInt(page.group(2)),List.copyOf(rabbits),"Only this observed page/filter is known; absent pages or rabbits are not inferred missing."));
    }
}
