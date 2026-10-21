package dev.veltraph.client.garden.pests;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Exact local protocol bindings for source-opaque labels/IDs. No aliases, fuzzy matching or guessed prices. */
public final class PestBindings {
    private PestBindings() {}
    public record Item(String registryId,String name,String skyblockId) {
        public Item { required(registryId,name,skyblockId);if(!registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid item registry"); }
    }
    public record Entity(String registryId,String name,String helmetIdentity) { public Entity {required(registryId,name);helmetIdentity=helmetIdentity==null?"":helmetIdentity;} }
    public record Trap(Entity npc,String menuTitle,String marker,String releaseButton,String trappedPrefix,String releaseAck,
                       Item vacuum,String bagPrefix,Item bait,int baitUnits,int baitSlot,String baitAck,Entity releasedPest) {
        public Trap {required(menuTitle,marker,releaseButton,trappedPrefix,releaseAck,bagPrefix,baitAck);Objects.requireNonNull(npc);Objects.requireNonNull(vacuum);Objects.requireNonNull(bait);Objects.requireNonNull(releasedPest);if(releasedPest.helmetIdentity().isBlank())throw new IllegalArgumentException("Exact released pest model required");
            if(baitUnits<1||baitUnits>64||baitSlot<0||baitSlot>53)throw new IllegalArgumentException("Invalid bait amount/destination"); }
    }
    public record Repellent(Item item,List<MenuStep> route,String purchaseTitle,String purchaseButton,String confirmTitle,String confirmButton,
                            String pricePrefix,String balancePrefix,String purchaseAck,String useAck,String effectSidebarLine,String openCommand) {
        public Repellent(Item item,List<MenuStep> route,String purchaseTitle,String purchaseButton,String confirmTitle,String confirmButton,String pricePrefix,String balancePrefix,String purchaseAck,String useAck,String effectSidebarLine){this(item,route,purchaseTitle,purchaseButton,confirmTitle,confirmButton,pricePrefix,balancePrefix,purchaseAck,useAck,effectSidebarLine,"");}
        public Repellent {openCommand=openCommand==null?"":openCommand;if(!openCommand.isEmpty()&&!openCommand.equals("desk"))throw new IllegalArgumentException("Only explicitly configured desk opening is supported; travel is coordinated separately");Objects.requireNonNull(item);route=route==null?List.of():List.copyOf(route);if(route.size()>5)throw new IllegalArgumentException("Too many menu steps");
            required(purchaseTitle,purchaseButton,confirmTitle,confirmButton,pricePrefix,balancePrefix,purchaseAck,useAck,effectSidebarLine);}
    }
    public record MenuStep(String title,String button,String nextTitle) {public MenuStep {required(title,button,nextTitle);} }
    public record Cleaner(Item vacuum,String bagPrefix,Entity pest,int maxPests,double reach,long holdMs,String captureAck) {
        public Cleaner {Objects.requireNonNull(vacuum);Objects.requireNonNull(pest);required(bagPrefix,captureAck);if(pest.helmetIdentity().isBlank()||maxPests<1||maxPests>8||!Double.isFinite(reach)||reach<1||reach>16||holdMs<50||holdMs>5000)
            throw new IllegalArgumentException("Cleaner requires exact model identity and bounded amount/range/hold");}
    }
    public record Supply(Item item,int quantity,String balancePrefix,String sacksRejectAck) {public Supply(Item item,int quantity,String balancePrefix){this(item,quantity,balancePrefix,"");}
        public Supply {sacksRejectAck=sacksRejectAck==null?"":sacksRejectAck;Objects.requireNonNull(item);required(balancePrefix);if(quantity<1||quantity>4096)throw new IllegalArgumentException("Invalid supply quantity");} }
    public static <T> T read(String raw,Class<T> kind){if(raw==null||raw.isBlank())throw new IllegalArgumentException("Configure exact observed Garden bindings");try{T value=new Gson().fromJson(raw,kind);return Objects.requireNonNull(value);}catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid Garden bindings: "+invalid.getMessage());}}
    private static void required(String... values){for(String s:values)if(s==null||s.isBlank()||s.indexOf('\n')>=0)throw new IllegalArgumentException("Required exact label/ID is empty or multiline");}
    public static OptionalInt count(List<String> lines,String prefix){
        if(prefix==null||prefix.isBlank())return OptionalInt.empty();Integer found=null;
        for(String line:lines)if(line.startsWith(prefix)){
            String value=line.substring(prefix.length()).trim();if(!value.matches("[0-9]{1,9}"))return OptionalInt.empty();
            try{if(found!=null)return OptionalInt.empty();found=Integer.parseInt(value);}catch(NumberFormatException bad){return OptionalInt.empty();}
        }return found==null?OptionalInt.empty():OptionalInt.of(found);
    }
    public static Optional<BigDecimal> amount(List<String> lines,String prefix,String suffix){
        if(prefix==null||prefix.isBlank())return Optional.empty();BigDecimal found=null;
        for(String line:lines)if(line.startsWith(prefix)){
            String raw=line.substring(prefix.length()).strip();if(suffix!=null&&!suffix.isBlank()){
                if(!raw.endsWith(" "+suffix))return Optional.empty();raw=raw.substring(0,raw.length()-suffix.length()).strip();}
            if(!raw.matches("(?:0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))return Optional.empty();
            if(found!=null)return Optional.empty();found=new BigDecimal(raw.replace(",",""));
        }return Optional.ofNullable(found);
    }
}
