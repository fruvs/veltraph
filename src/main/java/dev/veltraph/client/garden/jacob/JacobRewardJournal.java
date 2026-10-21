package dev.veltraph.client.garden.jacob;

import com.google.gson.*;
import java.util.*;

/** Local selected-extension intent ledger. It records no authenticated account, inventory or secrets. */
public final class JacobRewardJournal {
    public static final int MAX_ENTRIES=64,MAX_BYTES=65536;
    public enum Phase { UNRESOLVED, UNKNOWN, ACKNOWLEDGED, RECONCILED_APPLIED, RECONCILED_NOT_APPLIED }
    public enum Decision { APPLIED, NOT_APPLIED }
    public record Entry(UUID id,String configuredFarmProfile,String contractHash,Phase phase,UUID receiptId) {
        public Entry {
            Objects.requireNonNull(id);Objects.requireNonNull(configuredFarmProfile);Objects.requireNonNull(contractHash);Objects.requireNonNull(phase);
            if(configuredFarmProfile.isBlank()||configuredFarmProfile.length()>256||configuredFarmProfile.chars().anyMatch(Character::isISOControl)
                    ||!contractHash.matches("[a-f0-9]{64}")||(phase==Phase.ACKNOWLEDGED)!=(receiptId!=null))throw new IllegalArgumentException("Exact local intent/profile/contract/phase required");
        }
        public boolean pending(){return phase==Phase.UNRESOLVED||phase==Phase.UNKNOWN;}
    }
    private final long generation;private final Map<UUID,Entry> entries;
    private JacobRewardJournal(long generation,Map<UUID,Entry> entries){
        if(generation<entries.size()||entries.size()>MAX_ENTRIES||entries.values().stream().filter(Entry::pending).count()>1)throw new IllegalArgumentException("Bounded journal with one global unresolved intent required");
        this.generation=generation;this.entries=Map.copyOf(entries);
    }
    public static JacobRewardJournal empty(){return new JacobRewardJournal(0,Map.of());}
    public long generation(){return generation;}public List<Entry> entries(){return entries.values().stream().sorted(Comparator.comparing(e->e.id().toString())).toList();}
    public Optional<Entry> pending(){return entries.values().stream().filter(Entry::pending).findFirst();}
    public boolean canPrepare(){return pending().isEmpty()&&entries.size()<MAX_ENTRIES&&generation<Long.MAX_VALUE;}
    private JacobRewardJournal replace(Entry entry){if(generation==Long.MAX_VALUE)throw new IllegalStateException("Journal generation exhausted");var next=new HashMap<>(entries);next.put(entry.id(),entry);return new JacobRewardJournal(generation+1,next);}
    public JacobRewardJournal prepare(UUID id,String configuredFarmProfile,String contractHash){
        if(!canPrepare()||entries.containsKey(id))throw new IllegalStateException("Unresolved/full/duplicate journal; no new claim or eviction");
        return replace(new Entry(id,configuredFarmProfile,contractHash,Phase.UNRESOLVED,null));
    }
    private Entry unresolved(UUID id){var e=entries.get(id);if(e==null||!e.pending())throw new IllegalStateException("Exact unresolved intent required");return e;}
    public JacobRewardJournal unknown(UUID id){var e=unresolved(id);return e.phase()==Phase.UNKNOWN?this:replace(new Entry(e.id(),e.configuredFarmProfile(),e.contractHash(),Phase.UNKNOWN,null));}
    /** Separate verified outcome document: writing it never releases the original intent document. */
    public JacobRewardJournal recordQualifiedOutcome(Entry original,UUID actualReceipt){
        Objects.requireNonNull(original);Objects.requireNonNull(actualReceipt);if(!original.pending()||!canPrepare()||entries.containsKey(original.id()))throw new IllegalStateException("Exact new unresolved intent outcome required");
        return replace(new Entry(original.id(),original.configuredFarmProfile(),original.contractHash(),Phase.ACKNOWLEDGED,actualReceipt));
    }
    public boolean hasQualifiedOutcome(Entry released){return released!=null&&released.phase()==Phase.ACKNOWLEDGED&&entries().stream().anyMatch(e->e.phase()==Phase.ACKNOWLEDGED&&e.id().equals(released.id())&&e.configuredFarmProfile().equals(released.configuredFarmProfile())&&e.contractHash().equals(released.contractHash())&&e.receiptId().equals(released.receiptId()));}
    /** Native owner must supply its independently earned private receipt; this data method grants no effect. */
    public JacobRewardJournal acknowledge(UUID id,UUID actualReceiptId){var e=unresolved(id);return replace(new Entry(e.id(),e.configuredFarmProfile(),e.contractHash(),Phase.ACKNOWLEDGED,Objects.requireNonNull(actualReceiptId)));}
    /** Explicit metadata decision only. It cannot manufacture a ticket, ACK, receipt, money or farm return. */
    public JacobRewardJournal reconcile(UUID id,String exactContract,Decision decision){var e=unresolved(id);if(!e.contractHash().equals(exactContract))throw new IllegalArgumentException("Intent contract changed");Objects.requireNonNull(decision);return replace(new Entry(e.id(),e.configuredFarmProfile(),e.contractHash(),decision==Decision.APPLIED?Phase.RECONCILED_APPLIED:Phase.RECONCILED_NOT_APPLIED,null));}
    public String encode(){
        var root=new JsonObject();root.addProperty("schema",1);root.addProperty("generation",generation);var list=new JsonArray();
        for(var e:entries()){var row=new JsonObject();row.addProperty("id",e.id().toString());row.addProperty("configuredFarmProfile",e.configuredFarmProfile());row.addProperty("contractHash",e.contractHash());row.addProperty("phase",e.phase().name());if(e.receiptId()!=null)row.addProperty("receiptId",e.receiptId().toString());list.add(row);}root.add("entries",list);String value=root.toString();if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalStateException("Journal byte bound exceeded");return value;
    }
    private static UUID uuid(String value){var id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException("Canonical UUID required");return id;}
    public static JacobRewardJournal decode(String value){
        if(value==null||value.isBlank()||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Missing/corrupt journal refuses; never reset to empty");
        var root=JsonParser.parseString(value).getAsJsonObject();if(!root.keySet().equals(Set.of("schema","generation","entries"))||root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported journal schema");
        long generation=root.get("generation").getAsLong();if(!root.get("generation").getAsString().equals(Long.toString(generation)))throw new IllegalArgumentException("Exact journal generation required");var rows=root.getAsJsonArray("entries");if(rows.size()>MAX_ENTRIES)throw new IllegalArgumentException("Journal capacity exceeded");var entries=new HashMap<UUID,Entry>();
        for(var element:rows){var row=element.getAsJsonObject();var phase=Phase.valueOf(row.get("phase").getAsString());var keys=phase==Phase.ACKNOWLEDGED?Set.of("id","configuredFarmProfile","contractHash","phase","receiptId"):Set.of("id","configuredFarmProfile","contractHash","phase");if(!row.keySet().equals(keys))throw new IllegalArgumentException("Unsupported journal entry shape");var id=uuid(row.get("id").getAsString());var entry=new Entry(id,row.get("configuredFarmProfile").getAsString(),row.get("contractHash").getAsString(),phase,phase==Phase.ACKNOWLEDGED?uuid(row.get("receiptId").getAsString()):null);if(entries.put(id,entry)!=null)throw new IllegalArgumentException("Duplicate intent");}
        var decoded=new JacobRewardJournal(generation,entries);if(!value.equals(decoded.encode()))throw new IllegalArgumentException("Noncanonical/duplicate/ambiguous journal data refuses");return decoded;
    }
}
