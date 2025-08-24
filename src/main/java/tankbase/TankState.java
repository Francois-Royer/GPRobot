package tankbase;

import robocode.ScannedRobotEvent;

import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.signum;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static robocode.Rules.ACCELERATION;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.Rules.getTurnRateRadians;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankBase.GUN_COOLING_RATE;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.Enemy.MAX_GUN_HEAT;
import static tankbase.TankUtils.checkMinMax;
import static tankbase.TankUtils.trigoAngle;

public class TankState {
    private final double headingRadians;
    private final double velocity;
    private final double x;
    private final double y;
    private final int others;
    private final long time;
    private boolean isDecelerate;
    private double acceleration;
    private double energy;
    private double gunHeadingRadians;
    private double gunHeat;
    private double turnRate;
    private double turnRemaining;
    private double vmax;
    private double vmin;

    public TankState(double x, double y, double headingRadians, double gunHeadingRadians, double turnRemaining, double velocity,
                     double gunHeat, double energy, int others, long time, double acceleration, double turnRate, double vmax, double vmin) {
        this.x = x;
        this.y = y;
        this.headingRadians = headingRadians;
        this.gunHeadingRadians = gunHeadingRadians;
        this.turnRemaining = turnRemaining;
        this.velocity = velocity;
        this.gunHeat = gunHeat;
        this.energy = energy;
        this.others = others;
        this.time = time;
        this.acceleration = acceleration;
        this.turnRate = turnRate;
        this.vmax = max(vmax, velocity);
        this.vmin = min(vmin, velocity);
        this.isDecelerate = false;
    }

    public TankState(TankState previous, double x, double y, double headingRadians, double gunHeadingRadians, double turnRemaining,
                     double velocity,
                     double gunHeat, double energy, int others, long time) {
        this(x, y, headingRadians, gunHeadingRadians, turnRemaining, velocity, gunHeat, energy, others, time, 0, 0,
             previous != null ? previous.vmax : 0, previous != null ? previous.vmin : 0);
        computeDeltaTimeAccelerationAndTurnRate(previous);
    }

    // Create initial TankState from ScannedRobotEvent
    public TankState(ScannedRobotEvent sre, TankState scanner) {
        headingRadians = trigoAngle(sre.getHeadingRadians());
        double angle = normalAbsoluteAngle(scanner.getHeadingRadians() - sre.getBearingRadians());
        double distance = sre.getDistance();
        x = scanner.getX() + distance * cos(angle);
        y = scanner.getY() + distance * sin(angle);
        velocity = sre.getVelocity();
        gunHeat = MAX_GUN_HEAT;
        energy = sre.getEnergy();
        others = scanner.getOthers();
        time = sre.getTime();
        acceleration = 0;
        turnRate = 0;
        vmax = max(0, velocity);
        vmin = min(0, velocity);
    }

    // Create TankState from ScannedRobotEvent and previous TankState to calculate acceleration, turnRate, gunHeat
    public TankState(ScannedRobotEvent sre, TankState previous, TankState scanner) {
        this(sre, scanner);
        if (previous != null) {
            long deltaTime = computeDeltaTimeAccelerationAndTurnRate(previous);
            double newGunHeat = previous.gunHeat - GUN_COOLING_RATE * deltaTime;
            gunHeat = newGunHeat > 0 ? newGunHeat : 0;
            vmax = max(previous.vmax, vmax);
            vmin = min(previous.vmin, vmin);
        }
    }

