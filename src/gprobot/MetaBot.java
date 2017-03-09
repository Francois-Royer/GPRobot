package gprobot;

import java.io.*;
import java.lang.reflect.Array;

import static gprobot.RobocodeConf.*;

public class MetaBot implements Serializable {

    private static final long serialVersionUID = 5625044536646095912L;
    final static int SCAN_CHROMOS = 5;
    final static int HIT_CHROMOS = 2;
    final static int NUM_CHROMOS = SCAN_CHROMOS + HIT_CHROMOS;
    final static double
            PROB_CROSS_ROOT = 0.3,
            PROB_CROSS_TERMINAL = 0.1,
            PROB_JUMP_GENOMES = 0.05,
            PROB_MUTATE_ROOT = 0.3,
            PROB_MUTATE_TERMINAL = 0.15;

    //Class Fields //////////////////////////////////////////////////////////

    int memberGen = 0, memberID = 0, nodeCount;

    String
            botName = new String(),
            phenome[] = new String[NUM_CHROMOS],
            sourceCode = new String(),
            fileName;

    ExpressionNode genome[] = new ExpressionNode[NUM_CHROMOS];

    double fitness;
    int selection;

    // Class Methods /////////////////////////////////////////////////////////

    public MetaBot(int gen, int botID) {
        memberGen = gen;
        memberID = botID;
        botName = "X_GPbot_" + memberGen + "_" + memberID;
        fileName = targetPakage + "." + botName;
        selection=0;
        init();
    }

    public void init() {
        ExpressionNode.setScanEventTerminal();
        for (int i = 0; i < SCAN_CHROMOS; i++) {
            genome[i] = new ExpressionNode(0);
            genome[i].grow(0, 0);
        }
        ExpressionNode.setHitEventTerminal();
        for (int i = SCAN_CHROMOS; i < NUM_CHROMOS; i++) {
            genome[i] = new ExpressionNode(0);
            genome[i].grow(0, 0);
        }
    }

    public void construct() {
        for (int i = 0; i < NUM_CHROMOS; i++) {
            phenome[i] = genome[i].compose();
            setCode();
        }
    }

    public int countNodes() {
        this.nodeCount = 0;
        for (int i = 0; i < genome.length; i++)
            nodeCount += genome[i].countNodes();
        return nodeCount;
    }

    public void setDepths() {
        for (ExpressionNode exp : genome)
            exp.setDepths(0);
    }


    // Genetic Methods ////////////////////////////////////////////////////////////////////////

    public MetaBot crossover(MetaBot p2, int gen, int botID) {
        MetaBot child = new MetaBot(gen, botID);

        for (int i = 0; i < NUM_CHROMOS; i++) {
            child.genome[i] = this.genome[i].clone();
        }
        //*****************************************************************
        int xChromo1 = random.nextInt(NUM_CHROMOS);
        int xChromo2 = xChromo1;
        while (xChromo2 == xChromo1)
            xChromo2 = (xChromo1 < SCAN_CHROMOS)
                    ? random.nextInt(SCAN_CHROMOS)
                    : random.nextInt(HIT_CHROMOS) + SCAN_CHROMOS;


        if (random.nextDouble() < PROB_CROSS_ROOT) {    // swap entire chromosome
            if (random.nextDouble() < PROB_JUMP_GENOMES) {    // do not use the same chromosome
                child.genome[xChromo1].replaceWith(p2.genome[xChromo2]);
            } else    // swap same chromosome
                child.genome[xChromo1].replaceWith(p2.genome[xChromo1]);
        } else {    // use subtrees
            // determine if subtrees will be terminals or functions
            boolean useTerminal1 = (random.nextDouble() < PROB_CROSS_TERMINAL) ? true : false;
            boolean useTerminal2 = (random.nextDouble() < PROB_CROSS_TERMINAL) ? true : false;

            // select random subtrees of p2
            // cross-over the subtrees
            child.genome[xChromo1].insert(p2.genome[xChromo1].getSubTree(useTerminal1));
            child.genome[xChromo2].insert(p2.genome[xChromo2].getSubTree(useTerminal2));

        }

        child.setDepths();
        child.countNodes();
        return child;
    }

