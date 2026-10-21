package dev.veltraph.client.hydra;
/** External native control overrides registered parent selection, including denied/unverified scope. */
public final class SourceFloorCustody {
    private SourceFloorCustody(){}
    public static Object select(boolean externalActive,Object verifiedExternal,Object verifiedRegistered){return externalActive?verifiedExternal:verifiedRegistered;}
}
