package me.matoosh.softclaims.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.ChunkInfo;
import me.matoosh.softclaims.exception.WorldDisabledException;
import me.matoosh.softclaims.exception.faction.FactionDoesntExistException;
import me.matoosh.softclaims.exception.faction.FactionPermissionsDeniedException;
import me.matoosh.softclaims.exception.faction.core.ChunkClaimedByOtherFactionException;
import me.matoosh.softclaims.exception.faction.core.InvalidCoreBlockException;
import me.matoosh.softclaims.faction.Faction;
import me.matoosh.softclaims.faction.FactionPermission;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FactionCoreServiceTest {
    /**
     * Reference to the faction service.
     */
    @Mock
    private FactionService factionService;
    /**
     * Reference to the world service.
     */
    @Mock
    private WorldService worldService;

    /**
     * Faction core metadata storage.
     */
    @Mock
    private BlockMetadataStorage<String> factionCoreStorage;

    /**
     * Service under test.
     */
    private FactionCoreService factionCoreService;

    private final Faction sampleFaction = new Faction(
            "sampleFactionId", "Sample Faction", 42.0);
    private final Faction sampleFaction2 = new Faction(
            "sampleFactionId2", "Sample Faction 2", 3.0);

    private Block sampleCoreBlock;
    private Player samplePlayer;
    private World sampleWorld;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        sampleWorld = server.addSimpleWorld("testWorld");
        sampleCoreBlock = sampleWorld.getBlockAt(1,1,1);
        factionCoreService = new FactionCoreService(factionService, worldService, factionCoreStorage);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void createCoreInvalidMaterial() {
        // mock invalid core material
        sampleCoreBlock.setType(Material.OBSIDIAN);
        assertThrows(InvalidCoreBlockException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void createCoreDisabledWorld() {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock disabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(true);
        assertThrows(WorldDisabledException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void createCoreNoFaction() throws FactionDoesntExistException {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock enabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(false);
        // mock no faction
        when(factionService.getFaction(samplePlayer))
                .thenThrow(FactionDoesntExistException.class);
        assertThrows(FactionDoesntExistException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void createCoreNoFactionPermissions() throws FactionDoesntExistException {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock enabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(false);
        // mock faction
        when(factionService.getFaction(samplePlayer))
                .thenReturn(sampleFaction);
        // mock faction permissions
        when(factionService.hasPlayerPermission(sampleFaction.id(), samplePlayer, FactionPermission.CLAIM_CHUNK))
                .thenReturn(false);
        assertThrows(FactionPermissionsDeniedException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void createCoreAlreadyClaimedByAnotherFaction() throws FactionDoesntExistException {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock enabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(false);
        // mock faction
        when(factionService.getFaction(samplePlayer))
                .thenReturn(sampleFaction);
        // mock faction permissions
        when(factionService.hasPlayerPermission(sampleFaction.id(), samplePlayer, FactionPermission.CLAIM_CHUNK))
                .thenReturn(true);
        // mock chunk claim
        when(factionService.getFaction(sampleCoreBlock.getChunk()))
                .thenReturn(sampleFaction2);
        assertThrows(ChunkClaimedByOtherFactionException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void createCoreAlreadyCoreInChunk() throws FactionDoesntExistException {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock enabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(false);
        // mock faction
        when(factionService.getFaction(samplePlayer))
                .thenReturn(sampleFaction);
        // mock faction permissions
        when(factionService.hasPlayerPermission(sampleFaction.id(), samplePlayer, FactionPermission.CLAIM_CHUNK))
                .thenReturn(true);
        // mock chunk claim by our faction
        when(factionService.getFaction(sampleCoreBlock.getChunk()))
                .thenReturn(sampleFaction);
        // mock another core in this chunk
        Map<String, String> coreMetadata = new HashMap<>();
        coreMetadata.put("0,0,0", sampleFaction.id());
        when(factionCoreStorage.getMetadataInChunk(ChunkInfo.fromChunk(sampleCoreBlock.getChunk())))
                .thenReturn(CompletableFuture.completedFuture(coreMetadata));
        assertThrows(ExecutionException.class, () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

//    @Test
//    public void createCore() throws FactionDoesntExistException,
//            MultipleCoresInChunkException, InvalidCoreBlockException,
//            ChunkClaimedByOtherFactionException, WorldDisabledException,
//            FactionPermissionsDeniedException, ExecutionException, InterruptedException {
//        // mock valid core material
//        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
//        // mock enabled world
//        when(worldService.isWorldDisabled(sampleWorld))
//                .thenReturn(false);
//        // mock player's faction
//        when(factionService.getFaction(samplePlayer))
//                .thenReturn(sampleFaction);
//        // mock chunk faction
//        when(factionService.getFaction(sampleCoreBlock.getChunk()))
//                .thenThrow(FactionDoesntExistException.class);
//        // mock faction permissions
//        when(factionService.hasPlayerPermission(
//                sampleFaction.id(), samplePlayer, FactionPermission.CLAIM_CHUNK)).thenReturn(true);
//        // mock faction metadata storage
//        when(factionCoreStorage.getMetadataInChunk(sampleCoreBlock.getChunk()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//        when(factionCoreStorage.setMetadataInChunk(any(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        // create the core
//        factionCoreService.createCore(sampleCoreBlock, samplePlayer).get();
//
//        // ensure all chunks are claimed
//        Chunk centerChunk = sampleCoreBlock.getChunk();
//        World world = centerChunk.getWorld();
//        for (int x = -1; x <= 1; x++) {
//            for (int z = -1; z <= 1; z++) {
//                // verify that each chunk was claimed successfully
//                Chunk claimed = world.getChunkAt(centerChunk.getX() + x, centerChunk.getZ() + z);
//                verify(factionService, times(1)).claimChunk(
//                        eq(sampleFaction.id()),
//                        eq(claimed.getWorld()),
//                        eq(claimed.getX()),
//                        eq(claimed.getZ())
//                );
//            }
//        }
//
//        // ensure core has the metadata
//        verify(factionCoreStorage).setMetadata(sampleCoreBlock, sampleFaction.id());
//    }

    @Test
    public void createCoreNotEnoughPower() throws FactionDoesntExistException {
        // mock valid core material
        sampleCoreBlock.setType(Material.RESPAWN_ANCHOR);
        // mock enabled world
        when(worldService.isWorldDisabled(sampleWorld))
                .thenReturn(false);
        // mock player's faction
        when(factionService.getFaction(samplePlayer))
                .thenReturn(sampleFaction2);
        // mock chunk faction
        when(factionService.getFaction(sampleCoreBlock.getChunk()))
                .thenThrow(FactionDoesntExistException.class);
        // mock faction permissions
        when(factionService.hasPlayerPermission(
                sampleFaction2.id(), samplePlayer, FactionPermission.CLAIM_CHUNK))
                .thenReturn(true);
        // mock faction metadata storage
        when(factionCoreStorage.getMetadataInChunk(ChunkInfo.fromChunk(sampleCoreBlock.getChunk())))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThrows(ExecutionException.class,
                () -> factionCoreService.createCore(sampleCoreBlock, samplePlayer).get());
    }

    @Test
    public void testRemoveCore() {

    }

    @Test
    void getUnclaimedChunksAround() throws ExecutionException, InterruptedException {
        // mock no chunks are claimed
        when(factionService.isInFactionLand(any(Chunk.class)))
                .thenReturn(false);

        // all chunks around the sample chunk should be unclaimed
        Set<Chunk> unclaimed = factionCoreService
                .getUnclaimedChunksAround(sampleCoreBlock.getChunk()).get();

        // verify
        Chunk centerChunk = sampleCoreBlock.getChunk();
        assertEquals(9, unclaimed.size());
        for (Chunk c : unclaimed) {
            // calculate chunk distances to center chunk
            int distX = Math.abs(c.getX() - centerChunk.getX());
            int distZ = Math.abs(c.getZ() - centerChunk.getZ());
            assertFalse(distX > 1);
            assertFalse(distZ > 1);
        }
    }

    @Test
    void getUnclaimedChunksAroundSomeClaimed() throws ExecutionException, InterruptedException {
        // mock no chunks are claimed
        when(factionService.isInFactionLand(any(Chunk.class)))
                .thenReturn(false);

        // mock some chunks are claimed
        Chunk centerChunk = sampleCoreBlock.getChunk();
        Chunk chunkA = sampleCoreBlock.getWorld().getChunkAt(
                centerChunk.getX() + 1,
                centerChunk.getZ()
        );
        Chunk chunkB = sampleCoreBlock.getWorld().getChunkAt(
                centerChunk.getX() - 1,
                centerChunk.getZ() + 1
        );
        when(factionService.isInFactionLand(chunkA))
                .thenReturn(true);
        when(factionService.isInFactionLand(chunkB))
                .thenReturn(true);

        // all chunks around the sample chunk should be unclaimed
        Set<Chunk> unclaimed = factionCoreService
                .getUnclaimedChunksAround(sampleCoreBlock.getChunk()).get();

        // verify
        assertEquals(7, unclaimed.size());
        for (Chunk c : unclaimed) {
            // calculate chunk distances to center chunk
            int distX = Math.abs(c.getX() - centerChunk.getX());
            int distZ = Math.abs(c.getZ() - centerChunk.getZ());
            assertFalse(distX > 1);
            assertFalse(distZ > 1);
        }
    }
}