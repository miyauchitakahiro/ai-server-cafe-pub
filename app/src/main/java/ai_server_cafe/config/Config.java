package ai_server_cafe.config;

import ai_server_cafe.model.game.EnumCaptainType;
import ai_server_cafe.network.proto.ssl.gc.GcRefereeMessage;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.interfaces.AbstractCloneable;

import javax.annotation.Nonnull;
import java.awt.*;

public class Config extends AbstractCloneable {
    public boolean allowAutoReboot;
    public boolean resetStateAutoReboot;
    public boolean isVisionAreaVisible;
    public String visionAddress;
    public String visionInterfaceAddress;
    public int visionPort;
    public String visionTrackerAddress;
    public String visionTrackerInterfaceAddress;
    public int visionTrackerPort;
    public String transmitterAddress;
    public String transmitterInterfaceAddress;
    public int transmitterPort;
    public String grSimAddress;
    public String grSimInterfaceAddress;
    public int grSimPort;
    public String simAddress;
    public String simInterfaceAddress;
    public int simControlPort;
    public int simBluePort;
    public int simYellowPort;
    public String refBoxAddress;
    public String refBoxInterfaceAddress;
    public String commonInterfaceAddress;
    public String broadcastAddress;
    public boolean useCommonInterfaceAddress;
    public int refBoxPort;
    public int[] activeYellowRobots;
    public int[] activeBlueRobots;
    public int[] newDribblerIds;
    public int activeYellowCaptain;
    public int activeBlueCaptain;
    public boolean isGoalOfYellowPositive;
    public boolean useScoreBoard;
    public int scoreBoardPort;
    public int demoMatchTime;
    public double fps;
    public double cyclePS;
    public double sendPS;
    public int[] radioTypes;
    public double robotRadius;
    public double toFaceRadius;
    public double dribblerLength;
    public double ballRadius;
    public double ballBrake;    // ボールを転がしたときの減速度
    public double widePenaltyMargin;    // ストップゲーム時のペナルティアリアのマージン
    public double maxFieldWidth;
    public double maxFieldHeight;
    public Filter filterConfig;
    public PIDController controllerConfig;
    public LocalRefBox localRefBoxConfig;
    public GUIConfig guiConfig;
    public boolean useTracker;
    public Visibility visibility;
    public boolean exitPositive;
    public PK pk;
    public Demo demo;
    public DemoAutorefConfig demoAutorefConfig;
    public KeyboardConfig keyboardConfig;
    public DribbleState dribbleState;
    public String[] replayVisible;
    public double wheelAngle;
    public PathPlannerConfig pathPlannerConfig;
    public long fastFrame;
    public int performanceWaitFrame;
    public int saveFormat;
    public boolean enableRecord;
    public ConfigEditorConfig configEditorConfig;
    public RLDribble rlDribble;

    public Config() {
        this.allowAutoReboot = false;
        this.resetStateAutoReboot = true;
        this.isVisionAreaVisible = true;
        this.visionAddress = "224.5.23.2";
        this.visionInterfaceAddress = "10.22.254.149";
        this.visionPort = 10020;
        this.visionTrackerAddress = "224.5.23.2";
        this.visionTrackerInterfaceAddress = "10.22.254.149";
        this.visionTrackerPort = 10010;
        this.transmitterAddress = "224.4.23.4";
        this.transmitterInterfaceAddress = "10.22.254.149";
        this.transmitterPort = 10004;
        this.grSimAddress = "127.0.0.1";
        this.grSimInterfaceAddress = "10.22.254.149";
        this.grSimPort = 20011;
        this.simControlPort = 10300;
        this.simYellowPort = 10302;
        this.simBluePort = 10301;
        this.simAddress = "192.168.10.2";
        this.simInterfaceAddress = "127.0.0.1";
        this.refBoxAddress = "224.5.23.1";
        this.refBoxInterfaceAddress = "10.22.254.149";
        this.broadcastAddress = "255.255.255.255";
        this.refBoxPort = 10003;
        this.commonInterfaceAddress = "";
        this.useCommonInterfaceAddress = true;
        this.activeYellowRobots = new int[] {0,1,2,3,4,5,6,7,8,9,10};
        this.activeBlueRobots = new int[] {0,1,2,3,4,5,6,7,8,9,10};
        this.newDribblerIds = new int[] {3, 4, 5, 9};
        this.activeBlueCaptain = 0;
        this.activeYellowCaptain = 0;
        this.isGoalOfYellowPositive = false;
        this.useScoreBoard = false;
        this.scoreBoardPort = 8083;
        this.demoMatchTime = 180;
        this.fps = 60.0;
        this.cyclePS = 60.0;
        this.sendPS = 60.0;
        this.radioTypes = new int[] {0};
        this.robotRadius = 90.0;
        this.toFaceRadius = 68.0;
        this.dribblerLength = 100.0;
        this.ballRadius = 21.0;
        this.ballBrake = 700;
        this.widePenaltyMargin = 400.0;
        this.maxFieldWidth = 20000.0;
        this.maxFieldHeight = 15000.0;
        this.useTracker = false;
        this.filterConfig = new Filter();
        this.controllerConfig = new PIDController();
        this.controllerConfig.accelToTargetVelocity = 4000.0;
        this.controllerConfig.brakeToTargetVelocity = 5000.0;
        this.localRefBoxConfig = new LocalRefBox();
        this.guiConfig = new GUIConfig();
        this.visibility = new Visibility();
        this.exitPositive = true;
        this.pk = new PK();
        this.demo = new Demo();
        this.demoAutorefConfig = new DemoAutorefConfig();
        this.keyboardConfig = new KeyboardConfig();
        this.dribbleState = new DribbleState();
        this.replayVisible = new String[] {"robot", "path", "target", "action", "ball"};
        this.fastFrame = 120L;
        this.performanceWaitFrame = 240;
        this.wheelAngle = 63.0;
        this.pathPlannerConfig = new PathPlannerConfig();
        this.saveFormat = 1;
        this.enableRecord = true;
        this.configEditorConfig = new ConfigEditorConfig();
        this.rlDribble = new RLDribble();
    }

