package cn.edu.oslab.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Directory {
    public record Entry(String name,int attribute,int start,int length,int parent,int slot) {
        public boolean directory() { return attribute==8; }
    }
    private final BlockDisk disk;
    public Directory(BlockDisk disk) { this.disk=disk; }
    public List<Entry> list(int block) throws IOException {
        byte[] bytes=disk.buffer1.load(block); List<Entry> entries=new ArrayList<>();
        for(int slot=0;slot<8;slot++) {
            int p=slot*8; if(bytes[p]=='$') continue;
            String base=new String(bytes,p,3,StandardCharsets.US_ASCII).stripTrailing();
            String ext=new String(bytes,p+3,2,StandardCharsets.US_ASCII).stripTrailing();
            int attr=Byte.toUnsignedInt(bytes[p+5]);
            entries.add(new Entry(base+(ext.isEmpty()?"":"."+ext),attr,Byte.toUnsignedInt(bytes[p+6]),Byte.toUnsignedInt(bytes[p+7]),block,slot));
        }
        return entries;
    }
    public void put(Entry e) throws IOException {
        byte[] b=disk.buffer1.load(e.parent()); int p=e.slot()*8; Arrays.fill(b,p,p+8,(byte)' ');
        String[] parts=e.name().split("\\.",-1);
        byte[] base=parts[0].getBytes(StandardCharsets.US_ASCII); System.arraycopy(base,0,b,p,base.length);
        if(parts.length==2) { byte[] ext=parts[1].getBytes(StandardCharsets.US_ASCII); System.arraycopy(ext,0,b,p+3,ext.length); }
        b[p+5]=(byte)e.attribute(); b[p+6]=(byte)e.start(); b[p+7]=(byte)e.length(); disk.buffer1.dirty();
    }
    public void remove(Entry e) throws IOException { disk.buffer1.load(e.parent())[e.slot()*8]='$'; disk.buffer1.dirty(); }
    public void empty(int block) throws IOException { byte[] b=disk.buffer1.load(block); Arrays.fill(b,(byte)0); for(int i=0;i<64;i+=8)b[i]='$'; disk.buffer1.dirty(); }
    public int vacant(int block) throws IOException { byte[] b=disk.buffer1.load(block); for(int i=0;i<8;i++)if(b[i*8]=='$')return i; throw new FsException("目录已满（最多 8 项）"); }
    public static void name(String name, boolean directory) {
        String[] p=name.split("\\.",-1);
        if(p.length>(directory?1:2) || p[0].isEmpty() || p[0].length()>3 || (p.length==2 && (p[1].isEmpty() || p[1].length()>2))) throw new FsException("名称需为 1～3 字节，扩展名为 1～2 字节；目录无扩展名");
        for(String part:p)for(char c:part.toCharArray()) if(c<33 || c>126 || "$./\\".indexOf(c)>=0)throw new FsException("名称仅支持非空白 ASCII，不能含 $ . / 或反斜杠");
    }
    public static String path(String path) {
        if(path==null || !path.startsWith("/") || path.contains("//") || (path.length()>1 && path.endsWith("/"))) throw new FsException("请输入规范绝对路径，例如 /doc/a.tx");
        if(!path.equals("/")) { String[] parts=path.substring(1).split("/"); for(int i=0;i<parts.length;i++)name(parts[i],i<parts.length-1); }
        return path;
    }
    public Entry resolve(String path) throws IOException {
        path(path); if(path.equals("/"))return new Entry("/",8,2,0,-1,-1);
        Entry current=new Entry("/",8,2,0,-1,-1);
        for(String part:path.substring(1).split("/")) {
            if(!current.directory())throw new FsException("路径中间项不是目录");
            current=list(current.start()).stream().filter(e->e.name().equals(part)).findFirst().orElseThrow(()->new FsException("路径不存在："+part));
        }
        return current;
    }
}