    // Extrapolate next TankState based on current state return null if out of battlefield (hit wall)
    public TankState extrapolateNextState() {
        if (energy == 0 || acceleration == 0 && velocity == 0 && turnRate == 0)
            return new TankState(x, y, headingRadians, gunHeadingRadians, turnRemaining, velocity, gunHeat, energy, others, time,
                                 acceleration, turnRate, vmax, vmin);

        double nextVelocity = checkMinMax(velocity + acceleration, max(vmin, -MAX_VELOCITY), min(vmax, MAX_VELOCITY));
        double nextAcceleration = nextVelocity - velocity;
        double nextHeading = headingRadians + min(abs(turnRate), getTurnRateRadians(velocity)) * signum(turnRate);
        double nextX = x + nextVelocity * cos(nextHeading);
        double nextY = y + nextVelocity * sin(nextHeading);
        double nextGunHeat = gunHeat > 0 ? gunHeat - GUN_COOLING_RATE : 0;

        if (TankUtils.pointInBattleField(new Point2D.Double(nextX, nextY), TANK_SIZE / 2.5)) {
            return new TankState(nextX, nextY, nextHeading, gunHeadingRadians, turnRemaining, nextVelocity, nextGunHeat, energy, others,
                                 time + 1, nextAcceleration, turnRate, vmax, vmin);
        }

        return null;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getHeadingRadians() {
        return headingRadians;
    }

    public double getGunHeadingRadians() {
        return gunHeadingRadians;
    }

    public double getTurnRemaining() {
        return turnRemaining;
    }

    public double getVelocity() {
        return velocity;
    }

    public double getGunHeat() {
        return gunHeat;
    }

    public void setGunHeat(double gunHeat) {
        this.gunHeat = gunHeat;
    }

    public double getEnergy() {
        return energy;
    }

    public TankState setEnergy(double energy) {
        this.energy = energy;
        return this;
    }

    public int getOthers() {
        return others;
    }

    public long getTime() {
        return time;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public double getTurnRate() {
        return turnRate;
    }

    public double getVmax() {
        return vmax;
    }

    public double getVmin() {
        return vmin;
    }

    public double getMovingDirection() {
        return normalRelativeAngle(headingRadians + ((velocity >= 0) ? 0 : PI));
    }

    public Point2D.Double getPosition() {
        return new Point2D.Double(x, y);
    }

    public boolean isDecelerate() {
        return isDecelerate;
    }

    private long computeDeltaTimeAccelerationAndTurnRate(TankState previous) {
        if (previous == null) return 0;
        long deltaTime = time - previous.time;
        if (deltaTime <= 0) return 0;
        isDecelerate = abs(velocity - previous.getVelocity()) > ACCELERATION;
        acceleration = (velocity - previous.velocity) / deltaTime;
        turnRate = (headingRadians - previous.headingRadians) / deltaTime;
        return deltaTime;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TankState tankState = (TankState) o;
        return Double.compare(headingRadians, tankState.headingRadians) == 0 &&
                Double.compare(velocity, tankState.velocity) == 0 &&
                Double.compare(x, tankState.x) == 0 &&
                Double.compare(y,tankState.y) == 0 &&
                others == tankState.others &&
                time == tankState.time &&
                isDecelerate == tankState.isDecelerate &&
                Double.compare(acceleration, tankState.acceleration) == 0 &&
                Double.compare(energy, tankState.energy) == 0 && Double.compare(
                gunHeadingRadians, tankState.gunHeadingRadians) == 0 &&
                Double.compare(gunHeat, tankState.gunHeat) == 0 && Double.compare(
                turnRate, tankState.turnRate) == 0 &&
                Double.compare(turnRemaining, tankState.turnRemaining) == 0 &&
                Double.compare(vmax, tankState.vmax) == 0 &&
                Double.compare( vmin, tankState.vmin) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(headingRadians);
        result = 31 * result + Double.hashCode(velocity);
        result = 31 * result + Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + others;
        result = 31 * result + Long.hashCode(time);
        result = 31 * result + Boolean.hashCode(isDecelerate);
        result = 31 * result + Double.hashCode(acceleration);
        result = 31 * result + Double.hashCode(energy);
        result = 31 * result + Double.hashCode(gunHeadingRadians);
        result = 31 * result + Double.hashCode(gunHeat);
        result = 31 * result + Double.hashCode(turnRate);
        result = 31 * result + Double.hashCode(turnRemaining);
        result = 31 * result + Double.hashCode(vmax);
        result = 31 * result + Double.hashCode(vmin);
        return result;
    }

    @Override
    public String toString() {
        return String.format(
                "TankState: x=%.0f, y=%.0f, head=%.0f, v=%.0f, ernergy=%.0f, time=%d, accel=%.0f, turn=%.0f, gunHeat=%.1f vmax=%.0f, vmin=%.0f",
                x, y, toDegrees(headingRadians), velocity, energy, time, acceleration, turnRate, gunHeat, vmax, vmin);
    }
}