    public EnumCaptainType getCaptain(@Nonnull TeamColor color) {
        if (color.isYellow())
            return EnumCaptainType.getFromId(this.activeYellowCaptain);
        return EnumCaptainType.getFromId(this.activeBlueCaptain);
    }

    public boolean isUseKickRegulatorData(TeamColor color) {
        return this.getCaptain(color) != EnumCaptainType.KICK_REGULATOR;
    }

    public int[] getActiveRobots(@Nonnull TeamColor color) {
        if (color.isYellow())
            return this.activeYellowRobots.clone();
        return this.activeBlueRobots.clone();
    }

    public double getSendCycleTime() {
        return 1.0 / this.sendPS;
    }

    public double getCycleTime() {
        return 1.0 / this.cyclePS;
    }

    public double getFrameTime() {
        return 1.0 / this.fps;
    }

    @Override
    public Config clone() {
        Config result = new Config();
        result.allowAutoReboot = this.allowAutoReboot;
        result.resetStateAutoReboot = this.resetStateAutoReboot;
        result.isVisionAreaVisible = this.isVisionAreaVisible;
        result.visionAddress = this.visionAddress;
        result.visionInterfaceAddress = this.visionInterfaceAddress;
        result.visionPort = this.visionPort;
        result.visionTrackerAddress = this.visionTrackerAddress;
        result.visionTrackerInterfaceAddress = this.visionTrackerInterfaceAddress;
        result.visionTrackerPort = this.visionTrackerPort;
        result.transmitterAddress = this.transmitterAddress;
        result.transmitterInterfaceAddress = this.transmitterInterfaceAddress;
        result.transmitterPort = this.transmitterPort;
        result.grSimAddress = this.grSimAddress;
        result.grSimInterfaceAddress = this.grSimInterfaceAddress;
        result.grSimPort = this.grSimPort;
        result.simAddress = this.simAddress;
        result.simInterfaceAddress = this.simInterfaceAddress;
        result.simControlPort = this.simControlPort;
        result.simYellowPort = this.simYellowPort;
        result.simBluePort = this.simBluePort;
        result.refBoxAddress = this.refBoxAddress;
        result.refBoxInterfaceAddress = this.refBoxInterfaceAddress;
        result.refBoxPort = this.refBoxPort;
        result.commonInterfaceAddress = this.commonInterfaceAddress;
        result.broadcastAddress = this.broadcastAddress;
        result.activeYellowRobots = this.activeYellowRobots.clone();
        result.activeBlueRobots = this.activeBlueRobots.clone();
        result.activeBlueCaptain = this.activeBlueCaptain;
        result.activeYellowCaptain = this.activeYellowCaptain;
        result.isGoalOfYellowPositive = this.isGoalOfYellowPositive;
        result.useScoreBoard = this.useScoreBoard;
        result.scoreBoardPort = this.scoreBoardPort;
        result.demoMatchTime = this.demoMatchTime;
        result.fps = this.fps;
        result.cyclePS = this.cyclePS;
        result.sendPS = this.sendPS;
        result.useTracker = this.useTracker;
        result.radioTypes = this.radioTypes.clone();
        result.robotRadius = this.robotRadius;
        result.toFaceRadius = this.toFaceRadius;
        result.dribblerLength = this.dribblerLength;
        result.ballRadius = this.ballRadius;
        result.ballBrake = this.ballBrake;
        result.widePenaltyMargin = this.widePenaltyMargin;
        result.maxFieldWidth = this.maxFieldWidth;
        result.maxFieldHeight = this.maxFieldHeight;
        result.filterConfig = this.filterConfig.clone();
        result.controllerConfig = this.controllerConfig.clone();
        result.localRefBoxConfig = this.localRefBoxConfig.clone();
        result.guiConfig = this.guiConfig.clone();
        result.visibility = this.visibility.clone();
        result.exitPositive = this.exitPositive;
        result.pk = this.pk;
        result.demo = this.demo.clone();
        result.demoAutorefConfig = this.demoAutorefConfig.clone();
        result.useCommonInterfaceAddress = this.useCommonInterfaceAddress;
        result.dribbleState = this.dribbleState.clone();
        result.replayVisible = this.replayVisible.clone();
        result.fastFrame = this.fastFrame;
        result.performanceWaitFrame = this.performanceWaitFrame;
        result.wheelAngle = this.wheelAngle;
        result.pathPlannerConfig = this.pathPlannerConfig.clone();
        result.saveFormat = this.saveFormat;
        result.enableRecord = this.enableRecord;
        result.configEditorConfig = this.configEditorConfig;
        // 古いconfig.jsonには存在しないのでnullの場合はデフォルト値にする
        result.rlDribble = this.rlDribble == null ? new RLDribble() : this.rlDribble.clone();
        return result;
    }

