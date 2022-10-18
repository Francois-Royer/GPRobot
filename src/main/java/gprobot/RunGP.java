package gprobot;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;

import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static gprobot.RobocodeConf.*;
import static gprobot.RobotCodeUtil.*;

/**
 * This class represents the main genetic algorithm.
 * It assumes that Robocode is installed at ${user.home}/robocode,
 * and writes all files to the "${user.home}/robocode/robots/gpbase" directory.
 * The robocode.jar library must be included in the build path (${user.home}/robocode/libs/robocode.jar by default)
 *
 * @author Ted Hunter
 */
public class RunGP {
    private static final Logger log = Logger.getLogger(RunGP.class.getName());
    static PrintStream console = System.out;

    final private double[] fitnesses = new double[POP_SIZE];

    private MetaBot[] pool = new MetaBot[POP_SIZE];
    private MetaBot[] newPool = new MetaBot[POP_SIZE];
    private MetaBot bestSoFar = new MetaBot(-1, 0);
    private MetaBot bestLastGen = new MetaBot(-1, 0);

    private int genCount = 0;

    Kryo kryo;

    private BattleControler[] battleControlers;

    public static void main(String[] args) throws IOException {

        if (args.length >= 2)  {
            RUNNERS_COUNT = Integer.parseInt(args[0]);
            ONE2ONE = Boolean.parseBoolean(args[1]);
        }

        new StatServer().start();
        new RunGP().runGP();
    }

    public void runGP() {
        try {
            bestSoFar.fitness = 0;
            kryo = new Kryo();
            kryo.register(MetaBot.class);
            kryo.register(MetaBot[].class);
            kryo.register(ExpressionNode.class);
            kryo.register(ExpressionNode[].class);
            kryo.register(String[].class);

            initOrRestoreCtx();
            console.println("Prepare " + RUNNERS_COUNT + " Runners ");
            prepareBattleRunners();
            Runtime.getRuntime().addShutdownHook(new Thread(this::killAllRunner));


            // -- EC loop
            long begin = 0;
            while (genCount < MAX_GENS) {
                long beginGen = System.currentTimeMillis();

                console.println("#".repeat(80));
                console.printf("Compile %d Robots for generation %d: ", POP_SIZE, genCount);
                compilePool();
                long endComp = System.currentTimeMillis();
                console.println(sDuration(endComp - beginGen));

                scoreFitnessOnSet();
                long endBattle = System.currentTimeMillis();
                console.println(sDuration(endBattle - endComp));

                double totalFitness = 0;
                int best = 0;
                int avgNodeCount = 0;

                for (int i = 0; i < POP_SIZE; i++) {
                    totalFitness += (pool[i].fitness = fitnesses[i]);
                    if (pool[i].fitness > pool[best].fitness) best = i;
                    avgNodeCount += pool[i].countNodes();
                }

                avgNodeCount /= POP_SIZE;
                final double avgFitness = totalFitness / POP_SIZE;

                // store the best-in-generation
                bestLastGen = pool[best];
                if (pool[best].fitness > bestSoFar.fitness) bestSoFar = pool[best];

                console.printf("\nAvg. Fitness:\t%2.02f\t Avg # of nodes: %d%n",
                        avgFitness, avgNodeCount);
                console.printf("Best so far:\t%s - %2.02f (%2.02f)\t# nodes %d%n",
                        bestSoFar.getBotName(),bestSoFar.fitness, pool[0].fitness,bestSoFar.nodeCount);
                console.printf("Best in round:\t%s - %2.02f (%2.02f)\t# nodes %d%n",
                        bestLastGen.getBotName(),bestLastGen.fitness, pool[1].fitness,bestLastGen.nodeCount);

                // delete Generation files except best one
                RobotCodeUtil.clearBots(genCount, POP_SIZE, bestLastGen.memberID);

                storeRunData(genCount, avgFitness, bestLastGen.fitness, avgNodeCount, bestLastGen.nodeCount, bestLastGen.getBotName());

                genCount++;

                // breed next generation
                breedPool();

                // set newPool as pool, clear newPool
                pool = newPool;
                newPool = new MetaBot[POP_SIZE];

                saveCtx();

                // Time stat
                long end = System.currentTimeMillis();
                long genTime = end - beginGen;
                if (begin == 0) {
                    begin = beginGen - genTime * (genCount - 1);
                }
                long avgTime = (end - begin) / genCount;
                long eta = avgTime * (MAX_GENS - genCount);
                Date finished = new Date(end + eta);
                console.println("-------Time stat ---------- ");
                console.println("last gen=" + sDuration(genTime) +
                    ", avg=" + sDuration(avgTime) +
                    ", eta= " + sDuration(eta));
                console.println("Date Finished: " + finished);

            }

        } catch (Throwable e) {
            log.log(Level.SEVERE, "Fatal error", e);
            e.printStackTrace();

        }
        killAllRunner();
    }