    public MetaBot mutate(int gen, int botID) {
        MetaBot child = new MetaBot(gen, botID);

        for (int i = 0; i < NUM_CHROMOS; i++) {
            child.genome[i] = this.genome[i].clone();
        }

        int m = random.nextInt(NUM_CHROMOS);

        if (m < SCAN_CHROMOS) {
            ExpressionNode.setScanEventTerminal();
        } else {
            ExpressionNode.setHitEventTerminal();
        }

        if (random.nextDouble() < PROB_MUTATE_ROOT) {    // mutate entire chromosome
            child.genome[m] = new ExpressionNode(0);
            child.genome[m].grow(0, 0);
        } else if (random.nextDouble() < PROB_MUTATE_TERMINAL) {
            child.genome[m].mutateTerminal();
        } else {
            child.genome[m].mutateFunction();
        }
        child.setDepths();
        child.countNodes();
        return child;
    }

    public MetaBot replicate(int gen, int botID) {
        MetaBot child = new MetaBot(gen, botID);

        for (int i = 0; i < NUM_CHROMOS; i++) {
            child.genome[i] = this.genome[i].clone();
        }

        child.setDepths();
        child.nodeCount = this.nodeCount;
        return child;
    }


    // FileIO Methods ///////////////////////////////////////////////////////////////////////////

    private void setCode() {
        sourceCode = "package " + targetPakage + ";"
                + "\nimport robocode.*;"
                + "\nimport static robocode.Rules.*;"
                + "\nimport java.awt.Color;\n"
                + "\n"
                + "\npublic class " + botName + " extends AdvancedRobot {"
                + "\n"
                //+ "\n static double runVar1 = 0;"
                //+ "\n static double runVar2 = 0;"
                //+ "\n"
                + "\n    public void run() {"
                + "\n"
                + "\n        setAdjustGunForRobotTurn(true);"
                + "\n        setAdjustRadarForGunTurn(true);"
                + "\n"
                + "\n        setColors(Color.red,Color.blue,Color.green);"
                + "\n        while(true) {"
                + "\n            turnRadarRight(Double.POSITIVE_INFINITY);"
                //+ "\n            turnRight(runVar1);"
                //+ "\n            setAhead(runVar2);"
                + "\n        }"
                + "\n    }"
                + "\n"
                + "\n    public void onScannedRobot(ScannedRobotEvent e) {"
                + "\n        // --- PHENOME 1 ---"
                + "\n        double ahead = " + phenome[0] + ";"
                + "\n        // --- PHENOME 2 ---"
                + "\n        double turnRight = " + phenome[1] + ";"
                + "\n        // --- PHENOME 3 ---"
                + "\n        double turnGunRight = " + phenome[2] + ";"
                + "\n        // --- PHENOME 4 ---"
                + "\n        double turnRadarRight = " + phenome[3] + ";"
                + "\n        // --- PHENOME 5 ---"
                + "\n        double fire = " + phenome[4] + ";"
                + "\n"
                + "\n        //out.println(\"ahead=\" +ahead+ \", fire=\" + fire);"
                + "\n        //out.println(\"turnRight=\" +turnRight+ \", turnGunRight=\" + turnGunRight + \", turnRadarRight=\" + turnRadarRight);"
                + "\n        setAhead(ahead);"
                + "\n        setTurnRightRadians(turnRight);"
                + "\n        setTurnGunRightRadians(turnGunRight);"
                + "\n        setTurnRadarRightRadians(turnRadarRight);"
                + "\n        setFire(fire);"
                //+ "\n}"
                + "\n"
                //+ "\n // --- PHENOME 6,7 ---"
                //+ "\n		runVar1 = " + phenome[5] + ";"
                //+ "\n"
                //+ "\n		runVar2 = " + phenome[6] + ";"
                //+ "\n"
                + "\n	}"
                + "\npublic void onHitByBullet(HitByBulletEvent e) {"
                + "\n        // --- PHENOME 6 ---"
                + "\n        double ahead = " + phenome[5] + ";"
                + "\n        // --- PHENOME 7 ---"
                + "\n        double turnRight = " + phenome[6] + ";"
                + "\n        // --- PHENOME 8 ---"
                + "\n"
                + "\n        //out.println(\"ohbb ahead=\" +ahead+ \", fire=\" + fire);"
                + "\n        //out.println(\"ohbb turnRight=\" +turnRight+ \", turnGunRight=\" + turnGunRight + \", turnRadarRight=\" + turnRadarRight);"
                + "\n        setAhead(ahead);"
                + "\n        setTurnRightRadians(turnRight);"
                + "\n	}"
                + "\n}";
    }

    String writeSource() throws IOException {
        String sourceFile = RobotCodeUtil.botsrcFilePath(botName);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(sourceFile))) {
            out.write(sourceCode);
        }
        return sourceFile;
    }
}
