package net.corda.testing.node;

import net.corda.testing.driver.PortAllocation;
import net.corda.testing.driver.SharedMemoryPortAllocation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class PortAllocationRunner {

    public static void main(@NotNull String[] args) throws IOException {
        //each JVM will be launched with [allocationFile, spinnerFile, reportingIndex]
        int reportingIndex = Integer.parseInt(args[1]);

        RandomAccessFile spinnerBackingFile = new RandomAccessFile(args[0], "rw");
        MappedByteBuffer spinnerBuffer = spinnerBackingFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16);

        //notify back to launching process that we are ready to start using the reporting index we were allocated on launch
        spinnerBuffer.putShort(reportingIndex, ((short) 10));

        //wait for parent process to notify us that all waiting processes are good to go
        while (spinnerBuffer.getShort(0) != 8) {
        }

        //allocate ports
        PortAllocation pa = SharedMemoryPortAllocation.Companion.getINSTANCE();
        for (int i = 0; i < 10000; i++) {
            System.out.println(pa.nextPort());
        }
    }

}
