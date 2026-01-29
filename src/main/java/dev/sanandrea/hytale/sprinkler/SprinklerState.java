/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright © 2026 SanAndreaP
 * Full license text can be found within the LICENSE.md file
 */

package dev.sanandrea.hytale.sprinkler;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.WorldNotificationHandler;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.DestroyableBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.interaction.SeedPlacerHelper;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings({ "removal", "deprecation" })
public class SprinklerState
        extends BlockState
        implements TickableBlockState, DestroyableBlockState
{
    public static final BuilderCodec<SprinklerState> CODEC;
    protected           Data                         data;
    protected           int[][]                      perimeter = new int[0][];

    private Instant nextRun = null;

    @Override
    public boolean initialize(BlockType blockType) {
        if( super.initialize(blockType) && blockType.getState() instanceof Data stateData ) {
            this.data = stateData;
            this.perimeter = SprinklerHelper.generatePerimeter(this.data.range);
            return true;
        }

        return false;
    }

    @Override
    public void tick(float v, int i, ArchetypeChunk<ChunkStore> archetypeChunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        Instant now = SprinklerHelper.getGameTime(store);
        if( this.canRun(now) ) {
            this.activateWatering(store, now, false);
        }
    }

    private boolean waterSoil(TilledSoilBlock soil, BlockChunk blockChunk, int x, int y, int z, WorldChunk chunk, Instant gameTime) {
        return waterSoil(soil, blockChunk, x, y, z, chunk, gameTime, false);
    }

    private boolean forceWaterSoil(TilledSoilBlock soil, BlockChunk blockChunk, int x, int y, int z, WorldChunk chunk, Instant gameTime) {
        return waterSoil(soil, blockChunk, x, y, z, chunk, gameTime, true);
    }

    private boolean waterSoil(TilledSoilBlock soil, BlockChunk blockChunk, int x, int y, int z, WorldChunk chunk, Instant gameTime, boolean force) {
        Instant wateredUntil = soil.getWateredUntil();
        if( wateredUntil == null || force || gameTime.compareTo(soil.getWateredUntil()) > 0 ) {
            Instant nextWatering = gameTime.plus(this.data.duration, ChronoUnit.SECONDS);
            soil.setWateredUntil(nextWatering);
            // decay until 1 millennium passed - effectively "never" decay - or until sprinkler gets destroyed
            soil.setDecayTime(gameTime.plus(365L * 1000L, ChronoUnit.DAYS));
            chunk.setTicking(x, y, z, true);
            blockChunk.getSectionAtBlockY(y).scheduleTick(ChunkUtil.indexBlock(x, y, z), nextWatering);
            chunk.setTicking(x, y + 1, z, true); // tick plant as well, if exists...

            WorldNotificationHandler notificationHandler = chunk.getWorld().getNotificationHandler();
            notificationHandler.sendPacketIfChunkLoaded(
                    new SpawnParticleSystem("Water_Can_Splash", new Position(x + 0.5D, y + 1D, z + 0.5D), new Direction(), 0.5F,
                                            new Color((byte) 64, (byte) 96, (byte) 255)), x, z);

            return true;
        }

        return false;
    }

    private void activateWatering(Store<ChunkStore> chunkStore, Instant now, boolean force) {
        if( SprinklerHelper.callForPerimeter(this, chunkStore, this.perimeter, force ? this::forceWaterSoil : this::waterSoil) ) {
            WorldChunk chunk = this.getChunk();
            WorldNotificationHandler notificationHandler = chunk.getWorld().getNotificationHandler();

            int x = this.getBlockX();
            int z = this.getBlockZ();

            double ox = x + 0.5F;
            double oy = this.getBlockY();
            double oz = z + 0.5F;

            Color color = new Color((byte) 64, (byte) 96, (byte) 255);

            for( int i = 0; i < 4; i++ ) {
                float angle = (float) (i * (Math.PI / 2.0D));

                SpawnParticleSystem particle = new SpawnParticleSystem("SanAndreaP_Sprinkler_Stream", new Position(ox, oy, oz),
                                                                       new Direction(angle, 0F, 0F), 0.5F, color);
                notificationHandler.sendPacketIfChunkLoaded(particle, x, z);
            }
        }
        this.nextRun = now.plus(this.data.duration / 2, ChronoUnit.SECONDS);
    }

    public void activateWatering() {
        Store<ChunkStore> chunkStore = Optional.ofNullable(this.getChunk())
                                               .map(WorldChunk::getReference)
                                               .map(Ref::getStore).orElse(null);
        if( chunkStore == null ) {
            return;
        }

        Instant now = SprinklerHelper.getGameTime(chunkStore);

        activateWatering(chunkStore, now, true);
    }

    public boolean tryPlaceSeed(ItemStack itemStack, CommandBuffer<EntityStore> commandBuffer, InteractionContext interactionContext) {
        if( itemStack == null || itemStack.isEmpty() ) {
            return false;
        }

        String blockId = SeedPlacerHelper.INSTANCE.getBlockFromSeedId(itemStack.getItemId());
        if( blockId == null ) {
            return false;
        }

        final ItemContainer heldItemContainer = interactionContext.getHeldItemContainer();
        if( heldItemContainer == null ) {
            return false;
        }

        final Ref<EntityStore> store     = interactionContext.getEntity();
        final Vector3i         blockFace = BlockFace.DOWN.getDirection();
        final byte             slot      = interactionContext.getHeldItemSlot();
        return SprinklerHelper.callForPerimeter(this, this.getReference().getStore(), this.perimeter,
                                                (_, blockChunk, x, y, z, chunk, _) -> {
                                                    ItemStack currSlotItem = heldItemContainer.getItemStack(slot);
                                                    if( currSlotItem == null || currSlotItem.isEmpty() ) {
                                                        return false;
                                                    }

                                                    Inventory inv = null;
                                                    if( EntityUtils.getEntity(store, commandBuffer) instanceof LivingEntity le ) {
                                                        inv = le.getInventory();
                                                    }

                                                    BlockType currBlockType = BlockType.getAssetMap().getAsset(blockChunk.getBlock(x, y + 1, z));
                                                    if( currBlockType == null || "Empty".equals(currBlockType.getId()) ) {
                                                        BlockPlaceUtils.placeBlock(store, itemStack, blockId, heldItemContainer,
                                                                                   blockFace, new Vector3i(x, y + 1, z), new BlockRotation(), inv,
                                                                                   slot,
                                                                                   true, chunk.getReference(),
                                                                                   chunk.getWorld().getChunkStore().getStore(), commandBuffer);

                                                        return true;
                                                    }

                                                    return false;
                                                });
    }

    public boolean tryUpgradeSprinkler(ItemStack itemStack, CommandBuffer<EntityStore> commandBuffer, InteractionContext interactionContext) {
        if( itemStack == null || itemStack.isEmpty() || !itemStack.getItemId().equals("SanAndreaP_Sprinkler_Funnel") ) {
            return false;
        }

        final ItemContainer heldItemContainer = interactionContext.getHeldItemContainer();
        if( heldItemContainer == null ) {
            return false;
        }

        WorldChunk chunk = this.getChunk();
        if( chunk == null ) {
            return false;
        }

        BlockType current = this.getBlockType();
        if( current == null ) {
            return false;
        }

        String newState = current.getBlockKeyForState("Funnel");
        if( newState == null ) {
            return false;
        }

        int newStateId = BlockType.getAssetMap().getIndex(newState);
        if( newStateId == Integer.MIN_VALUE ) {
            return false;
        }

        BlockType newBlockType = BlockType.getAssetMap().getAsset(newStateId);
        if( newBlockType == null ) {
            return false;
        }

        final byte             slot            = interactionContext.getHeldItemSlot();
        final Ref<EntityStore> store           = interactionContext.getEntity();
        Player                 playerComponent = commandBuffer.getComponent(store, Player.getComponentType());
        boolean                isAdventureMode = (playerComponent == null || playerComponent.getGameMode() == GameMode.Adventure);
        if( isAdventureMode ) {
            ItemStackSlotTransaction transaction = heldItemContainer.removeItemStackFromSlot(slot, itemStack, 1);
            if( !transaction.succeeded() ) {
                return false;
            }
        }

        int settings = SetBlockSettings.NO_UPDATE_HEIGHTMAP | SetBlockSettings.NO_SET_FILLER;
        chunk.setBlock(this.getBlockX(), this.getBlockY(), this.getBlockZ(), newStateId, newBlockType, 0, 0, settings);

        return true;
    }

    public boolean isFunneled() {
        Optional<String> stateDefId = Optional.ofNullable(this.getBlockType())
                                              .flatMap(bt -> Optional.ofNullable(bt.getStateForBlock(bt)));
        return stateDefId.isPresent() && "Funnel".equals(stateDefId.get());
    }

    public boolean canRun(Instant gameTime) {
        return this.nextRun == null || this.nextRun.isBefore(gameTime);
    }

    @Override
    public void onDestroy() {
        Store<ChunkStore> store = this.getReference().getStore();
        SprinklerHelper.callForPerimeter(this, store, this.perimeter,
                                         (soil, blockChunk, x, y, z, chunk, _) -> {
                                             Instant soilDriesAt = Optional.ofNullable(soil.getWateredUntil()).orElse(Instant.now());
                                             // reset decay timer
                                             Instant soilDecaysAt = soilDriesAt.plus(this.data.duration, ChronoUnit.SECONDS);
                                             soil.setDecayTime(soilDecaysAt);
                                             chunk.setTicking(x, y, z, true);
                                             blockChunk.getSectionAtBlockY(y).scheduleTick(ChunkUtil.indexBlock(x, y, z), soilDecaysAt);
                                             chunk.setTicking(x, y + 1, z, true); // tick plant as well, if exists...

                                             SprinklerPlugin.LOGGER.at(Level.FINEST).atMostEvery(1, TimeUnit.SECONDS).log("Sprinkler destroyed!");

                                             return true;
                                         });
    }

    static {
        CODEC = BuilderCodec.builder(SprinklerState.class, SprinklerState::new, BlockState.BASE_CODEC)
                            .addField(new KeyedCodec<>("NextRun", Codec.INSTANT),
                                      (state, nr) -> state.nextRun = nr,
                                      state -> state.nextRun)
                            .build();
    }

    public static class Data
            extends StateData
    {
        @Nonnull
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new, StateData.DEFAULT_CODEC)
                .documentation("Waters tilled soil around it.")
                .append(new KeyedCodec<>("Range", Codec.INTEGER), (o, v) -> o.range = v, o -> o.range)
                    .documentation("The range of the sprinkler.")
                    .add()
                .append(new KeyedCodec<>("Duration", Codec.INTEGER), (o, v) -> o.duration = v, o -> o.duration)
                    .documentation("The duration (in ingame seconds) the soil stays watered.")
                    .add()
                .build();

        private int range    = 1;
        private int duration = 60 * 60 * 24;
    }
}
