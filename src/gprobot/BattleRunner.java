package gprobot;

import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static gprobot.RobocodeConf.roboCodePath;
import static gprobot.RunGP.*;

import java.util.Arrays;
import java.util.stream.Stream;

public class BattleRunner {
    RobocodeEngine engine;
    String runnerPath;
    String[] opponentsName;
    double[] opponentsSkill;

    public static void main(String args[]) {
        String runnerPath = args[0];
        double[] oSkill = Arrays.stream(args[2].split(",")).mapToDouble(s -> Double.parseDouble(s)).toArray();
        BattleRunner runner = new BattleRunner(runnerPath, args[1].split(","), oSkill);
        File runnerCommand = new File(runnerPath, "cmd");

        try {
            while (true) {
                RobotCodeUtil.waitForFileCreated(runnerCommand);
                try (BufferedReader reader = new BufferedReader(new FileReader(runnerCommand))) {
                    String cmd = reader.readLine();
                    switch (cmd) {
                        case "BATTLE":
                            runner.runBattle(reader.readLine());
                            break;
                        case "STOP":
                            System.exit(0);
                            break;
                        default:
                            Logger.getLogger(BattleRunner.class.getName()).warning("Ignore invalid received command");
                            break;
                    }
                }
                runnerCommand.delete();
            }
        } catch (Exception ex) {
            Logger.getLogger(BattleRunner.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    public BattleRunner(String runnerPath, String[] opponents, double[] opponentsSkills) {
        this.runnerPath = runnerPath;
        this.opponentsName = opponents;
        this.opponentsSkill = opponentsSkills;
        engine = new RobocodeEngine(new File(runnerPath));

    }

    public void runBattle(String bots) {
        long begin = System.currentTimeMillis();

        double[] fitness = runBatchWithSamples(bots.split(","));
        try {

            writeFitnessToFile(new File(runnerPath, "fitness.bin"), fitness);
        } catch (IOException ex) {
            Logger.getLogger(BattleRunner.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        System.out.println("Battle completed time: " + Duration.ofMillis(System.currentTimeMillis() - begin).toString().substring(2));

    }

    public double[] runBatchWithSamples(String[] bots) {
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);
        engine.setVisible(new File(roboCodePath, "visible").exists());

        BattlefieldSpecification battlefield = new BattlefieldSpecification(800, 600);

        double[] fitnesses = new double[bots.length];
        System.out.println("Running battles against oponents batch ");

        for (int i = 0; i < bots.length; i++) {
            double fitnessScore = 0;
            for (int j = 0; j < opponentsName.length; j++) {
                String bot = bots[i];
                String opponent = opponentsName[j];

                RobotSpecification[] selectedBots = engine.getLocalRepository(bot + ", " + opponent);
                BattleSpecification battleSpec = new BattleSpecification(RobocodeConf.ROUNDS, battlefield, selectedBots);
                engine.runBattle(battleSpec, true);

                BattleResults[] results = battleObserver.getResults();
                int myBot = results[0].getTeamLeaderName().equals(bot) ? 0 : 1;
                int opBot = myBot == 1 ? 0 : 1;
                int botScore = results[myBot].getScore();

                double totalScore = (double) botScore + results[opBot].getScore();
                double roundFitness = (botScore + RobocodeConf.BATTLE_HANDICAP) / (totalScore + RobocodeConf.BATTLE_HANDICAP);

                fitnessScore += roundFitness * opponentsSkill[j];
            }
            if (opponents.length > 1) {
                // More than one oponent is selected,
                final String bot = bots[i];
                RobotSpecification[] selectedBots = getRobotSpecification(bot, opponents);
                BattleSpecification battleSpec = new BattleSpecification(RobocodeConf.ROUNDS, battlefield, selectedBots);
                engine.runBattle(battleSpec, true);
                BattleResults[] results = battleObserver.getResults();
                int botSurvival = Stream.of(results).filter(br -> bot.equals(br.getTeamLeaderName()))
                        .findFirst().get().getSurvival();
                int totalSurvival = Stream.of(results).mapToInt(br -> br.getSurvival()).sum();

                double survivalFitness = ((double) botSurvival + RobocodeConf.BATTLE_HANDICAP) / (totalSurvival + RobocodeConf.BATTLE_HANDICAP);
                fitnesses[i] = (2 * fitnessScore + survivalFitness) / 3;
            } else {
                fitnesses[i] = fitnessScore;    // take average of each round score
            }
        }
        engine.close();
        return fitnesses;
    }

    public RobotSpecification[] getRobotSpecification(String bot, String[] oponents) {
        String robotNames = bot + ',' + String.join(",", oponents);
        return engine.getLocalRepository(robotNames);
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
