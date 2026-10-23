package ai_server_cafe.gui.registry;

import ai_server_cafe.Main;
import ai_server_cafe.config.Config;
import ai_server_cafe.config.EnumRLActionSource;
import ai_server_cafe.config.EnumRLEnvironment;
import ai_server_cafe.config.EnumRLResetMode;
import ai_server_cafe.device.keyboard.InputKeyListenerCafe;
import ai_server_cafe.game.strategy.XGType;
import ai_server_cafe.gui.ConfigArea;
import ai_server_cafe.gui.ConfigWindow;
import ai_server_cafe.gui.EnumVisibleType;
import ai_server_cafe.gui.GameWindow;
import ai_server_cafe.gui.LogArea;
import ai_server_cafe.gui.ReplayArea;
import ai_server_cafe.gui.SplitPane;
import ai_server_cafe.gui.TabArea;
import ai_server_cafe.gui.VisionArea;
import ai_server_cafe.gui.component.ButtonCafe;
import ai_server_cafe.gui.component.CheckBoxCafe;
import ai_server_cafe.gui.component.ComboBoxCafe;
import ai_server_cafe.gui.component.DynamicLabelCafe;
import ai_server_cafe.gui.component.GraphicalAreaCafe;
import ai_server_cafe.gui.component.LabelCafe;
import ai_server_cafe.gui.component.ProgressBarCafe;
import ai_server_cafe.gui.component.ScrollPaneCafe;
import ai_server_cafe.gui.component.StaticTextAreaCafe;
import ai_server_cafe.gui.component.SwitchBoxCafe;
import ai_server_cafe.gui.component.TextAreaCafe;
import ai_server_cafe.gui.item.basic.GraphCafe;
import ai_server_cafe.gui.item.basic.MemoryCafe;
import ai_server_cafe.gui.item.basic.RectCafe;
import ai_server_cafe.gui.item.basic.RoleTargetsCafe;
import ai_server_cafe.model.field.FilteredRobot;
import ai_server_cafe.model.field.IntegratedRobot;
import ai_server_cafe.model.game.EnumCaptainType;
import ai_server_cafe.network.proto.ssl.gc.GcRefereeMessage;
import ai_server_cafe.network.radio.EnumRadioType;
import ai_server_cafe.replay.ReplayState;
import ai_server_cafe.replay.clip.ClipState;
import ai_server_cafe.replay.clip.FileExtension;
import ai_server_cafe.rl.RLDribbleSession;
import ai_server_cafe.updater.ConfigBuffers;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.updater.UpdaterJoyStick;
import ai_server_cafe.updater.UpdaterLogEvent;
import ai_server_cafe.updater.UpdaterRefBox;
import ai_server_cafe.updater.UpdaterReplay;
import ai_server_cafe.updater.UpdaterScoreBoard;
import ai_server_cafe.updater.UpdaterStats;
import ai_server_cafe.updater.UpdaterStrategy;
import ai_server_cafe.updater.UpdaterTestTasks;
import ai_server_cafe.updater.UpdaterThreadStatus;
import ai_server_cafe.updater.UpdaterWorld;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.gui.ColorHelper;
import ai_server_cafe.util.gui.EnumDisplayType;
import ai_server_cafe.util.gui.EnumLogLevel;
import ai_server_cafe.util.interfaces.IFuncParam1;
import ai_server_cafe.util.interfaces.IFunction;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.util.FastMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class RegistryInputComponents {
    public static final Logger LOGGER = LogManager.getLogger("inputs");
    private static boolean registering = false;

    public static void registerContents(@Nonnull GameWindow window) {
        registering = true;
        window.addKeyListener(new InputKeyListenerCafe());
        // Panelを追加
        VisionArea va = new VisionArea(FastMath.max(window.getWidth() - 450, 0), FastMath.max(window.getHeight() - 70, 0));
        ConfigArea ca = new ConfigArea(FastMath.max(window.getWidth() - 450, 0), 0, 450, window.getHeight() - 530);
        TabArea ta = new TabArea(FastMath.max(window.getWidth() - 450, 0), window.getHeight() - 530, 450, 280);
        LogArea la = new LogArea(FastMath.max(window.getWidth() - 450, 0), window.getHeight() - 250, 450, 250);
        ReplayArea ra = new ReplayArea(FastMath.max(window.getWidth() - 450, 0), 0, va.getWidth(), 70);
        window.addContent(va, null);
        window.addContent(ca, null);
        window.addContent(la, null);
        window.addContent(ta, null);
        window.addContent(ra, null);
        window.setContentPane(new SplitPane(JSplitPane.HORIZONTAL_SPLIT, va.getWidth(),
                new SplitPane(JSplitPane.VERTICAL_SPLIT, va.getHeight() - ra.getHeight(), va, ra),
                new SplitPane(JSplitPane.VERTICAL_SPLIT, ca.getHeight(), ca,
                        new SplitPane(JSplitPane.VERTICAL_SPLIT, ta.getHeight(), ta, la))));
        window.setResizable(true);

        // Switch等を追加
        List<Component> always = new ArrayList<>();
        setComponentAlways(always);
        window.addContents(always, ConfigArea.class);

        List<Component> tabArea = new ArrayList<>();
        setComponentHome(tabArea);
        setComponentLocalRef(window, tabArea);
        setComponentVisibility(tabArea);
        setComponentTeam(tabArea);
        setComponentNetwork(tabArea);
        setComponentInformation(tabArea);
        setComponentStats(tabArea);
        setComponentAnalyze(tabArea);
        setComponentDemo(tabArea);
        setComponentDribbleState(tabArea);
        setComponentObstacleView(tabArea);
        setComponentClipTab(tabArea);
        setComponentRLDribble(tabArea);
        window.addContents(tabArea, TabArea.class);

        List<Component> replayArea = new ArrayList<>();
        setComponentReplayArea(replayArea);
        window.addContents(replayArea, ReplayArea.class);

        List<Component> logArea = new ArrayList<>();
        setComponentLog(logArea);
        window.addContents(logArea, LogArea.class);
        registering = false;
    }

    private static void setComponentAlways(@Nonnull List<Component> components) {
        Config config = ConfigManager.getInstance().getConfig();
        IntegratedRobot robot = new IntegratedRobot(
                1,
                TeamColor.YELLOW,
                new FilteredRobot(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        RoleTargetsCafe roleTargetsCafe = new RoleTargetsCafe(
                Color.YELLOW,
                robot,
                false
        );

        IFuncParam1<Void, Boolean> onSwitchTeamColor = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().isGoalOfYellowPositive = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        components.add(new SwitchBoxCafe("Negative Side", 0.35, ConfigManager.getInstance().isInvert(TeamColor.YELLOW), 20, 0.1, 30,
                ColorHelper.ROBOT_YELLOW, ColorHelper.ROBOT_BLUE, ColorHelper.LINE_WHITE, onSwitchTeamColor,
                EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));

        IFuncParam1<Void, Boolean> onSwitchStart = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().setStart(arg);
                return null;
            }
        };
        components.add(new SwitchBoxCafe("Run", 0.35, ConfigManager.getInstance().isStart(), 20, 0.55, 30,
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchStart,
                EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, EnumCaptainType> onSwitchBlueCaptain = new IFuncParam1<Void, EnumCaptainType>() {
            @Override
            @Nullable
            public Void function(@Nonnull EnumCaptainType arg) {
                ConfigManager.getInstance().getEditableConfig().activeBlueCaptain = arg.getIdEnum();
                ConfigManager.getInstance().markDirty();
                if (!registering && arg == EnumCaptainType.TEST) {
                    File dir = new File("task");
                    JFileChooser fileChooser = new JFileChooser(dir);
                    if (fileChooser.showOpenDialog(fileChooser) == JFileChooser.APPROVE_OPTION) {
                        UpdaterTestTasks.getInstance().load(fileChooser.getSelectedFile(), TeamColor.BLUE);
                    }
                }
                return null;
            }
        };
        components.add(new ComboBoxCafe<>(EnumCaptainType.values(),
                ConfigManager.getInstance().getConfig().getCaptain(TeamColor.BLUE), 0.1, 85, 0.35, 30,
                onSwitchBlueCaptain, EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, EnumCaptainType> onSwitchYellowCaptain = new IFuncParam1<Void, EnumCaptainType>() {
            @Override
            @Nullable
            public Void function(@Nonnull EnumCaptainType arg) {
                ConfigManager.getInstance().getEditableConfig().activeYellowCaptain = arg.getIdEnum();
                ConfigManager.getInstance().markDirty();
                if (!registering && arg == EnumCaptainType.TEST) {
                    File dir = new File("task");
                    JFileChooser fileChooser = new JFileChooser(dir);
                    if (fileChooser.showOpenDialog(fileChooser) == JFileChooser.APPROVE_OPTION) {
                        UpdaterTestTasks.getInstance().load(fileChooser.getSelectedFile(), TeamColor.YELLOW);
                    }
                }
                return null;
            }
        };
        components.add(new ComboBoxCafe<>(EnumCaptainType.values(),
                ConfigManager.getInstance().getConfig().getCaptain(TeamColor.YELLOW), 0.55, 85, 0.35, 30,
                onSwitchYellowCaptain, EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new LabelCafe("Blue Captain : ", 0.1, 60, EnumVisibleType.ALWAYS).setDisplayType(0,
                EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new LabelCafe("Yellow Captain : ", 0.55, 60, EnumVisibleType.ALWAYS).setDisplayType(0,
                EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, EnumVisibleType> onSwitchVisible = new IFuncParam1<Void, EnumVisibleType>() {
            @Override
            @Nullable
            public Void function(@Nonnull EnumVisibleType arg) {
                ConfigManager.getInstance().setGuiVisibleType(arg);
                return null;
            }
        };
        components.add(new ComboBoxCafe<>(EnumVisibleType.getExcludedAlways(), EnumVisibleType.HOME, 0.3, 35, 0.4, 30,
                onSwitchVisible, EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO).setDisplayType(1, EnumDisplayType.FIT));
        IFuncParam1<Void, Boolean> onButton = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigWindow window = ConfigWindow.getInstance();
                // ウィンドウを前に出す
                window.setVisible(false);
                window.reset();
                window.setVisible(true);
                return null;
            }
        };
        components.add(new ButtonCafe("Config Editor", 0.3, 0, 0.4, 20, onButton,
                EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
    }

    private static void setComponentHome(@Nonnull List<Component> components) {
        double graphMaxX = 25.0;
        double graphMaxY = 500.0;
        double graphMinY = 0.0;
        components.add(new GraphicalAreaCafe(0.1, 20, 0.8, 150, EnumVisibleType.HOME, new GraphCafe(0, 0, 1.0, 150, EnumDisplayType.RATIO,
                EnumDisplayType.DEFAULT, EnumDisplayType.RATIO, EnumDisplayType.DEFAULT) {
            public void update() {
                count++;
                if (count % 20 == 0) {
                    double time = UpdaterWorld.getInstance().getCommonTime();
                    addPlot("Vision [p/s]", time, UpdaterWorld.getInstance().getVisionPerSec());
                    addPlot("Tracker [p/s]", time, UpdaterWorld.getInstance().getTrackerPerSec());
                    addPlot("RefBox [p/s]", time, UpdaterRefBox.getInstance().getUpdatingPerSec());
                    Map<String, Double> tpsMap = UpdaterThreadStatus.getInstance().getAllTPSData().get();
                    for (String key : tpsMap.keySet()) {
                        newGraph(key, ColorHelper.getRandomColor());
                        addPlot(key, time, tpsMap.get(key));
                    }
                }
            }
        }.newGraph("Vision [p/s]", ColorHelper.ARCHIVE_SKY).newGraph("Tracker [p/s]", ColorHelper.TARGET_EMERALD)
                .newGraph("Vision [p/s]", ColorHelper.ARCHIVE_SKY)
                .newGraph("RefBox [p/s]", ColorHelper.WEIGHT_VIOLET).setProperty(GraphCafe.Property.Y_MAX, graphMaxY)
                .setProperty(GraphCafe.Property.X_WIDTH, graphMaxX * 0.8).setProperty(GraphCafe.Property.X_MAX_WIDTH, graphMaxX)
                .setProperty(GraphCafe.Property.X_SCALE_WIDTH, graphMaxX * 0.2).setProperty(GraphCafe.Property.Y_MIN, graphMinY)
                .setProperty(GraphCafe.Property.Y_SCALE_WIDTH, (graphMaxY - graphMinY) * 0.2).setProperty(GraphCafe.Property.Y_SUBSCALE_WIDTH, (graphMaxY - graphMinY) * 0.04))
                .setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new GraphicalAreaCafe(0.1, 200, 0.9, 40, EnumVisibleType.HOME, new MemoryCafe(0, 0, 0.4, 40, EnumDisplayType.RATIO,
                EnumDisplayType.DEFAULT, EnumDisplayType.RATIO, EnumDisplayType.DEFAULT)).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
    }

    private static void setComponentLocalRef(@Nonnull GameWindow window, @Nonnull List<Component> components) {
        IFuncParam1<Void, Boolean> onSwitchLocalRef = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().setUseLocalRef(arg);
                return null;
            }
        };
        components.add(
                new SwitchBoxCafe("LocalRef Active", 0.35, ConfigManager.getInstance().isUseLocalRef(), 20, 0.1, 20,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchLocalRef,
                        EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, GcRefereeMessage.Referee.Command> onSwitchCommand =
                new IFuncParam1<Void, GcRefereeMessage.Referee.Command>() {
                    @Override
                    @Nullable
                    public Void function(@Nonnull GcRefereeMessage.Referee.Command arg) {
                        ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.command = arg;
                        ConfigManager.getInstance().markDirty();
                        return null;
                    }
                };
        components.add(new ComboBoxCafe<>(GcRefereeMessage.Referee.Command.values(),
                ConfigManager.getInstance().getConfig().localRefBoxConfig.command, 0.1, 50, 0.35, 30, onSwitchCommand,
                EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, GcRefereeMessage.Referee.Stage> onSwitchStage =
                new IFuncParam1<Void, GcRefereeMessage.Referee.Stage>() {
                    @Override
                    @Nullable
                    public Void function(@Nonnull GcRefereeMessage.Referee.Stage arg) {
                        ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.stage = arg;
                        ConfigManager.getInstance().markDirty();
                        return null;
                    }
                };
        components.add(new ComboBoxCafe<>(GcRefereeMessage.Referee.Stage.values(),
                ConfigManager.getInstance().getConfig().localRefBoxConfig.stage, 0.55, 50, 0.35, 30, onSwitchStage,
                EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        Integer[] ids = new Integer[16];
        for (int id = 0; id < 16; id++) {
            ids[id] = Integer.valueOf(id);
        }
        IFuncParam1<Void, Integer> onSwitchBlueKeeper = new IFuncParam1<Void, Integer>() {
            @Override
            @Nullable
            public Void function(@Nonnull Integer arg) {
                ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.blueGoalKeeper = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        components.add(new LabelCafe("Blue Goalie : ", 0.1, 85, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(
                new ComboBoxCafe<>(ids, ConfigManager.getInstance().getConfig().localRefBoxConfig.blueGoalKeeper, 0.35,
                        80, 0.1, 30, onSwitchBlueKeeper, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0,
                        EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, Integer> onSwitchYellowKeeper = new IFuncParam1<Void, Integer>() {
            @Override
            @Nullable
            public Void function(@Nonnull Integer arg) {
                ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.yellowGoalKeeper = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        components.add(new LabelCafe("Yellow Goalie : ", 0.55, 85, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(
                new ComboBoxCafe<>(ids, ConfigManager.getInstance().getConfig().localRefBoxConfig.yellowGoalKeeper,
                        0.80, 80, 0.1, 30, onSwitchYellowKeeper, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0,
                        EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        LabelCafe lc0 = new LabelCafe("Ball Placement Position : (x , y) = (", 0.1, 115,
                EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO);
        components.add(lc0);
        LabelCafe lc1 =
                new LabelCafe(", ", 0.1, 115, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(lc0.getWidth() + 50);
        components.add(lc1);
        LabelCafe lc2 =
                new LabelCafe(")", 0.1, 115, EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(lc0.getWidth() + 50 + lc1.getWidth() + 50);
        components.add(lc2);
        IFuncParam1<Void, String> onInputAbpX = new IFuncParam1<Void, String>() {
            @Override
            public Void function(String s) {
                if (s != null) {
                    try {
                        double value = Double.valueOf(s);
                        ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.ballPlacePosX = value;
                        ConfigManager.getInstance().markDirty();
                    } catch (NumberFormatException e) {
                        // Do nothing
                    }
                }
                return null;
            }
        };
        IFuncParam1<Void, String> onInputAbpY = new IFuncParam1<Void, String>() {
            @Override
            public Void function(String s) {
                if (s != null) {
                    try {
                        double value = Double.valueOf(s);
                        ConfigManager.getInstance().getEditableConfig().localRefBoxConfig.ballPlacePosY = value;
                        ConfigManager.getInstance().markDirty();
                    } catch (NumberFormatException e) {
                        // Do nothing
                    }
                }
                return null;
            }
        };
        components.add(new TextAreaCafe(
                String.valueOf(ConfigManager.getInstance().getConfig().localRefBoxConfig.ballPlacePosX), 0.1, 115, 50,
                1, onInputAbpX, EnumVisibleType.LOCAL_REFEREE) {
            @Override
            public void updateManually() {
                setText("" + ConfigManager.getInstance().getConfig().localRefBoxConfig.ballPlacePosX);
            }
        }.setDisplayType(0, EnumDisplayType.RATIO).setOffset(lc0.getWidth()));
        components.add(new TextAreaCafe(
                String.valueOf(ConfigManager.getInstance().getConfig().localRefBoxConfig.ballPlacePosY), 0.1, 115, 50,
                1, onInputAbpY, EnumVisibleType.LOCAL_REFEREE) {
            @Override
            public void updateManually() {
                setText("" + ConfigManager.getInstance().getConfig().localRefBoxConfig.ballPlacePosY);
            }
        }.setDisplayType(0, EnumDisplayType.RATIO).setOffset(lc0.getWidth() + 50 + lc1.getWidth()));
        IFuncParam1<Void, Boolean> onSwitchFocus = new IFuncParam1<Void, Boolean>() {
            @Override
            public Void function(Boolean aBoolean) {
                window.requestFocus();
                UpdaterWorld.getInstance().setControllerMode(false);
                ConfigManager.getInstance().setNeedReset(TeamColor.BLUE, true);
                ConfigManager.getInstance().setNeedReset(TeamColor.YELLOW, true);
                return null;
            }
        };
        components.add(new ButtonCafe("Keyboard", 0.1, 150, 0.35, 30, onSwitchFocus
                , EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, Boolean> onSwitchScan = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                UpdaterJoyStick.getInstance().scan();
                UpdaterWorld.getInstance().setControllerMode(true);
                ConfigManager.getInstance().setNeedReset(TeamColor.BLUE, true);
                ConfigManager.getInstance().setNeedReset(TeamColor.YELLOW, true);
                return null;
            }
        };
        components.add(new ButtonCafe("Scan GamePad", 0.5, 150, 0.35, 30, onSwitchScan,
                EnumVisibleType.LOCAL_REFEREE).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
    }

    private static void setComponentVisibility(@Nonnull List<Component> components) {
        Config config = ConfigManager.getInstance().getConfig();
        IFuncParam1<Void, Boolean> onSwitchVision = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().isVisionAreaVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        components.add(new SwitchBoxCafe("Vision", 0.35, config.isVisionAreaVisible, 20, 0.1, 20,
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchVision,
                EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));

        IFuncParam1<Void, Boolean> onSwitchRobotSpeedView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean isVisible) {
                // すべてのロボットの速度表示を切り替える
                ConfigManager.getInstance().getEditableConfig().visibility.robotSpeedVisible = isVisible;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };

        SwitchBoxCafe robotSpeedVisibleSwitch = new SwitchBoxCafe(
                "Robot Speeds", 0.35, ConfigManager.getInstance().getConfig().visibility.robotSpeedVisible,
                20, 0.1, 170, // スイッチの位置
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE,
                onSwitchRobotSpeedView,
                EnumVisibleType.VISIBILITY
        ).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO);


        IFuncParam1<Void, Boolean> onSwitch = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean isVisible) {
                ConfigManager.getInstance().getEditableConfig().visibility.teamGoalColorVisible = isVisible;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe teamColorVisibleSwitch = new SwitchBoxCafe(
                "Goal Color", 0.35, ConfigManager.getInstance().getConfig().visibility.teamGoalColorVisible,
                20, 0.55, 170, // スイッチの位置
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE,
                onSwitch,
                EnumVisibleType.VISIBILITY
        ).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO);

        IFuncParam1<Void, Boolean> onSwitchInvert = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.invertVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe invertVisibleSwitch =
                new SwitchBoxCafe("World Invert", 0.35, ConfigManager.getInstance().getConfig().visibility.invertVisible, 20, 0.55, 20,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchInvert,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(invertVisibleSwitch);
        IFuncParam1<Void, Boolean> onSwitchPathView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.pathVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe pathVisibleSwitch =
                new SwitchBoxCafe("Path", 0.35, config.visibility.pathVisible, 20, 0.1, 50,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchPathView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(pathVisibleSwitch);
        IFuncParam1<Void, Boolean> onSwitchActionView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.actionVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe actionVisibleSwitch =
                new SwitchBoxCafe("Action", 0.35, config.visibility.actionVisible, 20, 0.55, 50,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchActionView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(actionVisibleSwitch);
        IFuncParam1<Void, Boolean> onSwitchRoleView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.roleVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe roleVisibleSwitch =
                new SwitchBoxCafe("Role", 0.35, config.visibility.roleVisible, 20, 0.1, 80,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchRoleView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(roleVisibleSwitch);
        IFuncParam1<Void, Boolean> onSwitchTargetsView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.targetsVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe targetVisibleSwitch =
                new SwitchBoxCafe("Targets", 0.35, ConfigManager.getInstance().getConfig().visibility.targetsVisible, 20, 0.55, 80,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchTargetsView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(targetVisibleSwitch);
        IFuncParam1<Void, Boolean> onSwitchWaiterBoxView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.waiterBoxVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe waiterBoxVisibleSwitch =
                new SwitchBoxCafe("WaiterBox", 0.35, ConfigManager.getInstance().getConfig().visibility.waiterBoxVisible, 20, 0.1, 110,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchWaiterBoxView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(waiterBoxVisibleSwitch);

        IFuncParam1<Void, Boolean> onSwitchBallSpeedView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.ballVelocityVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        IFuncParam1<Void, Boolean> onSwitchBluePassScoreView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.bluePassScoreVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        IFuncParam1<Void, Boolean> onSwitchyellowPassScoreView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.yellowPassScoreVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        IFuncParam1<Void, Boolean> onSwitchBlueXGScoreView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.blueXGScoreVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        IFuncParam1<Void, Boolean> onSwitchyellowXGScoreView = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                ConfigManager.getInstance().getEditableConfig().visibility.yellowXGScoreVisible = arg;
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        SwitchBoxCafe ballVelocityVisibleSwitch =
                new SwitchBoxCafe("Ball Velocity", 0.35, ConfigManager.getInstance().getConfig().visibility.ballVelocityVisible, 20, 0.55, 110,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchBallSpeedView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(ballVelocityVisibleSwitch);
        SwitchBoxCafe bluePassScoreVisibleSwitch =
                new SwitchBoxCafe("Blue PassScore", 0.35, ConfigManager.getInstance().getConfig().visibility.bluePassScoreVisible, 20, 0.1, 140,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchBluePassScoreView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(bluePassScoreVisibleSwitch);
        SwitchBoxCafe yellowPassScoreVisibleSwitch =
                new SwitchBoxCafe("yellow PassScore", 0.35, ConfigManager.getInstance().getConfig().visibility.yellowPassScoreVisible, 20, 0.55, 140,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchyellowPassScoreView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(yellowPassScoreVisibleSwitch);
        SwitchBoxCafe blueXGScoreVisibleSwitch =
                new SwitchBoxCafe("Blue XGScore", 0.35, ConfigManager.getInstance().getConfig().visibility.blueXGScoreVisible, 20, 0.1, 200,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchBlueXGScoreView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(blueXGScoreVisibleSwitch);
        SwitchBoxCafe yellowXGScoreVisibleSwitch =
                new SwitchBoxCafe("yellow XGScore", 0.35, ConfigManager.getInstance().getConfig().visibility.yellowXGScoreVisible, 20, 0.55, 200,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchyellowXGScoreView,
                        EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO);
        components.add(yellowXGScoreVisibleSwitch);

        IFuncParam1<Void, Boolean> onSwitchAllview = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean arg) {
                invertVisibleSwitch.setSelected(false);
                pathVisibleSwitch.setSelected(false);
                actionVisibleSwitch.setSelected(false);
                roleVisibleSwitch.setSelected(false);
                targetVisibleSwitch.setSelected(false);
                waiterBoxVisibleSwitch.setSelected(false);
                ballVelocityVisibleSwitch.setSelected(false);
                robotSpeedVisibleSwitch.setSelected(false);
                teamColorVisibleSwitch.setSelected(false);
                bluePassScoreVisibleSwitch.setSelected(false);
                yellowPassScoreVisibleSwitch.setSelected(false);
                blueXGScoreVisibleSwitch.setSelected(false);
                yellowXGScoreVisibleSwitch.setSelected(false);
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };

        components.add(robotSpeedVisibleSwitch);
        components.add(teamColorVisibleSwitch);
        components.add(
                new ButtonCafe("Reset", 0.3, 230, 0.4, 30,
                        onSwitchAllview, EnumVisibleType.VISIBILITY).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2,EnumDisplayType.RATIO)
            );
    }

    private static void setComponentTeam(@Nonnull List<Component> components) {
        Config config = ConfigManager.getInstance().getConfig();
        List<Integer> yellowActives = MathHelper.toList(config.activeYellowRobots);
        List<Integer> blueActives = MathHelper.toList(config.activeBlueRobots);
        List<CheckBoxCafe> yellowBoxes = new ArrayList<>();
        List<CheckBoxCafe> blueBoxes = new ArrayList<>();
        double x = 0.1;
        int y = 50;
        double width = 0.1;
        int widthI = 40;
        int height = 30;
        for (int id = 0; id < ConfigManager.MAX_ROBOTS; id++) {
            final int finalId = id;
            IFuncParam1<Void, Boolean> onCheckYellow = new IFuncParam1<Void, Boolean>() {
                @Override
                @Nullable
                public Void function(Boolean aBoolean) {
                    ConfigBuffers.getInstance().setYellowActive(finalId, aBoolean);
                    return null;
                }
            };
            yellowBoxes.add(new CheckBoxCafe(String.valueOf(id), yellowActives.contains(id), x + (id % 8) * width,
                    y + (double) (id / 8) * height, widthI, height, onCheckYellow, EnumVisibleType.TEAM).setDisplayType(
                    0, EnumDisplayType.RATIO));
            IFuncParam1<Void, Boolean> onCheckBlue = new IFuncParam1<Void, Boolean>() {
                @Override
                @Nullable
                public Void function(Boolean aBoolean) {
                    ConfigBuffers.getInstance().setBlueActive(finalId, aBoolean);
                    return null;
                }
            };
            blueBoxes.add(new CheckBoxCafe(String.valueOf(id), blueActives.contains(id), x + (id % 8) * width,
                    y + (double) (id / 8) * height + height * 4, widthI, height, onCheckBlue,
                    EnumVisibleType.TEAM).setDisplayType(0, EnumDisplayType.RATIO));
        }
        components.addAll(yellowBoxes);
        components.addAll(blueBoxes);
        IFuncParam1<Void, Boolean> onSwitchYellowActiveApply = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                ConfigBuffers.getInstance().sendYellowActivesToConfig();
                return null;
            }
        };
        components.add(new LabelCafe("Yellow Robots Active : ", 0.1, 25, EnumVisibleType.TEAM).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new ButtonCafe("Apply", 170, 15, 80, 30, onSwitchYellowActiveApply,
                EnumVisibleType.TEAM).setDisplayType(0, EnumDisplayType.FIT));
        IFuncParam1<Void, Boolean> onSwitchYellowAllActive = new IFuncParam1<Void, Boolean>() {
            @Override
            public Void function(Boolean value) {
                for (CheckBoxCafe checkBoxCafe : yellowBoxes) {
                    checkBoxCafe.setSelected(!value);
                    checkBoxCafe.onCheck.function(!value);
                }
                return null;
            }
        };
        components.add(new ButtonCafe("All", 80, 15, 80, 30, onSwitchYellowAllActive,
                EnumVisibleType.TEAM).setDisplayType(0, EnumDisplayType.FIT));

        IFuncParam1<Void, Boolean> onSwitchBlueActiveApply = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                ConfigBuffers.getInstance().sendBlueActivesToConfig();
                return null;
            }
        };
        components.add(
                new LabelCafe("Blue Robots Active : ", 0.1, 25 + height * 4, EnumVisibleType.TEAM).setDisplayType(0,
                        EnumDisplayType.RATIO));
        components.add(new ButtonCafe("Apply", 170, 15 + height * 4, 80, 30, onSwitchBlueActiveApply,
                EnumVisibleType.TEAM).setDisplayType(0, EnumDisplayType.FIT));
        IFuncParam1<Void, Boolean> onSwitchBlueAllActive = new IFuncParam1<Void, Boolean>() {
            @Override
            public Void function(Boolean value) {
                for (CheckBoxCafe checkBoxCafe : blueBoxes) {
                    checkBoxCafe.setSelected(!value);
                    checkBoxCafe.onCheck.function(!value);
                }
                return null;
            }
        };
        components.add(new ButtonCafe("All", 80, 15 + height * 4, 80, 30, onSwitchBlueAllActive,
                EnumVisibleType.TEAM).setDisplayType(0, EnumDisplayType.FIT));
    }

    private static void setComponentNetwork(@Nonnull List<Component> components) {
        LabelCafe lc0 = new LabelCafe("Set if-address : ", 0.1, 40, EnumVisibleType.NETWORK).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc0);
        IFuncParam1<Void, String> setInterfaceAddress = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigBuffers.getInstance().setInterfaceAddress(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(ConfigManager.getInstance().getConfig().commonInterfaceAddress, 0.1, 40, 160, 1,
                        setInterfaceAddress, EnumVisibleType.NETWORK).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(lc0.getWidth()));
        LabelCafe lc1 = new LabelCafe("Vision port : ", 0.1, 20, EnumVisibleType.NETWORK).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc1);
        IFuncParam1<Void, String> setVisionPort = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    try {
                        int port = Integer.parseInt(s);
                        ConfigBuffers.getInstance().setVisionPort(port);
                    } catch (NumberFormatException e) {
                        // Do nothing
                    }
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe("" + ConfigManager.getInstance().getConfig().visionPort, 0.1, 20, 160, 1,
                        setVisionPort, EnumVisibleType.NETWORK).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(lc0.getWidth()));

        List<Integer> activeIds = MathHelper.toList(ConfigManager.getInstance().getConfig().radioTypes);
        for (int i = 0; i < EnumRadioType.values().length; i++) {
            EnumRadioType target = EnumRadioType.values()[i];
            int finalI = i;
            IFuncParam1<Void, Boolean> onCheck = new IFuncParam1<Void, Boolean>() {
                @Override
                @Nonnull
                public Void function(Boolean aBoolean) {
                    ConfigBuffers.getInstance().setRadioActive(finalI, aBoolean);
                    return null;
                }
            };
            components.add(new CheckBoxCafe(target.getName(), activeIds.contains(i),
                    0.1 + 0.6 / EnumRadioType.values().length * i, 70, 60, 40, onCheck,
                    EnumVisibleType.NETWORK).setDisplayType(0, EnumDisplayType.RATIO));
        }
        IFuncParam1<Void, Boolean> onSwitchActiveRadioApply = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                ConfigBuffers.getInstance().sendRadiosToConfig();
                return null;
            }
        };
        components.add(new ButtonCafe("Apply", 120, 70, 90, 30, onSwitchActiveRadioApply,
                EnumVisibleType.NETWORK).setDisplayType(0, EnumDisplayType.FIT));
    }

    private static void setComponentInformation(@Nonnull List<Component> components) {
        components.add(
                new LabelCafe("Blue", 0.1, 20, EnumVisibleType.INFORMATION).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new LabelCafe("YellowCard", 0.1, 30, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYb = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int y;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty()) y = 0;
                else y = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCards();
                return String.valueOf(y);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 30, onYb, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb0 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .isEmpty()) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(0) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.1, 40, onYTb0, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb1 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .size() < 2) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(1) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.2, 40, onYTb1, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb2 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .size() < 3) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(2) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 40, onYTb2, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("RedCard", 0.1, 50, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onRb = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int r;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty()) r = 0;
                else r = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getRedCards();
                return String.valueOf(r);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 50, onRb, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("Yellow", 0.5, 20, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("YellowCard", 0.5, 30, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYy = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int y;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty()) y = 0;
                else y = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get().getYellowCards();

                //LogManager.getLogger().info(y);
                return String.valueOf(y);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 30, onYy, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy0 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().isEmpty()) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(0) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.5, 40, onYTy0, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy1 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().size() < 2) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(1) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.6, 40, onYTy1, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy2 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().size() < 3) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(2) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 40, onYTy2, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("RedCard", 0.5, 50, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onRy = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int r;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty()) r = 0;
                else r = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get().getRedCards();
                return String.valueOf(r);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 50, onRy, EnumVisibleType.INFORMATION).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onTeamInfo = new IFuncParam1<String, String>() {
            @Override
            public String function(String s) {
                return UpdaterRefBox.getInstance().getGcInfo().get();
            }
        };
        components.add(new ScrollPaneCafe<>(
                new StaticTextAreaCafe(40, 70, 0.8, 80, onTeamInfo, EnumVisibleType.INFORMATION).setDisplayType(2,
                        EnumDisplayType.RATIO)));
    }

    private static void setComponentStats(@Nonnull List<Component> components) {
        IFuncParam1<Void, Boolean> onSwitchReset = new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean aBoolean) {
                        UpdaterStats.reset();
                        return null;
                    }
                };
        components.add(new ButtonCafe("RESET", 10, 10, 80, 20, onSwitchReset, EnumVisibleType.STATS));
        components.add(new GraphicalAreaCafe(150, 10, 50, 300, EnumVisibleType.STATS,
                new RectCafe(0, 0, 48, 300, new Color(0, 100, 255, 60))));
        components.add(new GraphicalAreaCafe(200, 10, 50, 300, EnumVisibleType.STATS,
                new RectCafe(0, 0, 48, 300, new Color(255, 255, 0, 60))));
        components.add(new LabelCafe("BLUE", 152, 10, EnumVisibleType.STATS));
        components.add(new LabelCafe("YELLOW", 200, 10, EnumVisibleType.STATS));

        components.add(new LabelCafe("キック : ", 40, 35, EnumVisibleType.STATS));
        components.add(new LabelCafe("パス : ", 40, 60, EnumVisibleType.STATS));
        components.add(new LabelCafe("パスカット : ", 40, 85, EnumVisibleType.STATS));
        components.add(new LabelCafe("パスカット%: ", 40, 110, EnumVisibleType.STATS));
        components.add(new LabelCafe("シュート: ", 40, 135, EnumVisibleType.STATS));
        components.add(new LabelCafe("枠内シュート: ", 40, 160, EnumVisibleType.STATS));
        components.add(new LabelCafe("枠内シュート%: ", 40, 185, EnumVisibleType.STATS));
        components.add(new LabelCafe("累積ゴール期待値: ", 40, 210, EnumVisibleType.STATS));
        components.add(new LabelCafe("ボール保持率: ", 40, 235, EnumVisibleType.STATS));

        components.add(new DynamicLabelCafe("blue_kick_count", 152, 35,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getKickCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_kick_count", 200, 35,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getKickCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_pass_count", 152, 60,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_pass_count", 200, 60,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_pass_cut_count", 152, 85,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCutCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_pass_cut_count", 200, 85,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCutCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_pass_cut_rate", 152, 110,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int passcut = UpdaterStats.getInstance().getPassCutCount(TeamColor.BLUE);
                        int pass = UpdaterStats.getInstance().getPassCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", (pass + passcut == 0 ? 0 : (100.0 * passcut / (pass + passcut))));
                    };
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_pass_cut_rate", 200, 110,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int passcut = UpdaterStats.getInstance().getPassCutCount(TeamColor.YELLOW);
                        int pass = UpdaterStats.getInstance().getPassCount(TeamColor.BLUE);
                        return String.format("%1$.1f", (pass + passcut == 0 ? 0 : (100.0 * passcut / (pass + passcut))));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_shoot_count", 152, 135,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_shoot_count", 200, 135,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_shoot_in_frame_count", 152, 160,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_shoot_in_frame_count", 200, 160,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_shoot_in_frame_rate", 152, 185,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int shoot = UpdaterStats.getInstance().getShootCount(TeamColor.BLUE);
                        int shootInFrame = UpdaterStats.getInstance().getShootInFrameCount(TeamColor.BLUE);
                        return String.format("%1$.1f", (shoot == 0 ? 0 : (100.0 * shootInFrame / shoot)));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_shoot_in_frame_rate", 200, 185,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int shoot = UpdaterStats.getInstance().getShootCount(TeamColor.YELLOW);
                        int shootInFrame = UpdaterStats.getInstance().getShootInFrameCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", (shoot == 0 ? 0 : (100.0 * shootInFrame / shoot)));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_shoot_XG_in_game", 152, 210,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameXG(TeamColor.BLUE));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_shoot_XG_in_game", 200, 210,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameXG(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("blue_possession_rate", 152, 235,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int bluePosCount = UpdaterStats.getInstance().getBallPossessionCount(TeamColor.BLUE);
                        int yellowPosCount = UpdaterStats.getInstance().getBallPossessionCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", (100.0 * bluePosCount / (bluePosCount + yellowPosCount)));
                    }
                }, EnumVisibleType.STATS));
        components.add(new DynamicLabelCafe("yellow_possession_rate", 200, 235,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int bluePosCount = UpdaterStats.getInstance().getBallPossessionCount(TeamColor.BLUE);
                        int yellowPosCount = UpdaterStats.getInstance().getBallPossessionCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", 100.0 * yellowPosCount / (bluePosCount + yellowPosCount));
                    }
                }, EnumVisibleType.STATS));
    }

    private static void setComponentAnalyze(@Nonnull List<Component> components) {
        components.add(
                new LabelCafe("Blue", 0.1, 20, EnumVisibleType.GAME_ANALYZE).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new LabelCafe("YellowCard", 0.1, 30, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYb = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int y;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty()) y = 0;
                else y = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCards();
                return String.valueOf(y);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 30, onYb, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb0 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .isEmpty()) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(0) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.1, 40, onYTb0, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb1 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .size() < 2) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(1) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.2, 40, onYTb1, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTb2 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimesList()
                                .size() < 3) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getYellowCardTimes(2) /
                        1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 40, onYTb2, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("RedCard", 0.1, 50, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onRb = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int r;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().isEmpty()) r = 0;
                else r = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.BLUE).getInfo().get().getRedCards();
                return String.valueOf(r);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.3, 50, onRb, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("Yellow", 0.5, 20, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("YellowCard", 0.5, 30, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYy = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int y;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty()) y = 0;
                else y = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get().getYellowCards();
                return String.valueOf(y);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 30, onYy, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy0 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().isEmpty()) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(0) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.5, 40, onYTy0, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy1 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().size() < 2) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(1) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.6, 40, onYTy1, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onYTy2 = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int t;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty() ||
                        UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                                .getYellowCardTimesList().size() < 3) t = 0;
                else t = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get()
                        .getYellowCardTimes(2) / 1000000;
                return String.valueOf(t);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 40, onYTy2, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new LabelCafe("RedCard", 0.5, 50, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onRy = new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                int r;
                if (UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().isEmpty()) r = 0;
                else r = UpdaterRefBox.getInstance().getTeamInfo(TeamColor.YELLOW).getInfo().get().getRedCards();
                return String.valueOf(r);
            }
        };
        components.add(new DynamicLabelCafe("0", 0.7, 50, onRy, EnumVisibleType.GAME_ANALYZE).setDisplayType(0,
                EnumDisplayType.RATIO));
        IFuncParam1<String, String> onTeamInfo = new IFuncParam1<String, String>() {
            @Override
            public String function(String s) {
                return UpdaterRefBox.getInstance().getGcInfo().get();
            }
        };
        components.add(new ScrollPaneCafe<>(
                new StaticTextAreaCafe(40, 70, 0.8, 80, onTeamInfo, EnumVisibleType.GAME_ANALYZE).setDisplayType(2,
                        EnumDisplayType.RATIO)));
        components.add(new LabelCafe("BLUE", 152, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("YELLOW", 200, 160, EnumVisibleType.GAME_ANALYZE));

        components.add(new LabelCafe("キック : ", 40, 195, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("パス : ", 40, 220, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("パスカット : ", 40, 245, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("パスカット%: ", 40, 270, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("シュート: ", 40, 295, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("枠内シュート: ", 40, 320, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("枠内シュート%: ", 40, 345, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("累積ゴール期待値: ", 40, 370, EnumVisibleType.GAME_ANALYZE));

        components.add(new DynamicLabelCafe("blue_kick_count", 152, 195,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getKickCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_kick_count", 200, 195,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getKickCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_pass_count", 152, 220,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_pass_count", 200, 220,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_pass_cut_count", 152, 245,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCutCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_pass_cut_count", 200, 245,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getPassCutCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_pass_cut_rate", 152, 270,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int passcut = UpdaterStats.getInstance().getPassCutCount(TeamColor.BLUE);
                        int pass = UpdaterStats.getInstance().getPassCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", (pass + passcut == 0 ? 0 : (100.0 * passcut / (pass + passcut))));
                    };
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_pass_cut_rate", 200, 270,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int passcut = UpdaterStats.getInstance().getPassCutCount(TeamColor.YELLOW);
                        int pass = UpdaterStats.getInstance().getPassCount(TeamColor.BLUE);
                        return String.format("%1$.1f", (pass + passcut == 0 ? 0 : (100.0 * passcut / (pass + passcut))));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_shoot_count", 152, 295,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_shoot_count", 200, 295,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_shoot_in_frame_count", 152, 320,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameCount(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_shoot_in_frame_count", 200, 320,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameCount(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_shoot_in_frame_rate", 152, 345,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int shoot = UpdaterStats.getInstance().getShootCount(TeamColor.BLUE);
                        int shootInFrame = UpdaterStats.getInstance().getShootInFrameCount(TeamColor.BLUE);
                        return String.format("%1$.1f", (shoot == 0 ? 0 : (100.0 * shootInFrame / shoot)));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_shoot_in_frame_rate", 200, 345,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        int shoot = UpdaterStats.getInstance().getShootCount(TeamColor.YELLOW);
                        int shootInFrame = UpdaterStats.getInstance().getShootInFrameCount(TeamColor.YELLOW);
                        return String.format("%1$.1f", (shoot == 0 ? 0 : (100.0 * shootInFrame / shoot)));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("blue_shoot_XG_in_game", 152, 370,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameXG(TeamColor.BLUE));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        components.add(new DynamicLabelCafe("yellow_shoot_XG_in_game", 200, 370,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        return String.valueOf(UpdaterStats.getInstance().getShootInFrameXG(TeamColor.YELLOW));
                    }
                }, EnumVisibleType.GAME_ANALYZE));
        //ゴール期待値
        components.add(new LabelCafe("BLUE", 265, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("A", 335, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("B", 375, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("C", 415, 160, EnumVisibleType.GAME_ANALYZE));

        int MaxVisibleRobot = 13;
        for(int i=0;i<MaxVisibleRobot;i++){//blueXG
            final int id = i;  // i を final 変数に格納
            components.add(new LabelCafe("id :"+i, 270, 195+25*i, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("blue_XG", 330, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.BLUE,id, XGType.TYPEA));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("blue_XG", 370, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.BLUE,id,XGType.TYPEB));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("blue_XG", 410, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.BLUE,id, XGType.TYPEC));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
        }

        components.add(new LabelCafe("YELLOW", 460, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("A", 535, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("B", 575, 160, EnumVisibleType.GAME_ANALYZE));
        components.add(new LabelCafe("C", 615, 160, EnumVisibleType.GAME_ANALYZE));

        for(int i=0;i<MaxVisibleRobot;i++){//yellowXG
            final int id = i;  // i を final 変数に格納
            components.add(new LabelCafe("id :"+i, 470, 195+25*i, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("yellow_XG", 530, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.YELLOW,id,XGType.TYPEA));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("yellow_XG", 570, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.YELLOW,id,XGType.TYPEB));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
            components.add(new DynamicLabelCafe("yellow_XG", 610, 195+25*i,
                    new IFuncParam1<String, String>() {
                        @Override
                        public String function(String s) {
                            return String.valueOf(UpdaterStrategy.getInstance().getXG(TeamColor.YELLOW,id,XGType.TYPEC));
                        }
                    }, EnumVisibleType.GAME_ANALYZE));
        }
    }

    public static void setComponentDemo(@Nonnull List<Component> components) {
        Config config = ConfigManager.getInstance().getConfig();
        double x = 0.15;

        // Demo Difficulty ///////////////////////////////////////////
        components.add(new LabelCafe("Demo Difficulty", 0.1, 40, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO));
        if (config.demo.enable){
            Integer[] demo_difficulty = {0,1,2};
            components.add(new ComboBoxCafe<>(demo_difficulty, config.demo.difficulty, 0.4, 35, 0.2, 30,
                    new IFuncParam1<Void, Integer>() {
                        @Override
                        @Nullable
                        public Void function(@Nonnull Integer difficulty) {
                            ConfigManager.getInstance().getEditableConfig().demo.difficulty = difficulty;
                            ConfigManager.getInstance().markDirty();
                            return null;
                        }
                    }, EnumVisibleType.DEMO)
                    .setDisplayType(0, EnumDisplayType.RATIO)
                    .setDisplayType(2, EnumDisplayType.RATIO));
        } else {
            components.add(new LabelCafe("N/A", 0.4, 40, EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO));
            components.add(new ButtonCafe("demo有効化", 0.5,35, ((1 - x * 2) / 2 - 0.02), 25,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            ConfigManager.getInstance().getEditableConfig().demo.enable = true;
                            ConfigManager.getInstance().markDirty();
                            Main.exit(0,true);
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));

        }


        // ScoreBoard ////////////////////////////////////////////////
        int scy = 130;
        double pm_btn_width = 0.12; // +,-ボタンの幅
        Font font_score = new Font(Font.SANS_SERIF, Font.BOLD, 18);

        LabelCafe  scoreboard_label = new LabelCafe("ScoreBoard________", 0.1, scy - 35, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO);
        scoreboard_label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        components.add(scoreboard_label);

        if (config.useScoreBoard){
            // WebUIを開くボタン
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){

                ButtonCafe webUI_btn =  new ButtonCafe("WebUI", x + 0.25, scy - 35 , 0.15, 15,
                        new IFuncParam1<Void, Boolean>() {
                            @Override
                            @Nullable
                            public Void function(Boolean aBoolean) {
                                try {
                                    Desktop.getDesktop().browse(new URI("http://localhost:" + ConfigManager.getInstance().getConfig().scoreBoardPort));
                                } catch (IOException | URISyntaxException e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        }, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO);
                webUI_btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                webUI_btn.setForeground(ColorHelper.MEMORY_BLACK);
                ButtonCafe QR_btn =  new ButtonCafe("QR", x + 0.42, scy - 35 , 0.15, 15,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            try {
                                Desktop.getDesktop().browse(new URI("http://localhost:" + ConfigManager.getInstance().getConfig().scoreBoardPort + "/qr"));
                            } catch (IOException | URISyntaxException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    }, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO);
                QR_btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));

                components.add(webUI_btn);
                components.add(QR_btn);
            }


            // YELLOW
            components.add(new LabelCafe("Yellow", x, scy + 5, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO));
            components.add(new ButtonCafe("+",x + 0.2, scy,pm_btn_width,25,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().addScoreYellow(1);
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
            DynamicLabelCafe yellowScoreLabel = new DynamicLabelCafe("0", x + 0.395, scy + 5, new IFuncParam1<String, String>() {
                @Override
                @Nonnull
                public String function(String s) {
                    // UpdaterScoreBoardから得点を取得する
                    int score = UpdaterScoreBoard.getInstance().getScoreYellow();
                    return String.valueOf(score);
                }
            }, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO);
            yellowScoreLabel.setFont(font_score);
            components.add(yellowScoreLabel);
            components.add(new ButtonCafe("-",x + 0.5, scy,pm_btn_width,25,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().addScoreYellow(-1);
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    }, EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));


            // BLUE
            components.add(new LabelCafe("Blue", x, scy + 45, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO));
            components.add(new ButtonCafe("+",x + 0.2, scy + 40,pm_btn_width,25,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().addScoreBlue(1);
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
            DynamicLabelCafe blueScoreLabel = new DynamicLabelCafe("0", x + 0.395, scy + 45, new IFuncParam1<String, String>() {
                @Override
                @Nonnull
                public String function(String s) {
                    // UpdaterScoreBoardから得点を取得する
                    int score = UpdaterScoreBoard.getInstance().getScoreBlue();
                    return String.valueOf(score);
                }
            }, EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO);
            blueScoreLabel.setFont(font_score);
            components.add(blueScoreLabel);
            components.add(new ButtonCafe("-",x + 0.5, scy + 40,pm_btn_width,25,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().addScoreBlue(-1);
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));

            // Reset, Start

            components.add(new ButtonCafe("Reset Game", x, scy+83, ((1 - x * 2) / 2 - 0.02), 30,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().reset();
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO)
                    .setDisplayType(2, EnumDisplayType.RATIO));
            components.add(new ButtonCafe("Start Game", 0.5, scy+83, ((1 - x * 2) / 2 - 0.02), 30,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            UpdaterScoreBoard.getInstance().startGame();
                            if (UpdaterScoreBoard.getInstance().isDirty()) {
                                for (UpdaterScoreBoard.ScoreUpdateListener listener : UpdaterScoreBoard.getInstance().getListeners().get()) {
                                    listener.onScoreUpdate();
                                }
                                UpdaterScoreBoard.getInstance().resetDirty();
                            }
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0, EnumDisplayType.RATIO)
                    .setDisplayType(2, EnumDisplayType.RATIO));
        } else {
            components.add(new LabelCafe("得点板は無効になっています", x, scy,EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO));
            components.add(new LabelCafe("有効化するにはconfig.useScoreBoardをtrueに設定", x, scy + 20,EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO));
            components.add(new ButtonCafe("得点板を有効化する", x, scy + 40, ((1 - x * 2) / 2 - 0.02), 30,
                    new IFuncParam1<Void, Boolean>() {
                        @Override
                        @Nullable
                        public Void function(Boolean aBoolean) {
                            ConfigManager.getInstance().getEditableConfig().useScoreBoard = true;
                            ConfigManager.getInstance().markDirty();
                            Main.exit(0,true);
                            return null;
                        }
                    },
                    EnumVisibleType.DEMO).setDisplayType(0,EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        }

    }

    private static void setComponentDribbleState(@Nonnull List<Component> components) {
        IFuncParam1<Void, Boolean> setIsBack = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.isBack = s;
                }
                return null;
            }
        };
        SwitchBoxCafe isBackSwitch = new SwitchBoxCafe(
                "backDribble", 0.35, ConfigManager.getInstance().getConfig().dribbleState.isBack,
                20, 0.1, 10, // スイッチの位置
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE,
                setIsBack,
                EnumVisibleType.DRIBBLESTATE
        ).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO);
        components.add(isBackSwitch);
        LabelCafe lc0 = new LabelCafe("omega : ", 0.1, 40, EnumVisibleType.DRIBBLESTATE).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc0);
        IFuncParam1<Void, String> setOmega = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.omega = Double.valueOf(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(String.valueOf(ConfigManager.getInstance().getConfig().dribbleState.omega), 0.2, 40, 160, 1,
                        setOmega, EnumVisibleType.DRIBBLESTATE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(100));
        LabelCafe lc1 = new LabelCafe("forwardVelocityMax :", 0.1, lc0.getY() + 20, EnumVisibleType.DRIBBLESTATE).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc1);
        IFuncParam1<Void, String> setForwardVelocityMax = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.forwardVelocityMax = Double.valueOf(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(String.valueOf(ConfigManager.getInstance().getConfig().dribbleState.forwardVelocityMax), 0.2, lc1.getY(), 160, 1,
                        setForwardVelocityMax, EnumVisibleType.DRIBBLESTATE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(100));
        LabelCafe lc2 = new LabelCafe("backVelocityMax :", 0.1, lc1.getY() + 20, EnumVisibleType.DRIBBLESTATE).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc2);
        IFuncParam1<Void, String> setBackVelocityMax = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.backVelocityMax = Double.valueOf(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(String.valueOf(ConfigManager.getInstance().getConfig().dribbleState.forwardVelocityMax), 0.2, lc2.getY(), 160, 1,
                        setBackVelocityMax, EnumVisibleType.DRIBBLESTATE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(100));
        LabelCafe lc3 = new LabelCafe("forwardAcc : ", 0.1, lc2.getY() + 20, EnumVisibleType.DRIBBLESTATE).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc3);
        IFuncParam1<Void, String> setForwardAcc = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.forwardAcc = Double.valueOf(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(String.valueOf(ConfigManager.getInstance().getConfig().dribbleState.forwardAcc), 0.2, lc3.getY(), 160, 1,
                        setForwardAcc, EnumVisibleType.DRIBBLESTATE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(100));
        LabelCafe lc4 = new LabelCafe("backAcc : ", 0.1, lc3.getY() + 20, EnumVisibleType.DRIBBLESTATE).setDisplayType(0,
                EnumDisplayType.RATIO);
        components.add(lc4);
        IFuncParam1<Void, String> setBackAcc = new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s != null) {
                    ConfigManager.getInstance().getEditableConfig().dribbleState.backAcc = Double.valueOf(s);
                }
                return null;
            }
        };
        components.add(
                new TextAreaCafe(String.valueOf(ConfigManager.getInstance().getConfig().dribbleState.backAcc), 0.2, lc4.getY(), 160, 1,
                        setBackAcc, EnumVisibleType.DRIBBLESTATE).setDisplayType(0, EnumDisplayType.RATIO)
                        .setOffset(100));
    }

    /**
     * ドリブル強化学習 (パス・シュート) のデータ収集タブ
     * 報酬の重みなどここに無い設定は Config Editor の rlDribble から変更する
     */
    private static void setComponentRLDribble(@Nonnull List<Component> components) {
        final EnumVisibleType tab = EnumVisibleType.RL_DRIBBLE;
        final Config.RLDribble cfg = ConfigManager.getInstance().getConfig().rlDribble;

        components.add(new ButtonCafe("Start", 0.05, 5, 0.2, 22, new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                RLDribbleSession.getInstance().start();
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new ButtonCafe("Stop", 0.27, 5, 0.2, 22, new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                RLDribbleSession.getInstance().stop();
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        // 実機では1回終わるごとに止まるので、ボールやロボットを確かめてから押して次へ進める
        components.add(new ButtonCafe("Resume", 0.76, 5, 0.2, 22, new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                RLDribbleSession.getInstance().resume();
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new SwitchBoxCafe("save data", 0.4, cfg.saveData, 20, 0.52, 77,
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean arg) {
                Config.RLDribble editable = ConfigManager.getInstance().getEditableConfig().rlDribble;
                if (editable.saveData != arg) {
                    editable.saveData = arg;
                    ConfigManager.getInstance().markDirty();
                }
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));

        components.add(new DynamicLabelCafe("idle", 0.05, 35, new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                return "status : " + RLDribbleSession.getInstance().getStatusText();
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new DynamicLabelCafe("-", 0.05, 55, new IFuncParam1<String, String>() {
            @Override
            @Nonnull
            public String function(String s) {
                return "output : " + RLDribbleSession.getInstance().getOutputText();
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO));

        components.add(new LabelCafe("action source :", 0.05, 80, tab).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new ComboBoxCafe<>(EnumRLActionSource.values(), cfg.actionSource, 0.35, 76, 0.35, 22,
                new IFuncParam1<Void, EnumRLActionSource>() {
                    @Override
                    @Nullable
                    public Void function(@Nonnull EnumRLActionSource arg) {
                        Config.RLDribble editable = ConfigManager.getInstance().getEditableConfig().rlDribble;
                        if (editable.actionSource != arg) {
                            editable.actionSource = arg;
                            ConfigManager.getInstance().markDirty();
                        }
                        return null;
                    }
                }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new SwitchBoxCafe("draw", 0.25, cfg.drawScenario, 20, 0.73, 77,
                ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean arg) {
                Config.RLDribble editable = ConfigManager.getInstance().getEditableConfig().rlDribble;
                if (editable.drawScenario != arg) {
                    editable.drawScenario = arg;
                    ConfigManager.getInstance().markDirty();
                }
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));

        // 数値設定 (2列)
        final double left = 0.05;
        final double right = 0.52;
        final int top = 104;
        final int pitch = 19;
        addRLDribbleField(components, "episodes", left, top, true,
                () -> rlConfig().targetEpisodes, v -> rlConfig().targetEpisodes = (int) v);
        addRLDribbleField(components, "robot id", left, top + pitch, true,
                () -> rlConfig().robotId, v -> rlConfig().robotId = (int) v);
        addRLDribbleField(components, "max time [s]", left, top + pitch * 2, false,
                () -> rlConfig().maxEpisodeTime, v -> rlConfig().maxEpisodeTime = v);
        addRLDribbleField(components, "settle [s]", left, top + pitch * 3, false,
                () -> rlConfig().settleTime, v -> rlConfig().settleTime = v);
        addRLDribbleField(components, "ball start", left, top + pitch * 4, false,
                () -> rlConfig().ballStartDistance, v -> rlConfig().ballStartDistance = v);
        addRLDribbleField(components, "shoot ratio", left, top + pitch * 5, false,
                () -> rlConfig().shootRatio, v -> rlConfig().shootRatio = v);
        addRLDribbleField(components, "pass min", left, top + pitch * 6, false,
                () -> rlConfig().passMinDist, v -> rlConfig().passMinDist = v);
        addRLDribbleField(components, "pass max", left, top + pitch * 7, false,
                () -> rlConfig().passMaxDist, v -> rlConfig().passMaxDist = v);
        addRLDribbleField(components, "shoot max", left, top + pitch * 8, false,
                () -> rlConfig().shootMaxDist, v -> rlConfig().shootMaxDist = v);
        addRLDribbleField(components, "pass radius", right, top, false,
                () -> rlConfig().passSuccessRadius, v -> rlConfig().passSuccessRadius = v);
        addRLDribbleField(components, "shoot speed", right, top + pitch, false,
                () -> rlConfig().shootSpeed, v -> rlConfig().shootSpeed = v);
        addRLDribbleField(components, "kick hold [frm]", right, top + pitch * 2, true,
                () -> rlConfig().kickHoldTime, v -> rlConfig().kickHoldTime = (int) v);
        addRLDribbleField(components, "dribble limit", right, top + pitch * 3, false,
                () -> rlConfig().dribbleLimit, v -> rlConfig().dribbleLimit = v);
        addRLDribbleField(components, "lost distance", right, top + pitch * 4, false,
                () -> rlConfig().lostDistance, v -> rlConfig().lostDistance = v);
        addRLDribbleField(components, "noise vel std", right, top + pitch * 5, false,
                () -> rlConfig().noiseVelStd, v -> rlConfig().noiseVelStd = v);
        addRLDribbleField(components, "noise omega std", right, top + pitch * 6, false,
                () -> rlConfig().noiseOmegaStd, v -> rlConfig().noiseOmegaStd = v);
        addRLDribbleField(components, "seed (0:time)", right, top + pitch * 7, true,
                () -> rlConfig().seed, v -> rlConfig().seed = (long) v);
        addRLDribbleField(components, "reset pause [s]", right, top + pitch * 8, false,
                () -> rlConfig().resetPause, v -> rlConfig().resetPause = v);

        // 方策 (actionSourceがPOLICY系のときに使うONNXのパス)
        components.add(new LabelCafe("policy (.onnx) :", left, top + pitch * 9, tab).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new TextAreaCafe(cfg.policyPath, left, top + pitch * 9, 300, 1, new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s == null)
                    return null;
                Config.RLDribble editable = ConfigManager.getInstance().getEditableConfig().rlDribble;
                if (!s.trim().equals(editable.policyPath)) {
                    editable.policyPath = s.trim();
                    ConfigManager.getInstance().markDirty();
                }
                return null;
            }
        }, tab).setDisplayType(0, EnumDisplayType.RATIO).setOffset(115));

        // 環境 (シミュレータ / 実機) と初期配置の作り方 (テレポート / ボール位置から)
        final int envTop = top + pitch * 10 + 4;
        components.add(new LabelCafe("environment :", left, envTop + 4, tab).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new ComboBoxCafe<>(EnumRLEnvironment.values(), cfg.environment, 0.25, envTop, 0.22, 22,
                new IFuncParam1<Void, EnumRLEnvironment>() {
                    @Override
                    @Nullable
                    public Void function(@Nonnull EnumRLEnvironment arg) {
                        if (rlConfig().environment != arg) {
                            rlConfig().environment = arg;
                            ConfigManager.getInstance().markDirty();
                        }
                        return null;
                    }
                }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        components.add(new LabelCafe("reset :", right, envTop + 4, tab).setDisplayType(0, EnumDisplayType.RATIO));
        components.add(new ComboBoxCafe<>(EnumRLResetMode.values(), cfg.resetMode, 0.66, envTop, 0.25, 22,
                new IFuncParam1<Void, EnumRLResetMode>() {
                    @Override
                    @Nullable
                    public Void function(@Nonnull EnumRLResetMode arg) {
                        if (rlConfig().resetMode != arg) {
                            rlConfig().resetMode = arg;
                            ConfigManager.getInstance().markDirty();
                        }
                        return null;
                    }
                }, tab).setDisplayType(0, EnumDisplayType.RATIO).setDisplayType(2, EnumDisplayType.RATIO));
        addRLDribbleField(components, "approach limit [s]", left, envTop + 28, false,
                () -> rlConfig().approachTimeout, v -> rlConfig().approachTimeout = v);
    }

    @Nonnull
    private static Config.RLDribble rlConfig() {
        return ConfigManager.getInstance().getEditableConfig().rlDribble;
    }

    /**
     * ラベルと数値入力欄を1組追加する
     * 不正な入力は無視し、値が変わったときだけconfigをdirtyにする
     */
    private static void addRLDribbleField(@Nonnull List<Component> components, @Nonnull String label, double x, int y,
                                          boolean isInteger, @Nonnull DoubleSupplier getter, @Nonnull DoubleConsumer setter) {
        components.add(new LabelCafe(label + " :", x, y, EnumVisibleType.RL_DRIBBLE).setDisplayType(0, EnumDisplayType.RATIO));
        final double init = getter.getAsDouble();
        final String initText = isInteger ? String.valueOf((long) init) : String.valueOf(init);
        components.add(new TextAreaCafe(initText, x, y, 70, 1, new IFuncParam1<Void, String>() {
            @Override
            @Nullable
            public Void function(String s) {
                if (s == null)
                    return null;
                try {
                    final double value = isInteger ? Long.parseLong(s.trim()) : Double.parseDouble(s.trim());
                    if (value != getter.getAsDouble()) {
                        setter.accept(value);
                        ConfigManager.getInstance().markDirty();
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("invalid value for {} : {}", label, s);
                }
                return null;
            }
        }, EnumVisibleType.RL_DRIBBLE).setDisplayType(0, EnumDisplayType.RATIO).setOffset(115));
    }

    private static void setComponentLog(@Nonnull List<Component> components) {
        IFuncParam1<Void, EnumLogLevel> onSwitchLevel = new IFuncParam1<Void, EnumLogLevel>() {
            @Override
            @Nullable
            public Void function(@Nonnull EnumLogLevel arg) {
                ConfigManager.getInstance().setLogLevel(arg);
                ConfigManager.getInstance().markDirty();
                return null;
            }
        };
        double offset = 10;
        components.add(
                new ComboBoxCafe<>(EnumLogLevel.values(), ConfigManager.getInstance().getLogLevel(), 0.1, offset - 3,
                        0.35, 30, onSwitchLevel, EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<Void, Boolean> onSwitchFreeze = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(@Nonnull Boolean aBoolean) {
                ConfigManager.getInstance().setLogViewFreeze(aBoolean);
                return null;
            }
        };
        components.add(
                new SwitchBoxCafe("Freeze Log", 0.35, ConfigManager.getInstance().isLogViewFreeze(), 20, 0.55, offset,
                        ColorHelper.SWITCH_GRAY, ColorHelper.ARCHIVE_SKY, ColorHelper.LINE_WHITE, onSwitchFreeze,
                        EnumVisibleType.ALWAYS).setDisplayType(0, EnumDisplayType.RATIO)
                        .setDisplayType(2, EnumDisplayType.RATIO));
        IFuncParam1<String, String> onUpdateLog = new IFuncParam1<String, String>() {
            @Override
            public String function(String s) {
                if (ConfigManager.getInstance().isLogViewFreeze()) return s;
                return ConfigManager.getInstance().getLogLevel()
                        .splitLogger(UpdaterLogEvent.getInstance().getOutput().get().toString(), 2000);
            }
        };
        components.add(new ScrollPaneCafe<>(
                new StaticTextAreaCafe(10, offset + 30, 10, 10, onUpdateLog, EnumVisibleType.ALWAYS).setDisplayType(2,
                        EnumDisplayType.FIT).setDisplayType(3, EnumDisplayType.FIT)));
    }

    private static void setComponentObstacleView(@Nonnull List<Component> components) {
        Config config = ConfigManager.getInstance().getConfig();
        List<Integer> yellowObstacles = MathHelper.toList(config.visibility.yellowObstacle);
        List<Integer> blueObstacles = MathHelper.toList(config.visibility.blueObstacle);
        List<CheckBoxCafe> yellowBoxes = new ArrayList<>();
        List<CheckBoxCafe> blueBoxes = new ArrayList<>();
        double x = 0.1;
        int y = 50;
        double width = 0.1;
        int widthI = 40;
        int height = 30;
        for (int id = 0; id < ConfigManager.MAX_ROBOTS; id++) {
            final int finalId = id;
            IFuncParam1<Void, Boolean> onCheckYellow = new IFuncParam1<Void, Boolean>() {
                @Override
                @Nullable
                public Void function(Boolean aBoolean) {
                    ConfigBuffers.getInstance().setYellowObstacle(finalId, aBoolean);
                    return null;
                }
            };
            yellowBoxes.add(new CheckBoxCafe(String.valueOf(id), yellowObstacles.contains(id), x + (id % 8) * width,
                    y + (double) (id / 8) * height, widthI, height, onCheckYellow, EnumVisibleType.OBSTACLES).setDisplayType(
                    0, EnumDisplayType.RATIO));
            IFuncParam1<Void, Boolean> onCheckBlue = new IFuncParam1<Void, Boolean>() {
                @Override
                @Nullable
                public Void function(Boolean aBoolean) {
                    ConfigBuffers.getInstance().setBlueObstacle(finalId, aBoolean);
                    return null;
                }
            };
            blueBoxes.add(new CheckBoxCafe(String.valueOf(id), blueObstacles.contains(id), x + (id % 8) * width,
                    y + (double) (id / 8) * height + height * 4, widthI, height, onCheckBlue,
                    EnumVisibleType.OBSTACLES).setDisplayType(0, EnumDisplayType.RATIO));
        }
        components.addAll(yellowBoxes);
        components.addAll(blueBoxes);
        IFuncParam1<Void, Boolean> onSwitchYellowObstacleApply = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                ConfigBuffers.getInstance().sendYellowObstacleToConfig();
                return null;
            }
        };
        components.add(new LabelCafe("Yellow Obstacles : ", 0.1, 25, EnumVisibleType.OBSTACLES).setDisplayType(0,
                EnumDisplayType.RATIO));
        components.add(new ButtonCafe("Apply", 170, 15, 80, 30, onSwitchYellowObstacleApply,
                EnumVisibleType.OBSTACLES).setDisplayType(0, EnumDisplayType.FIT));
        IFuncParam1<Void, Boolean> onSwitchYellowAllObstacle = new IFuncParam1<Void, Boolean>() {
            @Override
            public Void function(Boolean value) {
                for (CheckBoxCafe checkBoxCafe : yellowBoxes) {
                    checkBoxCafe.setSelected(!value);
                    checkBoxCafe.onCheck.function(!value);
                }
                return null;
            }
        };
        components.add(new ButtonCafe("All", 80, 15, 80, 30, onSwitchYellowAllObstacle,
                EnumVisibleType.OBSTACLES).setDisplayType(0, EnumDisplayType.FIT));

        IFuncParam1<Void, Boolean> onSwitchBlueObstacleApply = new IFuncParam1<Void, Boolean>() {
            @Override
            @Nullable
            public Void function(Boolean aBoolean) {
                ConfigBuffers.getInstance().sendBlueObstacleToConfig();
                return null;
            }
        };
        components.add(
                new LabelCafe("Blue Obstacles : ", 0.1, 25 + height * 4, EnumVisibleType.OBSTACLES).setDisplayType(0,
                        EnumDisplayType.RATIO));
        components.add(new ButtonCafe("Apply", 170, 15 + height * 4, 80, 30, onSwitchBlueObstacleApply,
                EnumVisibleType.OBSTACLES).setDisplayType(0, EnumDisplayType.FIT));
        IFuncParam1<Void, Boolean> onSwitchBlueAllObstacle = new IFuncParam1<Void, Boolean>() {
            @Override
            public Void function(Boolean value) {
                for (CheckBoxCafe checkBoxCafe : blueBoxes) {
                    checkBoxCafe.setSelected(!value);
                    checkBoxCafe.onCheck.function(!value);
                }
                return null;
            }
        };
        components.add(new ButtonCafe("All", 80, 15 + height * 4, 80, 30, onSwitchBlueAllObstacle,
                EnumVisibleType.OBSTACLES).setDisplayType(0, EnumDisplayType.FIT));
    }

    private static void setComponentClipTab(@Nonnull List<Component> components) {
        // 切り取りキャンセルボタン
        final ButtonCafe clipCancelButton = new ButtonCafe("cancel", 0.1, 90, 0.3, 30,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        UpdaterReplay.getInstance().clipClose();
                        return null;
                    }
                }, EnumVisibleType.CLIP);
        // 録画切り取り実行ボタン
        final ButtonCafe clipButton = new ButtonCafe("clip", 0.1, 50, 0.3, 30,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        UpdaterReplay.getInstance().setClipState(ClipState.START);
                        return null;
                    }
                }, EnumVisibleType.CLIP);
        // 録画切り取りフレームを決定するボタン
        final ButtonCafe clipFrameButton = new ButtonCafe("set", 0.1, 10, 0.3, 30,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        if(!UpdaterReplay.getInstance().isPresent()) return null;
                        if(UpdaterReplay.getInstance().getClipInitialFrame().get().isEmpty()) {
                            // initial frame決定
                            UpdaterReplay.getInstance().setClipInitialFrame(UpdaterReplay.getInstance().getReplayFrame());
                        } else if(UpdaterReplay.getInstance().getClipInitialFrame().get().get()
                                < UpdaterReplay.getInstance().getReplayFrame()) {
                            // last frame決定
                            UpdaterReplay.getInstance().setClipLastFrame(UpdaterReplay.getInstance().getReplayFrame());
                            // 実行ボタンを押せるようにする
                            clipButton.setEnabled(true);
                        }
                        return null;
                    }
                }, EnumVisibleType.CLIP) {
            @Override
            public void update() {
                // 切り取り中はフレーム決定できないようにする
                this.setEnabled(!UpdaterReplay.getInstance().getReplayState().equals(ReplayState.STOP)
                        && !UpdaterReplay.getInstance().getClipState().equals(ClipState.CLIP));
            }
        };
        final DynamicLabelCafe clipInitialLabel = new DynamicLabelCafe("start:--", 0.5, 10,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String str) {
                        final Optional<Long> initialFrame = UpdaterReplay.getInstance().getClipInitialFrame().get();
                        if(initialFrame.isPresent()) {
                            clipCancelButton.setEnabled(true);
                            return "start:" + initialFrame.get();
                        } else {
                            clipCancelButton.setEnabled(false);
                            clipButton.setEnabled(false);
                            return "start:--";
                        }
                    }
                }, EnumVisibleType.CLIP);
        final DynamicLabelCafe clipLastLabel = new DynamicLabelCafe("end:--", 0.5, 30,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String str) {
                        final Optional<Long> lastFrame = UpdaterReplay.getInstance().getClipLastFrame().get();
                        if(lastFrame.isPresent()) {
                            return "end:" + lastFrame.get();
                        } else {
                            return "end:--";
                        }
                    }
                }, EnumVisibleType.CLIP);
        final LabelCafe sizeLabel = new LabelCafe("size:", 0.1, 130, EnumVisibleType.CLIP);
        // フレームを飛ばす幅を記入する
        final TextAreaCafe sampleTextArea = new TextAreaCafe(
                String.valueOf(UpdaterReplay.getInstance().getWindowSize()), 0.4, 130, 0.3, 1,
                new IFuncParam1<Void, String>() {
                    @Override
                    public Void function(String str) {
                        if(str != null && Double.valueOf(str).intValue() > 0) {
                            UpdaterReplay.getInstance().setWindowSize(Double.valueOf(str).intValue());
                        }
                        return null;
                    }
                }, EnumVisibleType.CLIP);
        // InitialからLastまでwindowSize分フレームを飛ばす場合に取得できるデータの総数
        final DynamicLabelCafe sizeDynamicLabel = new DynamicLabelCafe(
                String.valueOf(UpdaterReplay.getInstance().getWindowSize()), 0.1, 160,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String str) {
                        final Optional<Long> initialFrame = UpdaterReplay.getInstance().getClipInitialFrame().get();
                        final Optional<Long> lastFrame = UpdaterReplay.getInstance().getClipLastFrame().get();
                        if(initialFrame.isEmpty() || lastFrame.isEmpty()) {
                            return "samples:--";
                        } else {
                            return "samples:" + Math.max(1, (int) ((lastFrame.get() - initialFrame.get())
                                    / UpdaterReplay.getInstance().getWindowSize()));
                        }
                    }
                }, EnumVisibleType.CLIP);
        // 切り取ったデータのファイル名を決定する
        final LabelCafe fileNameLabel = new LabelCafe("fileName:", 0.1, 190, EnumVisibleType.CLIP);
        final TextAreaCafe fileNameArea = new TextAreaCafe("", 0.4, 190, 0.3, 1,
                new IFuncParam1<Void, String>() {
                    @Override
                    public Void function(String str) {
                        if(str != null) {
                            UpdaterReplay.getInstance().setClipFileName(str);
                        }
                        return null;
                    }
                }, EnumVisibleType.CLIP);
        // 切り取ったデータのファイル形式を決定する
        final ComboBoxCafe<FileExtension> extComboBox = new ComboBoxCafe<>(FileExtension.values(),
                FileExtension.CSV, 0.8, 190, 0.1, 20,
                    new IFuncParam1<Void, FileExtension>() {
                        @Override
                        public Void function(FileExtension ext) {
                            UpdaterReplay.getInstance().setFileExtension(ext);
                            if(ext.equals(FileExtension.BIN)) {
                                sampleTextArea.setEnabled(false);
                                UpdaterReplay.getInstance().setWindowSize(1L);
                            } else {
                                sampleTextArea.setEnabled(true);
                                sampleTextArea.setText("10");
                                UpdaterReplay.getInstance().setWindowSize(10L);
                            }
                            return null;
                        }
                    }, EnumVisibleType.CLIP);
        // ClipCafeの進捗を描画する
        final LabelCafe progressLabel = new LabelCafe("progress:", 0.1, 230, EnumVisibleType.CLIP);
        final ProgressBarCafe clipProgressBar = new ProgressBarCafe(0.4, 230, 0.5, 20,
                        new IFuncParam1<Integer, Integer>() {
                            @Override
                            public Integer function(Integer integer) {
                                UpdaterReplay replay = UpdaterReplay.getInstance();
                                if (replay.getClipState().equals(ClipState.CLIP)) {
                                    final Optional<Long> initialFrame = replay.getClipInitialFrame().get();
                                    final Optional<Long> lastFrame = replay.getClipLastFrame().get();
                                    if(initialFrame.isPresent() && lastFrame.isPresent()
                                            && initialFrame.get() < lastFrame.get()) {
                                        return (int) ((replay.getClipFrame() - initialFrame.get()) * 100
                                                / (lastFrame.get() - initialFrame.get()));
                                    }
                                }
                                return 0;
                            }
                        }, 0, EnumVisibleType.CLIP, true);
        components.add(clipFrameButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(clipButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(clipInitialLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(clipLastLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(clipCancelButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(sampleTextArea.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(sizeLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(sizeDynamicLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(fileNameLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(fileNameArea.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(extComboBox.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(progressLabel.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(clipProgressBar.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
    }

    private static void setComponentReplayArea(@Nonnull List<Component> components) {
        // 開いている録画ファイル名を表示する
        components.add(new DynamicLabelCafe("no file", 0.1, 0,
                new IFuncParam1<String, String>() {
                    @Override
                    public String function(String s) {
                        if(UpdaterReplay.getInstance().getExternalFile().get().isPresent()) {
                            return UpdaterReplay.getInstance().getExternalFile().get().get().getName();
                        } else {
                            return "no file";
                        }
                    }
                }, EnumVisibleType.ALWAYS)
                .setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        // ファイルの読み込み具合や，再生時間を描画する
        ProgressBarCafe replayBar = new ProgressBarCafe(0.1, 20, 0.8, 15,
                new IFuncParam1<Integer, Integer>() {
                    @Override
                    public Integer function(Integer value) {
                        if (UpdaterReplay.getInstance().getExternalFile().get().isPresent()) {
                            UpdaterReplay replay = UpdaterReplay.getInstance();
                            if (replay.getReplayLastFrame() <= replay.getReplayInitialFrame()) return 0;
                            else {
                                // 録画ファイルのシークバーを計算する
                                return (int) ((replay.getReplayFrame() - replay.getReplayInitialFrame()) * 100
                                        / (replay.getReplayLastFrame() - replay.getReplayInitialFrame()));
                            }
                        } else {
                            // 録画ファイル読み込みの進捗度を表示する
                            return UpdaterReplay.getInstance().getPercent();
                        }
                    }
                }, 0, EnumVisibleType.ALWAYS, true) {
            @Override
            public void update() {
                if(UpdaterReplay.getInstance().isPresent()) {
                    // 録画ファイル再生中は赤色のProgressBarにする
                    this.setForeground(Color.RED);
                    this.setString(UpdaterReplay.getInstance().getCurrentTime()
                            .concat("/").concat(UpdaterReplay.getInstance().getLastTime()));
                } else {
                    // 録画ファイル読み込み中は青色のProgressBarにする
                    this.setForeground(Color.BLUE);
                    this.setString(null);
                }
            }
        };
        // 左クリックでreplayBarを触れるようにする
        replayBar.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(UpdaterReplay.getInstance().isPresent()) {
                    final int x = e.getX();
                    final int maxX = replayBar.getWidth();
                    final long initialFrame = UpdaterReplay.getInstance().getReplayInitialFrame();
                    final long lastFrame = UpdaterReplay.getInstance().getReplayLastFrame();
                    UpdaterReplay.getInstance().setReplayFrame(x * (lastFrame - initialFrame) / maxX + initialFrame);
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {}
            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
        // ドラッグでreplayBarを触れるようにする
        replayBar.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if(UpdaterReplay.getInstance().isPresent()) {
                    final int x = e.getX();
                    final int maxX = replayBar.getWidth();
                    final long initialFrame = UpdaterReplay.getInstance().getReplayInitialFrame();
                    final long lastFrame = UpdaterReplay.getInstance().getReplayLastFrame();
                    UpdaterReplay.getInstance().setReplayFrame(x * (lastFrame - initialFrame) / maxX + initialFrame);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {}
        });
        components.add(replayBar.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        boolean enable = UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP;
        // replay/pause切り替えボタン
        ButtonCafe replayPauseButton = new ButtonCafe("REPLAY/PAUSE", 0.1, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        if (UpdaterReplay.getInstance().getReplayState() == ReplayState.REPLAY) {
                            UpdaterReplay.getInstance().setReplayState(ReplayState.PAUSE);
                        } else if (UpdaterReplay.getInstance().getReplayState() == ReplayState.PAUSE) {
                            if(UpdaterReplay.getInstance().getReplayFrame() ==
                                    UpdaterReplay.getInstance().getReplayLastFrame()) {
                                // ボタンを押したときに最後のフレームだった場合，最初から再生する
                                UpdaterReplay.getInstance().setReplayFrame(UpdaterReplay.getInstance().getReplayInitialFrame());
                            }
                            UpdaterReplay.getInstance().setReplayState(ReplayState.REPLAY);
                        }
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                if(this.isVisible()) this.setEnabled(UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP);
                if(UpdaterReplay.getInstance().isPresent()){
                    this.setText(UpdaterReplay.getInstance().getReplayState().toString());
                } else {
                    this.setText("REPLAY/PAUSE");
                }
            }

            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.setContentAreaFilled(false);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                if(UpdaterReplay.getInstance().getReplayState().equals(ReplayState.PAUSE)) {
                    // pause中は正三角形
                    int x[] = new int[]{32, 58, 32};
                    int y[] = new int[]{10, 25, 40};
                    graphics.fillPolygon(x, y, 3);
                } else {
                    // replay中は2個の長方形
                    if(graphics instanceof Graphics2D) {
                        ((Graphics2D) graphics).setStroke(new BasicStroke(8));
                    }
                    graphics.drawLine(32, 12, 32, 38);
                    graphics.drawLine(48, 12, 48, 38);
                }
            }
        };
        replayPauseButton.setEnabled(enable);
        // 現在の録画時間から指定秒巻き戻すボタン
        ButtonCafe rewindButton = new ButtonCafe("<<", 0.3, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        final long commonFrame = UpdaterReplay.getInstance().getReplayFrame();
                        final long fastFrame = ConfigManager.getInstance().getConfig().fastFrame;
                        UpdaterReplay.getInstance().setReplayFrame(commonFrame - fastFrame);
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                if (this.isVisible()) this.setEnabled(UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP);
            }

            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.setContentAreaFilled(false);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                if(graphics instanceof Graphics2D) {
                    ((Graphics2D) graphics).setStroke(new BasicStroke(3));
                }
                // 巻き矢印
                graphics.drawArc(32, 15, 20, 20, -45, 270);
                graphics.drawLine(36, 25, 36, 33);
                graphics.drawString(String.valueOf(ConfigManager.getInstance().getConfig().fastFrame / 60), 39, 28);
                graphics.drawLine(28, 33, 36, 33);
            }
        };
        rewindButton.setEnabled(enable);
        // 現在の再生時間から1フレーム巻き戻すボタン
        ButtonCafe rewindFrameButton = new ButtonCafe("<", 0.4, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        final long commonFrame = UpdaterReplay.getInstance().getReplayFrame();
                        UpdaterReplay.getInstance().setReplayFrame(commonFrame - 1);
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                if (this.isVisible()) this.setEnabled(UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP);
            }

            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.setContentAreaFilled(false);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                // 正三角形に長方形をくっつけた形
                int x[] = new int[]{58, 32, 58};
                int y[] = new int[]{10, 25, 40};
                graphics.fillPolygon(x, y, 3);
                if(graphics instanceof Graphics2D) {
                    ((Graphics2D) graphics).setStroke(new BasicStroke(5));
                }
                graphics.drawLine(32, 11, 32, 39);
                graphics.drawLine(22, 11, 22, 39);
            }
        };
        rewindFrameButton.setEnabled(enable);
        // 現在の再生時間から指定秒早送りするボタン
        ButtonCafe forwardFrameButton = new ButtonCafe(">", 0.5, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        final long commonFrame = UpdaterReplay.getInstance().getReplayFrame();
                        UpdaterReplay.getInstance().setReplayFrame(commonFrame + 1);
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                if (this.isVisible()) this.setEnabled(UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP);
            }

            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.setContentAreaFilled(false);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                // 巻き矢印
                int x[] = new int[]{32, 58, 32};
                int y[] = new int[]{10, 25, 40};
                graphics.fillPolygon(x, y, 3);
                if(graphics instanceof Graphics2D) {
                    ((Graphics2D) graphics).setStroke(new BasicStroke(5));
                }
                graphics.drawLine(58, 11, 58, 39);
                graphics.drawLine(68, 11, 68, 39);
            }
        };
        forwardFrameButton.setEnabled(enable);
        // 現在の再生時間から1フレーム早送りするボタン
        ButtonCafe fastForwardButton = new ButtonCafe(">>", 0.6, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        final long commonFrame = UpdaterReplay.getInstance().getReplayFrame();
                        final long fastFrame = ConfigManager.getInstance().getConfig().fastFrame;
                        UpdaterReplay.getInstance().setReplayFrame(commonFrame + fastFrame);
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                if (this.isVisible()) this.setEnabled(UpdaterReplay.getInstance().getReplayState() != ReplayState.STOP);
            }

            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.setContentAreaFilled(false);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                if(graphics instanceof Graphics2D) {
                    ((Graphics2D) graphics).setStroke(new BasicStroke(3));
                }
                // 正三角形に長方形をくっつけた形
                graphics.drawArc(32, 15, 20, 20, -45, 270);
                graphics.drawLine(46, 25, 46, 33);
                graphics.drawString(String.valueOf(ConfigManager.getInstance().getConfig().fastFrame / 60), 38, 28);
                graphics.drawLine(46, 33, 54, 33);
            }
        };
        fastForwardButton.setEnabled(enable);
        // 開く録画ファイルを決めるボタン
        // 最後の引数でボタンにイラストを描画させる
        ButtonCafe openFileButton = new ButtonCafe("", 0.7, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        File dir = new File("data");
                        UpdaterReplay.getInstance().setReplayState(ReplayState.STOP);
                        JFileChooser fileChooser = new JFileChooser(dir);
                        if (fileChooser.showOpenDialog(fileChooser) == JFileChooser.APPROVE_OPTION) {
                            // ファイルを開けたらupdaterに設定する
                            UpdaterReplay.getInstance().setExternalFile(fileChooser.getSelectedFile(), new IFunction<Void>() {
                                @Override
                                public Void function(Object... args) {
                                    UpdaterReplay.getInstance().setReplayState(ReplayState.PAUSE);
                                    UpdaterReplay.getInstance().setReplayFrame(0L);
                                    return null;
                                }
                            });
                        } else {
                            UpdaterReplay.getInstance().setReplayState(ReplayState.STOP);
                        }
                        return null;
                    }
                }, EnumVisibleType.ALWAYS, new ImageIcon("src/main/resources/images/open-file-kiks.png")) {
            @Override
            public void update() {
                if(!UpdaterReplay.getInstance().getReplayState().equals(ReplayState.STOP)) this.setEnabled(false);
            }

            /**
             * ボタンにはなにも描画しない
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                super.setContentAreaFilled(false);
            }
        };
        // 再生停止ボタン
        ButtonCafe stopButton = new ButtonCafe("STOP", 0.2, 40, 0.1, 50,
                new IFuncParam1<Void, Boolean>() {
                    @Override
                    public Void function(Boolean value) {
                        UpdaterReplay.getInstance().setReplayState(ReplayState.STOP);
                        replayPauseButton.setEnabled(false);
                        fastForwardButton.setEnabled(false);
                        rewindButton.setEnabled(false);
                        rewindFrameButton.setEnabled(false);
                        forwardFrameButton.setEnabled(false);
                        openFileButton.setEnabled(true);
                        return null;
                    }
                }, EnumVisibleType.ALWAYS) {
            @Override
            public void update() {
                this.setEnabled(!UpdaterReplay.getInstance().getReplayState().equals(ReplayState.STOP)
                        && !UpdaterReplay.getInstance().getClipState().equals(ClipState.CLIP));
            }


            /**
             * ボタンの絵を描画する
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(ColorHelper.UI_SMOKE);
                if(UpdaterReplay.getInstance().isPresent()) {
                    graphics.setColor(Color.BLACK);
                } else {
                    graphics.setColor(ColorHelper.SWITCH_GRAY);
                }
                // 正方形
                int x[] = new int[]{31, 59, 59, 31};
                int y[] = new int[]{11, 11, 39, 39};
                graphics.fillPolygon(x, y, 4);
            }
        };
        components.add(replayPauseButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(stopButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(fastForwardButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(rewindButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(forwardFrameButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(rewindFrameButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
        components.add(openFileButton.setDisplayType(0, EnumDisplayType.RATIO)
                .setDisplayType(2, EnumDisplayType.RATIO));
    }
}
