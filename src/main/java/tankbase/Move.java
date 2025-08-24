package tankbase;

public class Move {
    private final double[] patternKdPoint; // Point for kdtree
    private final double turn;
    private final double distance;
    private final long duration;
    private double[] surferKdPoint; // Point for kdtree

    public Move(double[] patternKdPoint, double[] surferKdPoint, double turn, double distance, long duration) {
        this.patternKdPoint = patternKdPoint;
        this.surferKdPoint = surferKdPoint;
        this.turn = turn;
        this.distance = distance;
        this.duration = duration;
    }

    public double[] getPatternKdPoint() {
        return patternKdPoint;
    }

    public double[] getSurferKdPoint() {
        return surferKdPoint;
    }

    public void setSurferKdPoint(double[] surferKdPoint) {
        this.surferKdPoint = surferKdPoint;
    }

    public double getTurn() {
        return turn;
    }

    public double getDistance() {
        return distance;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return Double.toString(turn) + ',' + distance + ',' + duration + ',';
    }
}
