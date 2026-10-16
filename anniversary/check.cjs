// Browser smoke checks for the offline deliverable. Uses the bundled runtime.
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('C:/Users/Lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const history = JSON.parse(fs.readFileSync(path.join(__dirname,'history.json'),'utf8'));
const mainHead = execFileSync('git',['rev-parse','origin/master'],{cwd:path.dirname(__dirname),encoding:'utf8'}).trim();
const mainCount = Number(execFileSync('git',['rev-list','--count',mainHead],{cwd:path.dirname(__dirname),encoding:'utf8'}).trim());
assert.equal(history.branch,'master','Anniversary must use the main branch');
assert.equal(history.sourceRef,'origin/master');
assert.equal(history.head,mainHead,'History snapshot must match origin/master');
assert.equal(history.count,mainCount);

(async()=>{
  const browser = await chromium.launch({headless:true,channel:'msedge'});
  const context = await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const remoteRequests=[];
  page.on('request',r=>{if(/^https?:/.test(r.url()))remoteRequests.push(r.url());});
  await page.goto(pathToFileURL(path.join(__dirname,'dist/index.html')).href);
  await page.waitForSelector('.map-node');
  assert.equal(await page.locator('.map-node').count(),24);
  assert.equal(await page.locator('#chapter-tabs button').count(),8);
  assert.equal(await page.locator('.commit-row').count(),15);
  await page.screenshot({path:path.join(__dirname,'desktop-preview.png'),fullPage:false});
  for(let i=0;i<8;i++){
    await page.locator('[data-chapter="'+i+'"]').click();
    assert.equal(await page.locator('#architecture rect').count()>0,true);
    for(const era of ['before','after']){
      await page.locator('[data-era="'+era+'"]').click();
      const outside=await page.locator('#architecture').evaluate(svg=>{
        const width=svg.viewBox.baseVal.width;
        return [...svg.querySelectorAll('text')].filter(t=>{const b=t.getBBox();return b.x < -1||b.x+b.width > width+1;}).map(t=>t.textContent);
      });
      assert.deepEqual(outside,[],'Diagram text outside viewBox: '+i+' '+era);
    }
  }
  await page.locator('[data-chapter="3"]').click();
  await page.locator('#theater').screenshot({path:path.join(__dirname,'evolution-preview.png')});
  await page.locator('[data-node="23"]').click();
  assert.match(await page.locator('#node-detail').innerText(),/重看自己的结论/);
  await page.locator('#node-detail [data-commit]').click();
  assert.equal(await page.locator('#commit-dialog').evaluate(d=>d.open),true);
  assert.match(await page.locator('#dialog-title').innerText(),/Lua原子处理/);
  await page.locator('#close-dialog').click();
  await page.locator('#commit-search').fill('明天完善消息解析');
  assert.equal(await page.locator('.commit-row').count(),1);
  await page.locator('.commit-row').click();
  assert.match(await page.locator('#dialog-title').innerText(),/今天没有写java代码/);
  await page.keyboard.press('Escape');
  await page.locator('#commit-search').fill('不存在的周年测试关键词');
  assert.equal(await page.locator('.empty-state').count(),1);
  await page.locator('#commit-search').fill('');
  await page.locator('#hide-merges').check();
  assert.match(await page.locator('#archive-count').innerText(),/134/);
  await page.locator('#hide-merges').uncheck();
  for(let i=0;i<6&&await page.locator('#load-more').isVisible();i++)await page.locator('#load-more').click();
  assert.equal(await page.locator('.commit-row').count(),history.count);
  await page.locator('#wish-button').click();
  assert.equal(await page.locator('#cake').evaluate(e=>e.classList.contains('blown')),true);
  assert.match(await page.locator('#wish-message').innerText(),/一岁快乐/);
  await page.locator('#wish-button').click();
  assert.equal(await page.locator('#cake').evaluate(e=>e.classList.contains('blown')),false);
  await page.locator('#cinema-mode').click();
  assert.equal(await page.locator('#theater').evaluate(e=>e.classList.contains('cinema')),true);
  await page.keyboard.press('ArrowRight');
  assert.equal(await page.locator('[data-chapter="4"]').getAttribute('aria-pressed'),'true');
  await page.keyboard.press('Escape');
  await page.locator('#play-film').click();
  assert.equal(await page.locator('#play-film').getAttribute('aria-pressed'),'true');
  await page.waitForFunction(()=>Number(document.querySelector('#film-progress').getAttribute('aria-valuenow'))>0);
  await page.locator('#play-film').click();
  assert.equal(await page.locator('#play-film').getAttribute('aria-pressed'),'false');
  const overflows=[];
  for(const width of [1440,1024,768,390,320]){
    await page.setViewportSize({width,height:900});
    await page.waitForTimeout(120);
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
    if(overflow)overflows.push(width);
    for(let i=0;i<8;i++){
      await page.locator('[data-chapter="'+i+'"]').click();
      const clipped=await page.locator('#architecture').evaluate(svg=>{const w=svg.viewBox.baseVal.width;return [...svg.querySelectorAll('text')].filter(t=>{const b=t.getBBox();return b.x<0||b.x+b.width>w+1;}).map(t=>t.textContent);});
      assert.deepEqual(clipped,[],'Narrow diagram clipping at '+width+' chapter '+i);
    }
    if(width===390){await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(__dirname,'mobile-preview.png'),fullPage:false});}
  }
  assert.deepEqual(overflows,[],'Horizontal page overflow');
  assert.deepEqual(errors,[],'Browser errors');
  assert.deepEqual(remoteRequests,[],'Offline page attempted network');
  const result={status:'passed',sourceRef:history.sourceRef,head:history.head,milestones:24,chapters:8,archiveRecords:history.count,checkedWidths:[1440,1024,768,390,320],pageErrors:errors,networkRequests:remoteRequests};
  fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify(result));
  await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
