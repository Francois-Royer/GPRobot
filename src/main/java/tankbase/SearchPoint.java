package tankbase;

import java.awt.geom.Point2D;

public class SearchPoint extends Point2D.Double {
    private int visited;

    public SearchPoint(double x, double y) {
        super(x, y);
    }

    public void reset() {
        visited = 0;
    }

    public void visit() {
        visited++;
    }

    public int visited() {
        return visited;
    }
}

