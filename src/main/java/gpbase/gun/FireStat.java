package gpbase.gun;

public class FireStat {
    long fireCount;
    long hitCount;

    public FireStat() {
        reset();
    }

    public void reset() {
        fireCount = hitCount = 0;
    }

    public void fire(){ fireCount++; }
    public void hit(){ hitCount++; }
    public double getHitRate(){ return fireCount == 0 ? 0 :(double) hitCount/fireCount;}
}
