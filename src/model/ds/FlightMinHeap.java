package model.ds;

import model.Flight;
import java.util.Arrays;

/**
 * Custom Min-Heap implementation for storing Flights.
 * Ordered based on Flight's natural ordering (Urgency Score).
 * <p>
 * This implementation uses an array to store the heap elements.
 * It is required to satisfy the project constraints (No PriorityQueue allowed).
 * </p>
 */
public class FlightMinHeap {
    private Flight[] heap;
    private int size;
    private int capacity;

    private static final int DEFAULT_CAPACITY = 20;

    public FlightMinHeap(int capacity) {
        this.capacity = capacity;
        this.size = 0;
        this.heap = new Flight[capacity];
    }

    public FlightMinHeap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Inserts a new flight into the heap.
     * Maintains the min-heap property by bubbling up the new element.
     * <p>
     * <b>Time Complexity: O(log N), where N is the number of elements in the
     * heap.
     * The new element travels up the height of the tree.
     * </p>
     * 
     * @param f The flight to insert.
     */
    public void insert(Flight f) {
        if (size == capacity) {
            resize();
        }

        // Insert at the end
        int current = size;
        heap[size] = f;
        size++;

        // Bubble Up
        while (current > 0 && heap[current].compareTo(heap[parent(current)]) < 0) {
            swap(current, parent(current));
            current = parent(current);
        }
    }

    /**
     * Removes and returns the flight with the highest priority (min value).
     * Maintains the min-heap property by bubbling down the new root.
     * <p>
     * <b>Time Complexity: O(log N), where N is the number of elements in the
     * heap.
     * The last element is moved to the root and travels down the height of the
     * tree.
     * </p>
     * 
     * @return The most urgent Flight, or null if empty.
     */
    public Flight extractMin() {
        if (isEmpty()) {
            return null;
        }

        Flight min = heap[0];

        // Move last element to root
        heap[0] = heap[size - 1];
        heap[size - 1] = null; // Help GC
        size--;

        // Bubble Down
        heapify(0);

        return min;
    }

    /**
     * Recursive helper method to maintain the heap property from a given index
     * downwards.
     * <p>
     * <b>Time Complexity:<O(log N).
     * </p>
     * 
     * @param index The index to start heapifying from.
     */
    private void heapify(int index) {
        int left = leftChild(index);
        int right = rightChild(index);
        int smallest = index;

        // Compare with left child
        if (left < size && heap[left].compareTo(heap[smallest]) < 0) {
            smallest = left;
        }

        // Compare with right child
        if (right < size && heap[right].compareTo(heap[smallest]) < 0) {
            smallest = right;
        }

        // If smallest is not current, swap and continue
        if (smallest != index) {
            swap(index, smallest);
            heapify(smallest);
        }
    }

    private int parent(int i) {
        return (i - 1) / 2;
    }

    private int leftChild(int i) {
        return 2 * i + 1;
    }

    private int rightChild(int i) {
        return 2 * i + 2;
    }

    private void swap(int i, int j) {
        Flight temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    private void resize() {
        capacity *= 2;
        heap = Arrays.copyOf(heap, capacity);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }
}
