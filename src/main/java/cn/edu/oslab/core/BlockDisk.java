package cn.edu.oslab.core;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** 唯一磁盘 I/O 入口；没有整盘缓存。两个缓冲区共享于所有打开文件。 */
public final class BlockDisk implements AutoCloseable {
    public static final int SIZE = 64, BLOCKS = 128;
    private final RandomAccessFile file;
    private final FileLock lock;
    private final Consumer<String> log;
    public final Buffer buffer1 = new Buffer("buffer1 · FAT / 目录");
    public final Buffer buffer2 = new Buffer("buffer2 · 文件数据");

    public BlockDisk(Path path, Consumer<String> log) throws IOException {
        this.log = log;
        file = new RandomAccessFile(path.toFile(), "rw");
        FileLock acquired;
        try { acquired = file.getChannel().tryLock(); }
        catch (RuntimeException | IOException e) { file.close(); throw new IOException("磁盘已被另一个程序占用", e); }
        if (acquired == null) { file.close(); throw new IOException("磁盘已被另一个程序占用"); }
        lock = acquired;
        if (file.length() != SIZE * BLOCKS) { close(); throw new IOException("磁盘长度必须为 8192 字节"); }
    }

    public static void initialize(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            for (int i = 0; i < BLOCKS; i++) {
                byte[] block = new byte[SIZE];
                if (i < 2) for (int j = 0; j < SIZE; j++) {
                    int n = i * SIZE + j;
                    block[j] = (byte) (n < 3 ? 255 : n == 23 || n == 49 ? 254 : 0);
                }
                if (i == 2) for (int j = 0; j < SIZE; j += 8) block[j] = '$';
                out.write(block);
            }
        }
    }
    private void valid(int block) { if (block < 0 || block >= BLOCKS) throw new FsException("块号越界：" + block); }
    public byte[] inspect(int block) throws IOException {
        valid(block);
        if (buffer1.block == block) return buffer1.data.clone();
        if (buffer2.block == block) return buffer2.data.clone();
        byte[] bytes = new byte[SIZE]; file.seek((long) block * SIZE); file.readFully(bytes); return bytes;
    }
    public void flush() throws IOException { buffer1.flush(); buffer2.flush(); file.getFD().sync(); }
    public void close() throws IOException { try { flush(); } finally { try { lock.release(); } finally { file.close(); } } }

    public final class Buffer {
        private final String name;
        private final byte[] data = new byte[SIZE];
        private int block = -1;
        private boolean dirty;
        private Buffer(String name) { this.name = name; }
        public byte[] load(int next) throws IOException {
            valid(next);
            Buffer other = this == buffer1 ? buffer2 : buffer1;
            if (other.block == next) { other.flush(); other.block = -1; }
            if (block != next) {
                flush(); file.seek((long) next * SIZE); file.readFully(data); block = next;
                log.accept(name + " 读入块 " + next);
            }
            return data;
        }
        public void dirty() { dirty = true; }
        public void flush() throws IOException {
            if (dirty) { file.seek((long) block * SIZE); file.write(data); dirty = false; log.accept(name + " 写回块 " + block); }
        }
        public Map<String,Object> snapshot() { return Map.of("name",name,"block",block,"dirty",dirty,"hex",HexFormat.ofDelimiter(" ").formatHex(data)); }
    }
}
