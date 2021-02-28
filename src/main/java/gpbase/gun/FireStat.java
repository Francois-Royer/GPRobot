package gpbase.gun;

public class FireStat {
    long fireCount;
    long hitCount;
    long hbbCount;

    public FireStat(long fireCount, long hitCount) {
        reset();
        this.fireCount = fireCount;
        this.hitCount = hitCount;
    }

    public FireStat() {
        reset();
    }

    public void reset() {
        fireCount = hitCount = hbbCount = 0;
    }

    public void fire(){ fireCount++; }
    public void hitByBullet(){ hbbCount++; }
    public void hit(){ hitCount++; }
    public double getHitRate(){ return fireCount == 0 ? 0 :(double) hitCount/fireCount;}
    public double getHitByBulletRate(){ return fireCount == 0 ? 0 :(double) hbbCount/fireCount;}
    public long getHitCount() { return hitCount; }
    public long getFireCount() { return fireCount; }
}
