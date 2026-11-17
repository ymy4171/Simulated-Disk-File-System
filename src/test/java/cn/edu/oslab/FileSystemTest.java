package cn.edu.oslab;

import cn.edu.oslab.core.*;
import cn.edu.oslab.core.FileSystem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FileSystemTest {
    @TempDir Path temp;
    FileSystem fs; Path image;
    @BeforeEach void setup()throws Exception{image=temp.resolve("disk.img");fs=new FileSystem(image);fs.initialize(true);}
    @AfterEach void finish()throws Exception{fs.close();}
    void write(String path,String s)throws Exception{byte[] b=s.getBytes(StandardCharsets.UTF_8);fs.write_file(path,b,b.length);}
    byte[] physical()throws Exception{fs.close();byte[] bytes=Files.readAllBytes(image);fs=new FileSystem(image);return bytes;}
    @Test void layout()throws Exception{
        assertEquals(8192,Files.size(image));fs.close();byte[] b=Files.readAllBytes(image);fs=new FileSystem(image);
        assertEquals(255,b[0]&255);assertEquals(255,b[2]&255);assertEquals(254,b[23]&255);assertEquals(254,b[49]&255);
        assertEquals('$',b[128]);assertEquals(123,fs.state().get("free"));assertThrows(FsException.class,()->fs.block(128));
    }
    @ParameterizedTest @ValueSource(ints={0,1,63,64,127,128,250})
    void boundaryAndRestart(int n)throws Exception{
        fs.create_file("/a.tx",4);write("/a.tx","x".repeat(n));fs.close_file("/a.tx");
        assertEquals((n+64)/64,fs.dir("/").getFirst().get("blocks"));assertEquals("x".repeat(n),fs.typefile("/a.tx").get("text"));
        fs.close();fs=new FileSystem(image);assertEquals(n,fs.typefile("/a.tx").get("bytes"));
        write("/a.tx","yz");fs.close_file("/a.tx");assertEquals("x".repeat(n)+"yz",fs.typefile("/a.tx").get("text"));
        fs.delete_file("/a.tx");assertEquals(123,fs.state().get("free"));
    }
    @Test void directoriesAndValidation()throws Exception{
        fs.md("/a");fs.md("/a/b");fs.create_file("/a/b/x",4);fs.close_all();
        assertThrows(FsException.class,()->fs.rd("/a"));assertThrows(FsException.class,()->fs.rd("/"));
        assertThrows(FsException.class,()->fs.md("/a"));assertThrows(FsException.class,()->fs.md("/bad/x"));
        assertThrows(FsException.class,()->fs.md("/a.b"));assertThrows(FsException.class,()->fs.create_file("/long.tx",4));
        assertThrows(FsException.class,()->fs.create_file("/中.tx",4));assertThrows(FsException.class,()->fs.create_file("a.tx",4));
        fs.delete_file("/a/b/x");fs.rd("/a/b");fs.rd("/a");assertEquals(123,fs.state().get("free"));
        for(int i=0;i<8;i++)fs.md("/d"+i);assertThrows(FsException.class,()->fs.md("/d8"));
    }
    @Test void permissionsAndOpenTable()throws Exception{
        assertThrows(FsException.class,()->fs.create_file("/ro",5));
        for(int i=0;i<5;i++)fs.create_file("/f"+i,4);
        assertThrows(FsException.class,()->fs.create_file("/f5",4));
        assertThrows(FsException.class,()->fs.read_file("/f0",1));assertThrows(FsException.class,()->fs.delete_file("/f0"));
        assertThrows(FsException.class,()->fs.typefile("/f0"));assertThrows(FsException.class,()->fs.change("/f0",5));
        fs.close_all();fs.change("/f0",5);assertThrows(FsException.class,()->write("/f0","x"));
        fs.open_file("/f1","r");assertThrows(FsException.class,()->write("/f1","x"));fs.close_all();
        fs.change("/f0",2);write("/f0","system");fs.close_all();assertEquals("system",fs.typefile("/f0").get("text"));
    }
    @Test void readPointersAndUtf8()throws Exception{
        fs.create_file("/a",4);write("/a","ab中");fs.close_all();
        assertEquals("ab",fs.read_file("/a",2).get("text"));assertEquals(false,fs.read_file("/a",1).get("validUtf8"));
        assertEquals(2,fs.read_file("/a",64).get("bytes"));assertEquals(0,fs.read_file("/a",64).get("bytes"));
        fs.close_all();assertEquals("ab中",fs.typefile("/a").get("text"));
    }
    @Test void reclaimedDataBlockBecomesDirectoryThenFile()throws Exception{
        fs.create_file("/a",4);write("/a","old data");fs.close_all();fs.delete_file("/a");
        fs.md("/dir");assertTrue(fs.dir("/dir").isEmpty());fs.rd("/dir");
        fs.create_file("/new",4);write("/new","new");fs.close_all();fs.close();fs=new FileSystem(image);
        assertEquals("new",fs.typefile("/new").get("text"));
    }
    @Test void alternatingWritersAndSnapshotDoesNotFlush()throws Exception{
        fs.create_file("/a",4);fs.create_file("/b",4);write("/a","A".repeat(65));
        var buffers=(List<Map<String,Object>>)fs.state().get("buffers");assertEquals(true,buffers.get(1).get("dirty"));
        write("/b","B".repeat(64));write("/a","tail");fs.close_file("/b");fs.close_file("/a");
        assertEquals("A".repeat(65)+"tail",fs.typefile("/a").get("text"));assertEquals("B".repeat(64),fs.typefile("/b").get("text"));
    }
    @Test void noSpaceAndInvalidWritesDoNotMutate()throws Exception{
        fs.create_file("/a",4);write("/a","X".repeat(123*64-1));fs.close_all();byte[] before=physical();
        assertThrows(FsException.class,()->write("/a","Y"));assertArrayEquals(before,physical());
        assertThrows(FsException.class,()->write("/a","#"));assertArrayEquals(before,physical());
        assertEquals(0,fs.state().get("free"));fs.delete_file("/a");assertEquals(123,fs.state().get("free"));
    }
    @Test void concurrentOperationsSerialized()throws Exception{
        fs.create_file("/a",4);
        try(var pool=Executors.newFixedThreadPool(4)){
            List<Future<?>> tasks=new ArrayList<>();for(int i=0;i<30;i++)tasks.add(pool.submit(()->{try{write("/a","abc");fs.state();}catch(Exception e){throw new RuntimeException(e);}}));
            for(var task:tasks)task.get();
        }
        fs.close_all();assertEquals(90,fs.typefile("/a").get("bytes"));
    }
    @ParameterizedTest @ValueSource(strings={"cycle","shared","illegal","size","terminator","bad","orphan"})
    void corruptionRejectedWithoutOverwrite(String kind)throws Exception{
        fs.create_file("/a",4);fs.close_all();fs.create_file("/b",4);fs.close_all();fs.close();
        try(var f=new RandomAccessFile(image.toFile(),"rw")){
            switch(kind){
                case "cycle" -> {f.seek(3);f.write(3);}
                case "shared" -> {f.seek(128+8+6);f.write(3);}
                case "illegal" -> {f.seek(3);f.write(200);}
                case "size" -> f.setLength(8000);
                case "terminator" -> {f.seek(3*64);f.write(new byte[64]);}
                case "bad" -> {f.seek(23);f.write(0);}
                case "orphan" -> {f.seek(20);f.write(255);}
            }
        }
        byte[] before=physical();fs=new FileSystem(image);assertEquals(false,fs.state().get("ready"));assertNotNull(fs.state().get("error"));assertArrayEquals(before,physical());
    }
}

