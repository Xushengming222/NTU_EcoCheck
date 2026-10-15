import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const source = readFileSync(new URL('../../app/src/main/assets/payment-autofill.js', import.meta.url), 'utf8');
const target = {
  campusId: '1', campusName: '启东校区',
  buildingId: '2', buildingName: '2号楼', roomId: '123', roomName: '201',
  credentials: {studentId: 'test-student', password: "a'\"\\\n测试"}
};
function page(html, url = 'https://pay.ntu.edu.cn/elecdetails5118E017') {
  const dom = new JSDOM(html, {url, runScripts: 'outside-only'});
  const w = dom.window;
  let now = 100_000;
  w.Date.now = () => now;
  // jsdom has no layout. Model visibility explicitly, including hidden ancestors.
  w.HTMLElement.prototype.getClientRects = function () {
    for (let el = this; el; el = el.parentElement) {
      if (el.hidden || w.getComputedStyle(el).display === 'none') return [];
    }
    return [{}];
  };
  w.eval(source);
  return {w, d: w.document, tick: (ms = 1100) => {now += ms;}, step: (cfg = target) => w.ecoCheckPaymentStep(cfg)};
}
const loginHtml = `
  <form id="phoneFromId" style="display:none"><input name="username"><input id="login_submit" type="button"></form>
  <form id="pwdFromId" action="/authserver/login">
    <input name="username"><input type="password" name="passwordText">
    <input name="captcha" style="display:none"><span id="showErrorTip"></span>
    <a id="login_submit">登录</a><input id="saltPassword" type="hidden" name="password">
  </form>`;
function login(html = loginHtml) {
  const p = page(html, 'https://authserver.ntu.edu.cn/authserver/login?service=test');
  p.form = p.d.getElementById('pwdFromId');
  p.clicks = 0;
  p.form.querySelector('#login_submit').addEventListener('click', () => p.clicks++);
  return p;
}
function authorizeReady(p, cfg = target) {
  const ready = p.step(cfg);
  assert.equal(ready.stage, 'login_ready');
  assert.equal(p.clicks, 0);
  return p.step({...cfg, authorizeLoginNonce: ready.nonce});
}
function dorm() {
  const p = page(`
    <select id="schoolid"><option value="">请选择</option><option value="1">启东校区</option></select>
    <select id="building"><option value="">请选择</option></select>
    <select id="room"><option value="">请选择</option></select>
    <input id="amount" value=""><button id="pay">确认缴费</button><button id="order">创建订单</button>`);
  p.changes = [];
  for (const id of ['schoolid', 'building', 'room']) {
    p.d.getElementById(id).addEventListener('change', () => p.changes.push(id));
  }
  p.d.getElementById('schoolid').addEventListener('change', () => {
    p.d.getElementById('building').innerHTML = '<option value="">请选择</option><option value="2">2号楼</option>';
    p.d.getElementById('room').innerHTML = '<option value="">请选择</option>';
  });
  p.d.getElementById('building').addEventListener('change', () => {
    p.d.getElementById('room').innerHTML = '<option value="">请选择</option><option value="123">201</option>';
  });
  p.payClicks = 0;
  p.d.querySelectorAll('button').forEach(el => el.addEventListener('click', () => p.payClicks++));
  return p;
}

