/* Read only already-loaded QR-sized images/canvases. No network requests, form reads or clicks. */
function ecoCheckQrImages() {
    'use strict';
    if (location.protocol !== 'https:') return [];
    var images = [], total = 0;
    function visible(el, win) {
        for (var node = el; node; node = node.parentElement) {
            var style = win.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
        }
        return true;
    }
    function collect(doc, depth) {
        Array.from(doc.querySelectorAll('img, canvas')).forEach(function (el) {
            if (images.length >= 6) return;
            var box = el.getBoundingClientRect(), win = doc.defaultView;
            if (!visible(el, win) ||
                box.width < 64 || box.height < 64 || box.width / box.height > 1.6 || box.height / box.width > 1.6 ||
                box.bottom <= 0 || box.right <= 0 || box.top >= win.innerHeight || box.left >= win.innerWidth) return;
            var width = el.naturalWidth || el.width, height = el.naturalHeight || el.height;
            if (!width || !height || (el.tagName === 'IMG' && !el.complete)) return;
            try {
                var scale = Math.min(1, 1024 / width, 1024 / height);
                var canvas = doc.createElement('canvas');
                canvas.width = Math.max(1, Math.round(width * scale));
                canvas.height = Math.max(1, Math.round(height * scale));
                var ctx = canvas.getContext('2d');
                ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(el, 0, 0, canvas.width, canvas.height);
                var data = canvas.toDataURL('image/png');
                if (data.startsWith('data:image/png;base64,') && data.length <= 2000000 &&
                    total + data.length <= 4000000 && images.indexOf(data) < 0) {
                    images.push(data); total += data.length;
                }
            } catch (_) { /* Cross-origin images are handled by the native window capture. */ }
        });
        Array.from(doc.querySelectorAll('iframe')).forEach(function (frame) {
            try { if (depth < 2 && visible(frame, doc.defaultView) && frame.contentDocument) collect(frame.contentDocument, depth + 1); } catch (_) {}
        });
    }
    collect(document, 0);
    return images;
}
