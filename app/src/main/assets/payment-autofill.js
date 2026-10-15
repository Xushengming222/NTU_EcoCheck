/* Only opens the electricity project, logs in once, and selects the dormitory.
 * Amount, order creation, and payment controls are deliberately outside this flow. */
function ecoCheckPaymentStep(config) {
    'use strict';
    function reply(stage, message) { return {stage: stage, message: message, queryStep: state && state.queryStep || 0}; }
    function visible(el) { return !!el && el.getClientRects().length > 0; }
    function text(value) { return String(value || '').replace(/\s+/g, ''); }
    function fill(el, value) {
        el.value = value;
        el.dispatchEvent(new Event('input', {bubbles: true}));
        el.dispatchEvent(new Event('change', {bubbles: true}));
    }
    if (location.protocol !== 'https:') return reply('manual', '请使用 HTTPS 官方页面');
    var host = location.hostname;
    if (host !== 'pay.ntu.edu.cn' && host !== 'authserver.ntu.edu.cn') {
        return reply('manual', '请在官方页面继续操作');
    }
    var state = window.__ntuEcoPayment || (window.__ntuEcoPayment = {});

    function manualLogin(message) {
        state.manualLogin = true;
        state.manualMessage = message;
        return reply('manual_login', message);
    }

    function challengeVisible(form) {
        var captcha = form && form.querySelector('input[name="captcha"]');
        return visible(captcha) || window.needCaptcha === true || window.needCaptcha === 'true' ||
            Array.from(document.querySelectorAll('#sliderCaptchaDiv, .geetest_panel, .yidun_popup, #captcha_verify_container'))
                .some(function (el) { return visible(el) && (el.children.length > 0 || el.textContent.trim()); });
    }

    if (host === 'authserver.ntu.edu.cn') {
        state.queryStep = 1;
        var form = document.getElementById('pwdFromId');
        if (!visible(form)) {
            // The current site initially hides all forms and reveals one after JS initialization.
            state.authWaitAt = state.authWaitAt || Date.now();
            if (form && Date.now() - state.authWaitAt < 5000) {
                return reply('loading', '正在等待学校登录表单');
            }
            return manualLogin('请在官方页面选择账号登录');
        }
        var error = form.querySelector('#showErrorTip');
        if (visible(error) && error.textContent.trim()) {
            return manualLogin('登录未完成：' + error.textContent.trim().slice(0, 160) + '；自动提交已停止');
        }
        if (state.manualLogin) return reply('manual_login', state.manualMessage || '请手动完成验证并登录，应用不会再次自动提交');
        // An authorization is valid only in the document that requested it.
        if (config.authorizeLoginNonce && config.authorizeLoginNonce !== state.loginNonce) {
            return manualLogin('登录页面已变化，请手动完成登录');
        }
        if (!config.credentials) return manualLogin('请手动完成验证并登录，应用不会再次自动提交');
        // Scope selectors to the password form: the current page has duplicate IDs.
        var user = form.querySelector('input[name="username"]');
        var password = form.querySelector('input[type="password"]');
        var submit = form.querySelector('#login_submit');
        var action = new URL(form.getAttribute('action') || location.href, location.href);
        if (!user || !password || !submit || action.protocol !== 'https:' ||
                action.hostname !== 'authserver.ntu.edu.cn' || action.pathname !== '/authserver/login') {
            return manualLogin('登录页面已变化，请手动登录');
        }
        if (!state.loginFilledAt) {
            fill(user, config.credentials.studentId);
            fill(password, config.credentials.password);
            // The current official script checks captcha requirements on username focusout.
            state.loginFilledAt = Date.now();
            state.loginNonce = Date.now().toString(36) + Math.random().toString(36).slice(2);
            if (!challengeVisible(form)) user.dispatchEvent(new Event('focusout', {bubbles: true}));
        }
        if (challengeVisible(form)) return manualLogin('请手动完成滑块或验证码，自动提交已停止');
        if (Date.now() - state.loginFilledAt < 1500) {
            return reply('loading', '已填写账号，正在等待登录校验');
        }
        if (user.value !== config.credentials.studentId) return manualLogin('登录账号已变化，请手动登录');
        if (window.jQuery && window.jQuery.active > 0) return reply('loading', '正在等待登录校验');
        if (state.loginSubmitted) return manualLogin('已提交一次登录，请手动处理验证或错误提示');
        if (!config.authorizeLoginNonce) {
            var ready = reply('login_ready', '登录表单已就绪');
            ready.nonce = state.loginNonce;
            return ready;
        }
        state.loginSubmitted = true;
        submit.click(); // Native code reserves the one-time budget durably BEFORE this click.
        return reply('login_submitted', '正在登录，请留意官方页面的验证提示');
    }

    var campus = document.getElementById('schoolid');
    var building = document.getElementById('building');
    var room = document.getElementById('room');
    if (campus && building && room) {
        state.queryStep = Math.min(5 + (state.fieldIndex || 0), 7);
        function completedSelection() {
            if (!config.queryOnly) return reply('ready', '宿舍已填好，请核对后填写金额并付款');
            if (campus.value !== config.campusId || building.value !== config.buildingId || room.value !== config.roomId) {
                return reply('mismatch', '宿舍选择已变化，请重新查询');
            }
            // Match the original query's td#zhye, whose text may also contain a unit or label.
            // Unrelated background AJAX must not prevent reading a completed room result.
            var balance = document.querySelector('td#zhye') || document.getElementById('zhye');
            var raw = balance && balance.textContent.trim();
            if (raw && /错误|失败|超时|异常|error|fail|timeout/i.test(raw)) return reply('manual', '电量查询失败：' + raw.slice(0, 160));
            var match = raw && raw.match(/-?\d+(?:\.\d+)?/);
            if (!match) return reply('loading', '正在等待读取电量');
            var now = new Date();
            state.queryStep = 8;
            var result = reply('query_result', '查询完成');
            result.data = {campus:config.campusName, building:config.buildingName, room:config.roomName,
                electricity:match[0], date:now.getFullYear()+'-'+String(now.getMonth()+1).padStart(2,'0')+'-'+String(now.getDate()).padStart(2,'0'),
                time:String(now.getHours()).padStart(2,'0')+':'+String(now.getMinutes()).padStart(2,'0')+':'+String(now.getSeconds()).padStart(2,'0')};
            return result;
        }
        if (state.done) return completedSelection();
        var fields = [campus, building, room];
        var ids = [config.campusId, config.buildingId, config.roomId];
        var names = [config.campusName, config.buildingName, config.roomName];
        var labels = ['校区', '楼栋', '房间'];
        var index = state.fieldIndex || 0;
        if (state.changedAt && Date.now() - state.changedAt < 1000) {
            return reply('loading', '正在加载' + labels[Math.min(index, 2)]);
        }
        if (!config.queryOnly && window.jQuery && window.jQuery.active > 0) return reply('loading', '正在加载宿舍信息');
        // Recheck parents so a delayed response or manual change cannot select another dormitory.
        for (var previous = 0; previous < index; previous++) {
            if (fields[previous].value !== ids[previous]) {
                return reply('mismatch', '宿舍选择已变化，请手动核对并选择');
            }
        }
        if (index >= fields.length) {
            for (var check = 0; check < fields.length; check++) {
                var chosen = fields[check].options[fields[check].selectedIndex];
                if (!chosen || chosen.value !== ids[check] || text(chosen.text) !== text(names[check])) {
                    return reply('mismatch', '宿舍名称与查询配置不一致，请手动核对');
                }
            }
            state.done = true;
            return completedSelection();
        }
        var select = fields[index];
        var option = Array.from(select.options).find(function (o) { return o.value === ids[index]; });
        if (!option) {
            state.waitSince = state.waitSince || Date.now();
            if (Date.now() - state.waitSince > 15000) {
                return reply('mismatch', '找不到配置中的' + labels[index] + '，请手动选择');
            }
            return reply('loading', '正在等待' + labels[index] + '选项');
        }
        if (text(option.text) !== text(names[index])) {
            return reply('mismatch', labels[index] + '名称与查询配置不一致，请手动核对');
        }
        select.value = ids[index];
        state.fieldIndex = index + 1;
        state.changedAt = Date.now();
        state.waitSince = 0;
        select.dispatchEvent(new Event('change', {bubbles: true}));
        return reply('loading', '已选择' + labels[index] + '：' + names[index]);
    }

    var sso = document.querySelector('a[href^="/signAuthentication"]');
    if (sso && !state.openedSso) {
        state.queryStep = 1;
        state.openedSso = true;
        sso.click();
        return reply('loading', '正在打开学校统一身份认证');
    }
    var project = document.querySelector('a[onclick*="elecdetails5118E017"]');
    if (project && !state.openedProject) {
        state.queryStep = 4;
        state.openedProject = true;
        project.click();
        return reply('loading', config.queryOnly ? '正在进入宿舍电量查询' : '正在进入学生宿舍电费');
    }
    return reply('loading', config.queryOnly ? '正在等待学校电量查询页面' : '正在等待官方缴费页面');
}
