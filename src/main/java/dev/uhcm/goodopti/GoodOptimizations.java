package dev.uhcm.goodopti;

import dev.uhcm.goodopti.config.GOConfig;
import dev.uhcm.goodopti.input.InputTickHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class GoodOptimizations implements ClientModInitializer {

    public static final String MOD_ID  = "goodopti";
    public static final Logger LOGGER  = LoggerFactory.getLogger("GoodOptimizations");

    @Override
    public void onInitializeClient() {
        GOConfig.load();
        InputTickHandler.register();
        LOGGER.info("[GoodOptimizations] Loaded on Minecraft {} — chunk culling: {}, input optimisation: {}",
                minecraftVersion(), GOConfig.CHUNK_CULLING_ENABLED, GOConfig.INPUT_OPT_ENABLED);
    }

    /**
     * Best-effort Minecraft version string for diagnostics. Uses only stable
     * Fabric Loader API so it works on every supported version.
     */
    public static String minecraftVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
