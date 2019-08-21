package net.corda.testing.node;

import net.corda.testing.driver.PortAllocation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class PortAllocationRunner {

    public static void main(@NotNull String[] args) throws IOException {
        /*
         * each JVM will be launched with [spinnerFile, reportingIndex]
         */
        int reportingIndex = Integer.parseInt(args[1]);

        RandomAccessFile spinnerBackingFile = new RandomAccessFile(args[0], "rw");
        MappedByteBuffer spinnerBuffer = spinnerBackingFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16);

        /*
         * notify back to launching process that we are ready to start using the reporting index we were allocated on launch
         */
        spinnerBuffer.putShort(reportingIndex, ((short) 10));

        /*
         * wait for parent process to notify us that all waiting processes are good to go
         * do not Thread.sleep as we want to ensure there is as much of an overlap between the ports selected by the spawned processes
         * and by sleeping, its frequently the case that one will complete selection before another wakes up resulting in  sequential ranges rather than overlapping
         */
        while (spinnerBuffer.getShort(0) != 8) {
        }

        /*
         * allocate ports and print out for later consumption by the spawning test
         */
        PortAllocation pa = PortAllocation.getDefaultAllocator();
        int iterCount = Integer.parseInt(args[2]);
        for (int i = 0; i < iterCount; i++) {
            System.out.println(pa.nextPort());
        }
    }

}
