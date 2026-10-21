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
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.sanandrea.hytale.sprinkler.SprinklerBlock;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3ic;

import javax.annotation.Nonnull;

public class SprinklerLifecycleHandler
        extends RefSystem<ChunkStore>
{
    private final @Nonnull ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoCT;
    private final @Nonnull ComponentType<ChunkStore, SprinklerBlock> sprinklerCT;
    private final @Nonnull Query<ChunkStore> query;

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

        Vector3ic blockCoords = SprinklerHelper.getGlobalPosition(blockStateInfo);
        if( blockCoords != null ) {
            sprinkler.activateWatering(blockCoords, store);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store,
                               @Nonnull CommandBuffer<ChunkStore> commandBuffer)
    {
        if (reason == RemoveReason.UNLOAD) {
            return;
        }

        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if( blockStateInfo == null ) {
            return;
        }

        SprinklerBlock sprinkler = commandBuffer.getComponent(ref, this.sprinklerCT);
        if( sprinkler == null ) {
            return;
        }

        Vector3ic blockCoords = SprinklerHelper.getGlobalPosition(blockStateInfo);
        if( blockCoords == null ) {
            return;
        }

        WorldChunk chunk = SprinklerHelper.getChunk(ref, blockCoords);
        if( chunk != null ) {
            sprinkler.destroy(chunk, blockCoords);
        }
    }

    @Override
    @NonNullDecl
    public Query<ChunkStore> getQuery() {
        return this.query;
    }
}
