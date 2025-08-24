package tankbase;

import java.awt.geom.Point2D;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class MovingPoint extends Point2D.Double {
    double velocity;
    double direction;

    long start;

    public MovingPoint(Point2D.Double origin, double velocity, double direction, long start) {
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
        return velocity * age(time);
    }

    public long age(long now) {
        return (now - start);
    }

    public long getStart() {
        return start;
    }

    public Point2D.Double getPosition(long time) {
        double d = getDistance(time);
        return new Point2D.Double(x + d * cos(direction), y + d * sin(direction));
    }

    @Override
    public String toString() {
        return String.format("MovingPoint[x=%.2f, y=%.2f, v=%.2f, d=%.2f, start=%d]", x, y, velocity, direction, start);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MovingPoint that = (MovingPoint) o;
        return java.lang.Double.compare(velocity, that.velocity) == 0 &&
                java.lang.Double.compare(direction, that.direction) == 0 &&
                start == that.start;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + java.lang.Double.hashCode(velocity);
        result = 31 * result + java.lang.Double.hashCode(direction);
        result = 31 * result + Long.hashCode(start);
        return result;
    }
}
