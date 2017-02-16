package gprobot;

import static gprobot.RobocodeConf.*;
import static gprobot.RobotCodeUtil.*;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.time.Duration;
import java.util.Date;
import java.util.stream.Stream;

import com.sun.org.apache.bcel.internal.generic.POP;
import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;

/**
 * This class represents the main genetic algorithm.
 * It assumes that Robocode is installed at ${user.home}/robocode,
 * and writes all files to the "${user.home}/robocode/robots/sampleex" directory.
 * The robocode.jar library must be included in the build path (${user.home}/robocode/libs/robocode.jar by default)
 *
 * @author Ted Hunter
 */
public class RunGP {

    private static double fitnesses[] = new double[POP_SIZE];
    private static double allAvgFitnesses[] = new double[MAX_GENS];
    private static double avgNumNodes[] = new double[MAX_GENS];

    private static MetaBot
            pool[] = new MetaBot[POP_SIZE],
            newPool[] = new MetaBot[POP_SIZE],
            candidates[] = new MetaBot[MAX_GENS],    // should probably store as String[] of file paths
            bestSoFar = new MetaBot(-1, 0);

    private static String botNames[] = new String[POP_SIZE];
    private static int genCount = 0;

    private static File[] battleRunnerDirs;

    //static String[] opponents = skilledRobots;
    static String[] opponents =sampleRobots;

