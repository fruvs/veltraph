package dev.veltraph.client.combat.ghost;

/** Source path assembly only; caller supplies an authorized opaque filename without reading accounts. */
public final class GhostSourceCatalogLocation {
    public static final String DIRECTORY_SEGMENT="ghost-route";
    private GhostSourceCatalogLocation() {}
    public static String relativeFile(String observedIdentityBasename,String observedJsonExtension) {
        if (observedIdentityBasename==null || !observedIdentityBasename.matches("[A-Za-z0-9_-]{1,128}")
                || observedJsonExtension==null || !observedJsonExtension.matches("[A-Za-z0-9]{1,12}"))
            throw new IllegalArgumentException("Explicit bounded filename and extension required");
        return DIRECTORY_SEGMENT+"/"+observedIdentityBasename+"."+observedJsonExtension;
    }
}
