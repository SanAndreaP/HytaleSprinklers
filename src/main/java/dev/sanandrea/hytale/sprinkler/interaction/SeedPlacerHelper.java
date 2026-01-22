/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.interaction;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.protocol.RootInteraction;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.sanandrea.hytale.sprinkler.SprinklerPlugin;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SeedPlacerHelper
{
    public static final SeedPlacerHelper INSTANCE = new SeedPlacerHelper();

    private final Map<String, String> SEED_TO_BLOCK = new HashMap<>();

    private SeedPlacerHelper() {}

    public static void onItemAssetLoad(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        final Map<String, Item> assets = event.getLoadedAssets();
        for( Map.Entry<String, Item> entry : assets.entrySet() ) {
            final Item                item            = entry.getValue();
            final Map<String, String> interactionVars = item.getInteractionVars();
            if( interactionVars.containsKey("SeedId") ) {
                String blockType = extractSeedBlockType(interactionVars.get("SeedId"), item.getData());
                INSTANCE.SEED_TO_BLOCK.put(item.getId(), blockType);
            }
        }
    }

    public String getBlockFromSeedId(String seedId) {
        return INSTANCE.SEED_TO_BLOCK.getOrDefault(seedId, null);
    }

    @SuppressWarnings("rawtypes")
    private static String extractSeedBlockType(Object seedIdKey, AssetExtraInfo.Data data) {
        Map<Class<? extends JsonAssetWithMap>, Map<Class<RootInteraction>, List<RawAsset<Object>>>> rawAssets = new HashMap<>();

        data.fetchContainedRawAssets(RootInteraction.class, rawAssets);

        List<RawAsset<Object>> assets = rawAssets.values().stream()
                                                 .flatMap(m -> m.values().stream())
                                                 .flatMap(List::stream)
                                                 .toList();

        for( RawAsset<Object> rawAsset : assets ) {
            if( !seedIdKey.equals(rawAsset.getKey()) ) {
                continue;
            }

            char[] buf = rawAsset.getBuffer();
            if( buf == null ) {
                continue;
            }

            String blockType = readBlockTypeFromBuffer(buf);
            if( blockType != null ) {
                return blockType;
            }
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    private static String readBlockTypeFromBuffer(char[] buf) {
        try( RawJsonReader reader = RawJsonReader.fromBuffer(buf) ) {
            BsonDocument doc = RawJsonReader.readBsonDocument(reader);

            if( !doc.isArray("Interactions") ) {
                return null;
            }

            for( BsonValue value : doc.getArray("Interactions") ) {
                if( value.isDocument() ) {
                    BsonDocument inter = value.asDocument();
                    if( inter.isString("BlockTypeToPlace") ) {
                        return inter.getString("BlockTypeToPlace").getValue();
                    }
                }
            }
        } catch( IOException e ) {
            SprinklerPlugin.LOGGER.at(Level.FINEST).log("Failed to read block-type from raw asset: %s", e.getMessage());
        }

        return null;
    }
}