test('opens the electricity project without touching other projects or payment controls', () => {
  const p = page('<a onclick="" id="other">热水</a><a onclick="openPayProjectDetail(\'elecdetails5118E017\',18)" id="electricity">学生宿舍电费</a>');
  const clicks = [];
  p.d.querySelectorAll('a').forEach(a => a.addEventListener('click', () => clicks.push(a.id)));
  p.step(); p.step();
  assert.deepEqual(clicks, ['electricity']);
});
test('uses the official SSO entry once', () => {
  const p = page('<a href="/signAuthentication?url=openUserPayProList">SSO</a>', 'https://pay.ntu.edu.cn/innerUserLogin');
  let clicks = 0;
  p.d.querySelector('a').addEventListener('click', e => {e.preventDefault(); clicks++;});
  p.step(); p.step();
  assert.equal(clicks, 1);
});
test('fills the password form, preserving punctuation and avoiding duplicate username IDs', () => {
  const p = login();
  assert.equal(p.step().stage, 'loading');
  assert.equal(p.form.querySelector('[name="username"]').value, target.credentials.studentId);
  // Password inputs remove newlines by HTML design; quotes, backslashes, and Unicode survive.
  assert.equal(p.form.querySelector('[type="password"]').value, target.credentials.password.replace(/\n/g, ''));
  assert.equal(p.d.querySelector('#phoneFromId [name="username"]').value, '');
  assert.equal(p.clicks, 0);
  p.tick(1600);
  assert.equal(authorizeReady(p).stage, 'login_submitted');
  p.step();
  assert.equal(p.clicks, 1);
  assert.equal(Object.values(p.w.__ntuEcoPayment).includes(target.credentials.password), false);
});
test('does not retry login after the native controller removes credentials', () => {
  const p = login(); p.step(); p.tick(1600); authorizeReady(p);
  const {credentials, ...withoutCredentials} = target;
  assert.equal(p.step(withoutCredentials).stage, 'manual_login');
  assert.equal(p.clicks, 1);
});
test('waits for the school script to reveal the password form before manual takeover', () => {
  const p = login(); p.form.style.display = 'none';
  assert.equal(p.step().stage, 'loading');
  p.tick(2000); p.form.style.display = '';
  assert.equal(p.step().stage, 'loading');
  assert.equal(p.form.querySelector('[name="username"]').value, target.credentials.studentId);
  const other = login(); other.form.style.display = 'none';
  assert.equal(other.step().stage, 'loading'); other.tick(5001);
  assert.equal(other.step().stage, 'manual_login');
});
test('a visible captcha requires manual login', () => {
  const p = login();
  p.form.querySelector('[name="captcha"]').style.display = '';
  assert.equal(p.step().stage, 'manual_login');
  assert.equal(p.clicks, 0);
});
test('a captcha appearing after username validation prevents automatic submit', () => {
  const p = login(); p.step(); p.tick(1600);
  p.form.querySelector('[name="captcha"]').style.display = '';
  assert.equal(p.step().stage, 'manual_login');
  assert.equal(p.clicks, 0);
});
test('waits for username captcha-check AJAX before submitting', () => {
  const p = login(); let checks = 0;
  p.form.querySelector('[name="username"]').addEventListener('focusout', () => checks++);
  p.step(); p.tick(1600); p.w.jQuery = {active: 1};
  assert.equal(checks, 1);
  assert.equal(p.step().stage, 'loading'); assert.equal(p.clicks, 0);
  p.w.jQuery.active = 0;
  assert.equal(authorizeReady(p).stage, 'login_submitted'); assert.equal(p.clicks, 1);
});
test('slider verification signaled by the official script requires manual login', () => {
  const p = login(); p.step(); p.tick(1600); p.w.needCaptcha = true;
  assert.equal(p.step().stage, 'manual_login'); assert.equal(p.clicks, 0);
});
test('server login errors require manual takeover', () => {
  const p = login(); p.form.querySelector('#showErrorTip').textContent = '用户名或密码错误';
  assert.equal(p.step().stage, 'manual_login');
  assert.equal(p.form.querySelector('[type="password"]').value, '');
  assert.equal(p.clicks, 0);
});
test('rejects foreign or insecure login form actions before filling credentials', () => {
  for (const action of ['https://example.com/collect', 'http://authserver.ntu.edu.cn/authserver/login']) {
    const p = login(); p.form.action = action;
    assert.equal(p.step().stage, 'manual_login');
    assert.equal(p.form.querySelector('[type="password"]').value, '');
  }
});
test('no credential filling or clicks on lookalike hosts or HTTP', () => {
  for (const url of ['https://authserver.ntu.edu.cn.example.com/authserver/login', 'http://authserver.ntu.edu.cn/authserver/login']) {
    const p = page(loginHtml, url);
    assert.equal(p.step().stage, 'manual');
    assert.equal(p.d.querySelector('[type="password"]').value, '');
  }
});
test('selects campus, then building, then room, and leaves amount and order buttons untouched', () => {
  const p = dorm();
  for (let i = 0; i < 3; i++) {assert.equal(p.step().stage, 'loading'); p.tick();}
  assert.equal(p.step().stage, 'ready');
  assert.deepEqual(p.changes, ['schoolid', 'building', 'room']);
  assert.equal(p.d.getElementById('amount').value, '');
  assert.equal(p.payClicks, 0);
});
test('waits for dependent options and AJAX before choosing a building', () => {
  const p = dorm(); p.step(); p.tick(); p.w.jQuery = {active: 1};
  assert.equal(p.step().stage, 'loading');
  assert.deepEqual(p.changes, ['schoolid']);
  p.w.jQuery.active = 0; p.step();
  assert.deepEqual(p.changes, ['schoolid', 'building']);
});
test('does not choose an ID that now refers to a different building', () => {
  const p = dorm(); p.step(); p.tick();
  p.d.querySelector('#building option[value="2"]').text = '别的楼栋';
  assert.equal(p.step().stage, 'mismatch');
  assert.equal(p.d.getElementById('building').value, '');
  assert.equal(p.payClicks, 0);
});
test('missing target options time out without selecting an alternative', () => {
  const p = dorm(); const cfg = {...target, campusId: 'missing'};
  assert.equal(p.step(cfg).stage, 'loading'); p.tick(15001);
  assert.equal(p.step(cfg).stage, 'mismatch');
  assert.deepEqual(p.changes, []);
});
test('manual parent changes stop filling child fields', () => {
  const p = dorm(); p.step(); p.tick(); p.d.getElementById('schoolid').value = '';
  assert.equal(p.step().stage, 'mismatch');
  assert.deepEqual(p.changes, ['schoolid']);
});
test('does not overwrite room or amount after the initial successful fill', () => {
  const p = dorm();
  for (let i = 0; i < 4; i++) {p.step(); p.tick();}
  p.d.getElementById('room').value = ''; p.d.getElementById('amount').value = '50';
  assert.equal(p.step().stage, 'ready');
  assert.equal(p.d.getElementById('room').value, '');
  assert.equal(p.d.getElementById('amount').value, '50');
  assert.equal(p.payClicks, 0);
});

