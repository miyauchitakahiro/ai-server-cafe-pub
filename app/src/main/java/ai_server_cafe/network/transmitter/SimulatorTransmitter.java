package ai_server_cafe.network.transmitter;

import ai_server_cafe.model.network.OptionalCommand;
import ai_server_cafe.model.network.SendCommand;
import ai_server_cafe.network.proto.ssl.simulation.SslGcCommon;
import ai_server_cafe.network.proto.ssl.simulation.SslSimulationControl;
import ai_server_cafe.network.proto.ssl.simulation.SslSimulationRobotControl;
import ai_server_cafe.network.proto.ssl.simulation.SslSimulationRobotFeedback;
import ai_server_cafe.network.radio.EnumRadioType;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.updater.UpdaterOptionalCommand;
import ai_server_cafe.updater.UpdaterRobotFeedback;
import ai_server_cafe.util.EnumKickType;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.math.KickConverter;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorTransmitter extends UDPMulticastTransmitter {
    private static SimulatorTransmitter instance = null;
    private final List<SendCommand> buffer;

    private SimulatorTransmitter() {
        super("ssl-sim-transmitter");
        this.buffer = new ArrayList<>();
    }

    public static SimulatorTransmitter getInstance() {
        if (instance == null) {
            instance = new SimulatorTransmitter();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public void sendOptionalCommand(OptionalCommand optionalCommand) {
        UpdaterOptionalCommand.getInstance().update(optionalCommand);
        SslSimulationControl.SimulatorControl.Builder controlBuilder = SslSimulationControl.SimulatorControl.newBuilder();
        if (optionalCommand.getBallPos().isPresent()) {
            SslSimulationControl.TeleportBall.Builder builderBall = SslSimulationControl.TeleportBall.newBuilder().setX(0f);
            builderBall.setX((float) (optionalCommand.getBallPos().get().getX() / 1000.0));
            builderBall.setY((float) (optionalCommand.getBallPos().get().getY() / 1000.0));
            if (optionalCommand.getBallVel().isPresent()) {
                builderBall.setVx((float) (optionalCommand.getBallVel().get().getX() / 1000.0));
                builderBall.setVy((float) (optionalCommand.getBallVel().get().getY() / 1000.0));
            } else {
                builderBall.setVx(0.0f);
                builderBall.setVy(0.0f);
            }
            controlBuilder.setTeleportBall(builderBall.build());
        }
        for (Map.Entry<Pair<TeamColor, Integer>, Vector2D> entry : optionalCommand.getRobotPos().entrySet()) {
            controlBuilder.addTeleportRobot(SslSimulationControl.TeleportRobot.newBuilder()
                    .setId(SslGcCommon.RobotId.newBuilder().setId(entry.getKey().getSecond())
                            .setTeam((entry.getKey().getFirst() == TeamColor.YELLOW) ? SslGcCommon.Team.YELLOW : SslGcCommon.Team.BLUE))
                    .setX((float) (entry.getValue().getX() / 1000.0))
                    .setY((float) (entry.getValue().getY() / 1000.0))
                    .setOrientation(0f)
                    .setVX(0f)
                    .setVY(0f)
                    .setVAngular(0f)
                    .build());
        }
        this.sendControl(controlBuilder.build());
    }

    /**
     * SimulatorControl (テレポート等) をそのまま送る
     * OptionalCommandでは表現できないロボットの向きなどを指定したいときに使う
     */
    public void sendControl(SslSimulationControl.SimulatorControl control) {
        if (this.socket == null || this.hostAddress == null) {
            this.logger.warn("simulator transmitter is not started");
            return;
        }
        SslSimulationControl.SimulatorCommand command = SslSimulationControl.SimulatorCommand.newBuilder().setControl(control).build();
        final int controlPort = ConfigManager.getInstance().getConfig().simControlPort;
        final byte[] sendBuf = command.toByteArray();
        try {
            final DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, new InetSocketAddress(this.hostAddress, controlPort));
            this.socket.send(packet);
        } catch (SocketException e) {
            this.logger.error("socket error occurred at single command sender");
        } catch (IOException e) {
            this.logger.error("socket io error occurred at single command sender");
        }
    }

    /**
     * シミュレータからのRobotControlResponseを受信する
     * 送信はsendCommandsが直接行うので親クラスの送信処理は使わない
     */
    @Override
    protected void loop() {
        if (this.socket == null || this.socket.isClosed())
            return;
        final byte[] receiveBuf = new byte[4096];
        final DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
        try {
            this.socket.receive(packet);
        } catch (SocketTimeoutException e) {
            return;
        } catch (IOException e) {
            if (!this.isInterrupted())
                this.logger.debug("simulator response could not be received");
            return;
        }
        final int fromPort = packet.getPort();
        final int bluePort = ConfigManager.getInstance().getConfig().simBluePort;
        final int yellowPort = ConfigManager.getInstance().getConfig().simYellowPort;
        // 制御ポートからのSimulatorResponseは対象外
        if (fromPort != bluePort && fromPort != yellowPort)
            return;
        final TeamColor color = fromPort == yellowPort ? TeamColor.YELLOW : TeamColor.BLUE;
        try {
            SslSimulationRobotFeedback.RobotControlResponse response = SslSimulationRobotFeedback.RobotControlResponse
                    .parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
            for (SslSimulationRobotFeedback.RobotFeedback feedback : response.getFeedbackList()) {
                if (feedback.hasDribblerBallContact())
                    UpdaterRobotFeedback.getInstance().updateBallContact(color, feedback.getId(), feedback.getDribblerBallContact());
            }
        } catch (InvalidProtocolBufferException e) {
            this.logger.debug("invalid robot control response");
        }
    }

    @Override
    protected int getTimeout() {
        return 10;
    }

    @Override
    public void sendCommands(@NotNull List<SendCommand> commandList) {
        Map<TeamColor, SslSimulationRobotControl.RobotControl.Builder> robotControlMap = new HashMap<>();
        for (SendCommand c : commandList) {
            double theta = c.getKickFlag().getKey() == EnumKickType.CHIP ? FastMath.PI / 3.0 : 0.0;
            SslSimulationRobotControl.MoveLocalVelocity moveLocalVelocity = SslSimulationRobotControl.MoveLocalVelocity.newBuilder()
                    .setAngular((float) c.getOmega())
                    .setForward((float) (c.getVx() / 1000.0))
                    .setLeft((float) (c.getVy() / 1000.0))
                    .build();
            SslSimulationRobotControl.RobotMoveCommand robotMoveCommand = SslSimulationRobotControl.RobotMoveCommand.newBuilder()
                    .setLocalVelocity(moveLocalVelocity)
                    .build();
            SslSimulationRobotControl.RobotCommand robotCommand = SslSimulationRobotControl.RobotCommand.newBuilder()
                    .setId(c.getId())
                    .setDribblerSpeed(500.0F * c.getDribble())
                    .setKickAngle((float)FastMath.toDegrees(theta))
                    .setKickSpeed(c.getKickFlag().getKey() == EnumKickType.NONE ? 0.0F
                            : (float) (KickConverter.toSpeed(c.getId(), c.getKickFlag(), c.getColor()) / 1000.0))
                    .setMoveCommand(robotMoveCommand)
                    .build();
            if (!robotControlMap.containsKey(c.getColor())) {
                robotControlMap.put(c.getColor(), SslSimulationRobotControl.RobotControl.newBuilder());
            }
            robotControlMap.get(c.getColor()).addRobotCommands(robotCommand);
        }
        for (TeamColor color : robotControlMap.keySet()) {
            this.port = color.isYellow() ? ConfigManager.getInstance().getConfig().simYellowPort :
                    ConfigManager.getInstance().getConfig().simBluePort;
            final byte[] sendBuf = robotControlMap.get(color).build().toByteArray();
            try {
                this.socket.setSendBufferSize(sendBuf.length);
                final DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, new InetSocketAddress(this.hostAddress, this.port));
                this.socket.send(packet);
            } catch (SocketException e) {
                this.logger.error("socket error occurred at single command sender");
            } catch (IOException e) {
                this.logger.error("socket io error occurred at single command sender");
            }
        }
    }

    @Override
    public synchronized void sendCommand(@NotNull SendCommand c) {
        this.buffer.add(c);
    }

    public synchronized void sendBuffer() {
        this.sendCommands(this.buffer);
        this.buffer.clear();
    }

    @Override
    public EnumRadioType getType() {
        return EnumRadioType.SIMULATOR;
    }
}
