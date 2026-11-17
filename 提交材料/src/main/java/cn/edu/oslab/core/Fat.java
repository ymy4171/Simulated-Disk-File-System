package cn.edu.oslab.core;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class Fat {
    private final BlockDisk disk;
    private final Consumer<String> log;
    public Fat(BlockDisk disk, Consumer<String> log) { this.disk = disk; this.log = log; }
    public int get(int block) throws IOException { return Byte.toUnsignedInt(disk.buffer1.load(block / 64)[block % 64]); }
    public void set(int block, int value) throws IOException { disk.buffer1.load(block / 64)[block % 64] = (byte)value; disk.buffer1.dirty(); }
    public int freeCount() throws IOException { int count=0; for(int i=3;i<128;i++) if(get(i)==0) count++; return count; }
    public int allocate() throws IOException {
        for(int i=3;i<128;i++) if(i!=23 && i!=49 && get(i)==0) { set(i,255); log.accept("分配块 " + i); return i; }
        throw new FsException("磁盘空间不足");
    }
    public List<Integer> chain(int start) throws IOException {
        List<Integer> result=new ArrayList<>(); Set<Integer> seen=new HashSet<>(); int n=start;
        while(true) {
            if(n<3 || n>=128 || n==23 || n==49) throw new FsException("FAT 非法指针："+n);
            if(!seen.add(n)) throw new FsException("FAT 循环链："+n);
            result.add(n); int next=get(n); if(next==255) return result; n=next;
        }
    }
    public void release(int start) throws IOException { for(int n:chain(start)) { set(n,0); log.accept("回收块 "+n); } }
}