    public static class Filter extends AbstractCloneable {
        public double lostDuration;
        public double controlDelay;
        public boolean useBallEstimator;
        public boolean useFreeRobotEstimator;
        public boolean useControlledRobotEstimator;
        public double robotConfidenceThreshold;
        public double visionDelay;

        public Filter() {
            this.lostDuration = 2.0;
            this.controlDelay = 0.02;
            this.useBallEstimator = true;
            this.useFreeRobotEstimator = true;
            this.useControlledRobotEstimator = true;
            this.robotConfidenceThreshold = 0.90;
            this.visionDelay = 0.080;
        }

        @Override
        public Filter clone() {
            Filter result = new Filter();
            result.lostDuration = this.lostDuration;
            result.controlDelay = this.controlDelay;
            result.useBallEstimator = this.useBallEstimator;
            result.useFreeRobotEstimator = this.useFreeRobotEstimator;
            result.useControlledRobotEstimator = this.useControlledRobotEstimator;
            result.robotConfidenceThreshold = this.robotConfidenceThreshold;
            result.visionDelay = this.visionDelay;
            return result;
        }
    }

    public static class PIDController extends AbstractCloneable {
        public double kp;
        public double ki;
        public double kd;
        public double kpAngle;
        public double kiAngle;
        public double kdAngle;
        public double velocityMax;
        public double accelToTargetVelocity;
        public double brakeToTargetVelocity;
        public double brakeToTargetPosition;
        public double velAngularMax;
        public double accelToTargetVelAngular;
        public double fieldMargin;
        public PIDController() {
            this.kp = 0.0;
            this.ki = 0.0;
            this.kd = 0.0;
            this.kpAngle = 0.0;
            this.kiAngle = 0.0;
            this.kdAngle = 0.0;
            this.velocityMax = 4000.0;
            this.accelToTargetVelocity = 4400.0;
            this.brakeToTargetVelocity = 5400.0;
            this.brakeToTargetPosition = 3400.0;
            this.velAngularMax = 10.0;
            this.accelToTargetVelAngular = 30.0;
            this.fieldMargin = 300.0;
        }

        @Override
        public PIDController clone() {
            PIDController result = new PIDController();
            result.kp = this.kp;
            result.ki = this.ki;
            result.kd = this.kd;
            result.kpAngle = this.kpAngle;
            result.kiAngle = this.kiAngle;
            result.kdAngle = this.kdAngle;
            result.velocityMax = this.velocityMax;
            result.accelToTargetVelocity = this.accelToTargetVelocity;
            result.brakeToTargetVelocity = this.brakeToTargetVelocity;
            result.brakeToTargetPosition = this.brakeToTargetPosition;
            result.velAngularMax = this.velAngularMax;
            result.accelToTargetVelAngular = this.accelToTargetVelAngular;
            result.fieldMargin = this.fieldMargin;
            return result;
        }
    }

