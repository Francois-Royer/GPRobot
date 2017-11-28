package gprobot;

import robocode.BattleResults;
import robocode.Robocode;
import robocode.control.BattleSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static gprobot.RobocodeConf.*;
import static gprobot.RobotCodeUtil.*;

/**
 * This class represents the main genetic algorithm.
 * It assumes that Robocode is installed at ${user.home}/robocode,
 * and writes all files to the "${user.home}/robocode/robots/sampleex" directory.
 * The robocode.jar library must be included in the build path (${user.home}/robocode/libs/robocode.jar by default)
 *
 * @author Ted Hunter
 */
public class RunGP {

    private static String anim = "|/-\\";
    private static String host;
    private static double fitnesses[] = new double[POP_SIZE];
    private static double allAvgFitnesses[] = new double[MAX_GENS];
    private static double avgNumNodes[] = new double[MAX_GENS];

    private static MetaBot
            pool[] = new MetaBot[POP_SIZE],
            newPool[] = new MetaBot[POP_SIZE],
            bestSoFar = new MetaBot(-1, 0),
            bestLastGen = new MetaBot(-1, 0);

    private static String botNames[] = new String[POP_SIZE];
    private static int genCount = 0;

    static String[] opponents = skilledRobots;
    static double[] skills;
    //static String[] opponents =sampleRobots;

