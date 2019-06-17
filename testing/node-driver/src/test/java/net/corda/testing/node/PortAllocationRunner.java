package net.corda.testing.node;

import net.corda.testing.driver.PortAllocation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class PortAllocationRunner {

    public static void main(@NotNull String[] args) throws IOException {
        //each JVM will be launched with [allocationFile, spinnerFile, reportingIndex]
        int reportingIndex = Integer.parseInt(args[2]);

        RandomAccessFile spinnerBackingFile = new RandomAccessFile(args[1], "rw");
        MappedByteBuffer spinnerBuffer = spinnerBackingFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16);

        //notify back to launching process that we are ready to start using the reporting index we were allocated on launch
        spinnerBuffer.putShort(reportingIndex, ((short) 10));

        //wait for parent process to notify us that all waiting processes are good to go
        while (spinnerBuffer.getShort(0) != 8) {
        }

        //allocate ports
        PortAllocation pa = new PortAllocation(0, new File(args[0]));
        for (int i = 0; i < 10000; i++) {
            System.out.println(pa.nextPort());
        }
    }

}