    public static class LocalRefBox extends AbstractCloneable {
        public GcRefereeMessage.Referee.Command command;
        public GcRefereeMessage.Referee.Stage stage;
        public double ballPlacePosX;
        public double ballPlacePosY;
        public String blueTeamName;
        public int blueScore;
        public int blueRedCards;
        public int[] blueYellowCardsTime;
        public int blueYellowCards;
        public int blueTimeouts;
        public int blueTimeoutTime;
        public int blueGoalKeeper;
        public int blueMaxAllowRobots;
        public String yellowTeamName;
        public int yellowScore;
        public int yellowRedCards;
        public int[] yellowYellowCardsTime;
        public int yellowYellowCards;
        public int yellowTimeouts;
        public int yellowTimeoutTime;
        public int yellowGoalKeeper;
        public int yellowMaxAllowRobots;
        public LocalRefBox() {
            this.command = GcRefereeMessage.Referee.Command.HALT;
            this.stage = GcRefereeMessage.Referee.Stage.NORMAL_FIRST_HALF_PRE;
            this.ballPlacePosX = 0.0;
            this.ballPlacePosY = 0.0;
            this.blueTeamName = "LocalRefBlue";
            this.blueScore = 0;
            this.blueRedCards = 0;
            this.blueYellowCardsTime = new int[] {};
            this.blueYellowCards = 0;
            this.blueTimeouts = 4;
            this.blueTimeoutTime = 300000000;
            this.blueGoalKeeper = 0;
            this.blueMaxAllowRobots = 11;
            this.yellowTeamName = "LocalRefYellow";
            this.yellowScore = 0;
            this.yellowRedCards = 0;
            this.yellowYellowCardsTime = new int[] {};
            this.yellowYellowCards = 0;
            this.yellowTimeouts = 4;
            this.yellowTimeoutTime = 300000000;
            this.yellowGoalKeeper = 0;
            this.yellowMaxAllowRobots = 11;
        }

        @Override
        public LocalRefBox clone() {
            LocalRefBox result = new LocalRefBox();
            result.command = this.command;
            result.stage = this.stage;
            result.ballPlacePosX = this.ballPlacePosX;
            result.ballPlacePosY = this.ballPlacePosY;
            result.blueTeamName = this.blueTeamName;
            result.blueScore = this.blueScore;
            result.blueRedCards = this.blueRedCards;
            result.blueYellowCardsTime = this.blueYellowCardsTime.clone();
            result.blueYellowCards = this.blueYellowCards;
            result.blueTimeouts = this.blueTimeouts;
            result.blueTimeoutTime = this.blueTimeoutTime;
            result.blueGoalKeeper = this.blueGoalKeeper;
            result.blueMaxAllowRobots = this.blueMaxAllowRobots;
            result.yellowTeamName = this.yellowTeamName;
            result.yellowScore = this.yellowScore;
            result.yellowRedCards = this.yellowRedCards;
            result.yellowYellowCardsTime = this.yellowYellowCardsTime.clone();
            result.yellowYellowCards = this.yellowYellowCards;
            result.yellowTimeouts = this.yellowTimeouts;
            result.yellowTimeoutTime = this.yellowTimeoutTime;
            result.yellowGoalKeeper = this.yellowGoalKeeper;
            result.yellowMaxAllowRobots = this.yellowMaxAllowRobots;
            return result;
        }
    }

    public static class GUIConfig extends AbstractCloneable {
        public String font;
        public int fontSize;
        public int fontStyle;
        public GUIConfig() {
            this.font = Font.SANS_SERIF;
            this.fontSize = 12;
            this.fontStyle = Font.BOLD;
        }

        @Override
        public GUIConfig clone() {
            GUIConfig result = new GUIConfig();
            result.font = this.font;
            result.fontSize = this.fontSize;
            result.fontStyle = this.fontStyle;
            return result;
        }
    }
    public static class Visibility extends AbstractCloneable {
        public boolean invertVisible;
        public boolean pathVisible;
        public boolean actionVisible;
        public boolean roleVisible;
        public boolean targetsVisible;
        public boolean waiterBoxVisible;
        public boolean ballVelocityVisible;
        public boolean robotSpeedVisible;
        public boolean teamGoalColorVisible;
        public boolean bluePassScoreVisible;
        public boolean yellowPassScoreVisible;
        public boolean blueXGScoreVisible;
        public boolean yellowXGScoreVisible;
        public int[] yellowObstacle;
        public int[] blueObstacle;
        public Visibility() {
            this.invertVisible = false;
            this.pathVisible = false;
            this.actionVisible = false;
            this.roleVisible = false;
            this.targetsVisible = false;
            this.waiterBoxVisible = false;
            this.ballVelocityVisible = false;
            this.robotSpeedVisible = false;
            this.bluePassScoreVisible = false;
            this.yellowPassScoreVisible = false;
            this.teamGoalColorVisible = false;
            this.blueXGScoreVisible = false;
            this.yellowXGScoreVisible = false;
            this.yellowObstacle = new int[] {};
            this.blueObstacle = new int[] {};
        }
        
