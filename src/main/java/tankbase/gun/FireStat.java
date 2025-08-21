package tankbase.gun;

public class FireStat {
    long fireCount;
    long hitCount;
    double dommage;
    double cost;

    public FireStat() {
        cost = dommage = fireCount = hitCount = 0;
    }

    public void fire(Double power) {
        fireCount++; cost += power;
    }

    public void unFire( Double power) {
        fireCount--;
        cost -= power;
    }

    public void hit(Double dommage) {
        this.dommage+=dommage;
        hitCount++;
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
