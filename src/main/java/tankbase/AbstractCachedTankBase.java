package tankbase;

import robocode.AdvancedRobot;
import tankbase.gun.kdformula.KDFormula;

import java.util.Collections;
import java.util.List;

import static tankbase.TankUtils.trigoAngle;

public abstract class AbstractCachedTankBase extends AdvancedRobot implements ITank {

    private TankState tankState;

    public void updateRobotCache() {
        updateRobotCache(super.getTime());
    }

    public void updateRobotCache(long now) {
        if (tankState == null || tankState.getTime() < now) {
            TankState prev = tankState;
            tankState = new TankState(prev,
                    super.getX(), super.getY(),
                    trigoAngle(super.getHeadingRadians()), trigoAngle(super.getGunHeadingRadians()),
                    super.getTurnRemaining(), super.getVelocity(), super.getGunHeat(), super.getEnergy(),
                    super.getOthers(), now);
        }
    }

    @Override
    public TankState getState() {
        return tankState;
    }

    @Override
    public double getX() {
        return tankState.getX();
    }

    @Override
    public double getY() {
        return tankState.getY();
    }

    @Override
    public double getHeadingRadians() {
        return tankState.getHeadingRadians();
    }

    @Override
    public double getGunHeadingRadians() {
        return tankState.getGunHeadingRadians();
    }

    @Override
    public double getTurnRemaining() {
        return tankState.getTurnRemaining();
    }

    @Override
    public double getVelocity() {
        return tankState.getVelocity();
    }

    @Override
    public double getGunHeat() {
        return tankState.getGunHeat();
    }

    @Override
    public double getEnergy() {
        return tankState.getEnergy();
    }

    @Override
    public long getTime() {
        return tankState.getTime();
    }

    @Override
    public KDFormula getPatternFormula() {
        return null;
    }

    @Override
    public KDFormula getSurferFormula() {
        return null;
    }

    @Override
    public List<KDMove> getMoveLog() {
        return Collections.emptyList();
    }

    @Override
    public void addFEnergy(double energy) {
        //NOOP
    }

    @Override
    public double getFEnergy() {
        return getEnergy();
    }

    @Override
    public long getLastStop() {
        return 0;
    }

    @Override
    public long getLastChangeDirection() {
        return 0;
    }

    @Override
    public long getLastVelocityChange() {
        return 0;
    }

    @Override
    public long getLastScan() {
        return 0;
    }

    @Override
    public int getOthers() {
        return tankState.getOthers();
    }

}