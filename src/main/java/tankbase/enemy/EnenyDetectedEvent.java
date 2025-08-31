package tankbase.enemy;

import robocode.BulletHitEvent;
import robocode.ScannedRobotEvent;
import tankbase.TankState;
import tankbase.gun.Fire;

import java.awt.geom.Point2D;

import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.trigoAngle;

public class EnenyDetectedEvent {
    private double heading;
    private double bearing;
    private double distance;
    private double velocity;
    private double energy;
    private long time;

    public EnenyDetectedEvent(ScannedRobotEvent sre) {
        this.time = sre.getTime();
        this.heading = trigoAngle(sre.getHeadingRadians());
        this.bearing = sre.getBearingRadians();
        this.energy= sre.getEnergy();
        this.distance = sre.getDistance();
        this.velocity = sre.getVelocity();
    }

    public EnenyDetectedEvent(BulletHitEvent bhe, Fire fire) {
        this.time = bhe.getTime();
        Point2D.Double position = fire.getPosition(time);
        TankState gunner = fire.getAimingData().getGunner().getGunner().getState();
        this.heading = getPointAngle(gunner, position);
        this.bearing = 0; // unknow
        this.energy= bhe.getEnergy();
        this.distance = gunner.distance(position);
        this.velocity = 0; // unknow;
    }

    public EnenyDetectedEvent(BulletHitEvent bhe, Fire fire, TankState prev) {
        this.time = bhe.getTime();
        Point2D.Double position = fire.getPosition(time);
        TankState gunner = fire.getAimingData().getGunner().getGunner().getState();
        this.heading = getPointAngle(gunner, position);
        this.bearing = gunner.getGunHeadingRadians()-prev.getHeadingRadians();
        this.energy= bhe.getEnergy();
        this.distance = gunner.distance(position);
        this.velocity = prev.getVelocity();
    }

    public double getHeadingRadians() {
        return heading;
    }

    public double getBearingRadians() {
        return bearing;
    }

    public double getDistance() {
        return distance;
    }

    public double getEnergy() {
        return energy;
    }

    public double getVelocity() {
        return velocity;
    }

    public long getTime() {
        return time;
    }
}