test('polling a ready login form never submits without a native one-time authorization', () => {
  const p = login(); p.step(); p.tick(1600);
  for (let i = 0; i < 100; i++) assert.equal(p.step().stage, 'login_ready');
  assert.equal(p.clicks, 0);
});
test('visible slider pauses permanently even if it later disappears or reports success', () => {
  const p = login(); p.step(); p.tick(1600);
  const slider = p.d.createElement('div'); slider.id = 'sliderCaptchaDiv'; slider.innerHTML = '<button>拖动滑块</button>';
  p.d.body.appendChild(slider);
  assert.equal(p.step().stage, 'manual_login');
  slider.remove(); p.w.needCaptcha = false;
  for (let i = 0; i < 100; i++) assert.equal(p.step().stage, 'manual_login');
  assert.equal(p.clicks, 0);
  p.form.querySelector('#login_submit').click(); // Human submission remains available.
  assert.equal(p.clicks, 1);
});
test('a delayed authorization cannot submit a replacement login document', () => {
  const p = login(); p.step(); p.tick(1600);
  const nonce = p.step().nonce;
  p.w.__ntuEcoPayment = undefined;
  assert.equal(p.step({...target, authorizeLoginNonce:nonce}).stage, 'manual_login');
  assert.equal(p.clicks, 0);
});
test('after a submit, repeated page initialization without credentials cannot retry login', () => {
  const p = login(); p.step(); p.tick(1600); authorizeReady(p);
  const {credentials, ...blocked} = target;
  for (let i = 0; i < 50; i++) {
    p.w.__ntuEcoPayment = undefined;
    assert.equal(p.step(blocked).stage, 'manual_login');
  }
  assert.equal(p.clicks, 1);
});
test('changing the account during preflight requires manual login', () => {
  const p = login(); p.step(); p.tick(1600);
  p.form.querySelector('[name="username"]').value = 'another-account';
  assert.equal(p.step().stage, 'manual_login'); assert.equal(p.clicks, 0);
});
test('query mode reads the selected dorm result without creating a payment order', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  for (let i = 0; i < 4; i++) { assert.equal(p.step(cfg).stage, 'loading'); p.tick(); }
  const balance = p.d.createElement('div'); balance.id = 'zhye'; balance.textContent = '236.85059 度';
  p.d.body.appendChild(balance);
  const result = p.step(cfg);
  assert.equal(result.stage, 'query_result'); assert.equal(result.data.electricity, '236.85059');
  assert.equal(result.data.room, target.roomName); assert.match(result.data.date, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(p.payClicks, 0); assert.equal(p.d.getElementById('amount').value, '');
});

