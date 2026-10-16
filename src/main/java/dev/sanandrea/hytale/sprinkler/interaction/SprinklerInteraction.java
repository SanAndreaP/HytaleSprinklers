/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.SprinklerBlock;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3i;

public class SprinklerInteraction
        extends SimpleBlockInteraction
{
    public static final BuilderCodec<SprinklerInteraction> CODEC = BuilderCodec.builder(SprinklerInteraction.class, SprinklerInteraction::new, SimpleBlockInteraction.CODEC)
                                                                               .documentation("Handles interactions for the sprinkler block")
                                                                               .build();

    @Override
    protected void interactWithBlock(@NonNullDecl World world, @NonNullDecl CommandBuffer<EntityStore> commandBuffer, @NonNullDecl InteractionType type, @NonNullDecl InteractionContext context, @NullableDecl ItemStack itemStack, @NonNullDecl Vector3i targetBlock, @NonNullDecl CooldownHandler cooldownHandler) {
        final WorldChunk chunk = SprinklerHelper.getChunk(world.getChunkStore(), targetBlock);
        if( chunk == null ) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<ChunkStore> chunkStoreRef = chunk.getReference();

        BlockSection section = SprinklerHelper.getBlockSection(chunkStoreRef, targetBlock);
        if( section == null ) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        SprinklerBlock sprinkler = SprinklerHelper.getBlockEntity(chunkStoreRef, section, targetBlock, SprinklerBlock.getComponent());
        if( sprinkler != null ) {
            if( itemStack == null || itemStack.isEmpty() ) {
                sprinkler.activateWatering(targetBlock, chunkStoreRef.getStore());
            } else {
                if( SprinklerBlock.isFunneled(section, targetBlock) ) {
                    if( sprinkler.tryPlaceSeed(chunk, targetBlock, itemStack, commandBuffer, context) ) {
                        return;
                    }
                    if( AssetsHelper.isSickle(itemStack) && sprinkler.tryHarvestCrop(chunk, targetBlock, commandBuffer, context) ) {
                        return;
                    }
                    context.getState().state = InteractionState.Failed;
                } else {
                    if( !SprinklerBlock.tryUpgradeSprinkler(section, targetBlock, itemStack, commandBuffer, context) ) {
                        context.getState().state = InteractionState.Failed;
                    }
                }
            }
        }
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType type, @NonNullDecl InteractionContext context, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl Vector3i targetBlock) {
        // no-op
    }
}
