package tankbase.kdtree.dataStructures;

/**
 *
 */
public interface MaxHeap<T> {
    int size();

    void offer(double key, T value);

    void replaceMax(double key, T value);

    void removeMax();

    T getMax();

    double getMaxKey();
}