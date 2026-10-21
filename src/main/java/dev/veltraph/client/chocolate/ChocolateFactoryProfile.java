package dev.veltraph.client.chocolate;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.*;

/** User-captured exact visible fields; no guessed Factory titles, slot numbers, or prices. */
public record ChocolateFactoryProfile(int schema,String scope,String verifiedOn,String title,Marker profileMarker,
        Field balance,Field production,Field capacity,Field prestigeProgress,Field prestigeGoal,
        List<Upgrade> upgrades,long freshnessMillis,long receiptTimeoutMillis,Field rabbitsOwned,Field rabbitCapacity,Field collectionTotal,Field chocobits,boolean progressAtCapacityVerified,ContextMode contextMode,String capturedLayout) {
    public enum ContextMode { PROFILE_MARKER, OBSERVED_NATIVE_SESSION }
    public ChocolateFactoryProfile(int schema,String scope,String date,String title,Marker marker,Field balance,Field production,Field capacity,Field progress,Field goal,List<Upgrade> upgrades,long fresh,long timeout,Field owned,Field rabbitCap,Field total,Field chocobits,boolean atCap){this(schema,scope,date,title,marker,balance,production,capacity,progress,goal,upgrades,fresh,timeout,owned,rabbitCap,total,chocobits,atCap,ContextMode.PROFILE_MARKER,null);}
    public boolean usesCapturedLayout(){return contextMode==ContextMode.OBSERVED_NATIVE_SESSION&&CapturedChocolateFactoryParser.VERSION.equals(capturedLayout);}
    public static final int SCHEMA=1;
    public record Marker(int slot,String itemId,String name,List<String> requiredLore,String numericNamePrefix,String numericNameSuffix) {
        public Marker(int slot,String itemId,String name,List<String> lore){this(slot,itemId,name,lore,null,null);}
        public Marker { requiredLore=requiredLore==null?List.of():List.copyOf(requiredLore); }
        public boolean matches(ChocolateFactoryObservation.Row row){return row!=null&&!row.playerInventory()&&row.count()>0&&row.slot()==slot&&Objects.equals(itemId,row.itemId())&&nameMatches(row.name())&&row.lore().containsAll(requiredLore);}
        private boolean nameMatches(String actual){
            if(numericNamePrefix==null||numericNameSuffix==null)return Objects.equals(name,actual);
            if(!actual.startsWith(numericNamePrefix)||!actual.endsWith(numericNameSuffix)||actual.length()<numericNamePrefix.length()+numericNameSuffix.length())return false;
            return actual.substring(numericNamePrefix.length(),actual.length()-numericNameSuffix.length()).matches("[0-9]+");
        }
    }
    public enum Source { LORE, NAME }
    public record Field(Marker marker,String prefix,String suffix,Source source) {
        public Field(Marker marker,String prefix,String suffix){this(marker,prefix,suffix,Source.LORE);}
        /** Exactly one matching visible line; exact plain decimal or comma-grouped number only. */
        public Optional<BigDecimal> read(Map<Integer,ChocolateFactoryObservation.Row> rows){
            if(marker==null||prefix==null||suffix==null||prefix.isBlank()&&suffix.isBlank())return Optional.empty();
            var row=rows.get(marker.slot());if(!marker.matches(row))return Optional.empty();BigDecimal value=null;
            for(String line:source==Source.NAME?List.of(row.name()):row.lore())if(line.startsWith(prefix)&&line.endsWith(suffix)&&line.length()>=prefix.length()+suffix.length()){
                if(value!=null)return Optional.empty();String raw=line.substring(prefix.length(),line.length()-suffix.length());
                var decoded=ChocolateNumbers.read(raw,true);if(decoded.isEmpty())return Optional.empty();value=decoded.get();
            }
            return Optional.ofNullable(value);
        }
    }
    public enum Kind { EMPLOYEE, PRODUCTION, CAPACITY, RABBIT_COLLECTION, PRESTIGE, FACTION, CONSUMABLE, CHOCOBIT }
    public record RateEvidence(int level,BigDecimal price,BigDecimal baseline,String fullIdentity,long capturedMillis) {}
    public record Upgrade(String id,Kind kind,Marker button,Field level,Field price,BigDecimal observedProductionDelta,String deltaAssumption,RateEvidence rateEvidence) {
        public Upgrade(String id,Kind kind,Marker button,Field level,Field price,BigDecimal delta,String assumption){this(id,kind,button,level,price,delta,assumption,null);}
    }
    public ChocolateFactoryProfile { upgrades=upgrades==null?List.of():List.copyOf(upgrades); }
    public void validate(){
        if(schema!=SCHEMA||scope==null||!scope.matches("[a-zA-Z0-9_-]{1,96}")||verifiedOn==null||!verifiedOn.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||title==null||title.isBlank())throw new IllegalArgumentException("Capture a dated, named local Chocolate Factory profile");
        if(!usesCapturedLayout()&&(profileMarker==null||balance==null||production==null||capacity==null))throw new IllegalArgumentException("Capture profile marker, balance, production and capacity fields");
        if(!usesCapturedLayout()&&profileMarker.requiredLore().isEmpty())throw new IllegalArgumentException("Profile marker requires an exact visible profile-specific lore line");
        if(contextMode==ContextMode.OBSERVED_NATIVE_SESSION&&(!usesCapturedLayout()||!title.equals(CapturedChocolateFactoryParser.TITLE)))throw new IllegalArgumentException("Unsupported captured-layout/session contract");
        if(freshnessMillis<100||freshnessMillis>60_000||receiptTimeoutMillis<1000||receiptTimeoutMillis>60_000)throw new IllegalArgumentException("Observation/receipt timing outside bounds");
        var ids=new HashSet<String>();var slots=new HashSet<Integer>();
        for(var u:upgrades)if(u==null||u.id()==null||u.id().isBlank()||!ids.add(u.id())||u.kind()==null||u.button()==null||!slots.add(u.button().slot())||u.level()==null||u.price()==null||u.level().marker()==null||u.price().marker()==null||u.level().marker().slot()!=u.button().slot()||u.price().marker().slot()!=u.button().slot()||u.button().requiredLore().isEmpty())throw new IllegalArgumentException("Upgrade contracts must have unique IDs/slots and exact level/price fields");
        for(var u:upgrades)if(u.observedProductionDelta()!=null&&(u.observedProductionDelta().signum()<=0||u.deltaAssumption()==null||u.deltaAssumption().isBlank()))throw new IllegalArgumentException("A supplied rate delta requires a positive value and explicit evidence/assumption");
    }
    public static ChocolateFactoryProfile decode(String json){try{var p=new Gson().fromJson(json,ChocolateFactoryProfile.class);if(p==null)throw new IllegalArgumentException("Capture Chocolate Factory profile first");p.validate();return p;}catch(RuntimeException e){throw new IllegalArgumentException("Incomplete Chocolate Factory profile: "+e.getMessage(),e);}}
    public String encode(){validate();return new Gson().toJson(this);}
}
