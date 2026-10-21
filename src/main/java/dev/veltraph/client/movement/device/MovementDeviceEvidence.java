package dev.veltraph.client.movement.device;

import java.util.*;
import java.util.regex.Pattern;

/** Item facts only: lore never establishes readiness, actual spending, or an applied movement effect. */
public final class MovementDeviceEvidence {
    public enum Kind { INSTANT_TRANSMISSION, ETHER_TRANSMISSION, SPEED_BOOST, GRAPPLING_HOOK, SPRING_BOOTS, LEAP, WITHER_IMPACT }
    public record Capability(Kind kind,double range,double manaCost,boolean sneak,boolean causesOtherActions) {
        public Capability { Objects.requireNonNull(kind);if(!Double.isFinite(range)||range<0||range>100||!Double.isFinite(manaCost)||manaCost<0||manaCost>10000)throw new IllegalArgumentException("Invalid tooltip values"); }
    }
    private static final Pattern HEADER=Pattern.compile("^Ability: (Instant Transmission|Ether Transmission|Speed Boost|Dragon Rage|Spring Boots|Leap|Wither Impact)\\s+(RIGHT CLICK|SNEAK RIGHT CLICK)$");
    private static final Pattern MANA=Pattern.compile("^Mana Cost: ([0-9]+(?:\\.[0-9]+)?)(?:\\uE003)?$");
    private static final Pattern RANGE=Pattern.compile("^Teleport ([0-9]+(?:\\.[0-9]+)?) blocks ahead of you and$");
    private static final Pattern ETHER_RANGE=Pattern.compile("^([0-9]+(?:\\.[0-9]+)?) blocks away\\.$");
    private MovementDeviceEvidence() {}
    public static List<Capability> parse(String serverId,List<String> rawLore) {
        if(serverId==null||rawLore==null||rawLore.size()>128)return List.of();
        var lines=new ArrayList<String>();for(var l:rawLore){if(l==null||l.length()>512)return List.of();lines.add(l.replaceAll("§.","").strip());}
        var out=new ArrayList<Capability>();
        for(int i=0;i<lines.size();i++) {
            var h=HEADER.matcher(lines.get(i));if(!h.matches())continue;
            int end=i+1;while(end<lines.size()&&!lines.get(end).startsWith("Ability:"))end++;
            String ability=h.group(1);boolean sneak=h.group(2).equals("SNEAK RIGHT CLICK");
            Kind kind=switch(ability){case "Instant Transmission"->Kind.INSTANT_TRANSMISSION;case "Ether Transmission"->Kind.ETHER_TRANSMISSION;case "Speed Boost"->Kind.SPEED_BOOST;case "Spring Boots"->Kind.SPRING_BOOTS;case "Leap"->Kind.LEAP;case "Wither Impact"->Kind.WITHER_IMPACT;default->null;};
            if(kind==null||!identityAllows(serverId,kind))continue;
            double mana=Double.NaN,range=kind==Kind.SPEED_BOOST?0:Double.NaN;boolean duplicate=false;
            for(int n=i+1;n<end;n++) {
                var m=MANA.matcher(lines.get(n));if(m.matches()){if(Double.isFinite(mana))duplicate=true;mana=number(m.group(1));}
                var r=(kind==Kind.ETHER_TRANSMISSION?ETHER_RANGE:RANGE).matcher(lines.get(n));if(r.matches()){if(Double.isFinite(range)&&kind!=Kind.SPEED_BOOST)duplicate=true;range=number(r.group(1));}
            }
            // Unqualified device variants remain absent, rather than inheriting guessed nominal values.
            if(!duplicate&&Double.isFinite(mana)&&Double.isFinite(range)&&range>=0&&range<=100&&mana>=0&&mana<=10000
                    &&(kind!=Kind.ETHER_TRANSMISSION||sneak))out.add(new Capability(kind,range,mana,sneak,kind==Kind.WITHER_IMPACT));
        }
        var seen=EnumSet.noneOf(Kind.class);for(var c:out)if(!seen.add(c.kind()))return List.of();
        return List.copyOf(out);
    }
    private static double number(String value){try{return Double.parseDouble(value);}catch(NumberFormatException invalid){return Double.NaN;}}
    public static boolean identityAllows(String id,Kind kind){return switch(kind){
        case INSTANT_TRANSMISSION,ETHER_TRANSMISSION->id.equals("ASPECT_OF_THE_END")||id.equals("ASPECT_OF_THE_VOID");
        case SPEED_BOOST->id.equals("ROGUE_SWORD");case GRAPPLING_HOOK->id.equals("GRAPPLING_HOOK");
        case SPRING_BOOTS->id.equals("SPRING_BOOTS");case LEAP->id.equals("LEAPING_SWORD")||id.equals("SILK_EDGE_SWORD");
        case WITHER_IMPACT->Set.of("HYPERION","ASTRAEA","SCYLLA","VALKYRIE").contains(id);};}
}
