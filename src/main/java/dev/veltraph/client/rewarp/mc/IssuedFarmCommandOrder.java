package dev.veltraph.client.rewarp.mc;

/** Local invocation bookkeeping only. It cannot authorize native work or construct a receipt. */
final class IssuedFarmCommandOrder {
    private int commands, packets, transports;
    private Object packet;
    private boolean refused, leaf, bodyEntered, bodyReturned, directWriteReturned, dispatchReturned, transportReturned, packetReturned, commandReturned;
    static boolean validCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 256 || command.charAt(0) == '/') return false;
        for (int i=0; i<command.length(); i++) if (command.charAt(i)<32 || command.charAt(i)==127) return false;
        return true;
    }
    boolean commandEntered() { if (++commands != 1 || commandReturned) refused = true; return !refused; }
    boolean bind(Object value) {
        if (commands != 1 || packets != 0 || packet != null || value == null) refused = true;
        else packet = value;
        return !refused;
    }
    boolean packetEntered(Object value) {
        if (++packets != 1 || commands != 1 || packet != value || packetReturned) refused = true;
        return !refused;
    }
    boolean transportEntered(Object value) {
        if (++transports != 1 || packets != 1 || commands != 1 || packet != value || transportReturned) refused = true;
        return !refused;
    }
    boolean leaf(Object value) {
        if (commands != 1 || packets != 1 || transports != 1 || packet != value || leaf) refused = true;
        if (!refused) leaf = true;
        return !refused;
    }
    boolean bodyEntered(Object value) {if(commands!=1||packets!=1||transports!=1||packet!=value||!leaf||bodyEntered)refused=true;if(!refused)bodyEntered=true;return !refused;}
    boolean directWriteReturned(Object value,boolean normal){if(!bodyEntered||bodyReturned||directWriteReturned||packet!=value||!normal)refused=true;if(!refused)directWriteReturned=true;return !refused;}
    void bodyExited(Object value,boolean normal,boolean scheduled){if(!bodyEntered||bodyReturned||packet!=value||!normal||(!scheduled&&!directWriteReturned))refused=true;if(!refused)bodyReturned=true;}
    void dispatchExited(Object value, boolean normal) {
        if (commands != 1 || packets != 1 || transports != 1 || packet != value || !normal || !leaf || !bodyReturned || dispatchReturned) refused = true;
        if (!refused) dispatchReturned = true;
    }
    void transportExited(Object value, boolean normal) {
        if (transports != 1 || packet != value || !normal || !dispatchReturned) refused = true;
        if (!refused) transportReturned = true;
        --transports;
    }
    void packetExited(Object value, boolean normal) {
        if (packets != 1 || transports != 0 || packet != value || !normal || !transportReturned) refused = true;
        if (!refused) packetReturned = true;
        --packets;
    }
    void commandExited(boolean normal) {
        if (commands != 1 || packets != 0 || transports != 0 || !normal || !packetReturned) refused = true;
        if (!refused) commandReturned = true;
        --commands;
    }
    void refuse() { refused = true; }
    boolean matches(Object value) { return packet != null && packet == value; }
    boolean complete() { return !refused && leaf && bodyEntered && bodyReturned && dispatchReturned && transportReturned && packetReturned && commandReturned && commands == 0 && packets == 0 && transports == 0; }
}
