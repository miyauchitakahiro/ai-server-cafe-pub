package ai_server_cafe;

import ai_server_cafe.config.Config;
import ai_server_cafe.game.GameThread;
import ai_server_cafe.game.planner.thread.ThreadPathPlanner;
import ai_server_cafe.gui.GuiThread;
import ai_server_cafe.model.ObserverThread;
import ai_server_cafe.network.receiver.RefBoxReceiver;
import ai_server_cafe.network.receiver.VisionReceiver;
import ai_server_cafe.network.receiver.VisionTrackerReceiver;
import ai_server_cafe.network.transmitter.BroadcastTransmitter;
import ai_server_cafe.network.transmitter.GrSimTransmitter;
import ai_server_cafe.network.transmitter.KIKSTransmitter;
import ai_server_cafe.network.transmitter.LocalRefBoxTransmitter;
import ai_server_cafe.network.transmitter.RobotDriver;
import ai_server_cafe.network.transmitter.SimulatorTransmitter;
import ai_server_cafe.records.RecordThread;
import ai_server_cafe.records.StrategyThread;
import ai_server_cafe.replay.ReplayThread;
import ai_server_cafe.scoreboard.demo_autoref.DemoAutoRef;
import ai_server_cafe.scoreboard.host_board.ScoreBoardThread;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.updater.DynamicsManager;
import ai_server_cafe.updater.KickManager;
import ai_server_cafe.updater.TransmitterManager;
import ai_server_cafe.updater.UpdaterKeyboard;
import ai_server_cafe.updater.UpdaterLogEvent;
import ai_server_cafe.updater.UpdaterPathPlanner;
import ai_server_cafe.updater.UpdaterRefBox;
import ai_server_cafe.updater.UpdaterStrategy;
import ai_server_cafe.updater.UpdaterWorld;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.natives.NativeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class Main {
	public static final Logger LOGGER = LogManager.getLogger("system");
	public static Optional<String[]> optArgs = Optional.empty();
	public static void main(String[] args) {
		optArgs = Optional.of(args);
		UpdaterLogEvent.getInstance();
		LOGGER.info("AI Server Cafe\n" +
				"     ) )\n" +
				"  |      |]\n" +
				"   `----'");
		Config config = ConfigManager.getInstance().getConfig();
		KickManager.getInstance(); // loadしておく
        DynamicsManager.getInstance(); // loadしておく
		VisionReceiver.getInstance().startWith(config.visionPort, config.visionAddress, config.useCommonInterfaceAddress ? config.commonInterfaceAddress : config.visionInterfaceAddress);
		VisionTrackerReceiver.getInstance().startWith(config.visionTrackerPort, config.visionTrackerAddress, config.useCommonInterfaceAddress ? config.commonInterfaceAddress : config.visionTrackerInterfaceAddress);
		RefBoxReceiver.getInstance().startWith(config.refBoxPort, config.refBoxAddress, config.useCommonInterfaceAddress ? config.commonInterfaceAddress : config.refBoxInterfaceAddress);
		TransmitterManager.getInstance().setTransmitters(config.radioTypes);
		GuiThread.getInstance().setVisible(true).start();
		StrategyThread.getInstance().start();
		RecordThread.getInstance().start();
		ReplayThread.getInstance().start();
		RobotDriver.getInstance().start();
		//ObserverThread.getInstance().start();
		GuiThread.getInstance().setVisible(true);
		GameThread.getInstance(TeamColor.BLUE).start();
		GameThread.getInstance(TeamColor.YELLOW).start();
		LocalRefBoxTransmitter.getInstance().start();
		ThreadPathPlanner.getInstance(TeamColor.YELLOW).start();
		ThreadPathPlanner.getInstance(TeamColor.BLUE).start();
		UpdaterKeyboard.getInstance().init();
		NativeRegistry.register("native/lib/controller", "jinput-dx8_64.dll", "native/lib/controller");
		NativeRegistry.register("native/lib/controller", "jinput-raw_64.dll", "native/lib/controller");
		NativeRegistry.register("native/lib/controller", "jinput-wintab.dll", "native/lib/controller");
		NativeRegistry.register("native/lib/controller", "libjinput-linux64.so", "native/lib/controller");
		NativeRegistry.register("native/lib/controller", "libjinput-osx.jnilib", "native/lib/controller");
        NativeRegistry.register("task", "move_wall.json", "task");
        NativeRegistry.register("task", "path_ball.json", "task");
        NativeRegistry.register("task", "move_test_obstacle_and_init.json", "task");
        NativeRegistry.register("task", "move_test_obstacle_and_init2.json", "task");
        NativeRegistry.register("task", "move_test_obstacle_and_init3.json", "task");
        NativeRegistry.register("task", "move_test_obstacle_and_init4.json", "task");
        NativeRegistry.register("task", "move_test_obstacle_and_init5.json", "task");
        NativeRegistry.register("task", "move_test_planner.json", "task");
		NativeRegistry.loadAll();
		if (config.useScoreBoard) {
			ScoreBoardThread.getInstance().start();
			DemoAutoRef.getInstance().start();
		}
    }

	public static void exit(int exitCode, boolean autoReboot) {
		VisionReceiver.getInstance().terminate();
		VisionTrackerReceiver.getInstance().terminate();
		ObserverThread.getInstance().terminate();
		GuiThread.getInstance().terminate();
		GameThread.getInstance(TeamColor.BLUE).terminate();
		GameThread.getInstance(TeamColor.YELLOW).terminate();
		StrategyThread.getInstance().terminate();
		RecordThread.getInstance().terminate();
		ReplayThread.getInstance().terminate();
		GrSimTransmitter.getInstance().terminate();
		RefBoxReceiver.getInstance().terminate();
		KIKSTransmitter.getInstance().terminate();
		BroadcastTransmitter.getInstance().terminate();
		SimulatorTransmitter.getInstance().terminate();
		RobotDriver.getInstance().terminate();
		ThreadPathPlanner.getInstance(TeamColor.YELLOW).terminate();
		ThreadPathPlanner.getInstance(TeamColor.BLUE).terminate();
		LocalRefBoxTransmitter.getInstance().terminate();
		ScoreBoardThread.getInstance().terminate();
		ConfigManager.getInstance().save();
		KickManager.getInstance().save();
        DynamicsManager.getInstance().save(); // loadしておく

        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            // Do nothing
        }
		UpdaterLogEvent.getInstance().terminate();
		LOGGER.info("Terminated with exit code : {}", exitCode);
		if (autoReboot) {
			boolean allowReboot = ConfigManager.getInstance().getConfig().allowAutoReboot;
			if (allowReboot) {
				boolean configReset = ConfigManager.getInstance().getConfig().resetStateAutoReboot;
				LOGGER.info("Auto-Rebooting....");
				VisionReceiver.reset();
				VisionTrackerReceiver.reset();
				GuiThread.reset();
				GameThread.reset();
				StrategyThread.reset();
				RecordThread.reset();
				ReplayThread.reset();
				GrSimTransmitter.reset();
				RefBoxReceiver.reset();
				KIKSTransmitter.reset();
				BroadcastTransmitter.reset();
				SimulatorTransmitter.reset();
				RobotDriver.reset();
                ObserverThread.reset();
				UpdaterPathPlanner.reset();
				LocalRefBoxTransmitter.reset();
				UpdaterStrategy.reset();
				UpdaterWorld.reset();
				if (configReset)
					ConfigManager.reset();
				UpdaterRefBox.reset();
				TransmitterManager.reset();
				ThreadPathPlanner.reset();
				ScoreBoardThread.reset();
				System.gc();
				try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
					// Do nothing
				}
				main(optArgs.orElse(new String[0]));
				return;
			} else {
				LOGGER.info("Auto-Reboot is not allowed by config.json");
			}
		}
		System.exit(exitCode);
	}
}
