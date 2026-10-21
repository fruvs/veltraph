package dev.veltraph.client.garden.pests;

import java.util.List;
import java.util.OptionalInt;

/** Recovered count token grammar, bound to explicit observed prefixes; no native label is guessed. */
public final class PestAutoAdmissionSignals {
    private PestAutoAdmissionSignals() {}
    /** Adapter supplies case-preserved native text and its exact, observed prefix contract. */
    public record Bindings(String tabPrefix,String sidebarPrefix) {}

    /**
     * TAB precedes SIDEBAR. Source splits on a literal space, including trailing empty tokens.
     * A matching TAB token beginning with x returns its signed integer after removing every x.
     * A malformed x token returns unknown immediately, without borrowing a SIDEBAR count.
     */
    public static OptionalInt read(Bindings bindings,List<String> tabRows,List<String> sidebarRows) {
        if(bindings==null||bindings.tabPrefix()==null||bindings.tabPrefix().isBlank()
                ||bindings.sidebarPrefix()==null||bindings.sidebarPrefix().isBlank()
                ||tabRows==null||sidebarRows==null)return OptionalInt.empty();
        for(String row:tabRows) {
            if(row==null||!row.startsWith(bindings.tabPrefix()))continue;
            String token=lastSpaceToken(row);
            if(token.startsWith("x"))return signedInteger(token.replace("x",""));
        }
        for(String row:sidebarRows)
            if(row!=null&&row.startsWith(bindings.sidebarPrefix()))return signedInteger(lastSpaceToken(row));
        return OptionalInt.empty();
    }
    private static String lastSpaceToken(String row) { return row.substring(row.lastIndexOf(' ')+1); }
    private static OptionalInt signedInteger(String token) {
        try { return OptionalInt.of(Integer.parseInt(token)); }
        catch(NumberFormatException invalid) { return OptionalInt.empty(); }
    }
}
