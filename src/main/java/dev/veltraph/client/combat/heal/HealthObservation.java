package dev.veltraph.client.combat.heal;

/** Typed, received health comparison units. The cue and provenance never assert a healing cause. */
public record HealthObservation(Format format,String cue,double health,double maximum,float absorption,
        CapturedHudVitals.Scope scope,long serial,long receivedAt) {
    public enum Format { CAPTURED_HUD, LEGACY_HEART, NATIVE_ENTITY }
    public HealthObservation {if(format==null||cue==null||cue.isBlank()||scope==null||!Double.isFinite(health)||!Double.isFinite(maximum)||health<0||maximum<=0||!Float.isFinite(absorption)||absorption<0||serial<0||receivedAt<0)throw new IllegalArgumentException("Invalid observed health");}
    public static HealthObservation captured(CapturedHudVitals.Reading reading){var f=reading.fields();return new HealthObservation(Format.CAPTURED_HUD,"SYSTEM_LITERAL_E010",f.currentHealth(),f.maximumHealth(),0,reading.scope(),reading.serial(),reading.receivedAt());}
    public boolean sameBasis(HealthObservation other){return other!=null&&format==other.format&&cue.equals(other.cue)&&maximum==other.maximum&&scope.same(other.scope);}
}