        @Override
        public Visibility clone() {
            Visibility result = new Visibility();
            result.invertVisible = this.invertVisible;
            result.pathVisible = this.pathVisible;
            result.actionVisible = this.actionVisible;
            result.roleVisible = this.roleVisible;
            result.targetsVisible = this.targetsVisible;
            result.waiterBoxVisible = this.waiterBoxVisible;
            result.ballVelocityVisible = this.ballVelocityVisible;
            result.robotSpeedVisible = this.robotSpeedVisible;
            result.bluePassScoreVisible = this.bluePassScoreVisible;
            result.yellowPassScoreVisible = this.yellowPassScoreVisible;
            result.teamGoalColorVisible=this.teamGoalColorVisible;
            result.blueXGScoreVisible = this.blueXGScoreVisible;
            result.yellowXGScoreVisible = this.yellowXGScoreVisible;
            result.blueObstacle = this.blueObstacle.clone();
            result.yellowObstacle = this.yellowObstacle.clone();
            return result;
        }
    }
    public static class Demo extends AbstractCloneable {
        public boolean enable;
        public int difficulty;
        public boolean isDifficultyAuto;
        public Demo(){
            this.enable = false;
            this.difficulty = 0;
            this.isDifficultyAuto = true;
        }
        @Override
        public Demo clone() {
            Demo result = new Demo();
            result.enable = this.enable;
            result.difficulty = this.difficulty;
            result.isDifficultyAuto = this.isDifficultyAuto;
            return result;
        }
    }

    public static class PK extends AbstractCloneable {
        public int assignedRobotId;
        public boolean isAssignedRobotId;

        public PK() {
            this.assignedRobotId = 0;
            this.isAssignedRobotId = false;
        }

        @Override
        public AbstractCloneable clone() {
            PK result = new PK();
            result.assignedRobotId = 0;
            result.isAssignedRobotId = false;
            return result;
        }
    }

    public static class DemoAutorefConfig extends AbstractCloneable {
        public int goalDecisionCount;
        public int velocityHistorySize;
        public double goalCoolTIme;
        public DemoAutorefConfig(){
            this.goalDecisionCount = 2;
            this.velocityHistorySize = 3;
            this.goalCoolTIme = 3.5;
        }
        @Override
        public DemoAutorefConfig clone(){
            DemoAutorefConfig result = new DemoAutorefConfig();
            result.goalDecisionCount = this.goalDecisionCount;
            result.velocityHistorySize = this.velocityHistorySize;
            result.goalCoolTIme = this.goalCoolTIme;
            return result;
        }
    }

    public static class KeyboardConfig extends AbstractCloneable {
        // ActionKeyboard
        public String forward;
        public String left;
        public String back;
        public String right;
        public String leftRotate;
        public String rightRotate;
        public String chip;
        public String straight;
        public String powerUp;
        public String powerDown;
        public String dribble;
        // ReplayArea
        public String fastForward;
        public String rewind;
        public String replayPause;
        public String openFile;
        public String closeFile;

        public KeyboardConfig() {
            this.forward = "w";
            this.left = "a";
            this.back = "s";
            this.right = "d";
            this.leftRotate = "left";
            this.rightRotate = "right";
            this.chip = "c";
            this.straight = "v";
            this.powerUp = "up";
            this.powerDown = "down";
            this.dribble = "space";
            this.fastForward = "l";
            this.replayPause = "k";
            this.rewind = "j";
            this.openFile = "o";
            this.closeFile = "esc";
        }
        @Override
        public AbstractCloneable clone() {
            KeyboardConfig result = new KeyboardConfig();
            result.forward = this.forward;
            result.left = this.left;
            result.back = this.back;
            result.right = this.right;
            result.leftRotate = this.leftRotate;
            result.rightRotate = this.rightRotate;
            result.chip = this.chip;
            result.straight = this.straight;
            result.powerUp = this.powerUp;
            result.powerDown = this.powerDown;
            result.dribble = this.dribble;
            result.fastForward = this.fastForward;
            result.replayPause = this.replayPause;
            result.rewind = this.rewind;
            result.openFile = this.openFile;
            result.closeFile = this.closeFile;
            return result;
        }
    }

    public static class DribbleState extends AbstractCloneable {
        public boolean isBack;
        public double omega;
        public double forwardVelocityMax;
        public double backVelocityMax;
        public double forwardAcc;
        public double backAcc;

        public DribbleState() {
            this.isBack = false;
            this.omega = 49.17;
            this.forwardVelocityMax = 3000.0;
            this.backVelocityMax = 3000.0;
            this.forwardAcc = 4400.0;
            this.backAcc = 5400.0;
        }

