package dev.veltraph.client.chocolate;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Captured readable menus; metadata recognition never grants a resource or item-delivery receipt. */
public final class ChocolateSelectedTransactions {
    public enum Action { SHOP, HITMAN, MILESTONE }
    public enum Currency { CHOCOLATE, COINS, NONE }
    public record Quote(Action action,int slot,String name,Currency currency,BigDecimal cost,int year,int stock,int purchased,int capacity,List<String> terms) {
        public Quote {terms=List.copyOf(terms);}
    }
    private ChocolateSelectedTransactions() {}
    public static String title(Action action){return switch(action){case SHOP->"Chocolate Shop";case HITMAN->"Rabbit Hitman";case MILESTONE->"Chocolate Factory Milestones";};}
    public static boolean complete(ChocolateFactoryObservation o){
        if(o==null)return false;int size=o.title().equals("Confirm Purchase")?27:54;
        return java.util.stream.IntStream.range(0,size).allMatch(i->o.rows().containsKey(i)&&!o.rows().get(i).playerInventory())&&o.rows().values().stream().filter(r->!r.playerInventory()).count()==size;
    }
    public static Optional<Quote> quote(Action action,ChocolateFactoryObservation o,int slot){
        if(o==null||!title(action).equals(o.title())||!complete(o))return Optional.empty();var r=o.rows().get(slot);
        if(r==null||r.playerInventory()||r.count()!=1)return Optional.empty();var lines=r.lore();
        if(action==Action.MILESTONE){
            // Only the actual paired XVI contract is recovered. Locked later rewards remain metadata.
            if(slot!=4||!r.itemId().equals("minecraft:purple_stained_glass_pane")||!r.name().equals("16th Chocolate Milestone")||!lines.equals(MILESTONE_TERMS))return Optional.empty();
            return Optional.of(new Quote(action,slot,r.name(),Currency.NONE,BigDecimal.ZERO,-1,-1,-1,-1,lines));
        }
        if(action==Action.HITMAN){
            int ordinal=HITMAN_SLOTS.indexOf(slot);var summary=o.rows().get(4);if(ordinal<0||summary==null||!summary.itemId().equals("minecraft:bow")||!summary.name().equals("Rabbit Hitman")||!r.itemId().equals("minecraft:gray_dye")||!r.name().equals("Egg Slot")||!lines.equals(List.of("Purchase more Egg Slots so the","Hitman can store more eggs you miss!","","Cost",HITMAN_COSTS.get(ordinal)+" Coins","","Click to purchase!")))return Optional.empty();
            var count=unique(summary.lore(),"Purchased slots: ([0-9]+)/([0-9]+)");var eggs=number(summary.lore(),"Available eggs: ","");if(count==null||eggs.isEmpty())return Optional.empty();
            try{int n=Integer.parseInt(count.group(1)),cap=Integer.parseInt(count.group(2));if(n!=ordinal||cap<=n||cap>28)return Optional.empty();return Optional.of(new Quote(action,slot,r.name(),Currency.COINS,ChocolateNumbers.read(HITMAN_COSTS.get(ordinal),false).orElseThrow(),-1,-1,n,cap,lines));}catch(RuntimeException invalid){return Optional.empty();}
        }
        if(!SHOP_SLOTS.contains(slot)||!r.itemId().equals("minecraft:player_head")||Collections.frequency(lines,"Click to trade!")!=1)return Optional.empty();
        int start=lines.indexOf("Cost");if(start<0||Collections.frequency(lines,"Cost")!=1||start+2>=lines.size()||!lines.get(start+2).isEmpty())return Optional.empty();
        var cost=number(List.of(lines.get(start+1)),""," Chocolate");var year=unique(lines,"Annual Stock Year ([0-9]+)");var stock=number(lines,""," remaining");
        if(cost.isEmpty()||cost.get().signum()<=0||year==null||stock.isEmpty()||lines.stream().anyMatch(l->l.startsWith("Requires")||l.equals("Not unlocked!")))return Optional.empty();
        try{int y=Integer.parseInt(year.group(1)),s=stock.get().intValueExact();if(s<=0||!lines.contains(s+" remaining")||shopSpent(o).isEmpty())return Optional.empty();return Optional.of(new Quote(action,slot,r.name(),Currency.CHOCOLATE,cost.get(),y,s,-1,-1,lines));}catch(RuntimeException invalid){return Optional.empty();}
    }
    static final List<String> MILESTONE_TERMS=List.of("Reach 10B Chocolate all-time to","unlock this special Chocolate Rabbit!","","Milestone XVI Reward","Peppermint Pearl Rabbit","","Grants +10 Chocolate and 0.005x","Chocolate per second to your","Chocolate Factory.","","Click to claim!");
    static final List<Integer> SHOP_SLOTS=List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33);
    static final List<Integer> HITMAN_SLOTS=List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43);
    static final List<String> HITMAN_COSTS=List.of("1,000","10,000","100,000","200,000","400,000","600,000","800,000","1,000,000","1,500,000","2,000,000","3,000,000","4,000,000","5,000,000","6,000,000","7,000,000","8,500,000","10,000,000","12,000,000","14,000,000","16,000,000","18,000,000","20,000,000","22,000,000","24,000,000","26,000,000","28,000,000","30,000,000","32,000,000");
    static java.util.regex.Matcher unique(List<String> lines,String regex){java.util.regex.Matcher result=null;for(var line:lines){var m=Pattern.compile(regex).matcher(line);if(m.matches()){if(result!=null)return null;result=m;}}return result;}
    static Optional<BigDecimal> number(List<String> lines,String prefix,String suffix){BigDecimal result=null;for(String l:lines)if(l.startsWith(prefix)&&l.endsWith(suffix)&&l.length()>=prefix.length()+suffix.length()){var n=ChocolateNumbers.read(l.substring(prefix.length(),l.length()-suffix.length()),false);if(n.isPresent()){if(result!=null)return Optional.empty();result=n.get();}}return Optional.ofNullable(result);}
    static Optional<BigDecimal> shopSpent(ChocolateFactoryObservation o){var row=o.rows().get(50);return row!=null&&row.itemId().equals("minecraft:ladder")&&row.name().equals("Chocolate Shop Milestones")?number(row.lore(),"Chocolate Spent: ",""):Optional.empty();}
    public static boolean preview(ChocolateFactoryObservation o){if(o==null||!o.title().equals("Confirm Purchase")||!complete(o))return false;var confirm=o.rows().get(11);var cancel=o.rows().get(15);return confirm.itemId().equals("minecraft:green_terracotta")&&confirm.count()==1&&confirm.name().equals("Confirm Purchase")&&confirm.lore().equals(List.of("Click to confirm!"))&&cancel.itemId().equals("minecraft:red_terracotta")&&cancel.count()==1&&cancel.name().equals("Cancel")&&cancel.lore().isEmpty();}
    public static boolean receipt(Quote q,ChocolateFactoryObservation before,ChocolateFactoryObservation after){
        if(!title(q.action()).equals(after.title())||!complete(after))return false;var r=after.rows().get(q.slot());if(r==null||r.count()!=1||!r.name().equals(q.name()))return false;
        if(q.action()==Action.MILESTONE){var terms=new ArrayList<>(q.terms());terms.set(terms.size()-1,"UNLOCKED");return r.itemId().equals("minecraft:player_head")&&r.lore().equals(terms)&&!Objects.equals(before.rows().get(q.slot()).fullIdentity(),r.fullIdentity());}
        if(q.action()==Action.HITMAN){var summary=after.rows().get(4);if(summary==null||!summary.itemId().equals("minecraft:bow")||!summary.name().equals("Rabbit Hitman"))return false;var count=unique(summary.lore(),"Purchased slots: ([0-9]+)/([0-9]+)");return count!=null&&count.group(1).equals(Integer.toString(q.purchased()+1))&&count.group(2).equals(Integer.toString(q.capacity()))&&number(summary.lore(),"Available eggs: ","").equals(number(before.rows().get(4).lore(),"Available eggs: ",""))&&r.itemId().equals("minecraft:lime_dye")&&r.lore().equals(List.of("Once you miss your next egg, your","Rabbit Hitman will snipe it for you.","","READY"));}
        var spent=shopSpent(after);if(spent.isEmpty()||spent.get().compareTo(shopSpent(before).orElseThrow().add(q.cost()))!=0)return false;
        var y=unique(r.lore(),"Annual Stock Year ([0-9]+)");var stock=number(r.lore(),""," remaining");
        // Exhausted stock uses the captured Sold out text rather than a fictional zero counter.
        var old=new ArrayList<>(q.terms());if(q.stock()>1){int i=old.indexOf(q.stock()+" remaining");if(i<0)return false;old.set(i,(q.stock()-1)+" remaining");return r.itemId().equals(before.rows().get(q.slot()).itemId())&&r.lore().equals(old)&&y!=null&&Integer.parseInt(y.group(1))==q.year()&&stock.filter(n->n.compareTo(BigDecimal.valueOf(q.stock()-1))==0).isPresent();}
        int annual=old.indexOf("Annual Stock Year "+q.year());if(annual<0)return false;var exhausted=new ArrayList<>(old.subList(0,annual));exhausted.add("Can only buy 1 per SkyBlock year!");return r.itemId().equals(before.rows().get(q.slot()).itemId())&&r.lore().equals(exhausted);
    }
}
