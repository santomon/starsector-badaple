package santomon.BadApple;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import santomon.BadApple.playback.BadApplePlaybackScript;

public class BadAppleModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        System.out.println("[BadApple] Bad Apple Mod Plugin loaded successfully.");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Reset any active transient playback script reference on game load
        BadApplePlaybackScript.ACTIVE_INSTANCE = null;
    }
}
