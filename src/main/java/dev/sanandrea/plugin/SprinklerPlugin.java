/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class SprinklerPlugin
        extends JavaPlugin {

    static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public SprinklerPlugin(@Nonnull JavaPluginInit init) {
        super(init);

        if( this.getClassLoader().isInServerClassPath() ) {
            LOGGER.setLevel(Level.FINEST);
        }

        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        this.getBlockStateRegistry().registerBlockState(SprinklerState.class, "SanAndreaP_Sprinkler", SprinklerState.CODEC,
                                                        SprinklerState.Data.class, SprinklerState.Data.CODEC);
    }
}