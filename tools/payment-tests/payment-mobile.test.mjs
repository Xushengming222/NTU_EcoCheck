import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';

const source = readFileSync(new URL('../../app/src/main/assets/payment-mobile.js', import.meta.url), 'utf8');
function page(url = 'https://pay.ntu.edu.cn/checkout') {
  const dom = new JSDOM(`<head><meta name="viewport" content="width=1200"><meta name="viewport" content="user-scalable=no"></head>
    <body><select id="room"><option selected value="TEST">测试房间</option></select>
    <input id="amount" value="50"><button id="pay">确认支付</button></body>`, {url, runScripts:'outside-only'});
  dom.window.eval(source);
  return dom;
}
test('uses a phone viewport, permits zoom, and removes conflicting desktop viewport tags', () => {
  const dom = page();
  const tags = dom.window.document.querySelectorAll('meta[name="viewport"]');
  assert.equal(tags.length, 1);
  assert.match(tags[0].content, /width=device-width/);
  assert.match(tags[0].content, /user-scalable=yes/);
  dom.window.close();
});
test('mobile layout is idempotent and preserves the selected room, amount and payment handlers', () => {
  const dom = page(); const d = dom.window.document; let clicks = 0;
  d.getElementById('pay').addEventListener('click', () => clicks++);
  dom.window.eval(source);
  assert.equal(d.querySelectorAll('#ecocheck-mobile-style').length, 1);
  assert.equal(d.getElementById('room').value, 'TEST');
  assert.equal(d.getElementById('amount').value, '50');
  assert.equal(clicks, 0);
  d.getElementById('pay').click(); assert.equal(clicks, 1);
  dom.window.close();
});
test('does not rewrite foreign payment gateways, lookalike domains or HTTP pages', () => {
  for (const url of ['https://example.com/pay', 'https://pay.ntu.edu.cn.example.com/pay', 'http://pay.ntu.edu.cn/pay']) {
    const dom = page(url);
    assert.equal(dom.window.document.getElementById('ecocheck-mobile-style'), null);
    dom.window.close();
  }
});
test('enlarges QR elements inserted asynchronously without clicking payment controls', async () => {
  const dom = page(); const d = dom.window.document;
  const qr = d.createElement('canvas'); qr.id = 'qrcode'; d.body.appendChild(qr);
  await new Promise(resolve => setTimeout(resolve, 120));
  assert.equal(qr.classList.contains('ecocheck-payment-qr'), true);
  dom.window.close();
});
test('stacks multi-column payment tables without moving inputs or changing any form values', () => {
  const dom = new JSDOM(`<table width="1100"><colgroup><col width="4%"><col width="1%"><col width="45%"><col width="50%"></colgroup>
    <tr><td>剩余电量</td><td id="zhye">236.85059</td><td>缴费金额</td><td><input id="amount" value="50"></td></tr>
    <tr><td>校区</td><td><select id="schoolid"><option>测试</option></select></td></tr></table>`,
    {url:'https://pay.ntu.edu.cn/elecdetails5118E017',runScripts:'outside-only'});
  const d = dom.window.document; const input = d.getElementById('amount');
  dom.window.eval(source);
  assert.equal(d.querySelector('table').classList.contains('ecocheck-form-table'), true);
  assert.equal(d.getElementById('amount'), input);
  assert.equal(input.value, '50'); assert.equal(d.getElementById('zhye').textContent, '236.85059');
  dom.window.ecoCheckMobileLayout(false);
  assert.equal(d.documentElement.classList.contains('ecocheck-mobile'), false);
  assert.match(d.querySelector('meta[name="viewport"]').content, /width=1100/);
  dom.window.close();
});
test('does not change the official slider dimensions or classes on authentication pages', () => {
  const dom = new JSDOM('<div id="sliderCaptchaDiv" style="width:310px;height:160px"><table><tr><td><input></td></tr></table></div>',
    {url:'https://authserver.ntu.edu.cn/authserver/login',runScripts:'outside-only'});
  dom.window.eval(source);
  const slider = dom.window.document.getElementById('sliderCaptchaDiv');
  assert.equal(slider.style.width,'310px'); assert.equal(slider.style.height,'160px');
  assert.equal(dom.window.document.querySelector('.ecocheck-form-table'),null);
  assert.equal(dom.window.document.querySelector('.ecocheck-shell'),null);
  dom.window.close();
});

test('adapts the later payment-method page while preserving official selection and confirmation handlers', () => {
  const html = readFileSync(new URL('./fixtures/payment-methods.html', import.meta.url), 'utf8');
  const dom = new JSDOM(html, {url:'https://pay.ntu.edu.cn/checkout', runScripts:'dangerously'});
  const d = dom.window.document, confirm = d.getElementById('confirm'), handler = confirm.onclick;
  const form = d.getElementById('officialPaymentForm');
  dom.window.eval(source); dom.window.eval(source);
  assert.equal(d.getElementById('confirm'), confirm); assert.equal(confirm.onclick, handler);
  assert.equal(confirm.value, '确认支付'); assert.equal(form.getAttribute('action'), '/test-only-do-not-submit');
  assert.equal(d.getElementById('payType').value, 'ALIPAY_TEST'); assert.equal(dom.window.paymentClicks, 0);
  assert.equal(d.querySelector('.logo_list img').classList.contains('ecocheck-payment-qr'), false);
  d.getElementById('wechat').click(); assert.equal(d.getElementById('payType').value, 'WECHAT_TEST');
  dom.window.ecoCheckMobileLayout(false); dom.window.ecoCheckMobileLayout(true);
  assert.equal(d.getElementById('payType').value, 'WECHAT_TEST'); assert.equal(confirm.value, '确认支付');
  confirm.click(); assert.equal(dom.window.paymentClicks, 1);
  dom.window.close();
});
