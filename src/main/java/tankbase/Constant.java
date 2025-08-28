package tankbase;

import static robocode.Rules.RADAR_TURN_RATE_RADIANS;

public class Constant {

    public static final double TANK_SIZE = 36;
    public static final double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 2;
    public static final double BORDER_OFFSET = TANK_SIZE * 9 / 8;
    public static final double FIRE_TOLERANCE = TANK_SIZE / 2.01;
    public static final double MAX_DANGER_RADIUS = TANK_SIZE * 2;

    public static final int TANK_SIZE_INT = (int) TANK_SIZE;
    public static final int MAX_DANGER_ZONE = 1500;
    public static final long MAX_NOT_SCAN_TIME = 10;
    public static final long MIN_CHANGE_TARGET_TIME = 10;

    private Constant() {
    }
}
