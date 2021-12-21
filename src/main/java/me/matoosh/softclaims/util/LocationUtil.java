package me.matoosh.softclaims.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LocationUtil {
    public static List<Player> getPlayersAroundPoint(Location location, int chunkRadius) {
        List<Player> players = new ArrayList<>();
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }

        // iterate through all chunks around center
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    // Add all players from this chunk to the list
                    players.addAll(
                            Arrays.stream(world.getChunkAt(x, z).getEntities()).parallel()
                            .filter((entity -> entity instanceof Player))
                            .map(entity -> (Player) entity)
                            .collect(Collectors.toList())
                    );
                }
            }
        }

        return players;
    }

}
