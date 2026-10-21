package dev.veltraph.client.combat.ghost;

import com.google.gson.*;
import dev.veltraph.client.combat.sven.SvenBindingProfile;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Current native contracts are explicit local bindings, never inferred from encrypted identities. */
public record GhostPreflightProfile(String warpCommand,String denial,Vec3 anchor,long timeoutMs,
        boolean modifyJumpBoost,long buffCooldownMs,SvenBindingProfile.Buff buff,String buffCloseMessagePrefix,long warpRetryIntervalMs) {
    public GhostPreflightProfile(String command,String denial,Vec3 anchor,long timeout,boolean modify,long cooldown,SvenBindingProfile.Buff buff){this(command,denial,anchor,timeout,modify,cooldown,buff,"",0);}
    public GhostPreflightProfile(String command,String denial,Vec3 anchor,long timeout,boolean modify,long cooldown,SvenBindingProfile.Buff buff,String prefix){this(command,denial,anchor,timeout,modify,cooldown,buff,prefix,0);}
    public static GhostPreflightProfile parse(String json) {
        if(json==null||json.isBlank())throw new IllegalArgumentException("Ghost source preflight is unbound");
        if(json.length()>32768)throw new IllegalArgumentException("Ghost preflight profile exceeds 32768 characters");
        var o=read(json);keys(o,Set.of("schema","warpCommand","denial","anchor","timeoutMs","modifyJumpBoost","buffCooldownMs","buff","buffCloseMessagePrefix","warpRetryIntervalMs"));
        if(integer(o,"schema")!=1)throw new IllegalArgumentException("Ghost preflight schema must be 1");
        var a=o.getAsJsonArray("anchor");if(a==null||a.size()!=3)throw new IllegalArgumentException("Explicit three-coordinate source anchor required");
        var anchor=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
        if(!Double.isFinite(anchor.x())||!Double.isFinite(anchor.y())||!Double.isFinite(anchor.z())||Math.abs(anchor.x())>30000000||Math.abs(anchor.z())>30000000||Math.abs(anchor.y())>2048)throw new IllegalArgumentException("Finite native anchor required");
        long retry=integer(o,"warpRetryIntervalMs");if(retry<1||retry>60000)throw new IllegalArgumentException("Explicit unbound source warpRetryIntervalMs must be 1..60000");
        long timeout=integer(o,"timeoutMs"),cooldown=integer(o,"buffCooldownMs");if(timeout<500||timeout>60000||cooldown<0||cooldown>3600000)throw new IllegalArgumentException("Explicit local timeout/cooldown out of bounds");
        if(!o.has("modifyJumpBoost")||!o.get("modifyJumpBoost").isJsonPrimitive()||!o.get("modifyJumpBoost").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Explicit MODIFY_JUMP_BOOST boolean required");
        var b=o.getAsJsonObject("buff");if(b==null)throw new IllegalArgumentException("Explicit cookie/potion menu and signal bindings required");
        keys(b,Set.of("skyblockItemId","skyblockItemName","skyblockMenu","cookieButton","cookieMenu","potionButton","potionMenuPrefix","effectNames","disabledLoreMarker","nextPageButton","openCommand","footerPattern","footerStart","footerEnd","footerInactive","rowLabel","rowNeighborMarker","nextRowInactivePrefix"));
        String id=text(b,"skyblockItemId");if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Explicit SkyBlock item namespace:path required");
        String regex=text(b,"footerPattern");java.util.regex.Pattern.compile(regex);
        List<String> effects=new ArrayList<>();for(var e:b.getAsJsonArray("effectNames")){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()||e.getAsString().isBlank()||e.getAsString().length()>100||e.getAsString().codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Invalid effect label");effects.add(e.getAsString());}
        if(effects.isEmpty()||effects.size()>32||new HashSet<>(effects).size()!=effects.size())throw new IllegalArgumentException("Distinct actual effect labels required");
        var buff=new SvenBindingProfile.Buff(id,text(b,"skyblockItemName"),text(b,"skyblockMenu"),text(b,"cookieButton"),text(b,"cookieMenu"),text(b,"potionButton"),text(b,"potionMenuPrefix"),effects,text(b,"disabledLoreMarker"),text(b,"nextPageButton"),b.has("openCommand")?command(text(b,"openCommand")):"",regex,text(b,"footerStart"),text(b,"footerEnd"),text(b,"footerInactive"),text(b,"rowLabel"),text(b,"rowNeighborMarker"),text(b,"nextRowInactivePrefix"));
        return new GhostPreflightProfile(command(text(o,"warpCommand")),text(o,"denial"),anchor,timeout,o.get("modifyJumpBoost").getAsBoolean(),cooldown,buff,o.has("buffCloseMessagePrefix")?text(o,"buffCloseMessagePrefix"):"",retry);
    }
    private static JsonObject read(String json){
        try{var r=new com.google.gson.stream.JsonReader(new java.io.StringReader(json));r.setStrictness(Strictness.STRICT);var o=JsonParser.parseReader(r).getAsJsonObject();if(r.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing Ghost profile data");return o;}
        catch(java.io.IOException invalid){throw new IllegalArgumentException("Invalid Ghost profile JSON",invalid);}
    }
    private static void keys(JsonObject o,Set<String> allowed){if(!allowed.containsAll(o.keySet()))throw new IllegalArgumentException("Unknown profile fields");}
    private static String text(JsonObject o,String key){if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing string: "+key);String s=o.get(key).getAsString();if(s.isBlank()||s.length()>256||s.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Invalid string: "+key);return s;}
    private static long integer(JsonObject o,String key){if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.get(key).getAsJsonPrimitive().isNumber()||!o.get(key).getAsString().matches("[0-9]+"))throw new IllegalArgumentException("Explicit integer: "+key);return o.get(key).getAsLong();}
    private static String command(String s){if(!s.matches("[a-z][a-z0-9_-]*(?: [a-zA-Z0-9 _.-]+)?"))throw new IllegalArgumentException("Single explicit slashless command required");return s;}
}