test('a preloaded balance cannot bypass selecting and verifying the requested dormitory', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  const balance = p.d.createElement('div'); balance.id = 'zhye'; balance.textContent = '0 度';
  p.d.body.appendChild(balance);
  for (let i = 0; i < 3; i++) { assert.equal(p.step(cfg).stage, 'loading'); p.tick(); }
  const result = p.step(cfg);
  assert.equal(result.stage, 'query_result'); assert.equal(result.data.electricity, '0');
  assert.deepEqual(p.changes, ['schoolid', 'building', 'room']);
  assert.equal(p.payClicks, 0);
});

test('query reads the completed room balance despite unrelated background AJAX', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  for (let i = 0; i < 4; i++) { assert.equal(p.step(cfg).stage, 'loading'); p.tick(); }
  const balance = p.d.createElement('div'); balance.id = 'zhye'; balance.textContent = '123';
  p.d.body.appendChild(balance); p.w.jQuery = {active:1};
  assert.equal(p.step(cfg).data.electricity, '123');
  balance.textContent = '-1.25 kWh';
  assert.equal(p.step(cfg).data.electricity, '-1.25');
  assert.equal(p.payClicks, 0);
});

test('query rejects error messages containing a number and a changed dormitory', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  for (let i = 0; i < 4; i++) { p.step(cfg); p.tick(); }
  const balance = p.d.createElement('div'); balance.id = 'zhye';
  balance.textContent = '查询失败（错误码500）'; p.d.body.appendChild(balance);
  assert.equal(p.step(cfg).stage, 'manual');
  balance.textContent = '236.85059'; p.d.getElementById('room').value = '';
  assert.equal(p.step(cfg).stage, 'mismatch'); assert.equal(p.payClicks, 0);
});

test('payment mode remains a cashier even when an electricity result is already available', () => {
  const p = dorm();
  const balance = p.d.createElement('div'); balance.id = 'zhye'; balance.textContent = '236.85059';
  p.d.body.appendChild(balance);
  for (let i = 0; i < 3; i++) { p.step(); p.tick(); }
  const result = p.step();
  assert.equal(result.stage, 'ready'); assert.equal(result.data, undefined);
  assert.equal(p.d.getElementById('amount').value, ''); assert.equal(p.payClicks, 0);
});

test('query uses the same manual slider takeover without submitting login', () => {
  const p = login(); const cfg = {...target, queryOnly:true};
  p.w.needCaptcha = true;
  for (let i = 0; i < 100; i++) { assert.equal(p.step(cfg).stage, 'manual_login'); p.tick(); }
  assert.equal(p.clicks, 0);
});

test('original td balance text with labels and units completes query with background requests active', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  p.w.jQuery = {active:1};
  for (let i = 0; i < 3; i++) { assert.equal(p.step(cfg).stage, 'loading'); p.tick(); }
  p.d.body.insertAdjacentHTML('beforeend', '<div id="zhye"></div><table><tr><td id="zhye">剩余电量：236.85059（度）</td></tr></table>');
  const result = p.step(cfg);
  assert.equal(result.stage, 'query_result'); assert.equal(result.queryStep, 8);
  assert.equal(result.data.electricity, '236.85059'); assert.equal(p.payClicks, 0);
});

test('query reports the original five-step progress while selecting the dormitory', () => {
  const p = dorm(); const cfg = {...target, queryOnly:true};
  for (const expected of [5, 6, 7]) { assert.equal(p.step(cfg).queryStep, expected); p.tick(); }
  assert.equal(p.step(cfg).queryStep, 7); assert.equal(p.payClicks, 0);
});

test('login errors preserve the actual official error message and never resubmit', () => {
  const p = login(); p.form.querySelector('#showErrorTip').textContent = '用户名或密码错误';
  const result = p.step({...target, queryOnly:true});
  assert.equal(result.stage, 'manual_login'); assert.match(result.message, /用户名或密码错误/);
  p.form.querySelector('#showErrorTip').textContent = '';
  assert.match(p.step().message, /用户名或密码错误/); assert.equal(p.clicks, 0);
});
