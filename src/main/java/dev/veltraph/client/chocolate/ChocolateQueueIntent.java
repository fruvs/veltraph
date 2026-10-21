package dev.veltraph.client.chocolate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Local durable request contract. Fingerprints support exact comparison, never authentication/account proof. */
public record ChocolateQueueIntent(int schema,String id,String scope,String profileJson,String title,String upgrade,
        ChocolateFactoryProfile.Kind kind,int slot,int level,String price,String targetFingerprint,
        String budget,String reserve,String beforeBalance,String beforeProduction,String beforeCapacity,
        String knownSpentBefore,String manualDebitBefore,int selectedHotbar,int beforeRevision,long capturedMillis,
        Map<Integer,String> protectedFingerprints,String identityEncoding,String originalContextFacts,String accountBoundary) {
    public ChocolateQueueIntent {
        if(schema!=1||id==null||id.isBlank()||scope==null||!scope.matches("[a-zA-Z0-9_-]{1,96}")||kind==null||level<0||level==Integer.MAX_VALUE||slot<0||selectedHotbar<0||selectedHotbar>8||capturedMillis<0||targetFingerprint==null||!targetFingerprint.matches("[a-f0-9]{64}")||!"native-persistent-tags-and-stable-row-sha256-v1".equals(identityEncoding))throw new IllegalArgumentException("Incomplete durable queue intent");
        var profile=ChocolateFactoryProfile.decode(profileJson);profile.validate();if(!profile.scope().equals(scope)||!profile.title().equals(title))throw new IllegalArgumentException("Original profile does not match queue intent");
        for(String value:List.of(price,budget,reserve,beforeBalance,beforeProduction,beforeCapacity,knownSpentBefore,manualDebitBefore))if(new BigDecimal(value).signum()<0)throw new IllegalArgumentException("Invalid durable queue accounting");
        if(new BigDecimal(price).signum()==0||new BigDecimal(beforeCapacity).signum()==0||new BigDecimal(beforeBalance).compareTo(new BigDecimal(beforeCapacity))>0||new BigDecimal(knownSpentBefore).add(new BigDecimal(manualDebitBefore)).add(new BigDecimal(price)).compareTo(new BigDecimal(budget))>0||new BigDecimal(beforeBalance).subtract(new BigDecimal(price)).compareTo(new BigDecimal(reserve))<0)throw new IllegalArgumentException("Inconsistent original queue budget/reserve");
        protectedFingerprints=Map.copyOf(protectedFingerprints);for(var e:protectedFingerprints.entrySet())if(e.getKey()<0||e.getValue()==null||!e.getValue().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid protected fingerprint");
        if(originalContextFacts==null||originalContextFacts.isBlank()||accountBoundary==null||accountBoundary.isBlank())throw new IllegalArgumentException("Original account boundary must be retained");
    }
    static ChocolateQueueIntent capture(ChocolateFactoryRuntime runtime,ChocolateFactoryProfile profile){var s=runtime.selection();if(s==null||s.selectedHotbar()<0)throw new IllegalArgumentException("Exact native selected hotbar/original intent required before request");var o=s.observation();var q=s.quote();if(!o.profileVerified()||!o.contextBound()||!o.cursorEmpty()||o.balance().isEmpty()||o.production().isEmpty()||o.capacity().isEmpty())throw new IllegalArgumentException("Original queue observation incomplete");return new ChocolateQueueIntent(1,s.intentId(),profile.scope(),profile.encode(),o.title(),q.id(),q.kind(),q.slot(),q.level(),q.price().toPlainString(),fingerprint(q.fullIdentity()),s.budget().toPlainString(),s.reserve().toPlainString(),o.balance().get().toPlainString(),o.production().get().toPlainString(),o.capacity().get().toPlainString(),runtime.spent().toPlainString(),runtime.manualDeclaredDebit().toPlainString(),s.selectedHotbar(),o.revision(),o.observedMillis(),protectedRows(o),"native-persistent-tags-and-stable-row-sha256-v1","process-local session="+System.identityHashCode(o.session())+",menu="+System.identityHashCode(o.menu())+",screen="+System.identityHashCode(o.screen())+",mode="+profile.contextMode()+"; diagnostic only, not persistent account proof",profile.usesCapturedLayout()?"Local observed-session scope; no persistent account identity captured":"Configured visible profile marker; not authenticated persistent account identity");}
    static String fingerprint(Object nativeIdentity){
        if(nativeIdentity==null)throw new IllegalArgumentException("Missing full native identity");String encoding;
        if(nativeIdentity instanceof dev.veltraph.client.inventory.NativeStackIdentity.Snapshot snapshot)encoding="native:"+snapshot.persistentCanonicalEncoding();
        else if(nativeIdentity instanceof String synthetic)encoding="synthetic-test-string:"+synthetic;
        else if(nativeIdentity instanceof ChocolateFactoryObservation.Row row){try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeUTF("protected-row-v1");out.writeInt(row.slot());out.writeBoolean(row.playerInventory());out.writeUTF(row.itemId());out.writeUTF(row.name());out.writeInt(row.count());out.writeInt(row.lore().size());for(String lore:row.lore())out.writeUTF(lore);out.writeUTF(fingerprint(row.fullIdentity()));out.flush();encoding="stable-row:"+Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(java.io.IOException unsupported){throw new IllegalArgumentException("Protected row cannot be canonically encoded",unsupported);}}
        else throw new IllegalArgumentException("Unsupported opaque identity: persistent native codec required; never hash object.toString");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoding.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static Map<Integer,String> protectedRows(ChocolateFactoryObservation o){var result=new TreeMap<Integer,String>();for(var row:o.rows().values())if(row.playerInventory())result.put(row.slot(),fingerprint(row));return Map.copyOf(result);}
}
