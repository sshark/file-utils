package org.teckhooi.lister;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirList {
    static final private Logger LOGGER = LoggerFactory.getLogger(DirList.class);

    static public long calculate(Queue<File> directories, ExecutorService es) {
        long totalSize = 0L;
        while (!directories.isEmpty()) {
            Future<Long> child = es.submit(() -> {
                File dir = directories.poll();
                if (dir != null) {
                    LOGGER.debug("Calculating {} on {}", dir.getName(), Thread.currentThread().getName());
                    return getTotalSize(dir, directories);
                } else {
                    return 0L;
                }
            });

            try {
                totalSize += child.get();
            } catch (Exception e) {
                LOGGER.error("Timeout while calculating the size of directory due to {}", e.getMessage());
            }
        }
        return totalSize;
    }

    static private long getTotalSize(File dir, Queue<File> directories) {
        long totalSize = 0L;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    totalSize += f.length();
                } else if (f.isDirectory()) {
                    directories.add(f);
                }
            }
            return totalSize;
        } else {
            return 0L;
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No directory path is given");
            System.exit(1);
        }

        ExecutorService es = Executors.newFixedThreadPool(8);

        File dir = new File(args[0]);
        if (!dir.exists() || !dir.isDirectory()) {
            LOGGER.error("Path \"{}\" does not exist or it is not a directory path.", args[0]);
            System.exit(1);
        }

        Queue<File> directories = new ConcurrentLinkedQueue<>();
        directories.add(new File(args[0]));

        try {
            long start = System.currentTimeMillis();
            long totalSize = DirList.calculate(directories, es);
            long stop = System.currentTimeMillis();
            LOGGER.info("Total file size: {}", totalSize);
            LOGGER.info("Time take: {}ms", stop - start);
            LOGGER.info("Is expected size 944294699093 bytes? {}", totalSize == 944_261_867_375L);
        } finally {
            es.shutdown();
        }
    }
}
