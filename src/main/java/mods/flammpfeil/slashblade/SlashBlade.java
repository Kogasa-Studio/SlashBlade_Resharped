package mods.flammpfeil.slashblade;

import com.google.common.base.CaseFormat;
import mods.flammpfeil.slashblade.ability.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.inputstate.InputState;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.capability.mobeffect.IMobEffectState;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.LockonCircleRender;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.entity.*;
import mods.flammpfeil.slashblade.client.renderer.gui.RankRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModel;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.BladeMotionManager;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.event.client.AdvancementsRecipeRenderer;
import mods.flammpfeil.slashblade.event.client.SneakingMotionCanceller;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.item.BladeStandItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemTierSlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.network.NetworkManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.Tag;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

@Mod(SlashBlade.modid)
public class SlashBlade
{
    public static final String modid = "slashblade";

    public static final CreativeModeTab SLASHBLADE = new CreativeModeTab(modid) {
        @OnlyIn(Dist.CLIENT)
        public ItemStack makeIcon() {
            ItemStack stack = new ItemStack(SBItems.slashblade);
            stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s->{
                s.setModel(new ResourceLocation(modid,"model/named/yamato.obj"));
                s.setTexture(new ResourceLocation(modid,"model/named/yamato.png"));
            });
            return stack;
        }
    };

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public SlashBlade() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading

        // Register the doClientStuff method for modloading
        DistExecutor.runWhenOn(Dist.CLIENT,()->()->{
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::Baked);
            //OBJLoader.INSTANCE.addDomain("slashblade");

            MinecraftForge.EVENT_BUS.addListener(MoveInputHandler::onPlayerPostTick);
        });


        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        NetworkManager.register();
    }

    private void setup(final FMLCommonSetupEvent event)
    {

        MinecraftForge.EVENT_BUS.addListener(KnockBackHandler::onLivingKnockBack);

        FallHandler.getInstance().register();
        LockOnManager.getInstance().register();

        MinecraftForge.EVENT_BUS.register(new CapabilityAttachHandler());
        MinecraftForge.EVENT_BUS.register(new StunManager());
        AnvilCrafting.getInstance().register();
        RefineHandler.getInstance().register();
        KillCounter.getInstance().register();
        RankPointHandler.getInstance().register();
        AllowFlightOverrwrite.getInstance().register();
        BlockPickCanceller.getInstance().register();

        MinecraftForge.EVENT_BUS.addListener(TargetSelector::onInputChange);
        SummonedSwordArts.getInstance().register();
        SlayerStyleArts.getInstance().register();
        Untouchable.getInstance().register();

        PlacePreviewEntryPoint.getInstance().register();

        // some preinit code
        //LOGGER.info("HELLO FROM PREINIT");
        //LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }

    @OnlyIn(Dist.CLIENT)
    private void doClientStuff(final FMLClientSetupEvent event) {

        MinecraftForge.EVENT_BUS.register(BladeModelManager.getInstance());
        MinecraftForge.EVENT_BUS.register(BladeMotionManager.getInstance());
/*
        Minecraft.getInstance().getEntityRenderDispatcher().getSkinMap().values().stream()
                .filter((er)-> er instanceof LivingEntityRenderer)
                .forEach((lr)-> ((LivingEntityRenderer)lr).addLayer(new LayerMainBlade((LivingEntityRenderer)lr)));
*/
        SneakingMotionCanceller.getInstance().register();
        UserPoseOverrider.getInstance().register();
        LockonCircleRender.getInstance().register();
        BladeComponentTooltips.getInstance().register();
        BladeMaterialTooltips.getInstance().register();
        AdvancementsRecipeRenderer.getInstance().register();


        RankRenderer.getInstance().register();

        // do something that can only be done on the client

        //OBJLoader.INSTANCE.addDomain("slashblade");

        /*
        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.SummonedSword, SummonedSwordRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.JudgementCut, JudgementCutRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.BladeItem, BladeItemEntityRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.BladeStand, BladeStandEntityRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.SlashEffect, SlashEffectRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(RegistryEvents.PlacePreview, PlacePreviewEntityRenderer::new);
        */
        //LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().options);
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> event) {
            // register a new block here
            IForgeRegistry<Block> registry = event.getRegistry();
            /*
            registry.register(new Block((Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F, 3.0F)))
                    .setRegistryName(modid,"material"));
                    */

            LOGGER.info("HELLO from Register Block");
        }

        @SubscribeEvent
        public static void onItemsRegistry(final RegistryEvent.Register<Item> event){
            IForgeRegistry<Item> registry = event.getRegistry();
            registry.register(
                    new ItemSlashBlade(
                            new ItemTierSlashBlade(() -> {
                                TagKey<Item> tags = ItemTags.create(new ResourceLocation("slashblade","proudsouls"));
                                return Ingredient.of(tags);
                                //Ingredient.fromItems(SBItems.proudsoul)
                            }),
                            1,
                            -2.4F,
                            (new Item.Properties()).tab(CreativeModeTab.TAB_COMBAT))
                            .setRegistryName(modid,"slashblade"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE)){
                        @Override
                        public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {

                            if(entity instanceof BladeItemEntity) return false;

                            CompoundTag tag = entity.serializeNBT();
                            tag.putInt("Health", 50);
                            int age = tag.getShort("Age");
                            entity.deserializeNBT(tag);

                            if(entity.isCurrentlyGlowing()){
                                entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.8,0.0,0.8).add(0.0D, +0.04D, 0.0D));
                            }else if(entity.isOnFire()) {
                                entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.8,0.5,0.8).add(0.0D, +0.04D, 0.0D));
                            }

                            return false;
                        }

                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }

                        @Override
                        public int getEnchantmentValue() {return 50;}
                    }.setRegistryName(modid,"proudsoul"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE)){
                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }
                        @Override
                        public int getEnchantmentValue() {return 100;}
                    }.setRegistryName(modid,"proudsoul_ingot"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE)){
                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }
                        @Override
                        public int getEnchantmentValue() {return 10;}
                    }.setRegistryName(modid,"proudsoul_tiny"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.UNCOMMON)){
                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }
                        @Override
                        public int getEnchantmentValue() {return 150;}
                    }.setRegistryName(modid,"proudsoul_sphere"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.RARE)){
                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }
                        @Override
                        public int getEnchantmentValue() {return 200;}
                    }.setRegistryName(modid,"proudsoul_crystal"));

            registry.register(
                    new Item((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.EPIC)){
                        @Override
                        public boolean isFoil(ItemStack stack) {
                            return true;//super.hasEffect(stack);
                        }
                        @Override
                        public int getEnchantmentValue() {return Integer.MAX_VALUE;}
                    }.setRegistryName(modid,"proudsoul_trapezohedron"));


            registry.register(
                    new BladeStandItem((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.COMMON))
                            .setRegistryName(modid,"bladestand_1"));
            registry.register(
                    new BladeStandItem((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.COMMON))
                            .setRegistryName(modid,"bladestand_2"));
            registry.register(
                    new BladeStandItem((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.COMMON))
                            .setRegistryName(modid,"bladestand_v"));
            registry.register(
                    new BladeStandItem((new Item.Properties()).tab(SLASHBLADE).rarity(Rarity.COMMON))
                            .setRegistryName(modid,"bladestand_s"));
            registry.register(
                    new BladeStandItem((new Item.Properties())
                            .tab(SLASHBLADE)
                            .rarity(Rarity.COMMON),true)
                            .setRegistryName(modid,"bladestand_1w"));
            registry.register(
                    new BladeStandItem((new Item.Properties())
                            .tab(SLASHBLADE)
                            .rarity(Rarity.COMMON),true)
                            .setRegistryName(modid,"bladestand_2w"));
        }

        public static final ResourceLocation BladeItemEntityLoc = new ResourceLocation(SlashBlade.modid, classToString(BladeItemEntity.class));
        public static EntityType<BladeItemEntity> BladeItem;

        public static final ResourceLocation BladeStandEntityLoc = new ResourceLocation(SlashBlade.modid, classToString(BladeStandEntity.class));
        public static EntityType<BladeStandEntity> BladeStand;



        public static final ResourceLocation SummonedSwordLoc = new ResourceLocation(SlashBlade.modid, classToString(EntityAbstractSummonedSword.class));
        public static EntityType<EntityAbstractSummonedSword> SummonedSword;

        public static final ResourceLocation JudgementCutLoc = new ResourceLocation(SlashBlade.modid, classToString(EntityJudgementCut.class));
        public static EntityType<EntityJudgementCut> JudgementCut;

        public static final ResourceLocation SlashEffectLoc = new ResourceLocation(SlashBlade.modid, classToString(EntitySlashEffect.class));
        public static EntityType<EntitySlashEffect> SlashEffect;




        public static final ResourceLocation PlacePreviewEntityLoc = new ResourceLocation(SlashBlade.modid, classToString(PlacePreviewEntity.class));
        public static EntityType<PlacePreviewEntity> PlacePreview;


        @SubscribeEvent
        public static void onEntitiesRegistry(final RegistryEvent.Register<EntityType<?>> event){
            {
                EntityType<EntityAbstractSummonedSword> entity = SummonedSword = EntityType.Builder
                        .of(EntityAbstractSummonedSword::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .setTrackingRange(4)
                        .setUpdateInterval(20)
                        .setCustomClientFactory(EntityAbstractSummonedSword::createInstance)
                        .build(SummonedSwordLoc.toString());
                entity.setRegistryName(SummonedSwordLoc);
                event.getRegistry().register(entity);
            }

            {
                EntityType<EntityJudgementCut> entity = JudgementCut = EntityType.Builder
                        .of(EntityJudgementCut::new, MobCategory.MISC)
                        .sized(2.5F, 2.5F)
                        .setTrackingRange(4)
                        .setUpdateInterval(20)
                        .setCustomClientFactory(EntityJudgementCut::createInstance)
                        .build(JudgementCutLoc.toString());
                entity.setRegistryName(JudgementCutLoc);
                event.getRegistry().register(entity);
            }

            {
                EntityType<BladeItemEntity> entity = BladeItem = EntityType.Builder
                        .of(BladeItemEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .setTrackingRange(4)
                        .setUpdateInterval(20)
                        .setCustomClientFactory(BladeItemEntity::createInstanceFromPacket)
                        .build(BladeItemEntityLoc.toString());
                entity.setRegistryName(BladeItemEntityLoc);
                event.getRegistry().register(entity);
            }

            {
                EntityType<BladeStandEntity> entity = BladeStand = EntityType.Builder
                        .of(BladeStandEntity::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .setTrackingRange(10)
                        .setUpdateInterval(20)
                        .setShouldReceiveVelocityUpdates(false)
                        .setCustomClientFactory(BladeStandEntity::createInstance)
                        .build(BladeStandEntityLoc.toString());
                entity.setRegistryName(BladeStandEntityLoc);
                event.getRegistry().register(entity);
            }

            {
                EntityType<EntitySlashEffect> entity = SlashEffect = EntityType.Builder
                        .of(EntitySlashEffect::new, MobCategory.MISC)
                        .sized(3.0F, 3.0F)
                        .setTrackingRange(4)
                        .setUpdateInterval(20)
                        .setCustomClientFactory(EntitySlashEffect::createInstance)
                        .build(SlashEffectLoc.toString());
                entity.setRegistryName(SlashEffectLoc);
                event.getRegistry().register(entity);
            }



            {
                EntityType<PlacePreviewEntity> entity = PlacePreview = EntityType.Builder
                        .of(PlacePreviewEntity::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .setTrackingRange(10)
                        .setUpdateInterval(20)
                        .setShouldReceiveVelocityUpdates(false)
                        .setCustomClientFactory(PlacePreviewEntity::createInstance)
                        .build(PlacePreviewEntityLoc.toString());
                entity.setRegistryName(PlacePreviewEntityLoc);
                event.getRegistry().register(entity);
            }
        }

        private static String classToString(Class<? extends Entity> entityClass) {
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entityClass.getSimpleName()).replace("entity_", "");
        }

        @SubscribeEvent
        public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event){
            event.registerEntityRenderer(RegistryEvents.SummonedSword, SummonedSwordRenderer::new);
            event.registerEntityRenderer(RegistryEvents.JudgementCut, JudgementCutRenderer::new);
            event.registerEntityRenderer(RegistryEvents.BladeItem, BladeItemEntityRenderer::new);
            event.registerEntityRenderer(RegistryEvents.BladeStand, BladeStandEntityRenderer::new);
            event.registerEntityRenderer(RegistryEvents.SlashEffect, SlashEffectRenderer::new);

            event.registerEntityRenderer(RegistryEvents.PlacePreview, PlacePreviewEntityRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterCapability(final RegisterCapabilitiesEvent event){
            CapabilitySlashBlade.register(event);
            CapabilityMobEffect.register(event);
            CapabilityInputState.register(event);
            CapabilityConcentrationRank.register(event);
        }

        public static ResourceLocation SWORD_SUMMONED;

        @SubscribeEvent
        public static void onCustomStat(final RegistryEvent.Register<StatType<?>> event){
            SWORD_SUMMONED = registerCustomStat("sword_summoned");
        }

        private static ResourceLocation registerCustomStat(String name) {
            ResourceLocation resourcelocation = new ResourceLocation(modid, name);
            Registry.register(Registry.CUSTOM_STAT, name, resourcelocation);
            Stats.CUSTOM.get(resourcelocation, StatFormatter.DEFAULT);
            return resourcelocation;
        }

        /**
         * /scoreboard objectives add stat minecraft.custom:slashblade.sword_summoned
         * /scoreboard objectives setdisplay sidebar stat
         */
    }

    @OnlyIn(Dist.CLIENT)
    private void Baked(final ModelBakeEvent event){
        {
            ModelResourceLocation loc = new ModelResourceLocation(
                    ForgeRegistries.ITEMS.getKey(SBItems.slashblade), "inventory");
            BladeModel model = new BladeModel(event.getModelRegistry().get(loc), event.getModelLoader());
            event.getModelRegistry().put(loc, model);
        }

        /*
        overrideModel(event, SBItems.proudsoul, new ResourceLocation(modid, "block/soul.obj"));
        overrideModel(event, SBItems.proudsoul_ingot, new ResourceLocation(modid, "block/ingot.obj"));
        overrideModel(event, SBItems.proudsoul_tiny, new ResourceLocation(modid, "block/tiny.obj"));
        overrideModel(event, SBItems.proudsoul_sphere, new ResourceLocation(modid, "block/sphere.obj"));
        overrideModel(event, SBItems.proudsoul_crystal, new ResourceLocation(modid, "block/crystal.obj"));
        overrideModel(event, SBItems.proudsoul_trapezohedron, new ResourceLocation(modid, "block/trapezohedron.obj"));


        overrideModelBlockType(event, SBItems.bladestand_1, new ResourceLocation(modid, "block/stand_1.obj"));
        overrideModelBlockType(event, SBItems.bladestand_2, new ResourceLocation(modid, "block/stand_2.obj"));
        overrideModelBlockType(event, SBItems.bladestand_v, new ResourceLocation(modid, "block/stand_v.obj"));
        overrideModelBlockType(event, SBItems.bladestand_s, new ResourceLocation(modid, "block/stand_s.obj"));
        overrideModelBlockType(event, SBItems.bladestand_1w, new ResourceLocation(modid, "block/stand_w_1.obj"));
        overrideModelBlockType(event, SBItems.bladestand_2w, new ResourceLocation(modid, "block/stand_w_2.obj"));
        */
    }

    /*
    @OnlyIn(Dist.CLIENT)
    private void overrideModelBlockType(final ModelBakeEvent event, Item item, ResourceLocation newLoc){
        EnumMap<ItemCameraTransforms.TransformType, TRSRTransformation> block = new EnumMap<>(ItemCameraTransforms.TransformType.class);

        TRSRTransformation thirdPersonBlock = ForgeBlockStateV1.Transforms.convert(0, 2.5f, 0, 75, 45, 0, 0.375f);
        block.put(ItemCameraTransforms.TransformType.GUI, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 30, 315, 0, 0.625f));
        block.put(ItemCameraTransforms.TransformType.GROUND, ForgeBlockStateV1.Transforms.convert(0, 3, 0, 0, 0, 0, 0.25f));
        block.put(ItemCameraTransforms.TransformType.FIXED, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 0, 0, 0, 0.5f));
        block.put(ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, thirdPersonBlock);
        block.put(ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, ForgeBlockStateV1.Transforms.leftify(thirdPersonBlock));
        block.put(ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 0, 45, 0, 0.4f));
        block.put(ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 0, 225, 0, 0.4f));

        SimpleModelState state = new SimpleModelState(ImmutableMap.copyOf(block));

        overrideModel(event, item, newLoc, state);
    }

    @OnlyIn(Dist.CLIENT)
    private void overrideModel(final ModelBakeEvent event, Item item, ResourceLocation newLoc){

        EnumMap<ItemCameraTransforms.TransformType, TRSRTransformation> block = new EnumMap<>(ItemCameraTransforms.TransformType.class);

        TRSRTransformation thirdPersonBlock = ForgeBlockStateV1.Transforms.convert(0, 2.5f, 0, 75, 45, 0, 0.375f);
        block.put(ItemCameraTransforms.TransformType.GUI, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 10, 0, 0, 0.9f));
        block.put(ItemCameraTransforms.TransformType.GROUND, ForgeBlockStateV1.Transforms.convert(0, 3, 0, 0, 0, 0, 0.5f));
        block.put(ItemCameraTransforms.TransformType.FIXED, ForgeBlockStateV1.Transforms.convert(0, 0, -10, -90, 0, 0, 1.0f));
        block.put(ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, thirdPersonBlock);
        block.put(ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, ForgeBlockStateV1.Transforms.leftify(thirdPersonBlock));
        block.put(ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 0, 45, 0, 0.4f));
        block.put(ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND, ForgeBlockStateV1.Transforms.convert(0, 0, 0, 0, 225, 0, 0.4f));

        SimpleModelState state = new SimpleModelState(ImmutableMap.copyOf(block));

        overrideModel(event, item, newLoc, state);
    }
    @OnlyIn(Dist.CLIENT)
    private void overrideModel(final ModelBakeEvent event, Item item, ResourceLocation newLoc, SimpleModelState state){
        ModelResourceLocation loc = new ModelResourceLocation(
                ForgeRegistries.ITEMS.getKey(item), "inventory");

        IModelGeometry unbaked = ModelLoaderRegistry.getmo.getModelOrMissing(newLoc);
        event.getModelRegistry().put(loc , unbaked.bake(event.getModelLoader(), ModelLoader.defaultTextureGetter(), (ISprite) state, DefaultVertexFormats.ITEM));
    }*/

}
