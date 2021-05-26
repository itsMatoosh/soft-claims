package me.matoosh.softclaims.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matoosh.softclaims.SoftClaimsPlugin;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

@Getter
@AllArgsConstructor
public class WorldService implements IReloadable {

    private final SoftClaimsPlugin plugin;

    /**
     * Set of all disabled worlds' names.
     */
    private final Set<String> disabledWorlds = new HashSet<>();

    @Override
    public void reload() {
        // reload disabled worlds
        disabledWorlds.clear();
        disabledWorlds.addAll(this.plugin.getConfig()
                .getStringList("disabledWorlds"));
    }

    /**
     * Checks whether a given world is disabled.
     * @param world The world.
     * @return Whether the given world is disabled.
     */
    public boolean isWorldDisabled (World world) {
        return disabledWorlds.contains(world.getName());
    }
}
