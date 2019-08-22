package net.corda.testing.driver.internal

import net.corda.core.utilities.contextLogger
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.isLocalPortBound
import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * It uses backing file to store information about last allocated port.
 * Implementation note:
 * The (small)file is read into memory and then `Unsafe` operation is used to work directly with that memory
 * performing atomic compare and swap operations as necessary
 * This enables the same file to be used my multiple processed running on the same machine such that they will be
 * able to concurrently allocate ports without clashing with each other.
 */
internal class SharedMemoryIncremental
private constructor(private val startPort: Int, private val endPort: Int,
                    file: File = File(System.getProperty("user.home"), "corda-$startPort-to-$endPort-port-allocator.bin")) : PortAllocation() {

    private val backingFile: RandomAccessFile = RandomAccessFile(file, "rw")
    private val mb: MappedByteBuffer
    private val memoryOffsetAddress: Long

    init {
        mb = backingFile.channel.map(FileChannel.MapMode.READ_WRITE, 0, 16) // TODO: Do we really need 16 bytes? Given that we care about Int it should be enough to have 4
        memoryOffsetAddress = (mb as DirectBuffer).address()
    }

    /**
     * An implementation of [PortAllocation] which allocates ports sequentially
     */
    companion object {

        private val UNSAFE: Unsafe = getUnsafe()
        private fun getUnsafe(): Unsafe {
            val f = Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            return f.get(null) as Unsafe
        }

        val INSTANCE = SharedMemoryIncremental(DEFAULT_START_PORT, FIRST_EPHEMERAL_PORT)

        val logger = contextLogger()
    }

    override fun nextPort(): Int {
        var newValue: Long

        do {
            val oldValue = UNSAFE.getLongVolatile(null, memoryOffsetAddress)
            newValue = if (oldValue + 1 >= endPort || oldValue < startPort) {
                logger.warn("Port allocation rolling over: oldValue=$oldValue, startPort=$startPort, endPort=$endPort")
                startPort.toLong()
            } else {
                (oldValue + 1)
            }
            val compareAndSwapSuccess = UNSAFE.compareAndSwapLong(null, memoryOffsetAddress, oldValue, newValue)
            val success = if (!compareAndSwapSuccess) false else {
                val alreadyBound = isLocalPortBound(newValue.toInt())
                if (alreadyBound) {
                    logger.warn("Port $newValue appears to be bound. Allocator will skip it.")
                }
                !alreadyBound
            }
        } while (!success)

        return newValue.toInt()
    }
}