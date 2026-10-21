package dev.veltraph.client.fishing.bait;

import com.google.gson.Gson;
import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit server menu vocabulary: original labels are salted, so no guessed server defaults. */
public record BaitProfile(String menuItemName,String menuItemId,String baitName,String baitItemId,
        String bazaarCommand,int stackSize,Map<String,String> titles,Map<String,String> buttons,List<Integer> bagSlots,
        String itemPrefix,String quantityPrefix,String costPrefix,String costSuffix,
        String disabledLore,String enabledLore,String receiptTemplate) {
    public BaitProfile {
        for(String value:List.of(menuItemName,menuItemId,baitName,baitItemId,bazaarCommand,itemPrefix,quantityPrefix,costPrefix,costSuffix,disabledLore,enabledLore,receiptTemplate))
            if(value.isBlank()||value.length()>256||value.contains("\n"))throw new IllegalArgumentException("Blank/oversized bait profile text");
        if(!Set.of("bz","bazaar","veltraph_bait").contains(bazaarCommand))throw new IllegalArgumentException("Only Bazaar menu aliases or the private fixture command are permitted");
        if(stackSize<1||stackSize>64)throw new IllegalArgumentException("Bait stack size must be 1..64");
        titles=Map.copyOf(titles);buttons=Map.copyOf(buttons);bagSlots=List.copyOf(bagSlots);
        for(String role:List.of("root","bags","bag","bazaar","fishing","baits","product","buy"))require(titles,role);
        for(String role:List.of("bags","bag","fishing","baits","buy","stack","fill","enable"))require(buttons,role);
        if(bagSlots.size()<1||bagSlots.size()>54||new HashSet<>(bagSlots).size()!=bagSlots.size()||bagSlots.stream().anyMatch(i->i<0||i>=54))throw new IllegalArgumentException("Declare distinct container storage slots 0..53");
        receiptPattern(receiptTemplate,baitName);
    }
    private static void require(Map<String,String> map,String role){if(!map.containsKey(role)||map.get(role).isBlank()||map.get(role).length()>128)throw new IllegalArgumentException("Missing exact bait profile role: "+role);}
    public static BaitProfile parse(String json){if(json==null||json.length()>16384)throw new IllegalArgumentException("Invalid bait profile size");try{return Objects.requireNonNull(new Gson().fromJson(json,BaitProfile.class));}catch(RuntimeException e){throw new IllegalArgumentException("Configure a complete exact bait menu profile",e);}}
    public boolean bait(String itemId,String name){return baitItemId.equals(itemId)&&baitName.equals(MenuSnapshot.clean(name));}
    public record Quote(int quantity,BigDecimal cost) {}
    public Quote quote(MenuSnapshot.Slot button){
        String name=only(button.lore(),itemPrefix);String amount=only(button.lore(),quantityPrefix);String price=only(button.lore(),costPrefix);
        if(!name.equals(baitName)||!amount.matches("[0-9]{1,6}")||!price.endsWith(costSuffix))throw new IllegalArgumentException("Unknown bait identity, quantity or quote");
        int quantity=Integer.parseInt(amount);BigDecimal cost=money(price.substring(0,price.length()-costSuffix.length()));
        if(quantity<1||cost.signum()<=0)throw new IllegalArgumentException("Bait quote must be positive");return new Quote(quantity,cost);
    }
    private static String only(List<String> lore,String prefix){var lines=lore.stream().map(MenuSnapshot::clean).filter(s->s.startsWith(prefix)).toList();if(lines.size()!=1)throw new IllegalArgumentException("Missing or ambiguous bait quote line");return lines.getFirst().substring(prefix.length()).strip();}
    public static BigDecimal money(String raw){String value=raw.strip();if(value.length()>32||!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,4})?"))throw new IllegalArgumentException("Unknown coin amount");return new BigDecimal(value.replace(",",""));}
    private static Pattern receiptPattern(String template,String item){
        StringBuilder pattern=new StringBuilder("^");int offset=0;
        for(String token:List.of("{quantity}","{item}","{cost}")){
            int at=template.indexOf(token,offset);if(at<0||template.indexOf(token,at+1)>=0)throw new IllegalArgumentException("Receipt requires quantity/item/cost tokens in order");
            pattern.append(Pattern.quote(template.substring(offset,at)));
            pattern.append(token.equals("{quantity}")?"(?<quantity>[0-9,]{1,16})":token.equals("{item}")?Pattern.quote(item):"(?<cost>[0-9,.]{1,32})");offset=at+token.length();
        }
        pattern.append(Pattern.quote(template.substring(offset))).append('$');return Pattern.compile(pattern.toString());
    }
    public Optional<Quote> receipt(String message){var match=receiptPattern(receiptTemplate,baitName).matcher(MenuSnapshot.clean(message));if(!match.matches())return Optional.empty();try{return Optional.of(new Quote(Integer.parseInt(match.group("quantity").replace(",","")),money(match.group("cost"))));}catch(RuntimeException e){return Optional.empty();}}
}
