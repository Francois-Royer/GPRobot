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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static gprobot.RobocodeConf.targetPakage;
import static gprobot.RobotCodeUtil.getRunnerUrl;
import static gprobot.RunGP.opponents;

public class BattleRunner extends UnicastRemoteObject implements RMIGPRobotBattleRunner {

    RobocodeEngine engine;
    BattlefieldSpecification battlefield;
    String runnerPath;
    String[] opponentsName;
    double[] opponentsSkill;

    public static void main(String args[]) {
        try {
            int runnerId = Integer.parseInt(args[0]);
            String runnerPath = args[1];
            String url = getRunnerUrl(InetAddress.getLocalHost().getHostAddress(), runnerId);
            BattleRunner runner = new BattleRunner(runnerPath);
            Naming.rebind(url, runner);
        } catch (Exception ex) {
            Logger.getLogger(BattleRunner.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    public BattleRunner(String runnerPath) throws RemoteException {
        this.runnerPath = runnerPath;
        engine = new RobocodeEngine(new File(runnerPath));
        battlefield = new BattlefieldSpecification(800, 600);
    }

    public RobotSpecification[] getRobotSpecification(String bot, String[] oponents) {
        String robotNames = bot + ',' + String.join(",", oponents);
        return engine.getLocalRepository(robotNames);
    }

    int getTotalScore(BattleResults result) {
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
        String robotClass = targetPakage+ "."+ robot;
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);

        RobotSpecification[] selectedBots = getRobotSpecification(robotClass, opponents);
        BattleSpecification battleSpec = new BattleSpecification(RobocodeConf.ROUNDS, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);
        double fitnessScore = computeFitness(robotClass, battleObserver.getResults());
        if (opponents.length > 1) {
            // More than one oponents, make also one to one battle against each oppenents
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

    @Override
    public void stopRunner() throws RemoteException {
        System.exit(0);
    }

    private double computeFitness(String robot, BattleResults[] results) {
        int botScore = getTotalScore(Stream.of(results).filter(br -> robot.equals(br.getTeamLeaderName()))
            .findFirst().get());
        int totalScore = getTotalScore(results[0]) + Stream.of(results).mapToInt(br -> getTotalScore(br)).sum();

        return ((double) botScore + RobocodeConf.BATTLE_HANDICAP) / (totalScore + RobocodeConf.BATTLE_HANDICAP);
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

        System.out.println("Error running battle: " + e.getError());
    }

    public BattleResults[] getResults() {
        return results;
    }

}
