package tankbase.gun;

import robocode.Rules;

public class FireStat {
    long fireCount;
    long hitCount;
    double dommage;
    double cost;

    public FireStat() {
        cost = dommage = fireCount = hitCount = 0;
    }

    public void hit(Double power) {
        cost += power;
        fireCount++;
        hitCount++;
        dommage+= Rules.getBulletDamage(power);
    }

    public void miss(Double power) {
        cost += power;
        fireCount++;
    }

    public double getHitRate() {
        return fireCount > 0 ? (double) getHitCount() / getFireCount() : 1;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getFireCount() {
        return fireCount;
    }

    public double getDommageCostRatio() { return cost >0 ? dommage/cost : 0; }
}