    public static void main(String args[]) {
        try {
            host = InetAddress.getLocalHost().getHostAddress();
            System.out.println("Start rmi registry on " + host);
            LocateRegistry.createRegistry(1099);
            bestSoFar.fitness = 0;

            skills = getOpponentsSkill(opponents);
            initOrRestoreCtx();

            System.out.println("Prepare " + RUNNERS_COUNT + " Runners");
            prepareBattleRunners(RUNNERS_COUNT);


            // -- EC loop
            long begin = 0;
            while (genCount < MAX_GENS) {
                long beginGen = System.currentTimeMillis();

                System.out.print("Compile " + POP_SIZE + " Robots: ");
                compilePool();
                long endComp = System.currentTimeMillis();
                System.out.println(sDuration(endComp - beginGen));

                for (int i = 0; i < POP_SIZE; i++)
                    botNames[i] = pool[i].fileName;

                scoreFitnessOnSet();

                double totalFitness = 0;
                int best = 0;
                double avgNodeCount = 0;

                for (int i = 0; i < POP_SIZE; i++) {
                    totalFitness += (pool[i].fitness = fitnesses[i]);
                    if (pool[i].fitness > pool[best].fitness) best = i;
                    avgNodeCount += pool[i].countNodes();
                }

                avgNumNodes[genCount] = (avgNodeCount /= POP_SIZE);

                final double avgFitness = totalFitness / POP_SIZE;
                allAvgFitnesses[genCount] = avgFitness;

                // store the best-in-generation
                bestLastGen = pool[best];
                if (pool[best].fitness > bestSoFar.fitness) bestSoFar = pool[best];

                System.out.println("\nROUND " + genCount
                        + "\nAvg. Fitness:\t" + avgFitness + "\t Avg # of nodes: " + avgNumNodes[genCount]
                        + "\nBest In Round:\t" + bestLastGen.botName + " - " + bestLastGen.fitness + " (" + pool[0].fitness + ") "
                        + "\t# nodes " + bestLastGen.nodeCount
                        + "\nBest So Far:\t" + bestSoFar.botName + " - " + bestSoFar.fitness + "(" + pool[1].fitness + ")\t# nodes " + bestSoFar.nodeCount + "\n");

                // delete Generation files except best one
                RobotCodeUtil.clearBots(genCount, POP_SIZE, bestLastGen.memberID);

                storeRunData(genCount, avgFitness, bestLastGen.fitness, avgNodeCount, bestLastGen.nodeCount, bestLastGen.fileName);

                genCount++;
                // breed next generation
                breedPool();


                // set newPool as pool, clear newPool
                pool = newPool;
                newPool = new MetaBot[POP_SIZE];

                try {
                    saveCtx();
                } catch (IOException e) {
                    Logger.getLogger("GPRobot").log(Level.SEVERE, "Unable to save context for restart", e);
                }

                // Time stat
                long end = System.currentTimeMillis();
                long genTime = end - beginGen;
                if (begin == 0) {
                    begin = beginGen - genTime * (genCount - 1);
                }
                long avgTime = (end - begin) / genCount;
                long eta = avgTime * (MAX_GENS - genCount);
                Date finished = new Date(end + eta);
                System.out.println("-------Time stat ---------- ");
                System.out.println("last gen=" + sDuration(genTime) +
                        ", avg=" + sDuration(avgTime) +
                        ", eta= " + sDuration(eta));
                System.out.println("Date Finished: " + finished.toString());

            }

            for (int i = 0; i < RUNNERS_COUNT; i++) {
                getGPRobotRunner(i).stopRunner();
            }

            System.out.println("-------Second Round Complete!-------");
            for (int i = 0; i < genCount; i++) {
                System.out.println("Round " + i + " average:\t" + allAvgFitnesses[i]);
            }
            for (int i = 0; i < genCount; i++) {
                System.out.println("Round " + i + " avg # nodes:\t" + avgNumNodes[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void initPool() {
        for (int i = 0; i < POP_SIZE; i++) {
            pool[i] = new MetaBot(0, i);
        }
    }

    private static void compilePool() {
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

    private static void breedPool() {
        // replicate best in last round
        newPool[0] = bestLastGen.replicate(genCount, 0);
        // replicate best so far
        newPool[1] = bestSoFar.replicate(genCount, 1);
        // breed next generation

        double geneticOperator;
        int newPop = 2;

        while (newPop < POP_SIZE) {
            geneticOperator = random.nextDouble();
            MetaBot b1 = candidateSelect();

            if (geneticOperator <= PROB_CROSSOVER) {
                MetaBot b2 = candidateSelect();
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

    private static double[] getOpponentsSkill(String[] opponents) {
        if (opponents.length == 1) return new double[]{1};

        RobocodeEngine engine = new RobocodeEngine(new File(roboCodePath));
        BattleObserver battleObserver = new BattleObserver();
        engine.addBattleListener(battleObserver);
        engine.setVisible(false);
        RobotSpecification[] selectedBots = engine.getLocalRepository(String.join(",", opponents));
        BattleSpecification battleSpec = new BattleSpecification(ROUNDS, battlefield, selectedBots);
        engine.runBattle(battleSpec, true);

        BattleResults[] results = battleObserver.getResults();
        double totalScore = Arrays.stream(results).mapToDouble(r -> r.getScore()).sum();

        return Arrays.stream(results).mapToDouble(r -> r.getScore()).map(d -> d / totalScore).toArray();
    }

    private static void scoreFitnessOnSet() {
        System.out.println("Run " + POP_SIZE + " Battle for generation " + genCount);

        try {
            fitnesses = new double[POP_SIZE];
            final Deque<Integer> queue = new LinkedBlockingDeque(IntStream.range(0, POP_SIZE).boxed().collect(Collectors.toList()));
            final CountDownLatch cdl = new CountDownLatch(POP_SIZE);
            for (int i = 0; i < RUNNERS_COUNT; i++) {
                final int runnerId = i;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Integer robotID = -1;
                        try {
                            RMIGPRobotBattleRunner runner = (RMIGPRobotBattleRunner) Naming.lookup(getRunnerUrl(host, runnerId));
                            runner.setOpponentsName(opponents);
                            runner.setOpponentsSkills(skills);

                            while ((robotID = queue.pollFirst()) != null) {
                                updateRunner(getRunnerDir(runnerId), genCount, robotID);
                                try {
                                    fitnesses[robotID] = runner.getRobotFitness(gRobotName(genCount, robotID));
                                    synchronized (fitnesses) {rk
                                        cdl.countDown();
                                        fitnesses.notify();
                                    }
                                } catch (Exception e) {
                                    Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE,"Exception in runner " + runnerId + " restarting it", e);
                                    // Runner may have crash, put back the robot in queue and restart runner
                                    queue.addFirst(robotID);
                                    restartProcess(runnerId);
                                    do {
                                        try {
                                            runner = (RMIGPRobotBattleRunner) Naming.lookup(getRunnerUrl(host, runnerId));
                                            runner.setOpponentsName(opponents);
                                            runner.setOpponentsSkills(skills);
                                        } catch (NotBoundException | ConnectException rmie) {
                                            Thread.sleep(1000);
                                            runner = null;
                                        }
                                    } while (runner == null);
                                    Logger.getLogger(RunGP.class.getName()).log(Level.INFO,"Runner " +runnerId + " is up again it");
                                }
                            }
                        } catch (Exception e) {
                            Logger.getLogger(RunGP.class.getName()).log(Level.SEVERE,"Exception in runner " + runnerId, e);
                        }
                    }
                }).start();
            }

            synchronized (fitnesses) {
                displayBattleProgress(queue.size());
                while (cdl.getCount() > 0) {
                    fitnesses.wait(5000);
                    displayBattleProgress(queue.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static MetaBot candidateSelect() {
        return tournementSelect();
    }

    public static MetaBot tournementSelect() {
        int subPool[] = new int[TOURNY_SIZE];
        for (int i = 0; i < TOURNY_SIZE; i++)
            subPool[i] = random.nextInt(POP_SIZE);
        int best = subPool[0];
        for (int i = 1; i < TOURNY_SIZE; i++)
            if (pool[subPool[i]].fitness > pool[best].fitness) best = subPool[i];

        return pool[best];
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
            oos.writeObject(bestLastGen);

            oos.writeObject(pool);
        }
    }

    public static void loadCtx() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(CTX_FILE)))) {
            genCount = ois.readInt();
            bestSoFar = (MetaBot) ois.readObject();
            bestLastGen = (MetaBot) ois.readObject();
            pool = (MetaBot[]) ois.readObject();
        }
    }

    public static void initOrRestoreCtx() {
        File f = new File(CTX_FILE);
        if (f.exists()) {
            System.out.println("Restore GP Robot Context");
            try {
                loadCtx();
            } catch (ClassNotFoundException | IOException e) {
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
        String[] runData = new String[]{
                "run_data.txt", "run_data_avgFitness.txt", "run_data_bestFitness.txt",
                "run_data_avgNodes.txt", "run_data_bestNodes.txt", "run_data_candidates.txt"
        };

        for (String s : runData) {
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

    private static RMIGPRobotBattleRunner getGPRobotRunner(int runnerId) throws RemoteException, NotBoundException, MalformedURLException {
        return (RMIGPRobotBattleRunner) Naming.lookup(getRunnerUrl(host, runnerId));
    }

    private static String progressBar(int i) {
        StringBuilder sb = new StringBuilder();
        int x = i / 2;
        sb.append("|");
        for (int k = 0; k < 50; k++)
            sb.append(((x <= k) ? " " : "="));
        sb.append("|");

        return sb.toString();
    }

    private static void displayBattleProgress(int remain) {
        char spin = anim.charAt((POP_SIZE - remain) % anim.length());
        int percent = 100 * (POP_SIZE - remain) / POP_SIZE;
        System.out.print(String.format("\r%c %s %d remaining battles   ", spin, progressBar(percent), remain));
    }
}