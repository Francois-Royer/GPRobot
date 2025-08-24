package tankbase.gun;

import robocode.Rules;

public class FireStat {
    long fireCount;
    long hitCount;
    double damage;
    double cost;

    public FireStat() {
        cost = damage = fireCount = hitCount = 0;
    }

    public void hit(Double power) {
        cost += power;
        fireCount++;
        hitCount++;
        damage += Rules.getBulletDamage(power);
    }

    public void miss(Double power) {
        cost += power;
        fireCount++;
    }

    public double getHitRate() {
        return fireCount > 0 ? (double) hitCount / fireCount : 1;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getFireCount() {
        return fireCount;
    }

    public double getDommageCostRatio() {
        return cost > 0 ? damage / cost : 0;
    }
}
