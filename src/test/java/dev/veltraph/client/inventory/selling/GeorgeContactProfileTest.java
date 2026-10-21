package dev.veltraph.client.inventory.selling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeorgeContactProfileTest {
    private static final String PROFILE="""
        {"prefix":"localcall ","contact":"Native George","title":"Offer Pets",
         "marker":{"registryId":"minecraft:paper","name":"Native Contact","components":"exact","count":1},
         "receipt":"Native George ready","rejections":["Native call refused"],"timeoutTicks":80}
        """;
    @Test void directPrefixAndContactRemainExplicitLiterals(){var profile=GeorgeContactProfile.parse(PROFILE);assertEquals("localcall Native George",profile.command());assertEquals("Offer Pets",profile.title());}
    @Test void defaultMissingAndUnknownFieldsRefuse(){for(String invalid:new String[]{"","{}","null",PROFILE.replace("\"prefix\":", "\"extra\":0,\"prefix\":")})assertThrows(RuntimeException.class,()->GeorgeContactProfile.parse(invalid));}
    @Test void markerCountComponentsAndNumericTypeRemainExact(){for(String invalid:new String[]{PROFILE.replace("\"count\":1","\"count\":2"),PROFILE.replace("\"exact\"","\"\""),PROFILE.replace("\"count\":1","\"count\":1.5")})assertThrows(RuntimeException.class,()->GeorgeContactProfile.parse(invalid));}
    @Test void commandAndReceiptCannotContainGuessedOrAmbiguousDefaults(){for(String invalid:new String[]{PROFILE.replace("localcall ","/call "),PROFILE.replace("Native George ready",""),PROFILE.replace("Native call refused","Native George ready")})assertThrows(RuntimeException.class,()->GeorgeContactProfile.parse(invalid));}
}
