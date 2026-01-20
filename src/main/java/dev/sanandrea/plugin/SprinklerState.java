/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.plugin;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings({ "removal", "deprecation" })
public class SprinklerState
        extends BlockState
        implements TickableBlockState
{
    public static final BuilderCodec<SprinklerState> CODEC = BuilderCodec.builder(SprinklerState.class, SprinklerState::new, BlockState.BASE_CODEC).build();
    protected Data data;
    protected int[][] perimeter = new int[0][];

    @Override
    public boolean initialize(BlockType blockType) {
        if( super.initialize(blockType) && blockType.getState() instanceof Data data ) {
            this.data = data;
            this.perimeter = generatePerimeter(this.data.range);
            return true;
        }

        return false;
    }

    @Override
    public void tick(float v, int i, ArchetypeChunk<ChunkStore> archetypeChunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        int currX = this.getBlockX();
        int currY = this.getBlockY();
        int currZ = this.getBlockZ();

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        WorldTimeResource timeResource = entityStore.getResource(WorldTimeResource.getResourceType());
        Instant instant = timeResource.getGameTime().plus(this.data.duration, ChronoUnit.SECONDS);

        for( int[] coord : this.perimeter ) {
            int x = currX + coord[0];
            int y = currY - 1;
            int z = currZ + coord[1];

            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if( chunk == null ) {
                continue;
            }
            BlockChunk blockChunk = commandBuffer.getComponent(chunk.getReference(), BlockChunk.getComponentType());
            if( blockChunk == null ) {
                continue;
            }
            Ref<ChunkStore> soilRef = chunk.getBlockComponentEntity(x, y, z);
            if( soilRef == null ) {
                continue;
            }
            TilledSoilBlock soil = commandBuffer.getComponent(soilRef, TilledSoilBlock.getComponentType());
            if( soil == null ) {
                continue;
            }
            Instant wateredUntil = soil.getWateredUntil();
            if( wateredUntil == null || timeResource.getGameTime().compareTo(soil.getWateredUntil()) > 0 ) {
                soil.setWateredUntil(instant);
                chunk.setTicking(x, y, z, true);
                blockChunk.getSectionAtBlockY(y).scheduleTick(ChunkUtil.indexBlock(x, y, z), instant);
                chunk.setTicking(x, y + 1, z, true); // tick plant as well, if exists...

                SprinklerPlugin.LOGGER.at(Level.FINEST).atMostEvery(1, TimeUnit.SECONDS).log("Sprinkler watered this soil!");
            }
        }
    }

    public static int[][] generatePerimeter(int range) {
        List<int[]> result = new ArrayList<>();

        for( int x = -range; x <= range; x++ ) {
            int maxZ = range - Math.abs(x);
            for( int z = -maxZ; z <= maxZ; z++ ) {
                if( x == 0 && z == 0 ) {
                    continue; // hole in the middle
                }
                result.add(new int[] { x, z });
            }
        }

        return result.toArray(new int[0][0]);
    }

    public static class Data
            extends StateData
    {
        @Nonnull
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new, StateData.DEFAULT_CODEC)
                .documentation("Waters tilled soil around it.")
                .append(new KeyedCodec<>("Range", Codec.INTEGER), (o, v) -> o.range = v, o -> o.range)
                    .documentation("The range of the sprinkler.")
                    .add()
                .append(new KeyedCodec<>("Duration", Codec.INTEGER), (o, v) -> o.duration = v, o -> o.duration)
                    .documentation("The duration (in ingame seconds) the soil stays watered")
                    .add()
               .build();

        private int range = 1;
        private int duration = 60*60*24;
    }
}