    private void compilePool() {
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
    }

    private void breedPool() {
        // replicate best so far
        newPool[0] = bestSoFar.replicate(genCount, 0);
        // replicate best in last round
        newPool[1] = bestLastGen.replicate(genCount, 1);
        // cross bestSoFar with best lastGen
        newPool[2] = bestSoFar.crossover(bestLastGen, genCount, 2);

        // breed next generation
        double geneticOperator;
        int newPop = 3;

        while (newPop < POP_SIZE) {
            geneticOperator = random.nextDouble();
            MetaBot b1 = candidateSelect();

            if (geneticOperator <= PROB_CROSSOVER) {
                MetaBot b2;
                do {
                    b2 = candidateSelect();
                } while (b2 == b1);
                newPool[newPop] = b1.crossover(b2, genCount, newPop);
            } else if (geneticOperator <= PROB_MUTATION) {
                newPool[newPop] = b1.mutate(genCount, newPop);
            } else {
                newPool[newPop] = b1.replicate(genCount, newPop);
            }

            if (geneticOperator <= PROB_MUTATION) {
                newPool[newPop] = newPool[newPop].mutate(genCount, newPop);
            }
            newPop++;
        }
    }

    private double[] getOpponentsSkill(String[] opponents) {
        if (opponents.length == 1) return new double[]{1};

        RobocodeEngine engine = new RobocodeEngine(new File(ROBO_CODE_PATH));
        BattleObserver battleObserver = new BattleObserver("opponents");
        engine.addBattleListener(battleObserver);
        engine.setVisible(false);
        RobotSpecification[] selectedBots = engine.getLocalRepository(String.join(",", opponents));
        BattleSpecification battleSpec = new BattleSpecification(ROUNDS, BATTLEFIELD, selectedBots);
        engine.runBattle(battleSpec, true);

        BattleResults[] results = battleObserver.getResults();
        double totalScore = Arrays.stream(results).mapToDouble(BattleResults::getScore).sum();

        return Arrays.stream(results).mapToDouble(BattleResults::getScore).map(d -> d / totalScore).toArray();
    }

    private void scoreFitnessOnSet() {
        try {
            Arrays.fill(fitnesses, 0);
            final Deque<Integer> queue = IntStream.range(0, POP_SIZE).boxed().collect(Collectors.toCollection(LinkedBlockingDeque::new));
            final CountDownLatch cdl = new CountDownLatch(POP_SIZE);
            for (int i = 0; i < RUNNERS_COUNT; i++) {
                final int runnerId = i;
                new Thread(() -> {
                    Integer robotID;
                    try {
                        while ((robotID = queue.pollFirst()) != null) {
                            String robotName = (robotID < POP_SIZE) ? getRobotName(genCount, robotID) : "GPBase";
                            try {
                                fitnesses[robotID] = battleControlers[runnerId].getRobotFitness(robotName);
                                synchronized (fitnesses) {
                                    cdl.countDown();
                                    fitnesses.notifyAll();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, "Exception in runner " + runnerId + " restarting it", e);
                                // Runner may have crash, put back the robot in queue and restart runner
                                queue.addFirst(robotID);

                                // TODO: implement restart
                            }
                        }
                    } catch (Exception e) {
                        Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, "Exception in runner " + runnerId, e);
                    }
                }).start();
            }

            synchronized (fitnesses) {
                while (cdl.getCount() > 0) {
                    fitnesses.wait(500);
                    displayBattleProgress((int) cdl.getCount(), queue.size());
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "scoreFitnessOnSet", e);
        }
    }

