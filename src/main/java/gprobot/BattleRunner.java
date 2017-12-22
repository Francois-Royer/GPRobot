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

import static gprobot.RobocodeConf.TARGET_PACKAGE;
import static gprobot.RobotCodeUtil.getRunnerUrl;
import static gprobot.RunGP.opponents;

public class BattleRunner extends UnicastRemoteObject implements RMIGPRobotBattleRunner {
    static Logger log = Logger.getLogger(BattleRunner.class.getName());
    transient RobocodeEngine engine;
    transient BattlefieldSpecification battlefield;
    transient String runnerPath;
    transient String[] opponentsName;
    transient double[] opponentsSkill;

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

    static int getTotalScore(BattleResults result) {
        return result.getSurvival()
            + result.getLastSurvivorBonus()
            + result.getBulletDamage()
            + result.getBulletDamageBonus()
            + result.getRamDamage()
            + result.getRamDamageBonus();
    }

    @Override
    public void setOpponentsName(String[] names) throws RemoteException {
        this.opponentsName = names;
    }

    @Override
    public void setOpponentsSkills(double[] skills) throws RemoteException {
        this.opponentsSkill = skills;
    }

    @Override
    public double getRobotFitness(String robot) throws RemoteException {
        String robotClass = TARGET_PACKAGE + "."+ robot;
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);

        RobotSpecification[] selectedBots = getRobotSpecification(robotClass, opponents);
        BattleSpecification battleSpec = new BattleSpecification(RobocodeConf.ROUNDS, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);
        double fitnessScore = computeFitness(robotClass, battleObserver.getResults());
        if (opponents.length > 1) {
            // More than one opponents, make also one to one battle against each opponents
            double fitness121 = 0;
            for (int j = 0; j < opponentsName.length; j++) {
                String opponent = opponentsName[j];

                selectedBots = engine.getLocalRepository(robotClass+ ", " + opponent);
                battleSpec = new BattleSpecification(RobocodeConf.ROUNDS, battlefield, selectedBots);
                engine.runBattle(battleSpec, true);
                fitness121 += computeFitness(robotClass, battleObserver.getResults()) * opponentsSkill[j];
            }
            fitnessScore += fitness121;
            fitnessScore /= 2;
        }
        engine.close();
        return fitnessScore;
    }

    private double computeFitness(String robot, BattleResults[] results) {
        Optional<BattleResults> br = Stream.of(results).filter(result -> robot.equals(result.getTeamLeaderName())).findFirst();
        int botScore = br.isPresent() ? getTotalScore(br.get()) : 0;
        int totalScore = Stream.of(results).mapToInt(BattleRunner::getTotalScore).sum();

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
        if (!Arrays.equals(opponentsName, that.opponentsName)) return false;
        return Arrays.equals(opponentsSkill, that.opponentsSkill);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + runnerPath.hashCode();
        result = 31 * result + Arrays.hashCode(opponentsName);
        result = 31 * result + Arrays.hashCode(opponentsSkill);
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