        public DribbleState clone() {
            DribbleState result = new DribbleState();
            result.isBack = this.isBack;
            result.omega = this.omega;
            result.forwardVelocityMax = this.forwardVelocityMax;
            result.backVelocityMax = this.backVelocityMax;
            result.forwardAcc = this.forwardAcc;
            result.backAcc = this.backAcc;
            return result;
        }
    }

    public static class PathPlannerConfig extends AbstractCloneable {
        public WithPlannerConfig withPlannerConfig;
        public CommonConfig commonConfig;
        public PlannerVisibility plannerVisibility;

        public PathPlannerConfig() {
            this.withPlannerConfig = new WithPlannerConfig();
            this.commonConfig = new CommonConfig();
            this.plannerVisibility = new PlannerVisibility();
        }

        public PathPlannerConfig clone() {
            PathPlannerConfig result = new PathPlannerConfig();
            result.commonConfig = this.commonConfig.clone();
            result.withPlannerConfig = this.withPlannerConfig.clone();
            result.plannerVisibility = this.plannerVisibility.clone();
            return result;
        }

        public static class PlannerVisibility extends AbstractCloneable {
            public boolean dashView;
            public boolean temporaryDestinationView;

            public PlannerVisibility() {
                this.dashView = true;
                this.temporaryDestinationView = false;
            }

            public PlannerVisibility clone() {
                PlannerVisibility result = new PlannerVisibility();
                result.dashView = this.dashView;
                result.temporaryDestinationView = this.temporaryDestinationView;
                return result;
            }
        }

        public static class WithPlannerConfig extends AbstractCloneable {
            public double defaultStep;
            public double minimumStep;
            public double fluctuationStepRange;
            public double velocityApplyToPlannerThreshold;
            public double forceOmniWheelThreshold;
            public double forceTwoWheelThreshold;
            public double restrictionStep;
            public double restrictionObstacleMargin;
            public double defaultAccelCentripetalRatioA;
            public double defaultAccelCentripetalRatioB;

            public WithPlannerConfig() {
                this.defaultStep = 80.0;
                this.minimumStep = 20.0;
                this.fluctuationStepRange = 20.0;
                this.velocityApplyToPlannerThreshold = 300.0;
                this.restrictionStep = 20.0;
                this.forceOmniWheelThreshold = 300.0;
                this.forceTwoWheelThreshold = 2000.0;
                this.restrictionObstacleMargin = 20.0;
                this.defaultAccelCentripetalRatioA = 0.510;
                this.defaultAccelCentripetalRatioB = 0.390;
            }

            @Override
            public WithPlannerConfig clone() {
                WithPlannerConfig result = new WithPlannerConfig();
                result.defaultStep = this.defaultStep;
                result.minimumStep = this.minimumStep;
                result.fluctuationStepRange = this.fluctuationStepRange;
                result.velocityApplyToPlannerThreshold = this.velocityApplyToPlannerThreshold;
                result.forceTwoWheelThreshold = this.forceTwoWheelThreshold;
                result.forceOmniWheelThreshold = this.forceOmniWheelThreshold;
                result.restrictionStep = this.restrictionStep;
                result.restrictionObstacleMargin = this.restrictionObstacleMargin;
                result.defaultAccelCentripetalRatioA = this.defaultAccelCentripetalRatioA;
                result.defaultAccelCentripetalRatioB = this.defaultAccelCentripetalRatioB;
                return result;
            }
        }
        public static class CommonConfig extends AbstractCloneable {
            public double pointIntegrationMarginRatio;
            public double timeLimitSplitRatio;
            public int tangentSearchDepth;
            public double tangentDeltaRatio;
            public double minimumMargin;
            public boolean enableSmoothingCalculation;
            public boolean enableSmoothingOutput;
            public double angleLimit;
            public double obstacleIntegrationStep;

            public CommonConfig() {
                this.pointIntegrationMarginRatio = 0.9;
                this.timeLimitSplitRatio = 0.45;
                this.tangentSearchDepth = 10;
                this.tangentDeltaRatio = 1.0;
                this.minimumMargin = 200.0;
                this.obstacleIntegrationStep = 10.0;
                this.enableSmoothingCalculation = false;
                this.enableSmoothingOutput = true;
                this.angleLimit = 2.2;
            }

            @Override
            public CommonConfig clone() {
                CommonConfig result = new CommonConfig();
                result.pointIntegrationMarginRatio = this.pointIntegrationMarginRatio;
                result.timeLimitSplitRatio = this.timeLimitSplitRatio;
                result.tangentSearchDepth = this.tangentSearchDepth;
                result.minimumMargin = this.minimumMargin;
                result.enableSmoothingOutput = this.enableSmoothingOutput;
                result.enableSmoothingCalculation = this.enableSmoothingCalculation;
                result.angleLimit = this.angleLimit;
                result.obstacleIntegrationStep = this.obstacleIntegrationStep;
                return result;
            }
        }
    }

