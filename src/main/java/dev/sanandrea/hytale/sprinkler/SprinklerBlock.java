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
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
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
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.WorldNotificationHandler;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.sanandrea.hytale.sprinkler.event.SprinklerLifecycleHandler;
import dev.sanandrea.hytale.sprinkler.event.SprinklerTickHandler;
import dev.sanandrea.hytale.sprinkler.interaction.SeedPlacerHelper;
import dev.sanandrea.hytale.sprinkler.util.SprinklerHelper;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings({ "removal", "deprecation" })
public class SprinklerBlock
        implements Component<ChunkStore>
{
    public static final BuilderCodec<SprinklerBlock>              CODEC;
    private static      ComponentType<ChunkStore, SprinklerBlock> component = null;

    public static void registerComponent(ComponentRegistryProxy<ChunkStore> registry) {
        if( component == null ) {
            component = registry.registerComponent(SprinklerBlock.class, "SanAndreaP_Sprinkler", SprinklerBlock.CODEC);
            registry.registerSystem(new SprinklerLifecycleHandler(component));
            registry.registerSystem(new SprinklerTickHandler(component));
        }
    }

    public static ComponentType<ChunkStore, SprinklerBlock> getComponent() {
        return component;
    }

    protected int[][] perimeter = new int[0][];
    protected int     duration  = 60 * 60 * 24;
    private   int     range     = 0;
    private   Instant nextRun   = null;

    public SprinklerBlock() {}

    public SprinklerBlock(int range, int[][] perimeter, int duration, Instant nextRun) {
        this.range = range;
        this.perimeter = perimeter;
        this.duration = duration;
        this.nextRun = nextRun;
    }

    private void updateRange(int range) {
        this.range = range;
        this.perimeter = SprinklerHelper.generatePerimeter(range);
    }

    public Instant getNextRun() {
        return this.nextRun;
    }

    public Instant setNextRun(Instant now) {
        this.nextRun = now.plus(this.duration / 2, ChronoUnit.SECONDS);
        return this.nextRun;
    }

    public void scheduleTick(@Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer,
                             @Nonnull BlockModule.BlockStateInfo blockStateInfo, boolean forNext)
    {
        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if( chunkRef.isValid() ) {
            int index = blockStateInfo.getIndex();
            int x     = ChunkUtil.xFromBlockInColumn(index);
            int y     = ChunkUtil.yFromBlockInColumn(index);
            int z     = ChunkUtil.zFromBlockInColumn(index);

            BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
            assert blockChunk != null;

            BlockSection blockSection = blockChunk.getSectionAtBlockY(y);
            Instant      nextRunTime  = this.getNextRun();
            if( nextRunTime == null || forNext ) {
                nextRunTime = this.setNextRun(SprinklerHelper.getGameTime(store));
            }
            if( nextRunTime == null ) {
                return;
            }

            blockSection.scheduleTick(ChunkUtil.indexBlock(x, y, z), nextRunTime);
        }
    }

    private boolean waterSoil(TilledSoilBlock soil, BlockChunk blockChunk, int x, int y, int z, WorldChunk chunk, Instant gameTime) {
        Instant nextWatering = gameTime.plus(this.duration, ChronoUnit.SECONDS);
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

    public void activateWatering(@Nonnull Vector3i blockCoords, Store<ChunkStore> chunkStore) {
        SprinklerHelper.callForPerimeter(blockCoords, chunkStore, this.perimeter, this::waterSoil,
                                         (bc, chunk, _) -> {
                                             WorldNotificationHandler notificationHandler = chunk.getWorld().getNotificationHandler();

                                             double ox = bc.x + 0.5F;
                                             double oz = bc.z + 0.5F;

                                             Color color = new Color((byte) 128, (byte) 192, (byte) 255);

                                             for( int i = 0; i < 4; i++ ) {
                                                 float angle = (float) (i * (Math.PI / 2.0D));

                                                 SpawnParticleSystem particle = new SpawnParticleSystem("SanAndreaP_Sprinkler_Stream",
                                                                                                        new Position(ox, bc.y, oz),
                                                                                                        new Direction(angle, 0F, 0F), 0.5F, color);
                                                 notificationHandler.sendPacketIfChunkLoaded(particle, bc.x, bc.z);
                                             }
                                         });
    }

    public boolean tryPlaceSeed(WorldChunk chunk, Vector3i targetBlock, ItemStack itemStack, CommandBuffer<EntityStore> commandBuffer, InteractionContext interactionContext) {
        if( itemStack == null || itemStack.isEmpty() ) {
            return false;
        }

        String blockTypeKey = SeedPlacerHelper.getBlockFromSeedId(itemStack.getItemId());
        if( blockTypeKey == null ) {
            return false;
        }

        final ItemContainer heldItemContainer = interactionContext.getHeldItemContainer();
        if( heldItemContainer == null ) {
            return false;
        }

        final Ref<EntityStore> eRef      = interactionContext.getEntity();
        final Vector3i         blockFace = BlockFace.DOWN.getDirection();
        final byte             slot      = interactionContext.getHeldItemSlot();
        return SprinklerHelper.callForPerimeter(targetBlock, chunk.getReference().getStore(), this.perimeter,
                                                (_, blockChunk, x, y, z, localChunk, _) -> {
                                                    ItemStack currSlotItem = heldItemContainer.getItemStack(slot);
                                                    if( currSlotItem == null || currSlotItem.isEmpty() ) {
                                                        return false;
                                                    }

                                                    Inventory inv = null;
                                                    if( EntityUtils.getEntity(eRef, commandBuffer) instanceof LivingEntity le ) {
                                                        inv = le.getInventory();
                                                    }

                                                    BlockType currBlockType = BlockType.getAssetMap().getAsset(blockChunk.getBlock(x, y + 1, z));
                                                    if( currBlockType == null || "Empty".equals(currBlockType.getId()) ) {
                                                        BlockPlaceUtils.placeBlock(eRef, itemStack, blockTypeKey, heldItemContainer,
                                                                                   blockFace, new Vector3i(x, y + 1, z), new BlockRotation(), inv,
                                                                                   slot,
                                                                                   true, localChunk.getReference(),
                                                                                   localChunk.getWorld().getChunkStore().getStore(), eRef.getStore(),
                                                                                   false);

                                                        return true;
                                                    }

                                                    return false;
                                                });
    }

    public static boolean tryUpgradeSprinkler(WorldChunk chunk, Vector3i targetBlock, ItemStack itemStack, CommandBuffer<EntityStore> commandBuffer, InteractionContext interactionContext) {
        if( itemStack == null || itemStack.isEmpty() || !itemStack.getItemId().equals("SanAndreaP_Sprinkler_Funnel") ) {
            return false;
        }

        final ItemContainer heldItemContainer = interactionContext.getHeldItemContainer();
        BlockType           current           = chunk.getBlockType(targetBlock);

        if( heldItemContainer == null || current == null ) {
            return false;
        }

        int newStateId = Optional.ofNullable(current.getBlockKeyForState("Funnel"))
                                 .map(k -> BlockType.getAssetMap().getIndex(k))
                                 .orElse(Integer.MIN_VALUE);
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
        chunk.setBlock(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), newStateId, newBlockType, 0, 0, settings);

        return true;
    }

    public static boolean isFunneled(WorldChunk chunk, Vector3i targetBlock) {
        Optional<String> stateDefId = Optional.ofNullable(chunk.getBlockType(targetBlock))
                                              .flatMap(bt -> Optional.ofNullable(bt.getStateForBlock(bt)));
        return stateDefId.isPresent() && "Funnel".equals(stateDefId.get());
    }

    public void destroy(WorldChunk chunk, Vector3i targetBlock) {
        SprinklerHelper.callForPerimeter(targetBlock, chunk.getReference().getStore(), this.perimeter,
                                         (soil, blockChunk, x, y, z, localChunk, _) -> {
                                             Instant soilDriesAt = Optional.ofNullable(soil.getWateredUntil()).orElse(Instant.now());
                                             // reset decay timer
                                             Instant soilDecaysAt = soilDriesAt.plus(this.duration, ChronoUnit.SECONDS);
                                             soil.setDecayTime(soilDecaysAt);
                                             localChunk.setTicking(x, y, z, true);
                                             blockChunk.getSectionAtBlockY(y).scheduleTick(ChunkUtil.indexBlock(x, y, z), soilDecaysAt);
                                             localChunk.setTicking(x, y + 1, z, true); // tick plant as well, if exists...

                                             SprinklerPlugin.LOGGER.at(Level.FINEST).atMostEvery(1, TimeUnit.SECONDS).log("Sprinkler destroyed!");

                                             return true;
                                         });
    }

    static {
        CODEC = BuilderCodec.builder(SprinklerBlock.class, SprinklerBlock::new)
                            .addField(new KeyedCodec<>("NextRun", Codec.INSTANT),
                                      (state, nr) -> state.nextRun = nr,
                                      state -> state.nextRun)
                            .addField(new KeyedCodec<>("Range", Codec.INTEGER),
                                      SprinklerBlock::updateRange,
                                      component -> component.range)
                            .build();
    }

    @NullableDecl
    @Override
    @SuppressWarnings({ "MethodDoesntCallSuperMethod", "java:S1182", "java:S2975" })
    public Component<ChunkStore> clone() {
        return new SprinklerBlock(this.range, this.perimeter, this.duration, this.nextRun);
    }
}
