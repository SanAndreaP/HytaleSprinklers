/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.util.function;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import java.time.Instant;

@FunctionalInterface
public interface PostProcessFunction
{
    void accept(@Nonnull Vector3i blockCoords, WorldChunk chunk, Instant gameTime);
}
