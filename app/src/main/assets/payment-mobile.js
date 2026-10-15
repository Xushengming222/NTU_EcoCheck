function ecoCheckMobileLayout(enabled) {
    'use strict';
    if (location.protocol !== 'https:' ||
        !['pay.ntu.edu.cn', 'authserver.ntu.edu.cn'].includes(location.hostname)) return false;
    var root = document.documentElement;
    if (enabled === undefined) enabled = true;
    root.classList.toggle('ecocheck-mobile', enabled);
    root.classList.toggle('ecocheck-school-pay', location.hostname === 'pay.ntu.edu.cn');
    var viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.appendChild(viewport);
    }
    viewport.content = enabled ? 'width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes' :
        'width=1100, initial-scale=1, maximum-scale=5, user-scalable=yes';
    document.querySelectorAll('meta[name="viewport"]').forEach(function (other) {
        if (other !== viewport) other.remove();
    });
    if (document.getElementById('ecocheck-mobile-style')) return true;
    var style = document.createElement('style');
    style.id = 'ecocheck-mobile-style';
    style.textContent = `
      html.ecocheck-mobile, html.ecocheck-mobile body { margin:0; overflow-x:auto; }
      html.ecocheck-mobile.ecocheck-school-pay body { min-width:0 !important; width:100% !important;
        max-width:100% !important; font-size:16px; line-height:1.5; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-shell {
        box-sizing:border-box !important; min-width:0 !important; width:100% !important;
        max-width:100% !important; margin-left:0 !important; margin-right:0 !important;
        padding-left:12px !important; padding-right:12px !important; height:auto !important; float:none !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-checkout-shell {
        left:auto !important; right:auto !important; text-indent:0 !important; clear:both !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-checkout-shell .ecocheck-checkout-shell {
        padding-left:0 !important; padding-right:0 !important; }
      html.ecocheck-mobile.ecocheck-school-pay .subnav h3,
      html.ecocheck-mobile.ecocheck-school-pay .cont-fr-tit h2,
      html.ecocheck-mobile.ecocheck-school-pay .tit-name,
      html.ecocheck-mobile.ecocheck-school-pay .tit-name p,
      html.ecocheck-mobile.ecocheck-school-pay .payment-w p {
        box-sizing:border-box !important; width:100% !important; max-width:100% !important;
        min-width:0 !important; height:auto !important; float:none !important; margin:0 !important;
        white-space:normal !important; overflow-wrap:anywhere !important; line-height:1.6 !important; font-size:16px !important; }
      html.ecocheck-mobile.ecocheck-school-pay .mode-pay {
        box-sizing:border-box !important; width:100% !important; min-width:0 !important; max-width:100% !important;
        padding:12px 0 !important; margin:0 !important; height:auto !important; overflow:visible !important; }
      html.ecocheck-mobile.ecocheck-school-pay .logo_list {
        box-sizing:border-box !important; display:flex !important; flex-wrap:wrap !important; gap:12px !important;
        width:100% !important; min-width:0 !important; padding:0 !important; margin:0 !important; list-style:none !important; }
      html.ecocheck-mobile.ecocheck-school-pay .logo_list li {
        box-sizing:border-box !important; flex:1 1 160px !important; float:none !important;
        width:auto !important; min-width:0 !important; max-width:100% !important; height:auto !important; margin:0 !important; }
      html.ecocheck-mobile.ecocheck-school-pay .logo_list a,
      html.ecocheck-mobile.ecocheck-school-pay .logo_list label,
      html.ecocheck-mobile.ecocheck-school-pay .bank-logo {
        box-sizing:border-box !important; display:block !important; max-width:100% !important;
        width:100% !important; min-height:44px !important; object-fit:contain !important; }
      html.ecocheck-mobile.ecocheck-school-pay .logo_list img {
        display:block !important; max-width:100% !important; height:auto !important; object-fit:contain !important; }
      html.ecocheck-mobile.ecocheck-school-pay .banner-w,
      html.ecocheck-mobile.ecocheck-school-pay .banner02-w { display:none !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > thead,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > tbody,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > tfoot,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > tr,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > * > tr,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > tr > td,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > * > tr > td,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table > * > tr > th {
        display:block !important; box-sizing:border-box !important; width:100% !important;
        min-width:0 !important; max-width:100% !important; height:auto !important;
        padding:6px 0 !important; margin:0 !important; float:none !important;
        table-layout:auto !important; overflow:visible !important; white-space:normal !important;
        word-break:normal !important; overflow-wrap:normal !important; font-size:16px !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table colgroup { display:none !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]),
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table select,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-form-table textarea,
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-confirm-control {
        box-sizing:border-box !important; width:100% !important; max-width:100% !important;
        min-width:0 !important; min-height:44px !important; height:auto !important; font-size:16px !important; }
      html.ecocheck-mobile.ecocheck-school-pay #zhye { white-space:nowrap !important; word-break:keep-all !important; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-confirm-control {
        display:block !important; float:none !important; margin:8px 0 !important; padding:12px 16px !important;
        left:auto !important; right:auto !important; text-indent:0 !important; text-align:center !important;
        line-height:1.5 !important; white-space:normal !important; color:white !important; border-radius:8px; }
      html.ecocheck-mobile.ecocheck-school-pay .ecocheck-confirm-label {
        background:#0052d9 !important; text-decoration:none !important; }
      html.ecocheck-mobile.ecocheck-school-pay img.ecocheck-payment-qr,
      html.ecocheck-mobile.ecocheck-school-pay canvas.ecocheck-payment-qr {
        display:block !important; width:min(78vw,300px) !important; max-width:100% !important;
        height:auto !important; margin:16px auto !important; image-rendering:pixelated; }
    `;
    document.head.appendChild(style);
    function fitParents(el) {
        for (var parent = el; parent && parent !== document.body; parent = parent.parentElement) {
            if (['DIV', 'FORM', 'SECTION', 'MAIN'].includes(parent.tagName)) {
                parent.classList.add('ecocheck-shell', 'ecocheck-checkout-shell');
            }
        }
    }
    function fit() {
        // Slider geometry belongs to the official widget; do not resize authentication nodes.
        if (location.hostname !== 'pay.ntu.edu.cn') return;
        document.querySelectorAll('.top, .header-w, .logo-w, .content-w, .cont-fr, .detail, .payment-w, .subnav, .tit-name')
            .forEach(function (el) { el.classList.add('ecocheck-shell'); });
        // Later checkout pages may contain only logos, hidden fields and a confirmation
        // control, so they cannot be detected through the electricity form's selects.
        document.querySelectorAll('.payment-w, .mode-pay, .logo_list, .button-w').forEach(fitParents);
        document.querySelectorAll('.button-w input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]), .button-w button, .button-w a')
            .forEach(function (control) {
                control.classList.add('ecocheck-confirm-control');
                var label = control.tagName === 'INPUT' ? control.value : control.textContent;
                control.classList.toggle('ecocheck-confirm-label', !!label.trim());
                // Keep image-only official labels; never replace a value or click handler.
                fitParents(control.parentElement);
            });
        document.querySelectorAll('table').forEach(function (table) {
            if (table.querySelector('select, input:not([type="hidden"]), textarea, #zhye, .button-w, .logo_list') || table.closest('.payment-w, .mode-pay')) {
                table.classList.add('ecocheck-form-table');
                for (var parent = table.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
                    if (['DIV', 'FORM', 'SECTION', 'MAIN'].includes(parent.tagName)) parent.classList.add('ecocheck-shell');
                }
            }
        });
        document.querySelectorAll('img, canvas').forEach(function (el) {
            var hint = [el.id, el.className, el.getAttribute('src'), el.getAttribute('alt'),
                el.parentElement && el.parentElement.id].join(' ');
            if (el.closest('.logo_list, .bank-logo') && !/qrcode|qr.?code|二维码/i.test(hint)) return;
            if (/qrcode|qr.?code|二维码|wxpay|weixinpay|wechatpay/i.test(hint)) {
                el.classList.add('ecocheck-payment-qr');
                for (var parent = el.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
                    if (['DIV', 'SECTION', 'MAIN'].includes(parent.tagName)) parent.classList.add('ecocheck-shell');
                }
            }
        });
    }
    fit();
    var pending;
    new MutationObserver(function () {
        clearTimeout(pending);
        pending = setTimeout(fit, 60);
    }).observe(document.body, {childList:true, subtree:true});
    return true;
}
ecoCheckMobileLayout();
