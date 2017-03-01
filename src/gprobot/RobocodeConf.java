package gprobot;

import java.io.File;
import java.net.URL;
import java.util.Random;

import robocode.control.BattlefieldSpecification;

/**
 * Created by gprobot on 17/01/17.
 */
public class RobocodeConf {

    public static final Random random = new Random(System.currentTimeMillis());

    public static final String roboCodePath = System.getProperty("ROBOCODE_PATH", 
            System.getProperty("os.name").toLowerCase().contains("win")
                ? "c:\\robocode"
                : System.getProperty("user.home") + File.separator + "robocode");
    public static final String roboCodeVersion = "1.9.2.6";

    // Jars
    public static final String roboCodeJar = "robocode.jar";
    public static final String roboCodeLibsPath = roboCodePath + File.separator + "libs" + File.separator;
    public static final String roboCodeJarPath = roboCodeLibsPath + roboCodeJar;

    public static final String targetPakage = "sampleex";
    public static final String targetFolder = roboCodePath + File.separator + "robots" + File.separator + targetPakage;
    public static final BattlefieldSpecification battlefield = new BattlefieldSpecification(800, 600);

    // constant that can be tunned
    public final static int POP_SIZE = 300;
    public final static int MAX_GENS = 4000;
    public final static int MIN_DEPTH = 2;
    public final static int MAX_DEPTH = 8;
    public final static int ROUNDS = 25;
    public final static int TOURNY_SIZE = 24; // Selection Pressure
    public final static int BATTLE_HANDICAP =1;
    public final static int RUNNERS_COUNT = 4; // /!\ POP_SIZE % RUNNER_COUNT must be 0

    public static double PROB_CROSSOVER = 0.85;
    public static double PROB_MUTATION = 0.01;
    public static double PROB_SEED = 0.15;

    public static String CTX_FILE = "GP_ctx.bin";

    static String[] sampleRobots = {
            //"sample.Corners",
            "sample.Crazy",
            //"sample.Fire",
            //"sample.Interactive",
            //"sample.Interactive_v2",
            //"sample.MyFirstJuniorRobot",
            //"sample.MyFirstRobot",
            //"sample.PaintingRobot",
            //"sample.RamFire",
            //"sample.SittingDuck",
            "sample.SpinBot",
            //"sample.Target",
            "sample.Tracker",
            //"sample.TrackFire",
            //"sample.VelociRobot",
            "sample.Walls"
    };
    static String[] skilledRobots = {
        "voidious.Diamond"
    };
}
