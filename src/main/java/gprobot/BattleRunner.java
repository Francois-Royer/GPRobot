package gprobot;

import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

import java.io.File;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static gprobot.RobocodeConf.ONE2ONE;
import static gprobot.RobocodeConf.TARGET_PACKAGE;
import static gprobot.RobotCodeUtil.getRunnerUrl;
import static gprobot.RobocodeConf.opponents;

public class BattleRunner extends UnicastRemoteObject implements RMIGPRobotBattleRunner {
    static Logger log = Logger.getLogger(BattleRunner.class.getName());
    transient RobocodeEngine engine;
    transient BattlefieldSpecification battlefield;
    transient String runnerPath;
    transient String[] opponentsName;

    public static void main(String[] args) {
        try {
            int runnerId = Integer.parseInt(args[0]);
            String runnerPath = args[1];
            String url = getRunnerUrl(InetAddress.getLocalHost().getHostAddress(), runnerId);
            BattleRunner runner = new BattleRunner(runnerPath);
            Naming.rebind(url, runner);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "main", ex);
            System.exit(1);
        }
    }

    public BattleRunner(String runnerPath) throws RemoteException {
        super();
        this.runnerPath = runnerPath;
        engine = new RobocodeEngine(new File(runnerPath));
        battlefield = new BattlefieldSpecification(800, 600);
    }

    public RobotSpecification[] getRobotSpecification(String bot, String[] oponents) {
        String robotNames = bot + ',' + String.join(",", oponents);
        return engine.getLocalRepository(robotNames);
    }

    public RobotSpecification[] getRobotSpecification(String bot, String oponent) {
        String robotNames = bot + ',' + oponent;
        return engine.getLocalRepository(robotNames);
    }

    static int getScore(BattleResults result) {
        /*return result.getSurvival()
            + result.getLastSurvivorBonus()*4
            + result.getBulletDamage()
            + result.getBulletDamageBonus()
            + result.getRamDamage()
            + result.getRamDamageBonus();*/

        return result.getScore();
    }

    @Override
    public void setOpponentsName(String[] names) throws RemoteException {
        this.opponentsName = names;
    }

    @Override
    public double getRobotFitness(String robot) throws RemoteException {
        return getRobotFitness(robot, opponents);
    }

    public double getRobotFitness(String robot, String[] opponentsRobots) throws RemoteException {
        String robotClass = TARGET_PACKAGE + "."+ robot;
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);

        RobotSpecification[] selectedBots = getRobotSpecification(robotClass, opponentsRobots);
        int rounds = RobocodeConf.ROUNDS * opponentsRobots.length;
        BattleSpecification battleSpec = new BattleSpecification(rounds, battlefield, selectedBots);
        //engine.setVisible(true);
        engine.runBattle(battleSpec, true);
        double fitnessScore = computeFitness(robotClass, battleObserver.getResults());
        engine.close();

        if (opponentsRobots.length > 1 && ONE2ONE)
            fitnessScore = (fitnessScore + Stream.of(opponentsRobots).mapToDouble(opponent -> {
                try {
                    return getRobotFitness(robot, new String[]{opponent});
                } catch (RemoteException e) {
                    log.log(Level.SEVERE, "main", e);
                    System.exit(1);
                }
                return 0;
            }).sum() / opponentsRobots.length) / 2;

        return fitnessScore;
    }

    private double computeFitness(String robot, BattleResults[] results) {
        Optional<BattleResults> br = Stream.of(results).filter(result -> robot.equals(result.getTeamLeaderName())).findFirst();
        int botScore = br.isPresent() ? getScore(br.get()) : 0;
        int totalScore = Stream.of(results).mapToInt(BattleRunner::getScore).sum();

        if (totalScore == 0) {
            // OMG, these robots are so poor that they do not score a single point
            return 0;
        }
        return (double) botScore / totalScore * 100;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BattleRunner that = (BattleRunner) o;

        if (!runnerPath.equals(that.runnerPath)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(opponentsName, that.opponentsName);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + runnerPath.hashCode();
        result = 31 * result + Arrays.hashCode(opponentsName);
        return result;
    }
}

// based on example from Robocode Control API JavaDocs
class BattleObserver extends BattleAdaptor {

    robocode.BattleResults[] results;

    @Override
    public void onBattleCompleted(BattleCompletedEvent e) {
        results = e.getIndexedResults();
    }

    @Override
    public void onBattleError(BattleErrorEvent e) {

        Logger.getLogger(this.getClass().getName()).severe("Battle error: " + e.getError());
    }

    public BattleResults[] getResults() {
        return results;
    }
}
