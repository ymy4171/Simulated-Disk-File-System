package cn.edu.oslab;

import cn.edu.oslab.core.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final FileSystem fs;
    public ApiController(FileSystem fs){this.fs=fs;}
    public record Command(String operation,String path,String mode,Integer attribute,String content,Integer count,Boolean confirmed){}
    @GetMapping("/state") public Map<String,Object> state()throws IOException{return fs.state();}
    @GetMapping("/blocks/{id}") public Map<String,Object> block(@PathVariable int id)throws IOException{return fs.block(id);}
    @PostMapping("/operations") public Map<String,Object> execute(@RequestBody Command c)throws IOException{
        synchronized(fs){Object result="操作完成";if(c.operation()==null)throw new FsException("缺少操作名称");
            switch(c.operation()){
                case "initialize" -> fs.initialize(Boolean.TRUE.equals(c.confirmed()));
                case "md" -> fs.md(c.path());
                case "dir" -> result=fs.dir(c.path());
                case "rd" -> fs.rd(c.path());
                case "create_file" -> fs.create_file(c.path(),c.attribute()==null?4:c.attribute());
                case "open_file" -> fs.open_file(c.path(),c.mode());
                case "read_file" -> result=fs.read_file(c.path(),c.count()==null?64:c.count());
                case "write_file" -> {byte[] b=Objects.requireNonNullElse(c.content(),"").getBytes(StandardCharsets.UTF_8);fs.write_file(c.path(),b,c.count()==null?b.length:c.count());}
                case "close_file" -> fs.close_file(c.path());
                case "delete_file" -> fs.delete_file(c.path());
                case "typefile" -> result=fs.typefile(c.path());
                case "change" -> {if(c.attribute()==null)throw new FsException("缺少属性");fs.change(c.path(),c.attribute());}
                case "close_all" -> fs.close_all();
                default -> throw new FsException("未知操作");
            }
            return Map.of("ok",true,"result",result,"state",fs.state());
        }
    }
    @ExceptionHandler(FsException.class) public ResponseEntity<?> invalid(FsException e){return ResponseEntity.badRequest().body(Map.of("ok",false,"error",e.getMessage()));}
    @ExceptionHandler(IOException.class) public ResponseEntity<?> io(IOException e){return ResponseEntity.internalServerError().body(Map.of("ok",false,"error","磁盘 I/O 失败："+e.getMessage()));}
}
