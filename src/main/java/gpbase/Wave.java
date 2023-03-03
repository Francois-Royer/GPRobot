package gpbase;

import robocode.Rules;

import java.awt.Point;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;

public class Wave extends MovingPoint {
    String name;
    double arc;

    public Wave(String name, double velocity, long start, Point.Double origin, GPBase gpbase) {
        super(origin, velocity, 0, start);
        this.name = name;
        Point.Double cp = gpbase.getCurrentPoint();
        double distance = origin.distance(cp);
        double time = distance/velocity;
        cp.x += cos(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();
        cp.y += sin(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();

        //this.arc = arc(origin, cp, gpbase.getCurrentPoint())+0.30;
        this.arc = arc(origin, cp, gpbase.getCurrentPoint())*2;
        cp = midle(cp, gpbase.getCurrentPoint());
        this.direction = getAngle(origin,cp);
        //this.direction = getAngle(origin,gpbase.getCurrentPoint());
    }

    public double getPower() {
        return (velocity-20.0D)/-3.0D;
    }
}
