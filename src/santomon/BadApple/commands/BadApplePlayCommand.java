package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import santomon.BadApple.data.BadAppleSession;
import santomon.BadApple.playback.BadApplePlaybackScript;

/**
 * Console command to start, configure, or control playback of Bad Apple on the Starsector campaign map.
 *
 * Syntax:
 *   badapple_play [delaySeconds|stop|pause|resume|reset] [startFrame]
 * Examples:
 *   badapple_play             -> Starts playback with default rate of 0.5 frames/sec (2.0s delay)
 *   badapple_play 0.5         -> Starts playback at 2 frames/sec (0.5s delay)
 *   badapple_play 2.0 100     -> Starts playback at 0.5 frames/sec starting from frame 100
 *   badapple_play pause       -> Pauses current playback
 *   badapple_play resume      -> Resumes current playback
 *   badapple_play stop        -> Stops playback and removes active script
 *   badapple_play reset       -> Resets frame index back to 0
 */
public class BadApplePlayCommand implements BaseCommand {

    public static final float DEFAULT_FRAME_DELAY_SECONDS = 1f / 24;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: badapple_play can only be run within campaign mode.");
            return CommandResult.WRONG_CONTEXT;
        }

        // =========================================================================
        // --- 1. Session Verification ---
        // =========================================================================
        BadAppleSession session = BadAppleSession.INSTANCE;
        if (session == null || !session.hasData()) {
            Console.showMessage("Error: No preprocessed Bad Apple data found in memory.");
            Console.showMessage("Please run 'badapple_prep' first to analyze star systems and frames.");
            return CommandResult.ERROR;
        }

        // =========================================================================
        // --- 2. Playback Control Directives (stop, pause, resume, reset) ---
        // =========================================================================
        String trimmedArgs = (args != null) ? args.trim() : "";
        String[] tokens = trimmedArgs.isEmpty() ? new String[0] : trimmedArgs.split("\\s+");

        if (tokens.length > 0) {
            String firstArg = tokens[0].toLowerCase();

            if (firstArg.equals("stop")) {
                if (BadApplePlaybackScript.ACTIVE_INSTANCE != null) {
                    BadApplePlaybackScript.ACTIVE_INSTANCE.stop();
                    BadApplePlaybackScript.ACTIVE_INSTANCE = null;
                    Console.showMessage("Bad Apple playback stopped.");
                } else {
                    Console.showMessage("No active Bad Apple playback to stop.");
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("pause")) {
                if (BadApplePlaybackScript.ACTIVE_INSTANCE != null) {
                    BadApplePlaybackScript.ACTIVE_INSTANCE.pause();
                    Console.showMessage(String.format("Bad Apple playback paused at frame %d / %d.",
                            BadApplePlaybackScript.ACTIVE_INSTANCE.getCurrentFrameIndex() + 1,
                            session.frameDeltas.size()));
                } else {
                    Console.showMessage("No active Bad Apple playback to pause.");
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("resume")) {
                if (BadApplePlaybackScript.ACTIVE_INSTANCE != null) {
                    BadApplePlaybackScript.ACTIVE_INSTANCE.resume();
                    Console.showMessage(String.format("Bad Apple playback resumed at frame %d / %d.",
                            BadApplePlaybackScript.ACTIVE_INSTANCE.getCurrentFrameIndex() + 1,
                            session.frameDeltas.size()));
                } else {
                    Console.showMessage("No active Bad Apple playback to resume. Starting new playback...");
                    startNewPlayback(session, DEFAULT_FRAME_DELAY_SECONDS, 0);
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("reset")) {
                if (BadApplePlaybackScript.ACTIVE_INSTANCE != null) {
                    BadApplePlaybackScript.ACTIVE_INSTANCE.setCurrentFrameIndex(0);
                    Console.showMessage("Bad Apple playback frame index reset to 0.");
                } else {
                    Console.showMessage("Bad Apple frame index reset to 0. Run 'badapple_play' to start.");
                }
                return CommandResult.SUCCESS;
            }
        }

        // =========================================================================
        // --- 3. Parameter Parsing & Launch ---
        // =========================================================================
        float frameDelaySeconds = DEFAULT_FRAME_DELAY_SECONDS;
        int startFrame = 0;

        if (tokens.length >= 1) {
            try {
                frameDelaySeconds = Float.parseFloat(tokens[0]);
                if (frameDelaySeconds <= 0f) {
                    frameDelaySeconds = DEFAULT_FRAME_DELAY_SECONDS;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (tokens.length >= 2) {
            try {
                startFrame = Integer.parseInt(tokens[1]);
                if (startFrame < 0) startFrame = 0;
            } catch (NumberFormatException ignored) {}
        }

        startNewPlayback(session, frameDelaySeconds, startFrame);
        return CommandResult.SUCCESS;
    }

    private void startNewPlayback(BadAppleSession session, float frameDelaySeconds, int startFrame) {
        // Stop and replace any existing playback script
        if (BadApplePlaybackScript.ACTIVE_INSTANCE != null) {
            BadApplePlaybackScript.ACTIVE_INSTANCE.stop();
            BadApplePlaybackScript.ACTIVE_INSTANCE = null;
        }

        BadApplePlaybackScript script = new BadApplePlaybackScript(session, frameDelaySeconds, startFrame);
        BadApplePlaybackScript.ACTIVE_INSTANCE = script;
        Global.getSector().addTransientScript(script);

        float fps = 1.0f / frameDelaySeconds;
        Console.showMessage("=== Bad Apple Playback Started ===");
        Console.showMessage(String.format("Speed: %.2f seconds/frame (%.2f frames/second)", frameDelaySeconds, fps));
        Console.showMessage(String.format("Starting at Frame: %d / %d", startFrame + 1, session.frameDeltas.size()));
        Console.showMessage("Playback running! Switch to campaign map view (TAB) to view rendered influence spheres.");
    }
}
