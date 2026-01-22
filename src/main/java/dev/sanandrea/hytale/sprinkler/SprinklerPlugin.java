/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.RootInteraction;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.sanandrea.hytale.sprinkler.interaction.SeedPlacerHelper;
import dev.sanandrea.hytale.sprinkler.interaction.SprinklerInteraction;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SprinklerPlugin
        extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        this.getEventRegistry().register((short) -32, LoadedAssetsEvent.class, Item.class, SeedPlacerHelper::onItemAssetLoad);
        this.getCodecRegistry(Interaction.CODEC).register("SanAndreaP_Sprinkler", SprinklerInteraction.class, SprinklerInteraction.CODEC);
        this.getBlockStateRegistry().registerBlockState(SprinklerState.class, "SanAndreaP_Sprinkler", SprinklerState.CODEC,
                                                        SprinklerState.Data.class, SprinklerState.Data.CODEC);
    }
}