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
import java.nio.file.FileSystems;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static gprobot.RobocodeConf.GET_FITNESS;
import static gprobot.RobocodeConf.MSG;
import static gprobot.RobocodeConf.ONE2ONE;
import static gprobot.RobocodeConf.READY;
import static gprobot.RobocodeConf.SET_OPPONENTS;
import static gprobot.RobocodeConf.TARGET_PACKAGE;
import static gprobot.RobocodeConf.opponents;
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
        engine = new RobocodeEngine(new File(runnerPath));
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

    static int getScore(BattleResults result) {
        /*return result.getSurvival()
            + result.getLastSurvivorBonus()*4
            + result.getBulletDamage()
            + result.getBulletDamageBonus()
            + result.getRamDamage()
            + result.getRamDamageBonus();*/
        return result.getScore();
        //return result.getScore();
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
        String robotClass = TARGET_PACKAGE + "." + robot;
        BattleObserver battleObserver = new BattleObserver(robot);
        engine.addBattleListener(battleObserver);

        RobotSpecification[] selectedBots = getRobotSpecification(robotClass, opponentsRobots);
        int rounds = RobocodeConf.ROUNDS;// * opponentsRobots.length;
        BattleSpecification battleSpec = new BattleSpecification(rounds, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);
        double fitnessScore = computeFitness(robotClass, battleObserver);
        engine.close();

        if (opponentsRobots.length > 1 && ONE2ONE)
            fitnessScore = (fitnessScore * opponentsRobots.length + Stream.of(opponentsRobots).mapToDouble(opponent ->
                                                                                                                   getRobotFitness(robot,
                                                                                                                                   new String[]{opponent})
            ).sum()) / 2 / opponents.length;

        return fitnessScore;
    }

    private double computeFitness(String robot, BattleObserver battleObserver) {
        BattleResults[] results = battleObserver.getResults();
        Optional<BattleResults> br = Stream.of(results).filter(result -> robot.equals(result.getTeamLeaderName()))
                                           .findFirst();

        int botScore = br.isPresent() ? getScore(br.get()) : 0;
        int totalScore = Stream.of(results).mapToInt(BattleRunner::getScore).sum();
        if (totalScore == 0)
            return 0;

        return (double) botScore / (totalScore) * 100;
    }

    public void startCmdReader() {
        String line = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
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
        Logger.getLogger(this.getClass().getName()).severe("Battle error: " + e.getError());
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

}