    public static void main(String args[]) {

        bestSoFar.fitness = 0;

        String opponentsSkill = "1";
        if (opponents.length > 1) {
            double[] skills = getOpponentsSkill(opponents);
            String[] sskills = new String[skills.length];
            for (int i = 0; i < opponents.length; i++) {
                System.out.println(opponents[i] + " skill: " + skills[i]);
                sskills[i] = Double.toString(skills[i]);
            }
            opponentsSkill = String.join(",", sskills);
        }

        initOrRestoreCtx();

        System.out.println("Prepare " + RUNNERS_COUNT + " Runners");
        battleRunnerDirs = prepareBattleRunners(RUNNERS_COUNT, String.join(",", opponents), opponentsSkill);


        // -- EC loop
        long begin = System.currentTimeMillis();
        while (genCount < MAX_GENS) {
            long beginGen = System.currentTimeMillis();

            compilePool();

            for (int i = 0; i < POP_SIZE; i++)
                botNames[i] = pool[i].fileName;

            scoreFitnessOnSet(opponents);

            double totalFitness = 0;
            double avgFitness = 0;
            int best = 0;
            double avgNodeCount = 0;

            for (int i = 0; i < POP_SIZE; i++) {
                totalFitness += (pool[i].fitness = fitnesses[i]);
                if (pool[i].fitness > pool[best].fitness) best = i;
                avgNodeCount += pool[i].countNodes();
            }

            avgNumNodes[genCount] = (avgNodeCount /= POP_SIZE);

            avgFitness = totalFitness / POP_SIZE;
            allAvgFitnesses[genCount] = avgFitness;

            // store the best-in-generation
            candidates[genCount] = pool[best];
            if (pool[best].fitness > bestSoFar.fitness) bestSoFar = pool[best];

            System.out.println("\nROUND " + genCount
                    + "\nAvg. Fitness:\t" + avgFitness + "\t Avg # of nodes: " + avgNumNodes[genCount]
                    + "\nBest In Round:\t" + candidates[genCount].botName + " - " + candidates[genCount].fitness
                    + "\t# nodes " + candidates[genCount].nodeCount
                    + "\nBest So Far:\t" + bestSoFar.botName + " - " + bestSoFar.fitness + "\n");

            storeRunData(genCount, avgFitness, pool[best].fitness, avgNodeCount, pool[best].nodeCount, pool[best].fileName);

            //if(++genCount == MAX_GENS) break;
            genCount++;
            // breed next generation
            System.out.println("In breeding stage");
            breedPool();


            // set newPool as pool, clear newPool
            pool = newPool;
            newPool = new MetaBot[POP_SIZE];

            // delete all old files
            RobotCodeUtil.clearBots(genCount - 1, POP_SIZE, candidates[genCount - 1].memberID);

            try {
                saveCtx();
            } catch (IOException e) {
                Logger.getLogger("GPRobot").log(Level.SEVERE, "Unable to save context for restart", e);
            }

            // Time stat
            long end = System.currentTimeMillis();
            long genTime = end - beginGen;
            long avgTime = (end - begin) / genCount;
            long eta = avgTime * (MAX_GENS - genCount);
            Date finished = new Date(end + eta);
            System.out.println("-------Time stat ---------- ");
            System.out.println("last gen=" + Duration.ofMillis(genTime).toString().substring(2) +
                    ", avg=" + Duration.ofMillis(avgTime).toString().substring(2) +
                    ", eta= " + Duration.ofMillis(eta).toString().substring(2));
            System.out.println("Date Finished: " + finished.toString());

        }

        try {
            for (int i = 0; i < RUNNERS_COUNT; i++) {
                File cmd = new File(battleRunnerDirs[i], "cmd");
                File write = new File(battleRunnerDirs[i], "cmdtmp");

                try (FileWriter out = new FileWriter(write)) {
                    out.write("STOP");
                }
                assert(write.renameTo(cmd));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("-------Second Round Complete!-------");
        for (int i = 0; i < genCount; i++) {
            System.out.println("Round " + i + " average:\t" + allAvgFitnesses[i]);
        }
        for (int i = 0; i < genCount; i++) {
            System.out.println("Round " + i + " avg # nodes:\t" + avgNumNodes[i]);
        }

    }

    ;

    private static void initPool() {
        for (int i = 0; i < POP_SIZE; i++) {
            pool[i] = new MetaBot(0, i);
        }
    }

    private static void compilePool() {
        //long begin = System.currentTimeMillis();
        try {
            String[] srcpool = new String[pool.length];

            for (int i = 0; i < pool.length; i++) {
                pool[i].construct();
                srcpool[i] = pool[i].writeSource();
            }

            compileBots(srcpool);
        } catch (Exception ex) {
            Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, "Fail to Compile robots", ex);
            System.exit(1);
        }
        //System.out.println("compile time: " + Duration.ofMillis(System.currentTimeMillis() - begin).toString().substring(2));
    }

    private static void breedPool() {
        // replicate best in last round
        newPool[0] = candidates[genCount - 1].replicate(genCount, 0);
        // replicate best so far
        newPool[1] = bestSoFar.replicate(genCount, 1);
        // breed next generation

        double geneticOporator;
        int newPop = 2;

        while (newPop < POP_SIZE) {
            geneticOporator = random.nextDouble();
            MetaBot b1 = tournamentSelect();
            
            if ((geneticOporator -= PROB_CROSSOVER) <= 0) {
                MetaBot b2 = tournamentSelect();
                //System.out.println("Crossing over bots " +p1+ " & " +p2+" -> " +newPop);

                newPool[newPop] = b1.crossover(b2, genCount, newPop);
                //newPool[newPop] = pool[tournamentSelect()].crossover(pool[tournamentSelect()], genCount+1, newPop);
            } else if ((geneticOporator -= PROB_MUTATION) <= 0) {
                //System.out.println("Mutating bot");
                newPool[newPop] = b1.mutate(genCount, newPop);
            } else {
                //System.out.println("Replicating Bot");
                newPool[newPop] = b1.replicate(genCount, newPop);
                
            }
            newPop++;
        }
    }

    private static double[] getOpponentsSkill(String[] opponents) {
        RobocodeEngine engine = new RobocodeEngine(new File(roboCodePath));
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);
        engine.setVisible(false);
        RobotSpecification[] selectedBots = engine.getLocalRepository(String.join(",", opponents));
        BattleSpecification battleSpec = new BattleSpecification(ROUNDS, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);

        BattleResults[] results = battleObserver.getResults();
        double totalScore = Stream.of(results).mapToDouble(r -> r.getScore()).sum();

        return Stream.of(results).mapToDouble(r -> r.getScore()).map(d -> d / totalScore).toArray();
    }

