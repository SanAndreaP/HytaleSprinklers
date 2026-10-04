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
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.RootInteraction;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.BlockConditionInteraction;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import dev.sanandrea.hytale.sprinkler.SprinklerPlugin;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3ic;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

public class SeedPlacerHelper
{
    private static final Map<String, String> SEED_TO_BLOCK = new HashMap<>();
    private static final Map<String, String> SEED_TO_SOILCND = new HashMap<>();
    private static final Map<String, BlockConditionData[]> SOILCND_TO_BLOCKS = new HashMap<>();

    private SeedPlacerHelper() {}

    public static void onItemAssetLoad(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        final Map<String, Item> assets = event.getLoadedAssets();
        for( Map.Entry<String, Item> entry : assets.entrySet() ) {
            final Item                item            = entry.getValue();
            final Map<String, String> interactionVars = item.getInteractionVars();

            if( interactionVars.containsKey("SeedId") ) {
                String blockType = extractSeedBlockType(interactionVars.get("SeedId"), item.getData());
                String itemId = item.getId();

                SEED_TO_BLOCK.put(itemId, blockType);

                final Map<InteractionType, String> interactions = item.getInteractions();
                if( interactions.containsKey(InteractionType.Secondary) ) {
                    SEED_TO_SOILCND.put(itemId, interactions.get(InteractionType.Secondary));
                }
            }
        }
    }

    public static void onInteractionAssetLoad(@Nonnull LoadedAssetsEvent<String, Interaction, DefaultAssetMap<String, Interaction>> event) {
        final Map<String, Interaction> assets = event.getLoadedAssets();
        for( Map.Entry<String, Interaction> entry : assets.entrySet() ) {
            final Interaction interaction = entry.getValue();
            final String assetId = interaction.getId();
            if( interaction instanceof BlockConditionInteraction && assetId.startsWith("Seed_Condition") ) {
                SOILCND_TO_BLOCKS.put(assetId, extractBlockIdsFromBlockConditionInteraction((BlockConditionInteraction) interaction));
            }
        }
    }

    //TODO: test for tags & states...
    public static boolean isSoil(BlockChunk chunk, Vector3ic blockPos) {
        final int blockId = chunk.getBlock(blockPos.x(), blockPos.y(), blockPos.z());
        final BlockType blockType = BlockType.getAssetMap().getAsset(blockId);

        return SOILCND_TO_BLOCKS.values().stream().anyMatch(bcData ->
                Arrays.stream(bcData).anyMatch(bcd -> bcd.test(blockType)));
    }

    public static String getBlockFromSeedId(String seedId) {
        return SEED_TO_BLOCK.getOrDefault(seedId, null);
    }

    private static Field matchersField = null;
    private static Field blockIdMatcherField = null;
    private static Field blockIdField = null;
    private static Field blockStateField = null;
    private static Field blockTagField = null;
    private static BlockConditionData[] extractBlockIdsFromBlockConditionInteraction(BlockConditionInteraction interaction) {
        try {
            if( matchersField == null ) {
                matchersField = BlockConditionInteraction.class.getDeclaredField("matchers");
                matchersField.setAccessible(true);
            }
            if( blockIdMatcherField == null ) {
                blockIdMatcherField = BlockConditionInteraction.BlockMatcher.class.getDeclaredField("block");
                blockIdMatcherField.setAccessible(true);
            }
            if( blockIdField == null ) {
                blockIdField = BlockConditionInteraction.BlockIdMatcher.class.getDeclaredField("id");
                blockIdField.setAccessible(true);
            }
            if( blockStateField == null ) {
                blockStateField = BlockConditionInteraction.BlockIdMatcher.class.getDeclaredField("state");
                blockStateField.setAccessible(true);
            }
            if( blockTagField == null ) {
                blockTagField = BlockConditionInteraction.BlockIdMatcher.class.getDeclaredField("tag");
                blockTagField.setAccessible(true);
            }

            return getBlockConditionData(interaction);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNullDecl
    private static BlockConditionData[] getBlockConditionData(BlockConditionInteraction interaction) throws IllegalAccessException {
        BlockConditionInteraction.BlockMatcher[] matchers = (BlockConditionInteraction.BlockMatcher[]) matchersField.get(interaction);
        List<BlockConditionData> bcData = new ArrayList<>();

        for( BlockConditionInteraction.BlockMatcher matcher : matchers ) {
            BlockConditionInteraction.BlockIdMatcher blockIdMatcher = (BlockConditionInteraction.BlockIdMatcher) blockIdMatcherField.get(matcher);

            bcData.add(new BlockConditionData((String) blockIdField.get(blockIdMatcher),
                                              (String) blockStateField.get(blockIdMatcher),
                                              (String) blockTagField.get(blockIdMatcher)));
        }
        return bcData.toArray(new BlockConditionData[0]);
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

    private record BlockConditionData(String id, String state, String tag) {
        public boolean test(BlockType type) {
            return this.id != null && this.id.equals(type.getId());
        }
    }
}
