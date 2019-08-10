package sampleex;

import java.awt.Point;

import static robocode.util.Utils.getRandom;
import static robocode.util.Utils.normalRelativeAngle;
import static sampleex.GPUtils.*;
import static sampleex.GPBase.*;
import static java.lang.Math.*;

public class Wave extends Point.Double {
    String name;
    double velocity;
    double direction;
    double arc;
    long start;
    boolean left;

    public Wave(String name, double velocity, long start, Point.Double origin, GPBase robot) {
        super(origin.getX(), origin.getY());
        this.name = name;
        this.velocity = velocity;
        this.direction = getAngle(origin, robot.getCurrentPoint());
        this.start = start;
        this.x = origin.x;
        this.y = origin.y;

        this.arc = maximumEscapeAngle(velocity)/2;

        left = normalRelativeAngle(trigoAngle(robot.getHeadingRadians()) - direction) * robot.forward > 0;
    }

    double getDistance(long tick) {
        return velocity * (tick - start);
    }

    Point.Double getPosition(long tick) {
        double d = getDistance(tick);
        return new Point.Double(x + d * cos(direction), y + d * sin(direction));
    }
}
