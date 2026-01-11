package dev.iseeethan.voxyworldgen.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu integration for VoxyWorldGen.
 * Provides a beautiful YACL-based config screen accessible from the mod menu.
 */
public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return VoxyWorldGenConfig::createConfigScreen;
    }
}
