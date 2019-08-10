package gpbase.gun;

import gpbase.Enemy;

public interface Gunner {
    String getName();
    double aim(Enemy target);
    double hitRate();
}
