package gpbase;

import gpbase.gun.CircularGunner;

import java.awt.*;
import java.awt.geom.Point2D;

import static gpbase.GPUtils.*;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class Wave extends MovingPoint {
    String name;
    double arc;

    Point2D.Double headon;
    Point2D.Double middle;
    Point2D.Double circular;


    public Wave(String name, double velocity, long start, Point.Double origin, GPBase gpbase) {
        super(origin, velocity, 0, start);
        this.name = name;
        headon = gpbase.getCurrentPoint();
        double distance = origin.distance(headon);
        double time = distance/velocity;

        circular = new Point2D.Double();

        circular.x = headon.x + cos(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();
        circular.y = headon.y + sin(trigoAngle(gpbase.getHeadingRadians())) * time * gpbase.getVelocity();

        this.arc = arc(origin, circular, headon)*3+.01;
        middle = midle(headon, gpbase.getCurrentPoint());
        this.direction = getAngle(origin, middle);
    }

    public double getPower() {
        return (velocity-20.0D)/-3.0D;
    }


}
