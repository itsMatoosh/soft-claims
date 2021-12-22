package me.matoosh.softclaims.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class LocationUtil {
    /**
     * Finds players around a given location.
     * @param location The location.
     * @param distance Radius around which to find players.
     * @return Players around a given location.
     */
    public static List<Player> getPlayersAroundPoint(Location location, int distance) {
        int distSquared = distance * distance;
        return Bukkit.getOnlinePlayers().parallelStream()
                .filter(p -> p.getWorld() == location.getWorld())
                .filter(p -> p.getLocation().distanceSquared(location) < distSquared)
                .collect(Collectors.toList());
    }

}
