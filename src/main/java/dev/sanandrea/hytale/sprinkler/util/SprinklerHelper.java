/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler.util;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.interaction.AssetsHelper;
import dev.sanandrea.hytale.sprinkler.util.function.PostProcessFunction;
import dev.sanandrea.hytale.sprinkler.util.function.TilledSoilFunction;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public final class SprinklerHelper
{
    private SprinklerHelper() {}

    public static int[][] generatePerimeter(int range) {
        List<int[]> result = new ArrayList<>();

        for( int x = -range; x <= range; x++ ) {
            for( int z = -range; z <= range; z++ ) {
                if( x == 0 && z == 0 ) {
                    continue; // hole in the middle
                }
                result.add(new int[]{ x, z });
            }
        }

        return result.toArray(new int[0][0]);
    }

    @Nonnull
    public static Instant getGameTime(Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStoreStore = world.getEntityStore().getStore();

        return entityStoreStore.getResource(WorldTimeResource.getResourceType()).getGameTime();
    }

    public static Vector3ic getGlobalPosition(BlockModule.BlockStateInfo blockStateInfo) {
        Ref<ChunkStore> sectionRef = blockStateInfo.getSectionRef();
        ChunkSection chunkSection = sectionRef.getStore().getComponent(sectionRef, ChunkSection.getComponentType());
        if( chunkSection == null ) {
            return null;
        }

        int index = blockStateInfo.getIndex();
        int x = ChunkUtil.xFromIndex(index);
        int y = ChunkUtil.yFromIndex(index);
        int z = ChunkUtil.zFromIndex(index);

        return getGlobalPosition(new Vector3i(x, y, z), chunkSection);
    }

    public static Vector3ic getGlobalPosition(Vector3ic localPosition, @Nonnull ChunkSection section)
    {
        int x = ChunkUtil.worldCoordFromLocalCoord(section.getX(), localPosition.x());
        int y = ChunkUtil.worldCoordFromLocalCoord(section.getY(), localPosition.y());
        int z = ChunkUtil.worldCoordFromLocalCoord(section.getZ(), localPosition.z());

        return new Vector3i(x, y, z);
    }

    public static boolean callForPerimeter(@Nonnull Vector3ic blockCoords, Store<ChunkStore> store,
                                           int[][] perimeterCoords, @Nonnull TilledSoilFunction process)
    {
        return callForPerimeter(blockCoords, store, perimeterCoords, process, null);
    }

    public static WorldChunk getChunk(Store<ChunkStore> store, int blockX, int blockZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        return store.getExternalData().getChunkComponent(chunkIndex, WorldChunk.getComponentType());
    }

    public static WorldChunk getChunk(Store<ChunkStore> store, Vector3ic blockCoords) {
        return getChunk(store, blockCoords.x(), blockCoords.z());
    }

    public static WorldChunk getChunk(Ref<ChunkStore> ref, Vector3ic blockCoords) {
        return getChunk(ref.getStore(), blockCoords);
    }

    public static WorldChunk getChunk(ChunkStore cs, Vector3ic blockCoords) {
        return getChunk(cs.getStore(), blockCoords.x(), blockCoords.z());
    }

    public static WorldChunk getChunk(ChunkStore cs, int blockX, int blockZ) {
        return getChunk(cs.getStore(), blockX, blockZ);
    }

    public static BlockSection getBlockSection(Ref<ChunkStore> chunkStoreRef, Vector3ic blockCoords) {
        return getBlockSection(chunkStoreRef, blockCoords.x(), blockCoords.y(), blockCoords.z());
    }

    public static BlockSection getBlockSection(Ref<ChunkStore> chunkStoreRef, int x, int y, int z) {
        Store<ChunkStore> chunkStore = chunkStoreRef.getStore();
        Ref<ChunkStore> section = chunkStore.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if( section == null || !section.isValid() ) {
            return null;
        }

        return chunkStore.getComponent(section, BlockSection.getComponentType());
    }

    public static <T extends Component<ChunkStore>> T getBlockEntity(Ref<ChunkStore> chunkStoreRef, BlockSection blockSection,
                                                                     Vector3ic blockCoords, ComponentType<ChunkStore, T> beType)
    {
        return getBlockEntity(chunkStoreRef, blockSection, blockCoords.x(), blockCoords.y(), blockCoords.z(), beType);
    }

    public static <T extends Component<ChunkStore>> T getBlockEntity(Ref<ChunkStore> chunkStoreRef, BlockSection blockSection,
                                                                     int x, int y, int z, ComponentType<ChunkStore, T> beType)
    {
        Store<ChunkStore> chunkStore = chunkStoreRef.getStore();
        Ref<ChunkStore> section = chunkStore.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if( section == null || !section.isValid() ) {
            return null;
        }

        BlockComponentSection bcSection = chunkStore.getComponent(section, BlockComponentSection.getComponentType());
        if( bcSection == null ) {
            return null;
        }

        int blockIndex = ChunkUtil.indexBlock(x, y, z);
        int blockId = blockSection.get(blockIndex);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if( blockType == null ) {
            return null;
        }

        Ref<ChunkStore> blockRef = bcSection.getBlockReference(blockIndex);
        if( (blockRef == null || !blockRef.isValid()) && BlockEntity.declaresComponent(blockType, TilledSoilBlock.getComponentType()) ) {
            blockRef = BlockEntity.ensureBlockEntity(chunkStore, section, bcSection, x, y, z, blockType,
                                                     blockSection.getFiller(blockIndex));
        }

        if( blockRef == null || !blockRef.isValid() ) {
            return null;
        }

        return chunkStore.getComponent(blockRef, beType);
    }

    public static boolean callForPerimeter(@Nonnull Vector3ic blockCoords, Store<ChunkStore> store,
                                           int[][] perimeterCoords, @Nonnull TilledSoilFunction process, PostProcessFunction postProcess)
    {
        int currX = blockCoords.x();
        int currY = blockCoords.y();
        int currZ = blockCoords.z();

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Instant gameTime = entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();

        boolean result = false;
        for( int[] coord : perimeterCoords ) {
            int x = currX + coord[0];
            int y = currY - 1;
            int z = currZ + coord[1];

            WorldChunk chunk = getChunk(world.getChunkStore(), x, z);
            if( chunk == null ) {
                continue;
            }

            BlockSection blockSection = getBlockSection(chunk.getReference(), x, y, z);
            if( blockSection == null ) {
                continue;
            }

            TilledSoilBlock soil = getBlockEntity(chunk.getReference(), blockSection, x, y, z, TilledSoilBlock.getComponentType());
            if( soil == null && !AssetsHelper.isSoil(blockSection, new Vector3i(x, y, z)) ) {
                continue;
            }

            result |= process.apply(soil, world, blockSection, x, y, z, gameTime);
        }

        if( result && postProcess != null ) {
            WorldChunk myChunk = getChunk(world.getChunkStore(), currX, currZ);
            if( myChunk != null ) {
                BlockSection blockSection = getBlockSection(myChunk.getReference(), currX, currY, currZ);
                if( blockSection != null ) {
                    postProcess.accept(blockCoords, world, blockSection, gameTime);
                }
            }
        }

        return result;
    }

    public static ItemStackSlotTransaction damageItem(Ref<EntityStore> ref, ItemStack itemStack, int inventoryId, int slotId, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        ItemContainer section = InventoryUtils.getSectionById(ref, inventoryId, componentAccessor);
        if (section == null) {
            return null;
        } else {
            ItemStackSlotTransaction transaction = ItemUtils.updateItemStackDurability(ref, itemStack, section, slotId, -1, componentAccessor);
            ItemStack slotAfter = transaction.getSlotAfter();
            if( transaction.succeeded() && slotAfter != null && slotAfter.isBroken() ) {
                EntityStatMap entityStatMap = componentAccessor.getComponent(ref, EntityStatMap.getComponentType());
                if( entityStatMap != null ) {
                    entityStatMap.getStatModifiersManager().scheduleRecalculate();
                }
            }

            return transaction;
        }
    }
}
