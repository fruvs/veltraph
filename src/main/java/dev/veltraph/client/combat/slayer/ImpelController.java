package dev.veltraph.client.combat.slayer;

import java.util.Locale;

/** Finite, one-response-per-prompt Impel actions. Never interprets text as an executable command. */
public final class ImpelController {
    public enum Command { NONE,JUMP,SNEAK,CLICK_UP,CLICK_DOWN }
    public enum Action { NONE,JUMP,SNEAK,TURN_UP,TURN_DOWN,CLICK,EXPIRED }
    public record Receipt(long serial,Command command,long received) {}
    private long seen,started;private Command command=Command.NONE;private boolean done,jumpSent;
    public static Command parse(String text) {
        String clean=text.replaceAll("§.","").strip().toUpperCase(Locale.ROOT);
        if(clean.startsWith("IMPEL: "))clean=clean.substring(7);
        return switch(clean) {case "JUMP"->Command.JUMP;case "SNEAK"->Command.SNEAK;case "CLICK UP"->Command.CLICK_UP;case "CLICK DOWN"->Command.CLICK_DOWN;default->Command.NONE;};
    }
    public Action tick(Receipt receipt,long now,float pitch,boolean onGround,boolean sneaking) {
        if(receipt.serial()>seen) { seen=receipt.serial();command=receipt.command();started=receipt.received();done=false;jumpSent=false; }
        if(receipt.command()==Command.NONE&&receipt.serial()>=seen) { command=Command.NONE;done=true; }
        if(done||command==Command.NONE)return Action.NONE;
        if(now-started>2000) { done=true;return Action.EXPIRED; }
        return switch(command) {
            case JUMP -> {if(!onGround){if(jumpSent)done=true;yield Action.NONE;}jumpSent=true;yield Action.JUMP;}
            case SNEAK -> {if(sneaking&&now-started>=150){done=true;yield Action.NONE;}yield Action.SNEAK;}
            case CLICK_UP -> {if(pitch<=-75){done=true;yield Action.CLICK;}yield Action.TURN_UP;}
            case CLICK_DOWN -> {if(pitch>=75){done=true;yield Action.CLICK;}yield Action.TURN_DOWN;}
            case NONE -> Action.NONE;
        };
    }
    public void reset(long serial) { seen=serial;command=Command.NONE;done=false;jumpSent=false;started=0; }
}