    /**
     * ドリブル強化学習 (ボールに寄って向きを作りパス・シュートする) のデータ収集設定
     * 長さの単位は mm, 時間の単位は s
     */
    public static class RLDribble extends AbstractCloneable {
        // 収集
        public EnumRLEnvironment environment; // シミュレータか実機か
        public EnumRLResetMode resetMode;   // 初期配置の作り方 (実機ではFROM_BALLのみ)
        public int targetEpisodes;          // 自動で収集するエピソード数
        public boolean saveData;            // falseなら動作のみでファイルに保存しない
        public String outputDir;            // 保存先 (この下に実行ごとのディレクトリを作る)
        public int robotId;                 // 動かすロボットID (-1なら見えている最初のロボット)
        public EnumRLActionSource actionSource;
        public String policyPath;           // actionSourceがPOLICY系のときに使う方策 (RL_testのexport_onnx.pyで作る.onnx)
        public long seed;                   // 0なら時刻から決める
        public boolean drawScenario;        // 開始位置とキック目標をフィールドに描画する
        // エピソード
        public double maxEpisodeTime;       // この時間内に蹴らなければ打ち切り
        public double settleTime;           // テレポート後に静止させて待つ時間
        public double resetPause;           // エピソード終了から次のテレポートまで停止させる時間
        public int maxResetRetries;         // 連続でリセットに失敗したら収集を止める
        public double approachTimeout;      // FROM_BALL: この時間内に開始位置に着かなければ初期配置を作り直す
        public boolean parkOtherRobots;     // 使わないロボットをタッチラインの外に並べる (TELEPORTのみ)
        // 初期配置
        public double spawnMargin;          // フィールド境界からの余白
        public double ballStartDistance;    // 開始時のロボット中心からボールまでの距離 (ボールはロボットの正面)
        public double shootRatio;           // シュートにするエピソードの割合 (残りはパス)
        public double passMinDist;          // ボールからパス目標までの距離
        public double passMaxDist;
        public double shootMaxDist;         // シュート時のボールと相手ゴール中心の最大距離
        public double shootAimWidth;        // シュート目標をゴール幅のこの割合の範囲に置く
        // キック (教師ActionKick)
        public double shootSpeed;           // シュートのボール速度 [mm/s] (パスはKickConverter.passSpeed)
        public int kickHoldTime;            // ActionKickが向きを合わせてから蹴るまで待つフレーム数
        // 終了判定
        public double dribbleLimit;         // ボールを保持した地点からこの距離を超えて運んだら失敗 (ルールの1m制限)
        public double holdDistance;         // ドリブラー前のボール位置からのずれがこれ未満で保持
        public double holdAngle;            // ロボットの向きとボール方向の差がこれ未満で保持 [rad]
        public double lostDistance;         // 蹴る前にロボット中心とボールの距離がこれを超えたらロスト
        public double kickDetectSpeed;      // キック指令後にボール速度がこれを超えたら蹴ったとみなす [mm/s]
        public double evalMaxTime;          // 蹴った後に結果を待つ最大時間
        public double passSuccessRadius;    // パス: ボールの軌道が目標からこの距離以内を通れば成功
        // 探索ノイズ (TEACHER_NOISE / POLICY_NOISE, vx・vy・omegaのみ)
        public double noiseVelStd;          // [mm/s]
        public double noiseOmegaStd;        // [rad/s]
        public double noiseCorrelation;     // AR(1)係数 0なら白色ノイズ
        public int settleDribble;           // リセット中のドリブラー出力
        // 報酬
        public double approachWeight;       // 保持していないときにドリブラーがボールに1m近づいたときの報酬
        public double aimWeight;            // 保持しているときに目標方向との角度差が1rad減ったときの報酬
        public double successReward;
        public double failurePenalty;       // ロスト・場外・ドリブル超過
        public double missWeight;           // 外したとき: 目標に最も近づいた距離 1m あたりのペナルティ (最大failurePenalty)
        public double timePenalty;          // 1秒あたり
        public double actionRatePenalty;    // 正規化した行動変化量の二乗に掛ける

