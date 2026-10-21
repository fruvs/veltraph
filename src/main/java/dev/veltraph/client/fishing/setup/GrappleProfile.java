package dev.veltraph.client.fishing.setup;

import com.google.gson.Gson;
import java.util.List;
import java.util.Objects;

/** Explicit ability identity and bounds; source salted tool/cooldowns have no guessed defaults. */
public record GrappleProfile(String itemName,String itemId,String targetName,Boolean avoidLava,
        Double maximumUseDistance,Double minimumHealthFraction,Integer itemConsumption,
        Long minimumUseIntervalMillis,Long requestTimeoutMillis,Long stageTimeoutMillis) {
    public GrappleProfile {
        for(String s:List.of(itemName,itemId))if(s.isBlank()||s.length()>128)throw new IllegalArgumentException("Exact grapple tool is required");
        Objects.requireNonNull(targetName);if(targetName.length()>128)throw new IllegalArgumentException("Invalid target name");
        for(Object v:List.of(avoidLava,maximumUseDistance,minimumHealthFraction,itemConsumption,minimumUseIntervalMillis,requestTimeoutMillis,stageTimeoutMillis))Objects.requireNonNull(v);
        if(!Double.isFinite(maximumUseDistance)||maximumUseDistance<=14||maximumUseDistance>64||!Double.isFinite(minimumHealthFraction)||minimumHealthFraction<0||minimumHealthFraction>1||itemConsumption<0||itemConsumption>1||minimumUseIntervalMillis<0||requestTimeoutMillis<1||requestTimeoutMillis>30000||stageTimeoutMillis<requestTimeoutMillis||stageTimeoutMillis>120000)throw new IllegalArgumentException("Declare bounded grapple range, health, consumption and timing contract");
    }
    public static GrappleProfile parse(String json){try{if(json.length()>16384)throw new IllegalArgumentException();return Objects.requireNonNull(new Gson().fromJson(json,GrappleProfile.class));}catch(RuntimeException e){throw new IllegalArgumentException("Configure exact grapple identity and explicit ability bounds",e);}}
}
