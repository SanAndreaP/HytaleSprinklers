/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.event;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.sanandrea.hytale.sprinkler.SprinklerBlock;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

public class SprinklerLifecycleHandler
        extends RefSystem<ChunkStore>
{
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoCT;
    @Nonnull
    private final ComponentType<ChunkStore, SprinklerBlock>             sprinklerCT;
    @Nonnull
    private final Query<ChunkStore>                                     query;

    public SprinklerLifecycleHandler(@Nonnull ComponentType<ChunkStore, SprinklerBlock> component) {
        this.blockStateInfoCT = BlockModule.BlockStateInfo.getComponentType();
        this.sprinklerCT = component;
        this.query = Query.and(this.blockStateInfoCT, component);
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> commandBuffer)
    {
        SprinklerBlock sprinkler = commandBuffer.getComponent(ref, this.sprinklerCT);
        if( sprinkler == null ) {
            return;
        }

        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, this.blockStateInfoCT);
        if( blockStateInfo == null ) {
            return;
        }

        sprinkler.scheduleTick(store, commandBuffer, blockStateInfo, false);

        Vector3i blockCoords = SprinklerHelper.getGlobalPosition(blockStateInfo, commandBuffer);
        sprinkler.activateWatering(blockCoords, store);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store,
                               @Nonnull CommandBuffer<ChunkStore> commandBuffer)
    {
        World world = store.getExternalData().getWorld();

        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, this.blockStateInfoCT);
        if( blockStateInfo == null ) {
            return;
        }

        SprinklerBlock sprinkler = commandBuffer.getComponent(ref, this.sprinklerCT);
        if( sprinkler == null ) {
            return;
        }

        Vector3i   blockCoords = SprinklerHelper.getGlobalPosition(blockStateInfo, commandBuffer);
        WorldChunk chunk       = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockCoords.x, blockCoords.z));

        if( chunk != null ) {
            sprinkler.destroy(chunk, blockCoords);
        }
    }

    @NonNullDecl
    @Override
    public Query<ChunkStore> getQuery() {
        return this.query;
    }
}