    public void prepareBattleRunners() throws IOException, InterruptedException {
        File runnerDirs = getRunnersDir();
        if (runnerDirs.exists()) {
            delete(runnerDirs);
        }
        runnerDirs.mkdir();
        battleControlers = new BattleControler[RUNNERS_COUNT];
        for (int i=0; i < RUNNERS_COUNT; i++)
            battleControlers[i] = new BattleControler(i);
        for (int i=0; i < RUNNERS_COUNT; i++)
            battleControlers[i].setOpponentsName(opponents);
    }

    public MetaBot candidateSelect() {
        return tournementSelect();
    }

    public MetaBot tournementSelect() {
        int[] subPool = new int[TOURNY_SIZE];
        for (int i = 0; i < TOURNY_SIZE; i++)
            subPool[i] = random.nextInt(POP_SIZE);
        int best = subPool[0];
        for (int i = 1; i < TOURNY_SIZE; i++)
            if (pool[subPool[i]].fitness > pool[best].fitness) best = subPool[i];

        return pool[best];
    }

    public void storeRunData(int round, double avgFit, double bestFit, double avgNode, int bestNode, String bestBotName) {
        // store each variable in its own file (for graphs)
        appendStringToFile("run_data.txt", round + "," + avgFit + "," + bestFit + "," + avgNode + "," + bestNode + "," + bestBotName+"\n");
    }

    public void saveCtx() {
        File tmpFile = new File(CTX_FILE+ ".tmp");

        try (Output out = new Output(new DeflaterOutputStream(new FileOutputStream(tmpFile)))) {
            out.writeInt(genCount);
            kryo.writeObject(out,bestSoFar);
            kryo.writeObject(out,pool);
        } catch (IOException e) {
            Logger.getLogger("GPRobot").log(Level.SEVERE, "Unable to save context for restart", e);
        }

        File ctxFile = new File(CTX_FILE);
        ctxFile.delete();
        tmpFile.renameTo(ctxFile);
    }

    public void loadCtx() throws IOException, ClassNotFoundException {
        try (Input in = new Input(new InflaterInputStream(new FileInputStream(CTX_FILE)))) {
            genCount = in.readInt();
            bestSoFar = kryo.readObject(in, MetaBot.class);
            pool = kryo.readObject(in, MetaBot[].class);
            bestLastGen = pool[0];
        }
    }

    public void initOrRestoreCtx() {
        try {
            File f = new File(CTX_FILE);
            if (f.exists()) {
                console.println("Restore GP Robot Context");
                loadCtx();

            } else {
                console.println("Initializing population");
                Arrays.setAll(pool, i -> new MetaBot(0, i));
                cleanRunData();
            }
        } catch (ClassNotFoundException | IOException e) {
            Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, "initOrRestoreCtx", e);
            System.exit(1);
        }
    }

    public void cleanRunData() throws IOException {
        Stream.of(
            new File(TARGET_FOLDER)
                .listFiles((d, name) -> name.startsWith(BOT_PREFFIX)))
            .forEach(File::delete);

        Stream.of(
            new File(".")
                .listFiles((d,name) ->  name.matches("run_data.*.txt")))
            .forEach(File::delete);

        appendStringToFile("run_data.txt", "Generation,Average fitness,Best fitness, Average nodes,Best nodes,Best name\n");
    }

    public void appendStringToFile(String file, String s) {
        try (FileWriter dataStream = new FileWriter(file, true)) {
            dataStream.write(s);
        } catch (IOException ex) {
            Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void killAllRunner() {
        Stream.of(battleControlers).forEach(BattleControler::destroy);
        try {
            delete(getRunnersDir());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String progressBar(int i) {
        StringBuilder sb = new StringBuilder();
        int x = i / 2;
        sb.append("|");
        for (int k = 0; k < 50; k++)
            sb.append(((x <= k) ? " " : "="));
        sb.append("|");

        return sb.toString();
    }

    private void displayBattleProgress(int remain, int queue_size) {
        int percent = 100 * (POP_SIZE - remain) / POP_SIZE;
        console.printf("\rBattles: %s %d %d  ", progressBar(percent), remain, remain - queue_size);
    }
}