        public RLDribble() {
            this.environment = EnumRLEnvironment.SIMULATOR;
            this.resetMode = EnumRLResetMode.TELEPORT;
            this.targetEpisodes = 100;
            this.saveData = true;
            this.outputDir = "rl_data";
            this.robotId = -1;
            this.actionSource = EnumRLActionSource.TEACHER;
            this.policyPath = "";
            this.seed = 0L;
            this.drawScenario = true;
            this.maxEpisodeTime = 8.0;
            this.settleTime = 0.6;
            this.resetPause = 1.0;
            this.maxResetRetries = 5;
            this.approachTimeout = 15.0;
            this.parkOtherRobots = true;
            this.spawnMargin = 600.0;
            this.ballStartDistance = 500.0;
            this.shootRatio = 0.5;
            this.passMinDist = 1500.0;
            this.passMaxDist = 4000.0;
            this.shootMaxDist = 4000.0;
            this.shootAimWidth = 0.6;
            this.shootSpeed = 6000.0;
            this.kickHoldTime = 20;
            this.dribbleLimit = 1000.0;
            this.holdDistance = 100.0;
            this.holdAngle = 0.8;
            this.lostDistance = 1000.0;
            this.kickDetectSpeed = 1500.0;
            this.evalMaxTime = 3.0;
            this.passSuccessRadius = 150.0;
            this.noiseVelStd = 300.0;
            this.noiseOmegaStd = 1.0;
            this.noiseCorrelation = 0.8;
            this.settleDribble = 12;
            this.approachWeight = 0.5;
            this.aimWeight = 0.5;
            this.successReward = 10.0;
            this.failurePenalty = 5.0;
            this.missWeight = 2.0;
            this.timePenalty = 0.5;
            this.actionRatePenalty = 0.05;
        }

        @Override
        public RLDribble clone() {
            RLDribble result = new RLDribble();
            result.environment = this.environment == null ? EnumRLEnvironment.SIMULATOR : this.environment;
            result.resetMode = this.resetMode == null ? EnumRLResetMode.TELEPORT : this.resetMode;
            result.targetEpisodes = this.targetEpisodes;
            result.saveData = this.saveData;
            result.outputDir = this.outputDir;
            result.robotId = this.robotId;
            result.actionSource = this.actionSource == null ? EnumRLActionSource.TEACHER : this.actionSource;
            result.policyPath = this.policyPath == null ? "" : this.policyPath;
            result.seed = this.seed;
            result.drawScenario = this.drawScenario;
            result.maxEpisodeTime = this.maxEpisodeTime;
            result.settleTime = this.settleTime;
            result.resetPause = this.resetPause;
            result.maxResetRetries = this.maxResetRetries;
            result.approachTimeout = this.approachTimeout;
            result.parkOtherRobots = this.parkOtherRobots;
            result.spawnMargin = this.spawnMargin;
            result.ballStartDistance = this.ballStartDistance;
            result.shootRatio = this.shootRatio;
            result.passMinDist = this.passMinDist;
            result.passMaxDist = this.passMaxDist;
            result.shootMaxDist = this.shootMaxDist;
            result.shootAimWidth = this.shootAimWidth;
            result.shootSpeed = this.shootSpeed;
            result.kickHoldTime = this.kickHoldTime;
            result.dribbleLimit = this.dribbleLimit;
            result.holdDistance = this.holdDistance;
            result.holdAngle = this.holdAngle;
            result.lostDistance = this.lostDistance;
            result.kickDetectSpeed = this.kickDetectSpeed;
            result.evalMaxTime = this.evalMaxTime;
            result.passSuccessRadius = this.passSuccessRadius;
            result.noiseVelStd = this.noiseVelStd;
            result.noiseOmegaStd = this.noiseOmegaStd;
            result.noiseCorrelation = this.noiseCorrelation;
            result.settleDribble = this.settleDribble;
            result.approachWeight = this.approachWeight;
            result.aimWeight = this.aimWeight;
            result.successReward = this.successReward;
            result.failurePenalty = this.failurePenalty;
            result.missWeight = this.missWeight;
            result.timePenalty = this.timePenalty;
            result.actionRatePenalty = this.actionRatePenalty;
            return result;
        }
    }

    public static class ConfigEditorConfig extends AbstractCloneable {
        public String loadShortcut;
        public String saveAsShortcut;
        public String saveShortcut;
        public String applyShortcut;

        public ConfigEditorConfig() {
            this.loadShortcut = "ctrl L";
            this.saveAsShortcut = "ctrl shift S";
            this.saveShortcut = "ctrl S";
            this.applyShortcut = "ctrl A";
        }

        @Override
        public ConfigEditorConfig clone() {
            ConfigEditorConfig result = new ConfigEditorConfig();
            result.loadShortcut = this.loadShortcut;
            result.saveAsShortcut = this.saveAsShortcut;
            result.saveShortcut = this.saveShortcut;
            result.applyShortcut = this.applyShortcut;
            return result;
        }
    }
}