    private static void scoreFitnessOnSet(String[] opponents) {
        try {
            // generate battle between member and opponents from samples package   	at java.awt.Component$FlipBufferStrategy.getDrawGraphics(Component.java:4141)
            fitnesses = new double[botNames.length];
            int chunkSize = botNames.length / RUNNERS_COUNT;
            for (int i = 0; i < RUNNERS_COUNT; i++) {
                File workerFolder = battleRunnerDirs[i];
                // update runner with new bot gen
                updateRunner(workerFolder, genCount, i * chunkSize, (i + 1) * chunkSize);

                String[] chunk = new String[chunkSize];
                System.arraycopy(botNames, chunkSize * i, chunk, 0, chunkSize);
                File cmd = new File(workerFolder, "cmd");
                File write = new File(workerFolder, "cmdtmp");

                try (FileWriter out = new FileWriter(write)) {
                    out.write("BATTLE" + System.lineSeparator() +
                            String.join(",", chunk) + System.lineSeparator());
                }
                write.renameTo(cmd);
            }

            for (int i = 0; i < RUNNERS_COUNT; i++) {
                File fitnessFile = new File(battleRunnerDirs[i], "fitness.bin");

                waitForFileCreated(fitnessFile);
                System.arraycopy(readFitnessFromFile(new File(battleRunnerDirs[i], "fitness.bin")), 0, fitnesses, chunkSize * i, chunkSize);
                fitnessFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static MetaBot tournamentSelect() {
        int size = TOURNY_SIZE;
        int subPool[] = new int[size];
        for (int i = 0; i < size; i++) {
            do {
              subPool[i] = random.nextInt(POP_SIZE);
            } while (pool[subPool[i]].selection > 2);
        }
        int best = subPool[0];
        for (int i = 1; i < size; i++)
            if (pool[subPool[i]].fitness > pool[best].fitness) best = subPool[i];
        pool[best].selection++;
        return pool[best];
    }

    public static void writeFitnessToFile(File file, double[] fitness) throws IOException {
        File write = new File(file.getPath() + ".tmp");
        try (ObjectOutputStream dataStream = new ObjectOutputStream(new FileOutputStream(write))) {
            dataStream.writeObject(fitness);
        }
        write.renameTo(file);
    }

    public static double[] readFitnessFromFile(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream dataStream = new ObjectInputStream(new FileInputStream(file))) {
            return (double[]) dataStream.readObject();
        }
    }

    public static void storeRunData(int round, double avgFit, double bestFit, double avgNode, int bestNode, String bestBotName) {
        // store each variable in its own file (for graphs)
        appendStringToFile("run_data.txt", round + "\t" + avgFit + "\t" + bestFit + "\t" + avgNode + "\t" + bestNode + "\n");
        appendStringToFile("run_data_avgFitness.txt", avgFit + "\n");
        appendStringToFile("run_data_bestFitness.txt", bestFit + "\n");
        appendStringToFile("run_data_avgNodes.txt", avgNode + "\n");
        appendStringToFile("run_data_bestNodes.txt", bestNode + "\n");
        appendStringToFile("run_data_candidates.txt", bestBotName + "\n");
    }

    public static void saveCtx() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CTX_FILE))) {
            oos.writeInt(genCount);
            oos.writeObject(bestSoFar);
            oos.writeObject(pool);
            oos.writeObject(candidates);
        }
    }

    public static void loadCtx() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CTX_FILE))) {
            genCount = ois.readInt();
            bestSoFar = (MetaBot) ois.readObject();
            pool = (MetaBot[]) ois.readObject();
            candidates = (MetaBot[]) ois.readObject();
        }
    }

    public static void initOrRestoreCtx() {
        File f = new File(CTX_FILE);
        if (f.exists()) {
            System.out.println("Restore GP Robot Context");
            try {
                loadCtx();
            } catch (ClassNotFoundException |IOException e) {
                Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, "Unable to restore context", e);
                System.exit(1);
            }
        } else {
            System.out.println("Initializing population");
            initPool();
            cleanRunData();
        }
    }

    public static void cleanRunData() {
        // store each variable in its own file (for graphs)
        String[] runData = new String[] {
                "run_data.txt", "run_data_avgFitness.txt", "run_data_bestFitness.txt",
                "run_data_avgNodes.txt", "run_data_bestNodes.txt", "run_data_candidates.txt"
        };

        for (String s: runData) {
            new File(s).delete();
        }
    }

    public static void appendStringToFile(String file, String s) {
        try (FileWriter dataStream = new FileWriter(file, true)) {
            dataStream.write(s);
        } catch (IOException ex) {
            Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}