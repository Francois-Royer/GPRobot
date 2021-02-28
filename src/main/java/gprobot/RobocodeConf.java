package gprobot;

import java.io.File;
import java.util.Random;

import robocode.control.BattlefieldSpecification;

/**
 * Created by gprobot on 17/01/17.
 */
public class RobocodeConf {
    private RobocodeConf() {
        // Const class
    }

    public static final Random random = new Random(System.currentTimeMillis());

    public static final String ROBO_CODE_PATH = System.getProperty("ROBOCODE_PATH",
            System.getProperty("os.name").toLowerCase().contains("win")
                ? "c:\\robocode"
                : System.getProperty("user.home") + File.separator + "robocode");

    // Jars
    public static final String ROBOCODE_JAR = "robocode.jar";


    public static final String ROBOTS_FOLDER = "robots";
    public static final String TARGET_PACKAGE = "sample";
    public static final String TARGET_FOLDER = ROBO_CODE_PATH + File.separator + ROBOTS_FOLDER + File.separator + TARGET_PACKAGE;
    public static final BattlefieldSpecification BATTLEFIELD = new BattlefieldSpecification(800, 600);

    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String BOT_PREFFIX = "X_GPbot_";


    // constant that can be tunned
    public static final int POP_SIZE = 300;
    public static final int MAX_GENS = 40000;
    public static final int MIN_DEPTH = 2;
    public static final int MAX_DEPTH = 7;
    public static final int ROUNDS = 10;
    public static final int TOURNY_SIZE = 6; // Selection Pressure
    public static int RUNNERS_COUNT = AVAILABLE_PROCESSORS;
    public static boolean ONE2ONE = true;
    public static final double PROB_CROSSOVER = 0.85;
    public static final double PROB_MUTATION = 0.05;

    public static final String CTX_FILE = "GP_ctx.bin";

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
            "sample.VelociRobot",
            "sample.Walls"
    };

    static String[] skilledRobots = {
        "voidious.Diamond"
    };
    static String[] opponents = sampleRobots;
}
