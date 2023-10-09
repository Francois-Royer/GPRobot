package tankbase;

import java.awt.*;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class MovingPoint extends Point.Double {
    double velocity;
    double direction;
    long start;

    public MovingPoint( Point.Double origin, double velocity, double direction,long start)  {
        super(origin.getX(), origin.getY());
        this.velocity = velocity;
        this.direction = direction;
        this.start = start;
    }

    public double getDirection() {
        return direction;
    }

    public double getVelocity() {
        return velocity;
    }

    public double getDistance(long time) {
        return velocity * (time - start);
    }

    public long age(long now) {
        return (now - start);
    }

    public Point.Double getPosition(long tick) {
        double d = getDistance(tick);
        return new Point.Double(x + d * cos(direction), y + d * sin(direction));
    }
}
