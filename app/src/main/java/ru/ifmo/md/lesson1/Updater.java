package ru.ifmo.md.lesson1;

import android.util.Log;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Updates whirl field.
 *
 * @author Zakhar Voit (zakharvoit@gmail.com)
 */
class Updater {
    static final int WIDTH = 240;
    static final int HEIGHT = 320;
    private static final int SIZE = WIDTH * HEIGHT;
    private static final int[] PALETTE = {
            0xFFFF0000, 0xFF800000, 0xFF808000, 0xFF008000, 0xFF00FF00, 0xFF008080,
            0xFF0000FF, 0xFF000080, 0xFF800080, 0xFFFFFFFF};
    private static final int MAX_COLOR = PALETTE.length;
    private static final int CPUS = Runtime.getRuntime().availableProcessors();

    private final ExecutorService pool =
            Executors.newFixedThreadPool(CPUS);
    private final CompletionService<Void> service = new ExecutorCompletionService<Void>(pool);
    private final Worker[] workers = new Worker[CPUS];
    private final Cacher cacher;

    private int[] field = new int[SIZE];
    private int[] field2 = new int[SIZE];
    private int[] colors = new int[SIZE];

    private float scaleX;
    private float scaleY;
    private Iterator<int[]> repeater;

    class Worker implements Callable<Void> {
        final int begin;
        final int end;

        Worker(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }

        @Override
        public Void call() throws Exception {
            update(begin, end);

            return null;
        }
    }

    Updater() {
        final int step = SIZE / CPUS;
        for (int i = 0; i < CPUS - 1; i++) {
            workers[i] = new Worker(step * i, step * (i + 1));
        }
        workers[CPUS - 1] = new Worker(step * (CPUS - 1), SIZE);

        cacher = new Cacher();
    }

    public void setScaleX(float scaleX) {
        this.scaleX = scaleX;
    }

    public void setScaleY(float scaleY) {
        this.scaleY = scaleY;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public int[] getColors() {
        return colors;
    }

    void initField() {
        Random rand = new Random();
        for (int i = 0; i < SIZE; i++) {
            field[i] = rand.nextInt(MAX_COLOR);
        }
    }

    void update(int start, int end) {
        for (int i = start; i < end; i++) {
            field2[i] = field[i];
            int next = (field[i] + 1) % MAX_COLOR;
            all:
            for (int dy = -WIDTH; dy <= WIDTH; dy += WIDTH) {
                for (int dx = -1; dx <= 1; dx++) {
                    int j = (i + dy + dx + SIZE) % SIZE;
                    if (field[j] == next) {
                        field2[i] = next;
                        colors[i] = PALETTE[next];
                        break all;
                    }
                }
            }
        }
    }


    void updateAll() {
        if (repeater == null) repeater = cacher.searchForCycle(field);
        if (repeater != null) {
            colors = repeater.next();
            return;
        }

        for (int i = 0; i < CPUS; i++) {
            service.submit(workers[i]);
        }

        for (int i = 0; i < CPUS; i++) {
            try {
                service.take();
            } catch (InterruptedException ignored) {

            }
        }

        if (!cacher.add(field, colors)) Log.i("CACHE", "Limit exceeded");

        int[] buf = field;
        field = field2;
        field2 = buf;
    }
}
