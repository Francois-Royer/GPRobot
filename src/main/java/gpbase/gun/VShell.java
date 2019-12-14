package gpbase.gun;

import gpbase.Enemy;
import gpbase.MovingPoint;

public class VShell extends MovingPoint {
    private Enemy target;
    private Gunner gunner;

    public VShell(Double origin, double velocity, double direction, long start, Enemy target, Gunner gunner) {
        super(origin, velocity, direction, start);
        this.target = target;
        this.gunner = gunner;
    }

    public Enemy getTarget(){
        return target;
    }

    public Gunner getGunner() {
        return gunner;
    }
}
