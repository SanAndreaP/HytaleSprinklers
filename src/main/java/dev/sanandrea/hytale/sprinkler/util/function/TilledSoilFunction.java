/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.util.function;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.time.Instant;
import java.util.Objects;

@FunctionalInterface
@SuppressWarnings("unused")
public interface TilledSoilFunction
{
    boolean apply(TilledSoilBlock soil, BlockChunk blockChunk, int x, int y, int z, WorldChunk chunk, Instant gameTime);

    default TilledSoilFunction andThen(final TilledSoilFunction after) {
        Objects.requireNonNull(after);
        return (s, bc, x, y, z, wc, gt) ->
                apply(s, bc, x, y, z, wc, gt) && after.apply(s, bc, x, y, z, wc, gt);
    }
}
