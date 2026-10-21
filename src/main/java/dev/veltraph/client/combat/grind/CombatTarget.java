package dev.veltraph.client.combat.grind;

import dev.veltraph.client.world.Vec3;

/** Only actual attackable mobs enter the controller, never player/NPC/nameplate entities. */
public record CombatTarget(int id,String name,Vec3 position,boolean alive,boolean protectedTarget) {}
