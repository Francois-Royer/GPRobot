package gprobot;

import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;
import robocode.control.events.TurnEndedEvent;
import robocode.control.snapshot.IRobotSnapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.security.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static gprobot.RobocodeConf.*;
import static gprobot.RobotCodeUtil.updateRunner;

public class BattleRunner {
    static Logger log = Logger.getLogger(BattleRunner.class.getName());
    RobocodeEngine engine;
    BattlefieldSpecification battlefield;
    int runnerId;
    String runnerPath;
    String[] opponentsName;

    public BattleRunner(int runnerId, String runnerPath) {
        super();
        this.runnerId = runnerId;
        this.runnerPath = FileSystems.getDefault().getPath(".").toAbsolutePath().toString();
        battlefield = new BattlefieldSpecification(800, 600);
    }

    public static void main(String[] args) {
        try {
            int runnerId = Integer.parseInt(args[0]);
            String runnerPath = args[1];
            BattleRunner runner = new BattleRunner(runnerId, runnerPath);
            System.out.println(MSG + " " + READY);
            runner.startCmdReader();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "main", ex);
            System.exit(1);
        }
    }

    static double getScore(BattleResults result) {
        /*return result.getSurvival()
            + result.getLastSurvivorBonus()*4
            + result.getBulletDamage()
            + result.getBulletDamageBonus()
            + result.getRamDamage()
            + result.getRamDamageBonus();*/
        //return result.getScore();
        return result.getBulletDamage()
            + result.getBulletDamageBonus();

    }

    public RobotSpecification[] getRobotSpecification(String bot, String[] oponents) {
        String robotNames = bot + ',' + String.join(",", oponents);
        return engine.getLocalRepository(robotNames);
    }

    public RobotSpecification[] getRobotSpecification(String bot, String oponent) {
        String robotNames = bot + ',' + oponent;
        return engine.getLocalRepository(robotNames);
    }

    public void setOpponentsName(String[] names) {
        this.opponentsName = names;
    }

    public double getRobotFitness(String robot) throws IOException {
        updateRunner(new File(runnerPath), robot);
        return getRobotFitness(robot, opponents);
    }

    public double getRobotFitness(String robot, String[] opponentsRobots) {
        double fitnessScore = 0;

        if (battleType == BattleType.MELEE || battleType == BattleType.ALL)
            fitnessScore = runBattle(robot, opponentsRobots);

        if (battleType == BattleType.DUEL || battleType == BattleType.ALL)
            fitnessScore = (fitnessScore * opponentsRobots.length + Stream.of(opponentsRobots)
                    .mapToDouble(opponent -> runBattle(robot, new String[]{opponent}))
                    .sum()) / opponents.length;

        if (battleType == BattleType.ALL)
            fitnessScore /= 2;

        return fitnessScore;
    }

    private double runBattle(String robot, String[] opponentsRobots) {
        String robotClass = TARGET_PACKAGE + "." + robot;
        BattleObserver battleObserver;
        System.gc();
        engine = new RobocodeEngine(new File(runnerPath));
        battleObserver = new BattleObserver(robot);
        engine.addBattleListener(battleObserver);
        RobotSpecification[] selectedBots = getRobotSpecification(robotClass, opponentsRobots);
        BattleSpecification battleSpec = new BattleSpecification(ROUNDS, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);
        engine.close();
        engine.removeBattleListener(battleObserver);
        return computeFitness(robotClass, battleObserver);
    }

    private double computeFitness(String robot, BattleObserver battleObserver) {
        BattleResults[] results = battleObserver.getResults();

        double botScore = Stream.of(results)
                .filter(result -> robot.equals(result.getTeamLeaderName()))
                .mapToDouble(br -> getScore(br))
                .sum();

        return results.length > 1 ? botScore/(results.length-1)/100 : botScore/100;
    }


    public void startCmdReader() {
        String line = null;

        try (Socket controlerSockert = new Socket("localhost", 33000 + runnerId)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(controlerSockert.getInputStream()));
            while ((line = in.readLine()) != null) {
                if (line.startsWith(SET_OPPONENTS))
                    setOpponentsName(line.substring(MSG.length() + 1).split(","));
                else if (line.startsWith(GET_FITNESS)) {
                    String robot = line.substring(GET_FITNESS.length() + 1);
                    System.out.println(MSG + " " + getRobotFitness(robot));
                }
            }
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "printMsg", ioe);
        }
    }
}

// based on example from Robocode Control API JavaDocs
class BattleObserver extends BattleAdaptor {

    String robotName;
    private BattleResults[] results;
    private long roundDuration = 0;
    private double remainEnergy = 0;
    private BattleErrorEvent error = null;


    public BattleObserver(String robotName) {
        this.robotName = TARGET_PACKAGE + "." + robotName;
    }

    @Override
    public void onBattleCompleted(BattleCompletedEvent e) {
        results = e.getIndexedResults();
    }

    @Override
    public void onTurnEnded(TurnEndedEvent e) {
        Optional<IRobotSnapshot> ors = Stream.of(e.getTurnSnapshot().getRobots()).filter(robot -> robot.getName().equals(robotName))
                .findFirst();
        if (ors.isPresent())
            remainEnergy += ors.get().getEnergy();
        roundDuration += e.getTurnSnapshot().getTurn();
    }

    @Override
    public void onBattleError(BattleErrorEvent e) {
        //Logger.getLogger(this.getClass().getName()).severe("Battle error: " + e.getError());
        this.error = e;
    }

    public BattleResults[] getResults() {
        return results;
    }

    public long getRoundDuration() {
        return roundDuration;
    }

    public double getRemainEnergy() {
        return remainEnergy;
    }

    public BattleErrorEvent getError() {
        return error;
    }
}
