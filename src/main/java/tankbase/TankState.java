package tankbase;

import tankbase.enemy.EnemyDetectedEvent;

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
import static robocode.Rules.DECELERATION;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.Rules.getTurnRateRadians;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankBase.GUN_COOLING_RATE;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.pointInBattleField;
import static tankbase.enemy.Enemy.MAX_GUN_HEAT;

public class TankState extends Point2D.Double {
    private final double headingRadians;
    private final int others;
    private final long time;
    private final double velocity;
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
        super(x, y);
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
    public TankState(EnemyDetectedEvent ede, TankState scanner) {
        headingRadians = ede.getHeadingRadians();
        double angle = normalAbsoluteAngle(scanner.getHeadingRadians() - ede.getBearingRadians());
        double distance = ede.getDistance();
        x = scanner.getX() + distance * cos(angle);
        y = scanner.getY() + distance * sin(angle);
        velocity = ede.getVelocity();
        gunHeat = MAX_GUN_HEAT;
        energy = ede.getEnergy();
        others = scanner.getOthers();
        time = ede.getTime();
        acceleration = 0;
        turnRate = 0;
        vmax = max(0, velocity);
        vmin = min(0, velocity);
    }

    // Create TankState from ScannedRobotEvent and previous TankState to calculate acceleration, turnRate, gunHeat
    public TankState(EnemyDetectedEvent ede, TankState previous, TankState scanner) {
        this(ede, scanner);
        if (previous != null) {
            long deltaTime = computeDeltaTimeAccelerationAndTurnRate(previous);
            double newGunHeat = previous.gunHeat - GUN_COOLING_RATE * deltaTime;
            gunHeat = newGunHeat > 0 ? newGunHeat : 0;
            vmax = max(previous.vmax, vmax);
            vmin = min(previous.vmin, vmin);
            //sysout.printf("%s accel=%02f, velocity=%02f%n", ede.getName(), acceleration, velocity);
        }
    }

    // Extrapolate next TankState based on current state return clone of this if next is out of battlefield (hit wall)
    public TankState extrapolateNextState() {
        return extrapolateNextState(true);
    }

    public TankState extrapolateNextState(boolean wall) {
        if (!(energy == 0 || acceleration == 0 && velocity == 0)) {
            //sysout.printf("extra accel=%02f, velocity=%02f%n", acceleration, velocity);

            double nextX = x + velocity * cos(headingRadians);
            double nextY = y + velocity * sin(headingRadians);
            double nextHeading = headingRadians + min(abs(turnRate), getTurnRateRadians(velocity)) * signum(turnRate);
            double nextVelocity = velocity + acceleration;
            if (abs(nextVelocity) > MAX_VELOCITY)
                nextVelocity = velocity;
            double nextAcceleration = acceleration;
            double nextGunHeat = gunHeat > 0 ? gunHeat - GUN_COOLING_RATE : 0;

            if (isDecelerate() && signum(nextVelocity) != signum(velocity)) {
                acceleration = ACCELERATION * signum(acceleration);
            }

            if (!wall || pointInBattleField(new Point2D.Double(nextX, nextY), TANK_SIZE / 2.1)) {
                return new TankState(nextX, nextY, nextHeading, gunHeadingRadians, turnRemaining, nextVelocity, nextGunHeat, energy, others,
                        time + 1, nextAcceleration, turnRate, vmax, vmin);
            }
        }

        return this;
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

    public boolean isDecelerate() {
        return isDecelerate;
    }

    private long computeDeltaTimeAccelerationAndTurnRate(TankState previous) {
        if (previous == null) return 0;
        long deltaTime = time - previous.time;
        if (deltaTime <= 0) return 0;
        acceleration = (velocity - previous.velocity) / deltaTime;
        isDecelerate = abs(acceleration) > ACCELERATION;
        if (abs(acceleration) > DECELERATION && velocity == 0) {
            // hit something
            acceleration = 0;
        }
        turnRate = (headingRadians - previous.headingRadians) / deltaTime;
        return deltaTime;
    }

    @Override
    public String toString() {
        return String.format(
                "TankState: x=%.0f, y=%.0f, head=%.0f, v=%.0f, ernergy=%.1f, time=%d, accel=%.1f, turn=%.0f, gunHeat=%.1f vmax=%.1f, vmin=%.1f",
                x, y, toDegrees(headingRadians), velocity, energy, time, acceleration, turnRate, gunHeat, vmax, vmin);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TankState tankState = (TankState) o;
        return java.lang.Double.compare(headingRadians, tankState.headingRadians) == 0 && java.lang.Double.compare(velocity, tankState.velocity) == 0 && others == tankState.others && time == tankState.time && isDecelerate == tankState.isDecelerate && java.lang.Double.compare(acceleration, tankState.acceleration) == 0 && java.lang.Double.compare(energy, tankState.energy) == 0 && java.lang.Double.compare(gunHeadingRadians, tankState.gunHeadingRadians) == 0 && java.lang.Double.compare(gunHeat, tankState.gunHeat) == 0 && java.lang.Double.compare(turnRate, tankState.turnRate) == 0 && java.lang.Double.compare(turnRemaining, tankState.turnRemaining) == 0 && java.lang.Double.compare(vmax, tankState.vmax) == 0 && java.lang.Double.compare(vmin, tankState.vmin) == 0;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + java.lang.Double.hashCode(headingRadians);
        result = 31 * result + java.lang.Double.hashCode(velocity);
        result = 31 * result + others;
        result = 31 * result + Long.hashCode(time);
        result = 31 * result + Boolean.hashCode(isDecelerate);
        result = 31 * result + java.lang.Double.hashCode(acceleration);
        result = 31 * result + java.lang.Double.hashCode(energy);
        result = 31 * result + java.lang.Double.hashCode(gunHeadingRadians);
        result = 31 * result + java.lang.Double.hashCode(gunHeat);
        result = 31 * result + java.lang.Double.hashCode(turnRate);
        result = 31 * result + java.lang.Double.hashCode(turnRemaining);
        result = 31 * result + java.lang.Double.hashCode(vmax);
        result = 31 * result + java.lang.Double.hashCode(vmin);
        return result;
    }
}
