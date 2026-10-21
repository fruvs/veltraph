package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
/** Source visibility tries eight eye offsets; actual terrain/entity ray acceptance stays native. */
public final class GhostSourceVisibilitySamples {
    public record Eye(double x,double y,double z) {}
    private GhostSourceVisibilitySamples() {}
    public static List<Eye> around(Eye eye) {
        if (eye==null || !Double.isFinite(eye.x()) || !Double.isFinite(eye.y()) || !Double.isFinite(eye.z()))
            throw new IllegalArgumentException("Finite actual native eye required");
        var values=new ArrayList<Eye>();
        for (double x:new double[]{-.5,.5}) for (double y:new double[]{-.5,.5}) for (double z:new double[]{-.5,.5})
            values.add(new Eye(eye.x()+x,eye.y()+y,eye.z()+z));
        return List.copyOf(values);
    }
}
