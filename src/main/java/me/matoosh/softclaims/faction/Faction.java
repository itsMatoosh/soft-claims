package me.matoosh.softclaims.faction;

import lombok.Data;

/**
 * Represents a faction.
 */
@Data
public class Faction {
    private final String name;
    private final double power;
    private final boolean system;
}
