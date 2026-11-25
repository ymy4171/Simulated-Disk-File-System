'use strict';
const $=id=>document.getElementById(id);
let state=null,cwd='/',selected='',busy=false;
const attrs={2:'系统读写',3:'系统只读',4:'普通读写',5:'普通只读',8:'目录'};
function el(tag,text,cls){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;}
function notice(text,error=false){$('notice').textContent=text;$('notice').className=error?'error-notice':'';}
let highlightTimer;
function showChanges(before,after){
 clearTimeout(highlightTimer);
 const changed=new Set((after.blocks||[]).filter(b=>JSON.stringify(b)!==JSON.stringify(before?.blocks?.find(x=>x.id===b.id))).map(b=>b.id));
 for(const b of after.buffers||[]){const old=before?.buffers?.find(x=>x.name===b.name);if(b.dirty&&b.hex!==old?.hex&&b.block>=0)changed.add(b.block);}
 [...$('disk').children].forEach((b,i)=>{if(changed.has(i))b.classList.add('changed');});
 if(JSON.stringify(before?.entries)!==JSON.stringify(after.entries))$('chain').classList.add('changed');
 if(JSON.stringify(before?.opened)!==JSON.stringify(after.opened)){ $('pointerFocus').classList.add('changed');$('opened').classList.add('changed'); }
 highlightTimer=setTimeout(()=>document.querySelectorAll('.changed').forEach(n=>n.classList.remove('changed')),2800);
}
async function request(url,options){const response=await fetch(url,options);const data=await response.json();if(!response.ok)throw Error(data.error||'请求失败');return data;}
function parent(path){const i=path.lastIndexOf('/');return i<=0?'/':path.slice(0,i);}
function navigate(path){cwd=path;selected=path==='/'?'':path;$('path').value=path;render();}
function render(){
 if(!state)return;$('workspace').hidden=!state.ready;$('welcome').hidden=state.ready;$('free').textContent=state.ready?state.free:'—';$('openCount').textContent=state.ready?state.opened.length:'—';
 if(!state.ready){$('welcomeText').textContent=state.error?'现有磁盘加载失败：'+state.error+'。可先备份磁盘文件，再选择重新初始化。':'首次运行需要创建磁盘镜像。只会操作本项目的模拟磁盘文件。';return;}
 if(cwd!=='/'&&!state.entries.some(e=>e.path===cwd&&e.directory))cwd='/';
 $('cwd').textContent=cwd;$('headerPath').textContent=selected||cwd;$('tree').replaceChildren();
 const root=el('button','▾  /',cwd==='/'&&!selected?'active':'');root.onclick=()=>{selected='';navigate('/');};$('tree').append(root);
 for(const e of state.entries){const b=el('button',(e.directory?'▾  ':'▤  ')+e.name,e.path===selected?'active':'');b.style.paddingLeft=(12+(e.path.split('/').length-2)*16)+'px';b.title=e.path;b.onclick=()=>{selected=e.path;if(e.directory)navigate(e.path);else{cwd=parent(e.path);$('path').value=e.path;$('attribute').value=e.attribute;render();}};$('tree').append(b);}
 $('files').replaceChildren();const files=state.entries.filter(e=>parent(e.path)===cwd);
 for(const e of files){const row=el('tr',undefined,e.path===selected?'selected':'');for(const text of [(e.directory?'▸ ':'▤ ')+e.name,attrs[e.attribute],e.start,e.blocks])row.append(el('td',text));row.onclick=()=>{selected=e.path;$('path').value=e.path;if(!e.directory)$('attribute').value=e.attribute;render();};row.ondblclick=()=>{if(e.directory)navigate(e.path);};$('files').append(row);}
 if(!files.length){const row=el('tr');const td=el('td','目录为空 · 使用上方工具栏创建文件或目录','empty');td.colSpan=4;row.append(td);$('files').append(row);}
 const chosen=state.entries.find(e=>e.path===selected);renderChain(chosen);$('disk').replaceChildren();
 for(const b of state.blocks){const button=el('button',String(b.id).padStart(2,'0'),b.kind+(chosen?.chain.includes(b.id)?' highlight':''));button.title=`块 ${b.id} | FAT ${b.fat} | ${b.owner||b.kind}`;button.setAttribute('aria-label',button.title);button.onclick=()=>inspect(b);$('disk').append(button);}
 $('opened').replaceChildren();for(const o of state.opened){const row=el('tr');for(const text of [o.path,attrs[o.attribute],o.start,o.mode==='w'?'写 / APPEND':'读 / READ',o.length,`${o.read.block} : ${o.read.offset}`,`${o.write.block} : ${o.write.offset}`])row.append(el('td',text));$('opened').append(row);}
 if(!state.opened.length){const row=el('tr');const td=el('td','暂无打开文件','empty');td.colSpan=7;row.append(td);$('opened').append(row);}
 $('buffers').replaceChildren();for(const b of state.buffers){const box=el('div',undefined,'buffer'),head=el('div',undefined,'buffer-head');head.append(el('span',b.name),el('span',`块 ${b.block<0?'—':b.block} · ${b.dirty?'DIRTY / 待写回':'CLEAN / 已同步'}`,b.dirty?'dirty':'clean'));box.append(head,el('pre',b.hex));$('buffers').append(box);}
 $('logs').textContent=state.logs.join('\n');$('logs').scrollTop=$('logs').scrollHeight;
}
function renderChain(chosen){
 const host=$('chain');host.replaceChildren();
 const open=state.opened.find(o=>o.path===chosen?.path);
 if(!chosen){host.textContent='选择文件查看分配链';$('pointerFocus').textContent='未选择文件';$('pointerHint').textContent='255 是链尾标记，不是盘块号';return;}
 for(const [index,block] of chosen.chain.entries()){
  if(index)host.append(el('span','→','chain-arrow'));
  const node=el('div',undefined,'chain-node');
  const reserved=open?.mode==='w'&&open.length%64===0&&block===open.write.block;
  node.append(el('span',block,'chain-block'),el('small',chosen.directory?'目录块':reserved?'预留结束符':'数据块'));host.append(node);
 }
 host.append(el('span','→','chain-arrow'));const end=el('div',undefined,'chain-node chain-end');end.append(el('span','255','chain-block'),el('small','链尾标记'));host.append(end);
 $('pointerFocus').textContent=open?`${open.mode==='w'?'写':'读'}指针 ${open.mode==='w'?open.write.block:open.read.block}:${open.mode==='w'?open.write.offset:open.read.offset}`:chosen.directory?'目录已选中':'文件已关闭';
 $('pointerHint').textContent=open?`${open.length} 字节 · ${open.mode==='w'?'关闭时写入结束符并更新目录':'从当前读指针继续读取'}`:'255 表示链尾，不对应任何盘块';
}
async function inspect(b){try{const data=await request('/api/blocks/'+b.id);$('blockTitle').textContent=`块 ${b.id} · FAT = ${b.fat} · ${b.owner||b.kind}`;$('blockHex').textContent=data.hex;$('blockTitle').parentElement.open=true;}catch(e){notice(e.message,true);}}
async function refresh(){if(busy)return;try{state=await request('/api/state');render();notice(state.ready?'文件系统已连接 · 所有操作仅作用于模拟磁盘':'请先初始化磁盘',!!state.error);}catch(e){notice('连接失败：'+e.message,true);}}
async function operate(operation,source){
 if(busy)return;
 if(['delete_file','rd'].includes(operation)&&!confirm('确认删除 '+$('path').value+'？'))return;
 if(operation==='initialize'&&!confirm('确认初始化模拟磁盘？已有模拟文件将被清空。此操作不可撤销。'))return;
 const command={operation,path:$('path').value,mode:$('mode').value,attribute:Number($('attribute').value),content:$('content').value,confirmed:operation==='initialize'};
 if(operation==='read_file')command.count=Number($('readCount').value);
 if(operation==='write_file'&&$('writeCount').value!=='')command.count=Number($('writeCount').value);
 if(command.count!==undefined&&!Number.isInteger(command.count)){notice('字节数必须是整数',true);return;}
 const before=state,caption=source?.textContent;
 busy=true;document.body.setAttribute('aria-busy','true');document.querySelectorAll('button,input,select,textarea').forEach(b=>b.disabled=true);
 if(source){source.classList.add('is-running');source.textContent='执行中…';}
 notice('正在执行 · '+(caption||operation));$('notice').className='pending-notice';
 try{
  const data=await request('/api/operations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(command)});state=data.state;
  if(operation==='initialize'){cwd='/';selected='';}
  if(operation==='create_file'||operation==='md'){selected=command.path;cwd=parent(command.path);}
  if(operation==='delete_file'||operation==='rd')selected='';
  else if(state.entries?.some(e=>e.path===command.path)){selected=command.path;cwd=parent(command.path);}
  const result=data.result;
  if(result&&typeof result==='object'&&!Array.isArray(result)&&'hex'in result){$('output').textContent=result.text||'（空内容）';$('outputHex').textContent=result.hex||'（0 字节）';$('resultMeta').textContent=result.bytes+' bytes';}
  else{$('output').textContent=Array.isArray(result)?JSON.stringify(result,null,2):`${operation} · ${command.path}\n${result}`;$('outputHex').textContent='—';$('resultMeta').textContent='';}
  render();notice(operation==='write_file'?'写入完成 · 请关闭文件以保存结束符及目录长度':operation==='close_all'?'全部文件已关闭并保存，现在可以停止服务。':'操作完成 · '+operation);
  showChanges(before,state);$('notice').className='success-notice';
 }catch(e){notice('操作失败 · '+e.message,true);}finally{busy=false;document.body.removeAttribute('aria-busy');document.querySelectorAll('button,input,select,textarea').forEach(b=>b.disabled=false);if(source){source.textContent=caption;source.classList.remove('is-running');}}
}
document.querySelectorAll('[data-op]').forEach(b=>b.onclick=()=>operate(b.dataset.op,b));
$('refresh').onclick=refresh;$('save').onclick=()=>operate('close_all',$('save'));$('initialize').onclick=()=>operate('initialize',$('initialize'));$('reset').onclick=()=>operate('initialize',$('reset'));$('up').onclick=()=>navigate(parent(cwd));
$('content').addEventListener('input',()=>{$('byteCount').textContent=new TextEncoder().encode($('content').value).length+' bytes';});
window.addEventListener('beforeunload',e=>{if(state?.opened.some(o=>o.mode==='w')){e.preventDefault();e.returnValue='';}});
refresh();
