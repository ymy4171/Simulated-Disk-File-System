package cn.edu.oslab.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import cn.edu.oslab.core.Directory.Entry;

/** 所有公开操作使用同一实例监视器；HTTP 与测试调用完全相同的核心。 */
public final class FileSystem implements AutoCloseable {
    private final Path path;
    private BlockDisk disk;
    private Fat fat;
    private Directory directories;
    private String loadError;
    private final LinkedHashMap<String,OpenFile> opened=new LinkedHashMap<>();
    private final Deque<String> logs=new ArrayDeque<>();
    private long sequence;
    private static class OpenFile {
        Entry entry; String mode; int length,read;
        OpenFile(Entry entry,String mode,int length){this.entry=entry;this.mode=mode;this.length=length;}
    }
    public FileSystem(Path path) { this.path=path.toAbsolutePath(); if(Files.exists(path))load(); }
    private void log(String message) { logs.addLast(String.format("%04d  %s",++sequence,message)); while(logs.size()>180)logs.removeFirst(); }
    private void load() {
        try {
            disk=new BlockDisk(path,this::log);fat=new Fat(disk,this::log);directories=new Directory(disk);
            validate();loadError=null;log("磁盘校验完成，载入已有文件系统");
        } catch(Exception e) {
            loadError=e.getMessage();if(disk!=null)try{disk.close();}catch(IOException ignored){} disk=null;
        }
    }
    private void ready(){if(disk==null)throw new FsException(loadError==null?"请先初始化模拟磁盘":"磁盘加载失败："+loadError);}
    public synchronized void initialize(boolean confirmed) throws IOException {
        if(!confirmed)throw new FsException("初始化需要明确确认");
        if(!opened.isEmpty())throw new FsException("请先关闭全部文件并保存");
        // 使用临时镜像，原镜像只在完整初始化成功后替换。
        Path temp=path.resolveSibling(path.getFileName()+".new-"+UUID.randomUUID());
        BlockDisk.initialize(temp);
        try {
            if(disk!=null){disk.close();disk=null;}
            // 即使当前镜像损坏，也验证它没有被其他进程锁定。
            if(Files.exists(path))try(var f=new java.io.RandomAccessFile(path.toFile(),"rw");var channel=f.getChannel()) {
                try(var lock=channel.tryLock()){if(lock==null)throw new FsException("磁盘正被其他程序占用");}
            }
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
            load();ready();log("初始化：128 块，123 个可分配块，坏块 23 / 49");
        } finally {Files.deleteIfExists(temp);}
    }
    private void validate() throws IOException {
        for(int i=0;i<3;i++)if(fat.get(i)!=255)throw new FsException("系统块 FAT 损坏："+i);
        if(fat.get(23)!=254 || fat.get(49)!=254)throw new FsException("坏块标记损坏");
        Set<Integer> used=new HashSet<>(List.of(0,1,2,23,49));validateDirectory(2,used);
        for(int i=3;i<128;i++)if(!used.contains(i)&&fat.get(i)!=0)throw new FsException("孤立分配块："+i);
    }
    private void validateDirectory(int block,Set<Integer> used) throws IOException {
        Set<String> names=new HashSet<>();
        for(Entry e:directories.list(block)) {
            Directory.name(e.name(),e.directory());if(!names.add(e.name()))throw new FsException("目录存在重名项");
            if(!e.directory())attribute(e.attribute());
            List<Integer> chain=fat.chain(e.start());
            for(int b:chain)if(!used.add(b))throw new FsException("共享块或目录循环："+b);
            if(e.directory()) {
                if(chain.size()!=1 || e.length()!=0)throw new FsException("目录结构损坏");
                validateDirectory(e.start(),used);
            } else {
                int n=length(e);if(e.length()!=chain.size() || (n+1+63)/64!=chain.size())throw new FsException("文件长度与 FAT 不一致");
            }
        }
    }
    private static void attribute(int a){if(a!=4&&a!=5&&a!=2&&a!=3)throw new FsException("文件属性仅支持 4、5、2、3");}
    private Entry file(String path) throws IOException {Entry e=directories.resolve(path);if(e.directory())throw new FsException("该路径是目录");return e;}
    private Entry creation(String path,boolean dir,int attr) throws IOException {
        Directory.path(path);if(path.equals("/"))throw new FsException("根目录已存在");
        int slash=path.lastIndexOf('/');String name=path.substring(slash+1);Directory.name(name,dir);
        Entry parent=directories.resolve(slash==0?"/":path.substring(0,slash));if(!parent.directory())throw new FsException("父路径不是目录");
        if(directories.list(parent.start()).stream().anyMatch(e->e.name().equals(name)))throw new FsException("同名文件或目录已存在");
        int slot=directories.vacant(parent.start());if(fat.freeCount()==0)throw new FsException("磁盘空间不足");
        return new Entry(name,attr,0,dir?0:1,parent.start(),slot);
    }
    public synchronized void md(String path) throws IOException {
        ready();Entry draft=creation(path,true,8);int block=fat.allocate();directories.empty(block);
        directories.put(new Entry(draft.name(),8,block,0,draft.parent(),draft.slot()));disk.flush();log("创建目录 "+path);
    }
    public synchronized List<Map<String,Object>> dir(String path) throws IOException {
        ready();Entry e=directories.resolve(path);if(!e.directory())throw new FsException("不是目录");
        List<Map<String,Object>> result=new ArrayList<>();for(Entry child:directories.list(e.start()))result.add(entryView(child,path.equals("/")?"/"+child.name():path+"/"+child.name()));return result;
    }
    public synchronized void rd(String path) throws IOException {
        ready();Entry e=directories.resolve(path);if(!e.directory() || path.equals("/"))throw new FsException("只能删除非根空目录");
        if(!directories.list(e.start()).isEmpty())throw new FsException("目录非空，不能删除");
        fat.release(e.start());directories.remove(e);disk.flush();log("删除空目录 "+path);
    }
    public synchronized void create_file(String path,int attr) throws IOException {
        ready();attribute(attr);if((attr&1)!=0)throw new FsException("不能创建只读文件");if(opened.size()>=5)throw new FsException("打开文件表已满（5 项）");
        Entry draft=creation(path,false,attr);int block=fat.allocate();
        byte[] data=disk.buffer2.load(block);Arrays.fill(data,(byte)0);data[0]='#';disk.buffer2.dirty();
        Entry e=new Entry(draft.name(),attr,block,1,draft.parent(),draft.slot());directories.put(e);
        opened.put(path,new OpenFile(e,"w",0));disk.flush();log("创建文件并以写模式打开 "+path);
    }
    private int length(Entry e) throws IOException {
        int count=0;for(int block:fat.chain(e.start()))for(byte b:disk.buffer2.load(block)){if(b=='#')return count;count++;}
        throw new FsException("文件缺少结束符 #："+e.name());
    }
    public synchronized void open_file(String path,String mode) throws IOException {
        ready();if(!List.of("r","w").contains(mode==null?"":mode))throw new FsException("打开模式必须为 r 或 w");
        Entry e=file(path);if(mode.equals("w")&&(e.attribute()&1)!=0)throw new FsException("只读文件不能写入");
        if(opened.containsKey(path)){if(!opened.get(path).mode.equals(mode))throw new FsException("请先关闭文件再切换模式");return;}
        if(opened.size()>=5)throw new FsException("打开文件表已满（5 项）");
        opened.put(path,new OpenFile(e,mode,length(e)));log("打开 "+path+"，模式 "+mode);
    }
    public synchronized Map<String,Object> read_file(String path,int count) throws IOException {
        ready();if(count<0 || count>8192)throw new FsException("读取字节数必须为 0～8192");open_file(path,"r");OpenFile o=opened.get(path);
        int n=Math.min(count,o.length-o.read);byte[] out=new byte[n];List<Integer> chain=fat.chain(o.entry.start());
        for(int i=0;i<n;i++){int pos=o.read+i;out[i]=disk.buffer2.load(chain.get(pos/64))[pos%64];}
        o.read+=n;log("读取 "+path+"："+n+" 字节，读指针 → "+o.read);return content(out);
    }
    public synchronized void write_file(String path,byte[] bytes,int count) throws IOException {
        ready();if(bytes==null||count<0||count>bytes.length)throw new FsException("写入长度超出缓冲内容");
        for(int i=0;i<count;i++)if(bytes[i]=='#')throw new FsException("内容不能包含结束符 #");
        Entry e=file(path);if((e.attribute()&1)!=0)throw new FsException("只读文件不能写入");
        OpenFile existing=opened.get(path);if(existing!=null&&!existing.mode.equals("w"))throw new FsException("请先关闭文件再切换模式");
        if(existing==null&&opened.size()>=5)throw new FsException("打开文件表已满（5 项）");
        int old=existing==null?length(e):existing.length;List<Integer> chain=fat.chain(e.start());
        long total=(long)old+count;int extra=(int)((total+1+63)/64)-chain.size();
        if(extra>fat.freeCount())throw new FsException("磁盘空间不足（已包含结束符所需空间），未写入");
        open_file(path,"w");OpenFile o=opened.get(path);
        for(int i=0;i<extra;i++){int block=fat.allocate();fat.set(chain.getLast(),block);chain.add(block);log("FAT 链接 → "+block);}
        for(int i=0;i<count;i++){int pos=old+i;disk.buffer2.load(chain.get(pos/64))[pos%64]=bytes[i];disk.buffer2.dirty();if(pos%64==63)disk.buffer2.flush();}
        o.length=(int)total;log("追加写入 "+path+"："+count+" 字节，写指针 → "+o.length+"；关闭时写入 #");
    }
    public synchronized void close_file(String path) throws IOException {
        ready();Directory.path(path);OpenFile o=opened.get(path);if(o==null)return;
        if(o.mode.equals("w")){
            List<Integer> chain=fat.chain(o.entry.start());disk.buffer2.load(chain.get(o.length/64))[o.length%64]='#';disk.buffer2.dirty();
            Entry e=o.entry;directories.put(new Entry(e.name(),e.attribute(),e.start(),chain.size(),e.parent(),e.slot()));disk.flush();
        }
        opened.remove(path);log("关闭并保存 "+path);
    }
    public synchronized void close_all() throws IOException {ready();for(String p:List.copyOf(opened.keySet()))close_file(p);disk.flush();}
    public synchronized void delete_file(String path) throws IOException {
        ready();Entry e=file(path);closed(path);fat.release(e.start());directories.remove(e);disk.flush();log("删除文件 "+path);
    }
    private void closed(String path){if(opened.containsKey(path))throw new FsException("文件已打开，请先关闭");}
    public synchronized Map<String,Object> typefile(String path) throws IOException {
        ready();Entry e=file(path);closed(path);int n=length(e);byte[] out=new byte[n];List<Integer> chain=fat.chain(e.start());
        for(int i=0;i<n;i++)out[i]=disk.buffer2.load(chain.get(i/64))[i%64];log("显示文件 "+path);return content(out);
    }
    public synchronized void change(String path,int attr) throws IOException {
        ready();attribute(attr);Entry e=file(path);closed(path);directories.put(new Entry(e.name(),attr,e.start(),e.length(),e.parent(),e.slot()));disk.flush();log("修改属性 "+path+" → "+attr);
    }
    private Map<String,Object> content(byte[] bytes){
        String text;boolean valid=true;try{text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){text="[包含不完整 UTF-8 字符，请查看十六进制字节]";valid=false;}
        return Map.of("text",text,"hex",HexFormat.ofDelimiter(" ").formatHex(bytes),"bytes",bytes.length,"validUtf8",valid);
    }
    private Map<String,Object> entryView(Entry e,String path) throws IOException {
        Map<String,Object> m=new LinkedHashMap<>();m.put("name",e.name());m.put("path",path);m.put("directory",e.directory());m.put("attribute",e.attribute());m.put("start",e.start());m.put("blocks",e.directory()?1:e.length());m.put("chain",fat.chain(e.start()));return m;
    }
    private void walk(int block,String prefix,List<Map<String,Object>> entries,Map<Integer,String> owners) throws IOException {
        for(Entry e:directories.list(block)) {
            String p=prefix+e.name();entries.add(entryView(e,p));for(int b:fat.chain(e.start()))owners.put(b,p);
            if(e.directory())walk(e.start(),p+"/",entries,owners);
        }
    }
    private Map<String,Integer> pointer(List<Integer> chain,int pos){return Map.of("block",chain.get(pos/64),"offset",pos%64);}
    public synchronized Map<String,Object> state() throws IOException {
        Map<String,Object> state=new LinkedHashMap<>();state.put("ready",disk!=null);state.put("error",loadError);state.put("diskPath",path.toString());
        if(disk!=null){
            // 状态遍历不触碰两个教学缓冲区：直接逐块检查，避免监视面板把脏缓冲刷走。
            List<Map<String,Object>> entries=new ArrayList<>();Map<Integer,String> owners=new HashMap<>();
            inspectTree(2,"/",entries,owners,new HashSet<>());
            List<Map<String,Object>> blocks=new ArrayList<>();Set<String> dirs=new HashSet<>();for(var e:entries)if((boolean)e.get("directory"))dirs.add((String)e.get("path"));
            int free=0;for(int i=0;i<128;i++){int value=inspectFat(i);String owner=owners.getOrDefault(i,"");String kind=i<3?"system":i==23||i==49?"bad":value==0?"free":dirs.contains(owner)?"directory":owner.isEmpty()?"error":"file";if(kind.equals("free"))free++;blocks.add(Map.of("id",i,"fat",value,"kind",kind,"owner",owner));}
            List<Map<String,Object>> table=new ArrayList<>();for(var pair:opened.entrySet()){
                OpenFile o=pair.getValue();List<Integer> chain=inspectChain(o.entry.start());
                table.add(Map.of("path",pair.getKey(),"attribute",o.entry.attribute(),"start",o.entry.start(),"mode",o.mode,"length",o.length,"read",pointer(chain,o.read),"write",pointer(chain,o.length)));
            }
            state.put("entries",entries);state.put("blocks",blocks);state.put("free",free);state.put("opened",table);state.put("buffers",List.of(disk.buffer1.snapshot(),disk.buffer2.snapshot()));
        }
        state.put("logs",List.copyOf(logs));return state;
    }
    private int inspectFat(int n)throws IOException{return Byte.toUnsignedInt(disk.inspect(n/64)[n%64]);}
    private List<Integer> inspectChain(int start)throws IOException{
        List<Integer> result=new ArrayList<>();int b=start;while(true){if(b<3||b>=128||b==23||b==49||result.contains(b))throw new FsException("异常 FAT 链");result.add(b);b=inspectFat(b);if(b==255)return result;}
    }
    private void inspectTree(int block,String prefix,List<Map<String,Object>> entries,Map<Integer,String> owners,Set<Integer> visited)throws IOException {
        if(!visited.add(block))throw new FsException("目录循环");byte[] data=disk.inspect(block);
        for(int s=0;s<8;s++){int p=s*8;if(data[p]=='$')continue;
            String name=new String(data,p,3,StandardCharsets.US_ASCII).stripTrailing(),ext=new String(data,p+3,2,StandardCharsets.US_ASCII).stripTrailing();if(!ext.isEmpty())name+="."+ext;
            int attr=Byte.toUnsignedInt(data[p+5]),start=Byte.toUnsignedInt(data[p+6]);String full=prefix+name;List<Integer> chain=inspectChain(start);
            for(int b:chain)if(owners.put(b,full)!=null)throw new FsException("共享盘块："+b);
            entries.add(Map.of("name",name,"path",full,"directory",attr==8,"attribute",attr,"start",start,"blocks",attr==8?1:Byte.toUnsignedInt(data[p+7]),"chain",chain));
            if(attr==8)inspectTree(start,full+"/",entries,owners,visited);
        }
    }
    public synchronized Map<String,Object> block(int n)throws IOException{ready();return Map.of("id",n,"hex",HexFormat.ofDelimiter(" ").formatHex(disk.inspect(n)));}
    @Override public synchronized void close() throws IOException {if(disk!=null){close_all();disk.close();disk=null;}}
}
