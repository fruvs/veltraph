package dev.veltraph.client.service.mc;
public interface NormalIdleMouseCarrier {default String cachedNormalIdleMouseDiagnostic(){return "UNKNOWN_CARRIER";}Object normalIdleMouseSnapshot();boolean normalIdleMouseCurrent(Object exactSnapshot);}
