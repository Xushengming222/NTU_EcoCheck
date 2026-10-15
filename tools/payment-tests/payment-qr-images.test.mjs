import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';

const source = readFileSync(new URL('../../app/src/main/assets/payment-qr-images.js', import.meta.url), 'utf8');
function fixture(html, url = 'https://pay.ntu.edu.cn/checkout') {
  const {window:w} = new JSDOM(html, {url, runScripts:'outside-only'});
  const drawn = [];
  function instrument(win) {
    win.HTMLElement.prototype.getBoundingClientRect = function () {
      const width = +(this.dataset.w || 256), height = +(this.dataset.h || 256);
      const top = +(this.dataset.top || 20), left = +(this.dataset.left || 20);
      return {width, height, top, left, bottom:top + height, right:left + width};
    };
    win.HTMLCanvasElement.prototype.getContext = function () {
      const canvas = this;
      return {fillRect() {}, drawImage(el) {
        if (el.dataset.tainted) throw new win.DOMException('Cross origin', 'SecurityError');
        canvas.qrValue = el.dataset.code || el.id; drawn.push(el.id);
      }};
    };
    win.HTMLCanvasElement.prototype.toDataURL = function () {
      return 'data:image/png;base64,' + w.btoa(this.qrValue);
    };
    for (const img of win.document.querySelectorAll('img')) {
      Object.defineProperties(img, {
        naturalWidth:{value:256}, naturalHeight:{value:256}, complete:{value:!img.dataset.loading}
      });
    }
  }
  instrument(w); w.eval(source);
  return {w, drawn, instrument, read:() => Array.from(w.ecoCheckQrImages())};
}

test('reads the displayed QR image/canvas pixels and deduplicates the same payment code', () => {
  const p = fixture('<img id="wechat" data-code="same"><canvas id="wechat-copy" data-code="same" width="256" height="256"></canvas><img id="alipay">');
  assert.equal(p.read().length, 2);
  assert.deepEqual(p.drawn, ['wechat', 'wechat-copy', 'alipay']);
});

test('cross-origin canvas security errors leave other QR candidates readable', () => {
  const p = fixture('<img id="cross-origin" data-tainted="true"><img id="alipay">');
  assert.deepEqual(p.read(), ['data:image/png;base64,' + p.w.btoa('alipay')]);
});

test('ignores hidden, offscreen, unloaded, tiny or banner images', () => {
  const p = fixture('<div style="display:none"><img id="hidden"></div><div style="opacity:0"><img id="transparent"></div>' +
    '<img id="below" data-top="3000"><img id="unloaded" data-loading="true"><img id="icon" data-w="24" data-h="24">' +
    '<img id="banner" data-w="600" data-h="100"><img id="qr">');
  assert.equal(p.read().length, 1); assert.deepEqual(p.drawn, ['qr']);
});

test('QR inspection does not read form values, alter the amount or click payment buttons', () => {
  const p = fixture('<img id="qr"><input id="amount" value="50.25"><input type="password" value="private"><button id="pay">支付</button>');
  let clicks = 0; p.w.document.querySelector('button').addEventListener('click', () => clicks++);
  p.read(); assert.equal(clicks, 0); assert.equal(p.w.document.getElementById('amount').value, '50.25');
  assert.deepEqual(p.drawn, ['qr']);
});

test('bounds the number of QR candidates and refuses insecure documents', () => {
  const p = fixture(Array.from({length:20}, (_, i) => `<img id="qr${i}">`).join(''));
  assert.equal(p.read().length, 6); assert.equal(p.drawn.length, 6);
  assert.deepEqual(fixture('<img id="qr">', 'http://pay.ntu.edu.cn/checkout').read(), []);
});

test('reads accessible iframe QR images while skipping blocked and hidden frames', () => {
  const p = fixture('<iframe id="allowed"></iframe><iframe id="blocked"></iframe><iframe id="hidden" style="display:none"></iframe>');
  const frame = p.w.document.getElementById('allowed');
  frame.contentDocument.body.innerHTML = '<img id="framed-qr">'; p.instrument(frame.contentWindow);
  Object.defineProperty(p.w.document.getElementById('blocked'), 'contentDocument', {get() {throw new Error('Cross origin');}});
  assert.equal(p.read().length, 1); assert.deepEqual(p.drawn, ['framed-qr']);
});
