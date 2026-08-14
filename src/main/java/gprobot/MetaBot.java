package gprobot;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import static gprobot.RobocodeConf.BOT_PREFFIX;
import static gprobot.RobocodeConf.TARGET_PACKAGE;
import static gprobot.RobocodeConf.random;

public class MetaBot implements Serializable {

    final static int SCAN_CHROMOS = 2;
    final static int HIT_BY_BULLET_CHROMOS = 4;
    final static int HIT_ROBOT_CHROMOS = 5;
    final static int NUM_CHROMOS = SCAN_CHROMOS;// + HIT_BY_BULLET_CHROMOS; //+ HIT_ROBOT_CHROMOS;
    final static double
            PROB_CROSS_ROOT = 0, //0.3,
            PROB_CROSS_TERMINAL = 0.1,
            PROB_JUMP_GENOMES = 0.05,
            PROB_MUTATE_ROOT = 0.01,
            PROB_MUTATE_TERMINAL = 0.15;
    private static final long serialVersionUID = 5625044536646095912L;
    static String robotTemplate;

    static {
        try (InputStream is = MetaBot.class.getResourceAsStream("/RobotTemplate.java")) {
            byte[] buff = new byte[is.available()];
            is.read(buff);
            robotTemplate = new String(buff);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Class Fields //////////////////////////////////////////////////////////
    transient String[] phenome;
    transient String sourceCode;
    transient String botName;

    int memberGen = 0, memberID = 0, nodeCount;
    double fitness;

    ExpressionNode[] genome = new ExpressionNode[NUM_CHROMOS];

    // Class Methods /////////////////////////////////////////////////////////
    public MetaBot() {
    }

    public MetaBot(int gen, int botID) {
        memberGen = gen;
        memberID = botID;
        init();
    }

    String getBotName() {
        if (botName == null)
            botName = BOT_PREFFIX + memberGen + "_" + memberID;
        return botName;
    }

    public String getFileName() {
        return TARGET_PACKAGE + "." + getBotName();
    }

    public void init() {
        //ExpressionNode.setScanEventTerminals();
        for (int i = 0; i < SCAN_CHROMOS; i++) {
            genome[i] = new ExpressionNode(0);
            genome[i].grow(0, 0);
        }
        /*ExpressionNode.setHitByBulletEventTerminals();
        for (int i = SCAN_CHROMOS; i < SCAN_CHROMOS+HIT_BY_BULLET_CHROMOS; i++) {
            genome[i] = new ExpressionNode(0);
            genome[i].grow(0, 0);
        }
        /*ExpressionNode.setHitRobotEventTerminals();
        for (int i = SCAN_CHROMOS+HIT_BY_BULLET_CHROMOS; i < NUM_CHROMOS; i++) {
            genome[i] = new ExpressionNode(0);
            genome[i].grow(0, 0);
        }*/
    }

    public void construct() {
        phenome = new String[NUM_CHROMOS];
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
            child.genome[i] = this.genome[i].gClone();
        }
        //*****************************************************************
        int xChromo1 = random.nextInt(NUM_CHROMOS);
        int xChromo2 = xChromo1;
        if (NUM_CHROMOS > 1)
            while (xChromo2 == xChromo1)
                xChromo2 = (xChromo1 < SCAN_CHROMOS)
                        ? random.nextInt(SCAN_CHROMOS)
                        : ((xChromo1 < SCAN_CHROMOS + HIT_BY_BULLET_CHROMOS)
                        ? random.nextInt(HIT_BY_BULLET_CHROMOS) + SCAN_CHROMOS
                        : random.nextInt(HIT_ROBOT_CHROMOS) + SCAN_CHROMOS + HIT_BY_BULLET_CHROMOS);

        if (random.nextDouble() < PROB_CROSS_ROOT) {    // swap entire chromosome
            if (random.nextDouble() < PROB_JUMP_GENOMES) {    // do not use the same chromosome
                child.genome[xChromo1].replaceWith(p2.genome[xChromo2]);
            } else    // swap same chromosome
                child.genome[xChromo1].replaceWith(p2.genome[xChromo1]);
        } else {    // use subtrees
            // determine if subtrees will be terminals or functions
            boolean useTerminal1 = random.nextDouble() < PROB_CROSS_TERMINAL;
            boolean useTerminal2 = random.nextDouble() < PROB_CROSS_TERMINAL;

            // select random subtrees of p2
            // cross-over the subtrees
            child.genome[xChromo1].insert(p2.genome[xChromo1].getSubTree(useTerminal1));
            if (xChromo1 != xChromo2)
                child.genome[xChromo2].insert(p2.genome[xChromo2].getSubTree(useTerminal2));
        }

        child.setDepths();
        child.countNodes();
        return child;
    }

    public MetaBot mutate(int gen, int botID) {
        MetaBot child = new MetaBot(gen, botID);

        for (int i = 0; i < NUM_CHROMOS; i++) {
            child.genome[i] = this.genome[i].gClone();
        }

        int m = random.nextInt(NUM_CHROMOS);

        /*if (m < SCAN_CHROMOS) {
            ExpressionNode.setScanEventTerminals();
        } else if (m < SCAN_CHROMOS+HIT_BY_BULLET_CHROMOS) {
            ExpressionNode.setHitByBulletEventTerminals();
        } else {
            ExpressionNode.setHitRobotEventTerminals();
        }*/

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
            child.genome[i] = this.genome[i].gClone();
        }

        child.setDepths();
        child.nodeCount = this.nodeCount;
        return child;
    }


    // FileIO Methods ///////////////////////////////////////////////////////////////////////////

    private void setCode() {
        Object[] params = new String[phenome.length + 1];
        params[0] = getBotName();
        System.arraycopy(phenome, 0, params, 1, phenome.length);
        sourceCode = String.format(robotTemplate, params);
    }

    String writeSource() throws IOException {
        String sourceFile = RobotCodeUtil.botsrcFilePath(getBotName());
        try (BufferedWriter out = new BufferedWriter(new FileWriter(sourceFile))) {
            out.write(sourceCode);
        }
        return sourceFile;
    }
}
