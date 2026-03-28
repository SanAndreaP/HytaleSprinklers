/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.sanandrea.hytale.sprinkler.SprinklerBlock;
import dev.sanandrea.hytale.sprinkler.SprinklerPlugin;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SprinklerTickHandler
        extends EntityTickingSystem<ChunkStore>
{
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoCT;
    @Nonnull
    private final ComponentType<ChunkStore, BlockSection>               blockSectionCT;
    @Nonnull
    private final ComponentType<ChunkStore, ChunkSection>               chunkSectionCT;
    @Nonnull
    private final ComponentType<ChunkStore, SprinklerBlock>             sprinklerCT;
    @Nonnull
    private final Query<ChunkStore>                                     query;

    public SprinklerTickHandler(@Nonnull ComponentType<ChunkStore, SprinklerBlock> component) {
        this.blockStateInfoCT = BlockModule.BlockStateInfo.getComponentType();
        this.blockSectionCT = BlockSection.getComponentType();
        this.chunkSectionCT = ChunkSection.getComponentType();
        this.sprinklerCT = component;
        this.query = Query.and(this.blockSectionCT, this.chunkSectionCT);
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        BlockSection blockSection = archetypeChunk.getComponent(index, this.blockSectionCT);
        if( blockSection == null ) {
            return;
        }

        if( blockSection.getTickingBlocksCountCopy() != 0 ) {
            ChunkSection chunkSection = archetypeChunk.getComponent(index, this.chunkSectionCT);
            if( chunkSection == null ) {
                return;
            }

            Ref<ChunkStore> chunkColumnRef = chunkSection.getChunkColumnReference();
            if( chunkColumnRef != null && chunkColumnRef.isValid() ) {
                BlockComponentChunk blockComponentChunk = commandBuffer.getComponent(chunkColumnRef, BlockComponentChunk.getComponentType());
                if( blockComponentChunk == null ) {
                    return;
                }

                blockSection.forEachTicking(blockComponentChunk, commandBuffer, chunkSection.getY(), this::tickBlock);
            }
        }
    }

    private BlockTickStrategy tickBlock(BlockComponentChunk blockComponentChunk, CommandBuffer<ChunkStore> commandBuffer,
                                        int localX, int localY, int localZ, int blockIndex)
    {
        int             blockId  = ChunkUtil.indexBlockInColumn(localX, localY, localZ);
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockId);
        if( blockRef != null ) {
            SprinklerBlock sprinkler = commandBuffer.getComponent(blockRef, this.sprinklerCT);
            if( sprinkler != null ) {
                this.tickSprinkler(new Vector3i(localX, localY, localZ), commandBuffer, blockRef, sprinkler);
                return BlockTickStrategy.SLEEP;
            }
        }

        return BlockTickStrategy.IGNORED;
    }

    private void tickSprinkler(@Nonnull Vector3i localCoords, CommandBuffer<ChunkStore> commandBuffer, Ref<ChunkStore> blockRef, SprinklerBlock sprinkler) {
        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(blockRef, this.blockStateInfoCT);
        if( blockStateInfo == null ) {
            return;
        }

        Store<ChunkStore> store = blockRef.getStore();

        Instant nextRun = sprinkler.getNextRun();
        Instant now     = SprinklerHelper.getGameTime(store);
        if( nextRun != null && nextRun.isAfter(now) ) {
            return;
        }

        Vector3i globalCoord = SprinklerHelper.getGlobalPosition(localCoords, blockStateInfo, commandBuffer);

        sprinkler.activateWatering(new Vector3i(globalCoord.getX(), globalCoord.getY(), globalCoord.getZ()), store);
        sprinkler.scheduleTick(store, commandBuffer, blockStateInfo, true);

        SprinklerPlugin.LOGGER.at(Level.FINEST).atMostEvery(1, TimeUnit.SECONDS)
                              .log("Sprinkler tick at [%d, %d, %d]", globalCoord.getX(), globalCoord.getY(), globalCoord.getZ());
    }

    @NonNullDecl
    @Override
    public Query<ChunkStore> getQuery() {
        return this.query;
    }
}
