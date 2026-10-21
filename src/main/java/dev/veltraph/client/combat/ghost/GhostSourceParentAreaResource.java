package dev.veltraph.client.combat.ghost;

/** Exact owning-source enum metadata; no acquisition, account identity or network behavior. */
public enum GhostSourceParentAreaResource {
    REV("rev","915b4bc13d6eda023ef8600d11e87f14056a2a714b1a815868e559476c19b4d2",-182,38,-160),
    SVEN("sven_cave","629bfb692c34aa4a50be49367643b04887953a1e9919a96ac6e083354291e88f",-410,45,-105),
    SVEN_RUINS("sven_ruins","2a08503018a02fd2956152bbbc036144a1d63809b08341819fc38b0daf1a060f",-275,80,30),
    SPIDER("spider_cave","9c821e37923b5e9befbc81e89bd8d03eb474f995f406a06c6f0596a71887a75e",-375,39,-325),
    VOIDGLOOM("voidgloom","ff79f37c0a735292e1e2723f3ce11215cc94db328bc81709d2ecdd4c081cf774",-630,5,-380);
    private final String resource,sha256;
    private final GhostSourceOrderedStandingProvider.Cell origin;
    GhostSourceParentAreaResource(String resource,String sha256,int x,int y,int z){
        this.resource=resource;this.sha256=sha256;this.origin=new GhostSourceOrderedStandingProvider.Cell(x,y,z);
    }
    public String resource(){return resource;}
    public String sha256(){return sha256;}
    public GhostSourceOrderedStandingProvider.Cell origin(){return origin;}
}
