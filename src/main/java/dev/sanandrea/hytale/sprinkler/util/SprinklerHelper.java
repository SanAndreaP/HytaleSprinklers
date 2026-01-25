/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.util;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.util.function.TilledSoilFunction;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("removal")
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

    public static boolean callForPerimeter(BlockState state, Store<ChunkStore> store, int[][] perimeterCoords, @Nonnull TilledSoilFunction process) {
        int currX = state.getBlockX();
        int currY = state.getBlockY();
        int currZ = state.getBlockZ();

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
            final Ref<ChunkStore> chunkRef   = chunk.getReference();
            BlockChunk            blockChunk = chunkRef.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
            if( blockChunk == null ) {
                continue;
            }
            Ref<ChunkStore> soilRef = chunk.getBlockComponentEntity(x, y, z);
            if( soilRef == null ) {
                continue;
            }
            TilledSoilBlock soil = soilRef.getStore().getComponent(soilRef, TilledSoilBlock.getComponentType());
            if( soil == null ) {
                continue;
            }

            result |= process.apply(soil, blockChunk, x, y, z, chunk, gameTime);
        }

        return result;
    }
}
