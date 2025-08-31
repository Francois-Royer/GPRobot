package tankbase.gun;

import robocode.Rules;
import tankbase.ITank;
import tankbase.MovingPoint;

public class Fire extends MovingPoint {
    private final transient Aiming aiming;
    private final double damage;

    public Fire(Double origin, Aiming aiming, long start) {
        super(origin, Rules.getBulletSpeed(aiming.getFirePower()), aiming.getDirection(), start);
        this.aiming = aiming;
        this.damage = Rules.getBulletDamage(aiming.getFirePower());
    }

    public ITank getTarget() {
        return aiming.getTarget();
    }

    public Gun getGunner() {
        return aiming.getGun();
    }

    public Aiming getAimingData() {
        return aiming;
    }

    public double getDamage() {
        return damage;
    }

    @Override
    public String toString() {
        return "Fire{" +
                super.toString() + ", " +
                aiming +
                ", damage=" + damage +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Fire fire = (Fire) o;
        return java.lang.Double.compare(damage, fire.damage) == 0;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + java.lang.Double.hashCode(damage);
        return result;
    }
}
