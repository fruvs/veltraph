package dev.veltraph.client.observation.unexpected;

import java.util.Locale;
import java.util.Optional;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.Value;

/** Ephemeral native UI submission parsing. Retains a command category/optional numeric target only. */
public final class NativeNavigationCommand {
    public record Correlation(Optional<Value> exactTarget) { public Correlation {exactTarget=java.util.Objects.requireNonNull(exactTarget);} }
    private NativeNavigationCommand() {}
    public static Optional<Correlation> parse(String submitted) {
        if(submitted==null||submitted.length()>256)return Optional.empty();
        String text=submitted.strip();if(!text.startsWith("/"))return Optional.empty();
        String[] parts=text.substring(1).split("\\s+");if(parts.length==0)return Optional.empty();
        String command=parts[0].toLowerCase(Locale.ROOT);
        if((command.equals("hub")||command.equals("lobby")||command.equals("home")||command.equals("is")||command.equals("island"))&&parts.length==1)
            return Optional.of(new Correlation(Optional.empty()));
        if(command.equals("warp")&&parts.length==2)return Optional.of(new Correlation(Optional.empty()));
        if(command.equals("tp")||command.equals("teleport")) {
            int offset=parts.length==5&&parts[1].equals("@s")?2:1;
            if(parts.length==offset+3) {
                try {
                    double x=Double.parseDouble(parts[offset]),y=Double.parseDouble(parts[offset+1]),z=Double.parseDouble(parts[offset+2]);
                    if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30_000_000&&Math.abs(z)<=30_000_000&&Math.abs(y)<=30_000_000)
                        return Optional.of(new Correlation(Optional.of(Value.position(x,y,z))));
                } catch(NumberFormatException ignored) { /* Relative coordinates have no exact resolved target here. */ }
                // A valid relative/local-coordinate form has a known navigation category but hidden target.
                String number="[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)";
                String coordinate="(?:[~^](?:"+number+")?|"+number+")";
                boolean anyLocal=false,anyRelative=false,allLocal=true,valid=true;
                for(int i=offset;i<offset+3;i++) {anyLocal|=parts[i].startsWith("^");anyRelative|=parts[i].startsWith("^")||parts[i].startsWith("~");allLocal&=parts[i].startsWith("^");valid&=parts[i].matches(coordinate);}
                if(valid&&anyRelative&&(!anyLocal||allLocal))
                    return Optional.of(new Correlation(Optional.empty()));
            }
        }
        return Optional.empty();
    }
}
