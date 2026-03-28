/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.util;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.util.function.PostProcessFunction;
import dev.sanandrea.hytale.sprinkler.util.function.TilledSoilFunction;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public final class SprinklerHelper
{
    private SprinklerHelper() {}

    public static int[][] generatePerimeter(int range) {
        List<int[]> result = new ArrayList<>();

        for( int x = -range; x <= range; x++ ) {
            for( int z = -range; z <= range; z++ ) {
                if( x == 0 && z == 0 ) {
                    continue; // hole in the middle
                }
                result.add(new int[] { x, z });
            }
        }

        return result.toArray(new int[0][0]);
    }

    @Nonnull
    public static Instant getGameTime(Store<ChunkStore> store) {
        World              world            = store.getExternalData().getWorld();
        Store<EntityStore> entityStoreStore = world.getEntityStore().getStore();

        return entityStoreStore.getResource(WorldTimeResource.getResourceType()).getGameTime();
    }

    public static <T extends Component<ChunkStore>> T getChunkComponent(WorldChunk chunk, Vector3i targetBlock,
                                                                        ComponentType<ChunkStore, T> componentType)
    {
        return getChunkComponent(chunk, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), componentType);
    }

    public static <T extends Component<ChunkStore>> T getChunkComponent(WorldChunk chunk, int x, int y, int z,
                                                                        ComponentType<ChunkStore, T> componentType)
    {
        Ref<ChunkStore> ref = chunk.getBlockComponentEntity(x, y, z);
        if( ref == null ) {
            return null;
        }

        return ref.getStore().getComponent(ref, componentType);
    }

    public static BlockChunk getBlockChunk(WorldChunk chunk) {
        Ref<ChunkStore> ref = chunk.getReference();
        if( ref == null ) {
            return null;
        }

        return ref.getStore().getComponent(ref, BlockChunk.getComponentType());
    }

    public static Vector3i getGlobalPosition(BlockModule.BlockStateInfo blockStateInfo, CommandBuffer<ChunkStore> commandBuffer) {
        int index = blockStateInfo.getIndex();
        int x     = ChunkUtil.xFromIndex(index);
        int y     = ChunkUtil.yFromBlockInColumn(index);
        int z     = ChunkUtil.zFromIndex(index);

        return getGlobalPosition(new Vector3i(x, y, z), blockStateInfo, commandBuffer);
    }

    public static Vector3i getGlobalPosition(Vector3i localPosition, BlockModule.BlockStateInfo blockStateInfo,
                                             CommandBuffer<ChunkStore> commandBuffer)
    {
        Ref<ChunkStore> chunkRef   = blockStateInfo.getChunkRef();
        BlockChunk      blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        assert blockChunk != null;

        int globalX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localPosition.getX());
        int globalZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localPosition.getZ());

        return new Vector3i(globalX, localPosition.getY(), globalZ);
    }

    public static boolean callForPerimeter(@Nonnull Vector3i blockCoords, Store<ChunkStore> store,
                                           int[][] perimeterCoords, @Nonnull TilledSoilFunction process)
    {
        return callForPerimeter(blockCoords, store, perimeterCoords, process, null);
    }

    public static boolean callForPerimeter(@Nonnull Vector3i blockCoords, Store<ChunkStore> store,
                                           int[][] perimeterCoords, @Nonnull TilledSoilFunction process, PostProcessFunction postProcess)
    {
        int currX = blockCoords.x;
        int currY = blockCoords.y;
        int currZ = blockCoords.z;

        World              world       = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Instant            gameTime    = entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();

        boolean result = false;
        for( int[] coord : perimeterCoords ) {
            int x = currX + coord[0];
            int y = currY - 1;
            int z = currZ + coord[1];

            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if( chunk == null ) {
                continue;
            }

            BlockChunk blockChunk = getBlockChunk(chunk);
            if( blockChunk == null ) {
                continue;
            }

            TilledSoilBlock soil = getChunkComponent(chunk, x, y, z, TilledSoilBlock.getComponentType());
            if( soil == null ) {
                continue;
            }

            result |= process.apply(soil, blockChunk, x, y, z, chunk, gameTime);
        }

        if( result && postProcess != null ) {
            WorldChunk myChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(currX, currZ));
            if( myChunk != null ) {
                postProcess.accept(blockCoords, myChunk, gameTime);
            }
        }

        return result;
    }
}
