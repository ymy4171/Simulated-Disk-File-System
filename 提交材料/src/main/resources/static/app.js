'use strict';
const $=id=>document.getElementById(id);
let state=null,cwd='/',selected='',busy=false;
const attrs={2:'系统读写',3:'系统只读',4:'普通读写',5:'普通只读',8:'目录'};
function el(tag,text,cls){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;}
function notice(text,error=false){$('notice').textContent=text;$('notice').className=error?'error-notice':'';}
async function request(url,options){const response=await fetch(url,options);const data=await response.json();if(!response.ok)throw Error(data.error||'请求失败');return data;}
function parent(path){const i=path.lastIndexOf('/');return i<=0?'/':path.slice(0,i);}
function navigate(path){cwd=path;$('path').value=path;render();}
function render(){
 if(!state)return;$('workspace').hidden=!state.ready;$('welcome').hidden=state.ready;$('free').textContent=state.ready?state.free:'—';$('openCount').textContent=state.ready?state.opened.length:'—';
 if(!state.ready){$('welcomeText').textContent=state.error?'现有磁盘加载失败：'+state.error+'。可先备份磁盘文件，再选择重新初始化。':'首次运行需要创建磁盘镜像。只会操作本项目的模拟磁盘文件。';return;}
 if(cwd!=='/'&&!state.entries.some(e=>e.path===cwd&&e.directory))cwd='/';
 $('cwd').textContent=cwd;$('tree').replaceChildren();
 for(const path of ['/',...state.entries.filter(e=>e.directory).map(e=>e.path)]){const b=el('button',path==='/'?'▦ 根目录':('  '.repeat(path.split('/').length-2)+'▸ '+path.split('/').pop()),path===cwd?'active':'');b.title=path;b.onclick=()=>navigate(path);$('tree').append(b);}
 $('files').replaceChildren();const files=state.entries.filter(e=>parent(e.path)===cwd);
 for(const e of files){const row=el('tr',undefined,e.path===selected?'selected':'');for(const text of [(e.directory?'▸ ':'▤ ')+e.name,attrs[e.attribute],e.start,e.blocks])row.append(el('td',text));row.onclick=()=>{selected=e.path;$('path').value=e.path;if(!e.directory)$('attribute').value=e.attribute;render();};row.ondblclick=()=>{if(e.directory)navigate(e.path);};$('files').append(row);}
 if(!files.length){const row=el('tr');const td=el('td','目录为空 · 在右侧创建第一个文件','empty');td.colSpan=4;row.append(td);$('files').append(row);}
 const chosen=state.entries.find(e=>e.path===selected);$('chain').textContent=chosen?chosen.chain.join(' → ')+' → END (255)':'选择文件查看分配链';$('disk').replaceChildren();
 for(const b of state.blocks){const button=el('button',String(b.id).padStart(2,'0'),b.kind+(chosen?.chain.includes(b.id)?' highlight':''));button.title=`块 ${b.id} | FAT ${b.fat} | ${b.owner||b.kind}`;button.setAttribute('aria-label',button.title);button.onclick=()=>inspect(b);$('disk').append(button);}
 $('opened').replaceChildren();for(const o of state.opened){const row=el('tr');for(const text of [o.path,attrs[o.attribute],o.start,o.mode==='w'?'写 / APPEND':'读 / READ',o.length,`${o.read.block} : ${o.read.offset}`,`${o.write.block} : ${o.write.offset}`])row.append(el('td',text));$('opened').append(row);}
 if(!state.opened.length){const row=el('tr');const td=el('td','暂无打开文件','empty');td.colSpan=7;row.append(td);$('opened').append(row);}
 $('buffers').replaceChildren();for(const b of state.buffers){const box=el('div',undefined,'buffer'),head=el('div',undefined,'buffer-head');head.append(el('span',b.name),el('span',`块 ${b.block<0?'—':b.block} · ${b.dirty?'DIRTY / 待写回':'CLEAN / 已同步'}`,b.dirty?'dirty':'clean'));box.append(head,el('pre',b.hex));$('buffers').append(box);}
 $('logs').textContent=state.logs.join('\n');$('logs').scrollTop=$('logs').scrollHeight;
}
async function inspect(b){try{const data=await request('/api/blocks/'+b.id);$('blockTitle').textContent=`块 ${b.id} · FAT = ${b.fat} · ${b.owner||b.kind}`;$('blockHex').textContent=data.hex;$('blockTitle').parentElement.open=true;}catch(e){notice(e.message,true);}}
async function refresh(){try{state=await request('/api/state');render();notice(state.ready?'文件系统已连接 · 所有操作仅作用于模拟磁盘':'请先初始化磁盘',!!state.error);}catch(e){notice('连接失败：'+e.message,true);}}
async function operate(operation){
 if(busy)return;
 if(['delete_file','rd'].includes(operation)&&!confirm('确认删除 '+$('path').value+'？'))return;
 if(operation==='initialize'&&!confirm('确认初始化模拟磁盘？已有模拟文件将被清空。此操作不可撤销。'))return;
 const command={operation,path:$('path').value,mode:$('mode').value,attribute:Number($('attribute').value),content:$('content').value,confirmed:operation==='initialize'};
 if(operation==='read_file')command.count=Number($('readCount').value);
 if(operation==='write_file'&&$('writeCount').value!=='')command.count=Number($('writeCount').value);
 if(command.count!==undefined&&!Number.isInteger(command.count)){notice('字节数必须是整数',true);return;}
 busy=true;document.querySelectorAll('button').forEach(b=>b.disabled=true);
 try{
  const data=await request('/api/operations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(command)});state=data.state;
  if(operation==='initialize'){cwd='/';selected='';}
  if(operation==='create_file'||operation==='md')selected=command.path;
  const result=data.result;
  if(result&&typeof result==='object'&&!Array.isArray(result)&&'hex'in result){$('output').textContent=result.text||'（空内容）';$('outputHex').textContent=result.hex||'（0 字节）';$('resultMeta').textContent=result.bytes+' bytes';}
  else{$('output').textContent=Array.isArray(result)?JSON.stringify(result,null,2):`${operation} · ${command.path}\n${result}`;$('outputHex').textContent='—';$('resultMeta').textContent='';}
  render();notice(operation==='write_file'?'写入完成 · 请关闭文件以保存结束符及目录长度':operation==='close_all'?'全部文件已关闭并保存，现在可以停止服务。':'操作完成 · '+operation);
 }catch(e){notice(e.message,true);}finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);}
}
document.querySelectorAll('[data-op]').forEach(b=>b.onclick=()=>operate(b.dataset.op));
$('refresh').onclick=refresh;$('save').onclick=()=>operate('close_all');$('initialize').onclick=()=>operate('initialize');$('reset').onclick=()=>operate('initialize');$('up').onclick=()=>navigate(parent(cwd));
$('content').addEventListener('input',()=>{$('byteCount').textContent=new TextEncoder().encode($('content').value).length+' bytes';});
window.addEventListener('beforeunload',e=>{if(state?.opened.some(o=>o.mode==='w')){e.preventDefault();e.returnValue='';}});
refresh();
