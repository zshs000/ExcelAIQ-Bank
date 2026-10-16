(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const commitByRef = new Map(HISTORY.commits.map(c => [c.hash,c]));
  const repo = HISTORY.repo;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const moment = c => c.date.slice(0,19).replace('T',' ')+' UTC+8';
  function docUrl(path) { return repo+'/blob/'+HISTORY.head+'/'+path.split('/').map(encodeURIComponent).join('/'); }

  // Every original quote opens its exact Git record. No network requests are made.
  const dialog = $('commit-dialog');
  let resumeAfterDialog = false;
  function openCommit(ref) {
    const c = commitByRef.get(ref);
    if (!c) return;
    resumeAfterDialog = playing;
    if (playing) pauseFilm();
    $('dialog-meta').textContent = c.hash+' / '+moment(c)+(c.merge?' / 合并提交':'');
    $('dialog-title').textContent = c.subject;
    const rest = c.body.split('\n').slice(1).join('\n').trim();
    $('dialog-body').textContent = rest;
    $('dialog-body').hidden = !rest;
    $('dialog-link').href = repo+'/commit/'+c.sha;
    dialog.showModal();
  }
  $('close-dialog').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}});
  dialog.addEventListener('close',()=>{if(resumeAfterDialog) {resumeAfterDialog=false;playFilm();}});
  document.addEventListener('click',e=>{
    const commit = e.target.closest('[data-commit]');
    if(commit) openCommit(commit.dataset.commit);
    const doc = e.target.closest('[data-doc]');
    if(doc) window.open(docUrl(doc.dataset.doc),'_blank','noopener,noreferrer');
  });

  // Actual month counts, including zero-commit months.
  const months = Array.from({length:13},(_,i)=>{
    const y=2025+Math.floor((9+i)/12),m=(9+i)%12+1,key=y+'-'+String(m).padStart(2,'0');
    return {key,m,n:HISTORY.monthly[key]||0};
  });
  const maxMonth = Math.max(...months.map(m=>m.n));
  $('month-chart').innerHTML=months.map(m=>{
    const count = Math.min(m.n,20),height=m.n?Math.max(12,m.n/maxMonth*64):0;
    return '<div class="month-cell '+(m.n===maxMonth?'peak':'')+'" role="img" aria-label="'+m.key+'，'+m.n+' 条提交"><span class="month-number">'+m.n+'</span>'+Array.from({length:count},(_,i)=>'<i class="month-stick" style="--h:'+Math.round(height*(.6+(i%5)*.1))+'px"></i>').join('')+'<span class="month-label">'+(m.m===10?m.key.slice(2).replace('-','.'):(m.m===1?'26.01':String(m.m).padStart(2,'0')))+'</span></div>';
  }).join('');
  // Sticks are a visual encoding of monthly volume; exact totals remain printed.
  const monthNote = document.querySelector('.month-note span');
  monthNote.textContent='每月提交数量，最多显示 20 根刻线；空白也属于这一年。';
  $('commit-count').textContent=HISTORY.count;
  $('repo-link').href=repo;
  $('source-scope').textContent='核查于 2026.10.16。以主分支 '+HISTORY.branch+'（'+HISTORY.sourceRef+'）、快照 '+HISTORY.head.slice(0,7)+' 为主线，共 '+HISTORY.count+' 条提交（含 '+(HISTORY.count-HISTORY.nonMergeCount)+' 条合并）。本地全部引用共 '+HISTORY.allRefCount+' 条；旁支独有记录未计入主线。最新主线提交为 2026.09.29，包含 PR #19 对验证码限流修复的合并。未提交文件不计入历史。';

  let selectedNode=0;
  const map=$('journey-map');
  map.innerHTML='<svg aria-hidden="true"><path id="map-path" fill="none" stroke="#b8c3b1" stroke-width="1.3" stroke-dasharray="3 5"/></svg>'+HISTORY.milestones.map((m,i)=>'<button type="button" class="map-node'+(i===0?' selected':'')+'" data-node="'+i+'" aria-pressed="'+(i===0)+'" aria-label="'+esc(m.date+' '+m.title)+'"><span class="map-dot">'+String(i+1).padStart(2,'0')+'</span><time datetime="'+m.date+'">'+m.date.slice(2).replaceAll('-','.')+'</time><span class="map-title">'+esc(m.title)+'</span></button>').join('');
  function layoutMap(){
    const cols=getComputedStyle(map).gridTemplateColumns.split(' ').length;
    const nodes=[...map.querySelectorAll('.map-node')];
    nodes.forEach((n,i)=>{const r=Math.floor(i/cols),c=r%2?cols-1-i%cols:i%cols;n.style.gridRow=r+1;n.style.gridColumn=c+1;});
    const mr=map.getBoundingClientRect();let path='';
    nodes.forEach((n,i)=>{const r=n.querySelector('.map-dot').getBoundingClientRect(),x=r.left-mr.left+r.width/2,y=r.top-mr.top+r.height/2;
      if(i===0){path='M'+x+' '+y;return;}
      const prev=nodes[i-1].querySelector('.map-dot').getBoundingClientRect(),px=prev.left-mr.left+prev.width/2,py=prev.top-mr.top+prev.height/2;
      if(Math.abs(py-y)<5)path+=' L'+x+' '+y;
      else{const side=x>mr.width/2?Math.min(mr.width-2,x+mr.width/cols*.45):Math.max(2,x-mr.width/cols*.45);path+=' C'+side+' '+py+' '+side+' '+y+' '+x+' '+y;}
    });
    $('map-path').setAttribute('d',path);
  }
  function selectNode(i, scroll=false){
    selectedNode=i;const m=HISTORY.milestones[i],c=commitByRef.get(m.ref);
    map.querySelectorAll('.map-node').forEach((n,j)=>{n.classList.toggle('selected',j===i);n.setAttribute('aria-pressed',String(j===i));});
    $('node-detail').innerHTML='<div class="node-index"><span>NODE '+String(i+1).padStart(2,'0')+' / 24</span><time>'+m.date.replaceAll('-','.')+'</time></div><h3>'+esc(m.title)+'</h3><p>'+esc(m.detail)+'</p><p class="node-subject">'+esc(c.subject)+'</p><span class="node-tag">'+esc(m.tag)+'</span><div class="node-actions"><button class="text-button" data-commit="'+m.ref+'">'+m.ref+' <span>原始提交 ↗</span></button><button class="text-button" data-scene="'+m.chapter+'">进入这一幕 <span>→</span></button></div>';
    if(scroll&&innerWidth<=720)$('node-detail').scrollIntoView({behavior:reduced?'instant':'smooth',block:'nearest'});
  }
  map.addEventListener('click',e=>{const n=e.target.closest('[data-node]');if(n)selectNode(Number(n.dataset.node),true);});
  selectNode(0);
  new ResizeObserver(layoutMap).observe(map);

  // The eight scenes share one real diagram, redrawn for the available width.
  let chapter=0,era='after',playing=false,playStarted=0,raf=0;
  const DURATION=23000;
  $('chapter-tabs').innerHTML=CHAPTERS.map((c,i)=>'<button type="button" data-chapter="'+i+'" aria-label="第 '+(i+1)+' 幕：'+esc(c.name)+'" aria-pressed="'+(i===0)+'" class="'+(i===0?'active':'')+'">'+String(i+1).padStart(2,'0')+'</button>').join('');
  function wrapText(str,max){
    const lines=[];let line='',size=0;
    for(const char of str){const weight=/[\x00-\x7f]/.test(char)?.58:1;if(size+weight>max){lines.push(line);line='';size=0;}line+=char;size+=weight;}
    if(line)lines.push(line);return lines;
  }
  function drawDiagram(){
    const svg=$('architecture');if(!svg)return;
    const data=CHAPTERS[chapter][era],width=Math.max(240,svg.parentElement.clientWidth),cols=width>=520?3:2;
    const gap=26,pad=4,w=(width-pad*2-(cols-1)*gap)/cols,h=79,rowGap=43,rows=Math.ceil(data.nodes.length/cols),height=rows*(h+rowGap)-rowGap+12;
    const pos=data.nodes.map((n,i)=>{const r=Math.floor(i/cols),c=r%2?cols-1-i%cols:i%cols;return{x:pad+c*(w+gap),y:6+r*(h+rowGap)};});
    const edges=data.edges||data.nodes.slice(1).map((_,i)=>[i,i+1]);
    let html='<title>'+esc(data.caption)+'</title><desc>'+esc(data.nodes.map(n=>n.join('：')).join('；'))+'</desc><defs><marker id="diagram-arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0 0L6 3L0 6" fill="none" stroke="#a5b99b" stroke-width="1"/></marker></defs>';
    for(const [a,b]of edges){const p=pos[a],q=pos[b];let d;
      if(p.y===q.y){const forward=q.x>p.x,x1=forward?p.x+w:p.x,x2=forward?q.x:q.x+w;d='M'+x1+' '+(p.y+h/2)+' L'+x2+' '+(q.y+h/2);}
      else if(p.x===q.x){d='M'+(p.x+w/2)+' '+(p.y+h)+' L'+(q.x+w/2)+' '+q.y;}
      else{const x1=p.x+w/2,y1=p.y+h,x2=q.x+w/2,y2=q.y;d='M'+x1+' '+y1+' C'+x1+' '+(y1+rowGap/2)+' '+x2+' '+(y2-rowGap/2)+' '+x2+' '+y2;}
      html+='<path d="'+d+'" fill="none" stroke="#829a78" stroke-width="1" marker-end="url(#diagram-arrow)"/>';
    }
    data.nodes.forEach((n,i)=>{const p=pos[i],accent=i===data.nodes.length-1;
      html+='<g><rect x="'+p.x+'" y="'+p.y+'" width="'+w+'" height="'+h+'" rx="3" fill="'+(accent?'#314a37':'#25392c')+'" stroke="'+(accent?'#b4be83':'#64775b')+'"/>';
      const lines=wrapText(n[0],Math.max(7,(w-18)/12));
      lines.forEach((line,j)=>{html+='<text x="'+(p.x+w/2)+'" y="'+(p.y+(lines.length>1?24:31)+j*17)+'" fill="#f6edda" font-size="12" text-anchor="middle">'+esc(line)+'</text>';});
      html+='<text x="'+(p.x+w/2)+'" y="'+(p.y+62)+'" fill="#b8c6ae" font-size="10" text-anchor="middle">'+esc(n[1])+'</text></g>';
    });
    svg.setAttribute('viewBox','0 0 '+width+' '+height);svg.style.height=height+'px';svg.innerHTML=html;
  }
  function renderChapter(){
    const c=CHAPTERS[chapter],diagram=c[era];
    const cite=c.quoteRef?'<button class="text-button" data-commit="'+c.quoteRef+'">'+c.quoteRef+' · '+commitByRef.get(c.quoteRef).date.slice(0,10)+'</button>':'<button class="text-button" data-doc="'+esc(c.quoteDoc)+'">作者复盘原文 ↗</button>';
    $('chapter-scene').innerHTML='<div class="chapter-copy"><div class="chapter-number"><span>ACT '+String(chapter+1).padStart(2,'0')+' / 08</span><i></i><span>'+c.range+'</span></div><h3>'+esc(c.title).replace('\n','<br>')+'</h3>'+c.paragraphs.map(p=>'<p>'+esc(p)+'</p>').join('')+'<p class="chapter-turn">'+esc(c.turn)+'</p><div class="chapter-evidence"><span class="fine">提交证据</span>'+c.refs.map(ref=>'<button data-commit="'+ref+'">'+ref+' ↗</button>').join('')+'</div></div><div class="chapter-visual"><div class="diagram-toggle" role="group" aria-label="比较图解"><button data-era="before" aria-pressed="'+(era==='before')+'" class="'+(era==='before'?'active':'')+'">当时的样子</button><button data-era="after" aria-pressed="'+(era==='after')+'" class="'+(era==='after'?'active':'')+'">后来的变化</button></div><p class="diagram-caption">'+esc(diagram.caption)+'</p><svg class="diagram-canvas" id="architecture" role="img" aria-label="'+esc(diagram.caption)+'"></svg><p class="diagram-note">'+esc(diagram.note)+'</p><blockquote class="chapter-quote">'+esc(c.quote)+'<cite>'+cite+'</cite></blockquote></div>';
    $('chapter-tabs').querySelectorAll('button').forEach((b,i)=>{b.classList.toggle('active',i===chapter);b.setAttribute('aria-pressed',String(i===chapter));});
    $('prev-chapter').disabled=chapter===0;
    $('next-chapter').disabled=chapter===CHAPTERS.length-1;
    drawDiagram();
  }
  function goChapter(i){chapter=Math.max(0,Math.min(CHAPTERS.length-1,i));era='after';playStarted=performance.now();setProgress(0);renderChapter();}
  function setProgress(p){$('film-progress').firstElementChild.style.width=p+'%';$('film-progress').setAttribute('aria-valuenow',String(Math.round(p)));}
  function tick(now){if(!playing)return;const p=Math.min(100,(now-playStarted)/DURATION*100);setProgress(p);if(p>=100){if(chapter===CHAPTERS.length-1){pauseFilm();setProgress(100);$('film-status').textContent='放映结束。下一幕，还在等下一条提交。';return;}goChapter(chapter+1);}raf=requestAnimationFrame(tick);}
  function playFilm(){if(playing)return;playing=true;playStarted=performance.now();$('play-film').textContent='Ⅱ 暂停放映';$('play-film').setAttribute('aria-pressed','true');$('film-status').textContent='正在放映 · 每幕 23 秒 · 随时可以暂停、翻页或查看提交';raf=requestAnimationFrame(tick);}
  function pauseFilm(){playing=false;cancelAnimationFrame(raf);$('play-film').textContent='▷ 自动放映';$('play-film').setAttribute('aria-pressed','false');$('film-status').textContent='已暂停。可以慢慢看，也可以继续放映。';}
  $('chapter-tabs').addEventListener('click',e=>{const b=e.target.closest('[data-chapter]');if(b)goChapter(Number(b.dataset.chapter));});
  $('chapter-scene').addEventListener('click',e=>{const b=e.target.closest('[data-era]');if(b){era=b.dataset.era;renderChapter();}});
  $('prev-chapter').addEventListener('click',()=>goChapter(chapter-1));
  $('next-chapter').addEventListener('click',()=>goChapter(chapter+1));
  $('play-film').addEventListener('click',()=>playing?pauseFilm():playFilm());
  let cinemaScroll=0;
  function cinema(enabled){const theater=$('theater');if(enabled&&!theater.classList.contains('cinema'))cinemaScroll=scrollY;theater.classList.toggle('cinema',enabled);document.body.style.overflow=enabled?'hidden':'';$('cinema-mode').textContent=enabled?'退出专注':'专注放映';$('cinema-mode').setAttribute('aria-pressed',String(enabled));if(!enabled)window.scrollTo({top:cinemaScroll,behavior:'instant'});drawDiagram();}
  $('cinema-mode').addEventListener('click',()=>cinema(!$('theater').classList.contains('cinema')));
  $('start-film').addEventListener('click',()=>{goChapter(0);$('theater').scrollIntoView({behavior:reduced?'instant':'smooth'});playFilm();});
  document.addEventListener('click',e=>{const b=e.target.closest('[data-scene]');if(b){goChapter(Number(b.dataset.scene));$('theater').scrollIntoView({behavior:reduced?'instant':'smooth'});}});
  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!dialog.open&&$('theater').classList.contains('cinema'))cinema(false);if(dialog.open||/INPUT|TEXTAREA|SELECT/.test(e.target.tagName))return;if($('theater').classList.contains('cinema')){if(e.key==='ArrowRight'){e.preventDefault();goChapter(chapter+1);}if(e.key==='ArrowLeft'){e.preventDefault();goChapter(chapter-1);}}});
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&playing)pauseFilm();});
  new ResizeObserver(()=>drawDiagram()).observe($('chapter-scene'));
  renderChapter();

  // Snapshot counts, deliberately labelled as files rather than tests or coverage.
  const maxTests=Math.max(...HISTORY.snapshots.map(s=>s.javaTestFiles));
  $('test-chart').innerHTML='<div class="test-bars" role="img" aria-label="Java 测试文件数：'+HISTORY.snapshots.map(s=>s.label+' '+s.javaTestFiles+' 个').join('，')+'">'+HISTORY.snapshots.map(s=>'<div class="test-column"><strong>'+s.javaTestFiles+'</strong><div class="test-bar" style="--h:'+Math.max(2,s.javaTestFiles/maxTests*118)+'px"></div></div>').join('')+'</div><div class="test-labels">'+HISTORY.snapshots.map(s=>'<span>'+s.label+'</span>').join('')+'</div>';

  let archiveLimit=15;
  function renderArchive(){
    const query=$('commit-search').value.trim().toLocaleLowerCase(),hide=$('hide-merges').checked;
    const records=HISTORY.commits.filter(c=>(!hide||!c.merge)&&(!query||(c.body+' '+c.date+' '+c.sha).toLocaleLowerCase().includes(query)));
    $('archive-count').textContent=records.length+' 条记录';
    $('commit-list').innerHTML=records.length?records.slice(0,archiveLimit).map(c=>'<button class="commit-row" data-commit="'+c.hash+'"><time datetime="'+c.date+'">'+c.date.slice(0,10).replaceAll('-','.')+'</time><span class="commit-hash">'+c.hash+'</span><span class="commit-title">'+esc(c.subject)+'</span><span class="commit-arrow" aria-hidden="true">↗</span></button>').join(''):'<p class="empty-state">这段话没有出现在提交里。换个关键词试试。</p>';
    $('load-more').hidden=records.length<=archiveLimit;
    $('load-more').textContent='继续翻阅 ↓ · 还有 '+Math.max(0,records.length-archiveLimit)+' 条';
  }
  $('commit-search').addEventListener('input',()=>{archiveLimit=15;renderArchive();});
  $('hide-merges').addEventListener('change',()=>{archiveLimit=15;renderArchive();});
  $('load-more').addEventListener('click',()=>{archiveLimit+=30;renderArchive();});
  renderArchive();

  let blown=false,confettiFrame=0,confettiTimer=0;
  function celebrate(){
    const canvas=$('confetti'),ctx=canvas.getContext('2d');
    if(reduced||!ctx)return;
    cancelAnimationFrame(confettiFrame);clearTimeout(confettiTimer);
    const dpr=Math.min(devicePixelRatio||1,2),w=innerWidth,h=innerHeight;canvas.width=w*dpr;canvas.height=h*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);canvas.style.display='block';
    const colors=['#e66c48','#e5b875','#64816e','#f0d7a1','#8a9fbd'];
    const bits=Array.from({length:110},()=>({x:w*.5,y:h*.6,vx:(Math.random()-.5)*12,vy:-7-Math.random()*12,r:Math.random()*Math.PI,spin:(Math.random()-.5)*.15,c:colors[Math.floor(Math.random()*colors.length)],s:3+Math.random()*4}));
    const start=performance.now();let previous=start;
    function paint(now){const t=Math.min((now-previous)/16.67,3);previous=now;ctx.clearRect(0,0,w,h);bits.forEach(b=>{b.x+=b.vx*t;b.y+=b.vy*t;b.vy+=.13*t;b.r+=b.spin*t;ctx.save();ctx.translate(b.x,b.y);ctx.rotate(b.r);ctx.fillStyle=b.c;ctx.fillRect(-b.s/2,-b.s/2,b.s,b.s*1.8);ctx.restore();});if(now-start<5000)confettiFrame=requestAnimationFrame(paint);else{canvas.style.display='none';ctx.clearRect(0,0,w,h);}}
    confettiFrame=requestAnimationFrame(paint);
    confettiTimer=setTimeout(()=>{cancelAnimationFrame(confettiFrame);canvas.style.display='none';},5500);
  }
  function wish(){blown=!blown;$('cake').classList.toggle('blown',blown);$('cake').setAttribute('aria-label',blown?'重新点亮生日蜡烛':'吹灭一岁生日蜡烛');$('wish-button').textContent=blown?'再点亮一次':'许个愿，吹蜡烛';$('wish-message').textContent=blown?'一岁快乐。下一条提交，继续慢慢写。':'希望明年再看，又能多明白一些。';if(blown)celebrate();}
  $('cake').addEventListener('click',wish);$('wish-button').addEventListener('click',wish);
})();
