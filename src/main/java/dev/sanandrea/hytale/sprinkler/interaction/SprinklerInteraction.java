/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.SprinklerPlugin;
import dev.sanandrea.hytale.sprinkler.SprinklerState;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.logging.Level;

public class SprinklerInteraction
        extends SimpleBlockInteraction
{
    public static final BuilderCodec<SprinklerInteraction> CODEC = BuilderCodec.builder(SprinklerInteraction.class, SprinklerInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Handles interactions for the sprinkler block").build();

    @Override
    protected void interactWithBlock(@NonNullDecl World world, @NonNullDecl CommandBuffer<EntityStore> commandBuffer, @NonNullDecl InteractionType type, @NonNullDecl InteractionContext context, @NullableDecl ItemStack itemStack, @NonNullDecl Vector3i targetBlock, @NonNullDecl CooldownHandler cooldownHandler) {
        final WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.getX(), targetBlock.getZ()));
        if( chunk == null ) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if( chunk.getState(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()) instanceof SprinklerState sprinkler ) {
            if( itemStack == null || itemStack.isEmpty() ) {
                sprinkler.activateWatering();
            } else {
                if( sprinkler.isFunneled() ) {
                    if( !sprinkler.tryPlaceSeed(itemStack, commandBuffer, context) ) {
                        context.getState().state = InteractionState.Failed;
                    }
                } else {
                    if( !sprinkler.tryUpgradeSprinkler(itemStack, commandBuffer, context) ) {
                        context.getState().state = InteractionState.Failed;
                    }
                }
            }
        }

        SprinklerPlugin.LOGGER.at(Level.FINEST).log("sprinkler clicked!");
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType type, @NonNullDecl InteractionContext context, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl Vector3i targetBlock) {

    }
}
