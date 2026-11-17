package cn.edu.oslab;

import cn.edu.oslab.core.FileSystem;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** 指导书要求的主函数测试；只创建独立临时磁盘。 */
public final class SelfTest {
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("fat-lab-selftest-");Path disk=folder.resolve("disk.img");
        try {
            try(FileSystem fs=new FileSystem(disk)){
                fs.initialize(true);fs.md("/doc");fs.create_file("/doc/a.tx",4);
                byte[] data="A".repeat(64).getBytes(StandardCharsets.UTF_8);fs.write_file("/doc/a.tx",data,data.length);fs.close_file("/doc/a.tx");
                if(!fs.typefile("/doc/a.tx").get("text").equals("A".repeat(64)))throw new AssertionError("跨块写入失败");
            }
            try(FileSystem fs=new FileSystem(disk)){
                if(!fs.typefile("/doc/a.tx").get("bytes").equals(64))throw new AssertionError("重启持久化失败");
                fs.delete_file("/doc/a.tx");fs.rd("/doc");if(!fs.state().get("free").equals(123))throw new AssertionError("回收失败");
            }
            System.out.println("SELF-TEST PASS: initialize / directory / cross-block write / restart / reclaim");
        } finally {Files.deleteIfExists(disk);Files.deleteIfExists(folder);}
    }
}
