package dev.veltraph.client.combat.grind;

import com.google.gson.*;
import java.util.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;

/** Explicit owner-declared opt-in. Unknown source callers and absent family entries retain fixed slots. */
public final class CombatWeaponPolicy {
    /** Only a selected parent with proven source interface dispatch may declare one of these. */
    public enum Family { SLAYER_SPIDER, SVEN_CAVE }
    public enum Mode { FIXED_SLOT, READABLE_COMMON, CONTEXTUAL }
    public record Resolved(CombatWeaponPolicy policy, String error) { public boolean valid() { return policy != null && error.isEmpty(); } }
    private final Mode mode;
    private final Family family;
    private final List<String> names;
    private CombatWeaponPolicy(Mode mode, Family family, List<String> names) { this.mode=mode;this.family=family;this.names=List.copyOf(names); }
    public static CombatWeaponPolicy fixed() { return new CombatWeaponPolicy(Mode.FIXED_SLOT,null,List.of()); }
    /** Retains accepted Bestiary C's common-score behavior when a caller explicitly opts in. */
    public static CombatWeaponPolicy common() { return new CombatWeaponPolicy(Mode.READABLE_COMMON,null,List.of()); }
    public Mode mode() { return mode; }
    public Optional<Family> family() { return Optional.ofNullable(family); }
    public List<String> contextualNames() { return names; }

    /** Schema1 local binding counterpart: source list identities are salted and remain unbound. */
    public static Resolved parse(String json, Family selected) {
        if (selected == null) return new Resolved(fixed(), ""); // unproven/unselected family does not opt in
        if (json == null) return new Resolved(null,"Contextual weapon profile is unavailable");
        if (json.isBlank()) return new Resolved(fixed(),"");
        if (json.length()>16384) return new Resolved(null,"Contextual weapon profile exceeds 16384 characters");
        try {
            uniqueJsonKeys(json);
            var root=JsonParser.parseString(json).getAsJsonObject();keys(root,Set.of("schema","families"));
            var schema=root.get("schema");if(schema==null||!schema.isJsonPrimitive()||!schema.getAsJsonPrimitive().isNumber()||!schema.getAsString().equals("1"))throw new IllegalArgumentException("Expected contextual weapon schema 1");
            if(!root.has("families")||!root.get("families").isJsonObject())throw new IllegalArgumentException("families must be an object");
            var families=root.getAsJsonObject("families");keys(families,Set.of("SLAYER_SPIDER","SVEN_CAVE"));
            // An absent selected-family entry does not opt in, regardless of unrelated payloads.
            if(!families.has(selected.name()))return new Resolved(fixed(),"");
            var parsed=new EnumMap<Family,List<String>>(Family.class);
            for(var entry:families.entrySet()) {
                if(!entry.getValue().isJsonObject())throw new IllegalArgumentException("Family contract must be an object");
                var contract=entry.getValue().getAsJsonObject();keys(contract,Set.of("preferredNames"));
                if(!contract.has("preferredNames")||!contract.get("preferredNames").isJsonArray())throw new IllegalArgumentException("preferredNames must be an array");
                var names=new ArrayList<String>();for(var value:contract.getAsJsonArray("preferredNames")) {
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Preferred name must be a string");
                    String name=value.getAsString();if(name.isBlank()||name.length()>256||name.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Preferred name is blank, too long or contains control/formatting codes");
                    names.add(name);
                }
                if(names.isEmpty()||names.size()>32||new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("preferredNames requires 1..32 distinct explicit labels");
                parsed.put(Family.valueOf(entry.getKey()),List.copyOf(names));
            }
            if(selected==null||!parsed.containsKey(selected))return new Resolved(fixed(),"");
            return new Resolved(new CombatWeaponPolicy(Mode.CONTEXTUAL,selected,parsed.get(selected)),"");
        }catch(RuntimeException bad){return new Resolved(null,"Invalid contextual weapon profile: "+bad.getMessage());}
    }
    private static void uniqueJsonKeys(String json) {
        try(var reader=new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);uniqueValue(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing profile content");
        }catch(IOException error){throw new IllegalArgumentException("Malformed profile JSON",error);}
    }
    private static void uniqueValue(JsonReader reader,int depth) throws IOException {
        if(depth>32)throw new IllegalArgumentException("Profile nesting exceeds32 levels");
        switch(reader.peek()) {
            case BEGIN_OBJECT -> {reader.beginObject();var seen=new HashSet<String>();while(reader.hasNext()){String key=reader.nextName();if(!seen.add(key))throw new IllegalArgumentException("Duplicate profile key: "+key);uniqueValue(reader,depth+1);}reader.endObject();}
            case BEGIN_ARRAY -> {reader.beginArray();while(reader.hasNext())uniqueValue(reader,depth+1);reader.endArray();}
            case STRING,NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("Malformed profile value");
        }
    }
    private static void keys(JsonObject object,Set<String> allowed){if(!allowed.containsAll(object.keySet()))throw new IllegalArgumentException("Unknown fields: "+object.keySet().stream().filter(k->!allowed.contains(k)).toList());}

    /** Fixed accepts a nonempty configured slot; its native adapter retains existing item eligibility. */
    public Optional<BestiaryWeaponSelector.Choice> select(List<BestiaryWeaponSelector.Candidate> items,
                                                         int fixedSlot,String globalPreferred) {
        if(items==null||mode!=Mode.FIXED_SLOT&&globalPreferred==null)return Optional.empty();
        var hotbar=new BestiaryWeaponSelector.Candidate[9];
        for(var item:items) {
            if(item==null||item.slot()<0||item.slot()>8)continue;
            if(hotbar[item.slot()]!=null)return Optional.empty();hotbar[item.slot()]=item;
        }
        if(mode==Mode.FIXED_SLOT) {
            if(fixedSlot<0||fixedSlot>8||hotbar[fixedSlot]==null||hotbar[fixedSlot].empty())return Optional.empty();
            return Optional.of(new BestiaryWeaponSelector.Choice(fixedSlot,0));
        }
        var context=mode==Mode.CONTEXTUAL?new ReadableWeaponScorer.ContextNames(names):null;
        BestiaryWeaponSelector.Choice choice=null;
        for(int slot=0;slot<9;slot++) {
            int score=ReadableWeaponScorer.score(hotbar[slot],globalPreferred,List.of(),context).total();
            if(score>0&&(choice==null||score>choice.score()))choice=new BestiaryWeaponSelector.Choice(slot,score);
        }
        return Optional.ofNullable(choice);
    }
    /** Semantic signature is deterministic and delimited as JSON; Core also guards raw profile drift. */
    public String signature(int fixedSlot,String globalPreferred) {
        return mode==Mode.FIXED_SLOT?"fixed:"+fixedSlot:new Gson().toJson(List.of(mode.name(),family==null?"":family.name(),names,globalPreferred==null?"<unbound>":globalPreferred));
    }
}
