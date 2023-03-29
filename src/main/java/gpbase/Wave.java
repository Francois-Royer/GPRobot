package gpbase;

import static robocode.Rules.*;

import java.awt.*;

import static gpbase.GPUtils.*;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static robocode.util.Utils.normalAbsoluteAngle;

public class Wave extends MovingPoint {
    Enemy enemy;
    double arc;
    Point.Double head;
    Point.Double middle;
    Point.Double circular;

    double median;
    double normalMedian;
    double deviation;

    public Wave(Enemy enemy, double velocity, long start, Point.Double origin, GPBase gpbase) {
        super(origin, velocity, 0, start);
        this.enemy = enemy;
        head = gpbase.getCurrentPoint();
        double distance = origin.distance(head);
        double time = distance/velocity;

        circular = new Point.Double();

        circular.x = head.x + cos(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();
        circular.y = head.y + sin(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();

        this.arc = getVertexAngle(origin, circular, head)+.17;
        middle = midle(head, gpbase.getCurrentPoint());
        if (velocity> MAX_VELOCITY*2/3) this.direction = getAngle(origin, circular);
        if (velocity> MAX_VELOCITY*1/3) this.direction = getAngle(origin, middle );
        else this.direction = getAngle(origin, head );

        median = normalAbsoluteAngle(direction);
        deviation = arc / 4;
        normalMedian = normalDistrib(median, median, deviation);
        /* (enemy.fireHead > enemy.fireMiddle) {
            if (enemy.fireHead > enemy.fireCircular)
                this.direction = getAngle(origin, head);
            else
                this.direction = getAngle(origin, circular);
        } else
            this.direction =  getAngle(origin, middle );
*/
    }

    public double getPower() {
        return (velocity-20.0D)/-3.0D;
    }
}
