package redxax.oxy.remotely;

//#if FABRIC
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
//#elseif FORGE
//#if MC >= 1.16.5
//$$ import net.minecraftforge.eventbus.api.IEventBus;
//$$ import net.minecraftforge.fml.common.Mod;
//$$ import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
//$$ import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//$$ import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
//$$ import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#else
//$$ import net.minecraftforge.fml.common.Mod;
//$$ import net.minecraftforge.fml.common.event.FMLInitializationEvent;
//#endif
//#elseif NEOFORGE
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.fml.common.Mod;
//$$ import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//$$ import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//$$ import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
//#if MC >= 1.21.1
//$$ import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//$$ import redxax.oxy.remotely.resync.bridge.ReSyncVanillaBridgeManager;
//$$ import redxax.oxy.remotely.resync.bridge.ReSyncVanillaPacketAdapter;
//#endif
//#endif

import redxax.oxy.remotely.host.MinecraftApplicationHost;

//#if FORGE-LIKE
//#if MC >= 1.16.5
//$$ @Mod(Constants.ID)
//#else
//$$ @Mod(modid = Constants.ID, version = Constants.VERSION)
//#endif
//#endif
public class Entrypoint
//#if FABRIC
    implements ModInitializer, ClientModInitializer, DedicatedServerModInitializer
//#endif
{
    //#if FORGE && MC >= 1.16.5
    //$$ public Entrypoint() {
    //$$     setupForgeEvents(FMLJavaModLoadingContext.get().modEventBus);
    //$$ }
    //#elseif NEOFORGE
    //$$ public Entrypoint(IEventBus modEventBus) {
    //$$     setupForgeEvents(modEventBus);
    //$$ }
    //#endif

    //#if FABRIC
    @Override
    //#elseif FORGE && MC <= 1.12.2
    //$$ @Mod.EventHandler
    //#endif
    public void onInitialize(
        //#if FORGE-LIKE
        //#if MC >= 1.16.5
        //$$ FMLCommonSetupEvent event
        //#else
        //$$ FMLInitializationEvent event
        //#endif
        //#endif
    ) {
        RemotelyInit.initCommon();
    }

    //#if FABRIC
    @Override
    //#elseif FORGE && MC <= 1.12.2
    //$$ @Mod.EventHandler
    //#endif
    public void onInitializeClient(
        //#if FORGE-LIKE
        //#if MC >= 1.16.5
        //$$ FMLClientSetupEvent event
        //#else
        //$$ FMLInitializationEvent event
        //#endif
        //#endif
    ) {
        //#if FORGE && MC <= 1.12.2
        //$$ if (!event.side.isClient) {
        //$$     return;
        //$$ }
        //#endif

        RemotelyInit.initClient(new MinecraftApplicationHost());
    }

    //#if FABRIC
    @Override
    //#elseif FORGE && MC <= 1.12.2
    //$$ @Mod.EventHandler
    //#endif
    public void onInitializeServer(
        //#if FORGE-LIKE
        //#if MC >= 1.16.5
        //$$ FMLDedicatedServerSetupEvent event
        //#else
        //$$ FMLInitializationEvent event
        //#endif
        //#endif
    ) {
        //#if FORGE && MC <= 1.12.2
        //$$ if (!event.side.isServer) {
        //$$     return;
        //$$ }
        //#endif
    }

    //#if FORGE-LIKE && MC >= 1.16.5
    //$$ public void setupForgeEvents(IEventBus modEventBus) {
    //$$     modEventBus.addListener(this::onInitialize);
    //$$     modEventBus.addListener(this::onInitializeClient);
    //$$     modEventBus.addListener(this::onInitializeServer);
    //#if NEOFORGE && MC >= 1.21.1
    //$$     modEventBus.addListener(this::registerPayloadHandlers);
    //#endif
    //$$ }
    //#endif

    //#if NEOFORGE && MC >= 1.21.1
    //$$ public void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
    //$$     event.registrar(Constants.ID)
    //$$         .optional()
    //#if MC >= 1.21.8 || MC >= 26.1
    //$$         .playBidirectional(
    //$$             ReSyncVanillaPacketAdapter.BridgePayload.TYPE,
    //$$             ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC,
    //$$             (payload, context) -> ReSyncVanillaBridgeManager.getInstance().handlePayload(payload.data()),
    //$$             (payload, context) -> ReSyncVanillaBridgeManager.getInstance().handlePayload(payload.data())
    //$$         );
    //#else
    //$$         .playBidirectional(
    //$$             ReSyncVanillaPacketAdapter.BridgePayload.TYPE,
    //$$             ReSyncVanillaPacketAdapter.BridgePayload.REGISTRY_STREAM_CODEC,
    //$$             (payload, context) -> ReSyncVanillaBridgeManager.getInstance().handlePayload(payload.data())
    //$$         );
    //#endif
    //$$ }
    //#endif
}
