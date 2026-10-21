package dev.veltraph.client.garden.pests;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Pure timestamp arithmetic over an actual received-message observation; no loadout authority. */
public final class PestEquipmentCooldown {
    public record Scope(Object player,Object world,Object connection,Object farm,Object policy,String profile) {
        public Scope {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(farm);Objects.requireNonNull(policy);
            if(profile==null||profile.isBlank())throw new IllegalArgumentException("Original profile required");}
    }
    public record Bindings(List<String> spawnPrefixes,int cooldownSeconds) {
        public Bindings {spawnPrefixes=List.copyOf(spawnPrefixes);if(spawnPrefixes.size()!=3||cooldownSeconds<1||cooldownSeconds>3600)
                throw new IllegalArgumentException("Three observed spawn prefixes and bounded configured cooldown required");
            for(int i=0;i<spawnPrefixes.size();i++){String a=spawnPrefixes.get(i);if(a==null||a.isBlank()||a.length()>256||a.indexOf('§')>=0||a.indexOf('\n')>=0||a.indexOf('\r')>=0)
                    throw new IllegalArgumentException("Exact bounded color-stripped received prefixes required");
                for(int j=i+1;j<spawnPrefixes.size();j++){String b=spawnPrefixes.get(j);if(b!=null&&(a.startsWith(b)||b.startsWith(a)))throw new IllegalArgumentException("Independent spawn prefixes required");}}
        }
    }
    private Scope scope;private Bindings bindings;private long serial=-1,receivedAt,last=-1;private boolean known,retired;
    /** Caller must be the actual Root receipt boundary; matching text alone is not an effect witness. */
    public boolean received(Scope actual,Bindings wanted,long nativeSerial,String colorStrippedMessage,long now){
        if(retired||actual==null||wanted==null||nativeSerial<0||now<0||colorStrippedMessage==null||colorStrippedMessage.length()>2048)return false;
        if(scope==null){scope=actual;bindings=wanted;}
        else if(!same(scope,actual)||!Objects.equals(bindings,wanted)){invalidate();return false;}
        if(nativeSerial<=serial||now<last){invalidate();return false;}
        serial=nativeSerial;last=now;
        if(wanted.spawnPrefixes().stream().noneMatch(colorStrippedMessage::startsWith))return false;
        receivedAt=now;known=true;return true;
    }
    public OptionalInt remaining(Scope actual,Bindings wanted,long now){
        if(retired||!known)return OptionalInt.empty();
        if(!same(scope,actual)||!Objects.equals(bindings,wanted)||now<last||now<receivedAt){invalidate();return OptionalInt.empty();}
        last=now;
        try{long left=(Math.addExact(receivedAt,Math.multiplyExact((long)bindings.cooldownSeconds(),1000))-now)/1000;
            if(left<Integer.MIN_VALUE||left>Integer.MAX_VALUE)return OptionalInt.empty();return OptionalInt.of((int)left);
        }catch(ArithmeticException unavailable){invalidate();return OptionalInt.empty();}
    }
    /** Revocation is permanent for this original Root generation; no replay/rebind can renew it. */
    public void invalidate(){retired=true;known=false;}
    private static boolean same(Scope a,Scope b){return a!=null&&b!=null&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.farm()==b.farm()&&a.policy()==b.policy()&&a.profile().equals(b.profile());}
}
