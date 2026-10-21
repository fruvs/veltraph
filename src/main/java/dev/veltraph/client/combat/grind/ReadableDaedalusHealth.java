package dev.veltraph.client.combat.grind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/** Last space-delimited heart token and source compact-number scales, with overflow denied. */
public final class ReadableDaedalusHealth {
    private ReadableDaedalusHealth() {}
    private static final Map<String,Long> SCALES=Map.of("k",1000L,"M",1000000L,"B",1000000000L,
            "T",1000000000000L,"P",1000000000000000L,"E",1000000000000000000L);
    public static Optional<Float> parseAssociatedPlate(String raw) {
        if(raw==null||raw.length()>1024)return Optional.empty();
        String text=raw.replaceAll("(?i)\u00a7[0-9A-FK-OR]","");
        String token=text.substring(text.lastIndexOf(' ')+1);
        if(!token.endsWith("\u2764")||token.contains("/"))return Optional.empty();
        String number=token.substring(0,token.length()-1).trim().replace(",","").replace(" ","");
        if(number.isEmpty())return Optional.empty();
        try {
            long integral;
            String suffix=number.substring(number.length()-1);Long scale=SCALES.get(suffix);
            if(scale==null)integral=Long.parseLong(number);
            else integral=new BigDecimal(number.substring(0,number.length()-1)).multiply(BigDecimal.valueOf(scale))
                    .setScale(0,RoundingMode.HALF_UP).longValueExact();
            float value=integral;
            return integral>0&&Float.isFinite(value)?Optional.of(value):Optional.empty();
        }catch(RuntimeException malformed){return Optional.empty();}
    }
}
