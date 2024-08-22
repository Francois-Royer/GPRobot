package tankbase.kdtree.dataStructures;

/**
 *
 */
public interface MinHeap<T> {
    int size();

    void offer(double key, T value);

    void replaceMin(double key, T value);

    void removeMin();

    T getMin();

    double getMinKey();
}
