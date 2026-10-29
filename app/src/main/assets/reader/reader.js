/*
 * Okur okuma motoru (WebView içinde çalışır).
 * - CSS sütunlarıyla sayfalama, parmağı takip eden akıcı sayfa çevirme
 * - Kaydırma modu, bölüm geçişleri, içi arama, sesli okuma için metin çıkarma
 * Yerel tarafla iletişim window.OkurBridge üzerinden yapılır.
 */
(function () {
  'use strict';
  var B = window.OkurBridge;
  if (!B || window.Okur) return;

  var cfg = {};
  try { cfg = JSON.parse(B.getConfig()); } catch (e) { cfg = {}; }

  var root = document.documentElement;
  root.classList.add('okur-loading');

  function scroller() { return document.scrollingElement || document.documentElement; }
  function headOrRoot() { return document.head || root; }

  var themeStyle = document.createElement('style');
  themeStyle.id = 'okur-theme';
  themeStyle.textContent = cfg.css || '';
  headOrRoot().appendChild(themeStyle);

  var layoutStyle = document.createElement('style');
  layoutStyle.id = 'okur-layout';
  headOrRoot().appendChild(layoutStyle);

  var W = 0, H = 0, page = 0, count = 1, ready = false, anim = null;

  function paged() { return cfg.mode === 'paged'; }
  function turnStyle() { return cfg.turn || 'slide'; }
  var chromeVisible = !!cfg.chrome;

  function applyLayout() {
    W = window.innerWidth;
    H = window.innerHeight;
    var m = cfg.margin || 20, v = cfg.vmargin || 24;
    var css;
    if (paged()) {
      var cols = (cfg.dual && W >= 840) ? 2 : 1;
      css =
        'html{height:' + H + 'px !important;width:' + W + 'px !important;overflow:hidden !important;' +
        'margin:0 !important;padding:0 !important;scroll-behavior:auto !important;}' +
        'body{height:' + H + 'px !important;width:' + W + 'px !important;margin:0 !important;' +
        'box-sizing:border-box !important;max-width:none !important;min-height:0 !important;' +
        'padding:' + v + 'px ' + m + 'px ' + (v + 16) + 'px ' + m + 'px !important;' +
        'column-count:' + cols + ' !important;column-width:auto !important;column-gap:' + (2 * m) + 'px !important;' +
        'column-fill:auto !important;overflow:visible !important;}' +
        'img,svg,video{max-height:' + Math.max(80, H - 2 * v - 24) + 'px !important;}';
    } else {
      css =
        'html{overflow-x:hidden !important;scroll-behavior:auto !important;}' +
        'body{margin:0 !important;max-width:none !important;box-sizing:border-box !important;' +
        'padding:' + (v + 44) + 'px ' + m + 'px ' + (v + 56) + 'px ' + m + 'px !important;}' +
        'img,svg,video{max-height:' + Math.round(H * 0.9) + 'px !important;}';
    }
    layoutStyle.textContent = css;
  }

  function measure() {
    root.style.removeProperty('width');
    if (paged()) {
      var sw = scroller().scrollWidth;
      count = Math.max(1, Math.ceil((sw - 2) / W));
      // Yarım dolu son sayfada tarayıcının kaydırmayı kırpmaması için genişliği tam sayfalara tamamla
      root.style.setProperty('width', (count * W) + 'px', 'important');
    } else {
      count = 1;
    }
  }

  function report() {
    if (!ready) return;
    if (paged()) B.onPage(page, count);
    else B.onProgress(scrollFraction());
  }

  function scrollFraction() {
    var s = scroller();
    var max = s.scrollHeight - H;
    return max <= 0 ? 1 : Math.max(0, Math.min(1, s.scrollTop / max));
  }

  function cancelAnim() { if (anim) { cancelAnimationFrame(anim); anim = null; } }

  function animateLeft(target, done) {
    cancelAnim();
    var s = scroller();
    var start = s.scrollLeft, delta = target - start;
    if (Math.abs(delta) < 1) { s.scrollLeft = target; if (done) done(); return; }
    var duration = Math.min(340, 160 + Math.abs(delta) / Math.max(1, W) * 140);
    var t0 = null;
    function step(ts) {
      if (t0 === null) t0 = ts;
      var p = Math.min(1, (ts - t0) / duration);
      var e = 1 - Math.pow(1 - p, 3);
      s.scrollLeft = start + delta * e;
      if (p < 1) anim = requestAnimationFrame(step);
      else { anim = null; s.scrollLeft = target; if (done) done(); }
    }
    anim = requestAnimationFrame(step);
  }

  function animateTop(target) {
    cancelAnim();
    var s = scroller();
    var start = s.scrollTop, delta = target - start, t0 = null;
    function step(ts) {
      if (t0 === null) t0 = ts;
      var p = Math.min(1, (ts - t0) / 260);
      s.scrollTop = start + delta * (1 - Math.pow(1 - p, 3));
      if (p < 1) anim = requestAnimationFrame(step); else anim = null;
    }
    anim = requestAnimationFrame(step);
  }

  function goTo(p, animate) {
    p = Math.max(0, Math.min(count - 1, p));
    page = p;
    if (animate) animateLeft(p * W);
    else { cancelAnim(); scroller().scrollLeft = p * W; }
    report();
  }

  function goToFraction(f) {
    if (paged()) {
      goTo(f >= 1 ? count - 1 : Math.round(f * count), false);
    } else {
      var s = scroller();
      s.scrollTop = f >= 1 ? s.scrollHeight : f * Math.max(0, s.scrollHeight - H);
      report();
    }
  }

  function currentFraction() {
    return paged() ? (count > 0 ? page / count : 0) : scrollFraction();
  }

  // ---- Görünür metin düğümleri ----
  function textWalker() {
    return document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        return n.nodeValue && n.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });
  }

  function isVisible(rc) {
    if (rc.width === 0 && rc.height === 0) return false;
    return paged() ? (rc.right > 1 && rc.left < W - 1) : (rc.bottom > 1 && rc.top < H - 1);
  }

  function firstVisibleNode() {
    if (!document.body) return null;
    var w = textWalker(), n, r = document.createRange();
    while ((n = w.nextNode())) {
      r.selectNodeContents(n);
      var rects = r.getClientRects();
      for (var i = 0; i < rects.length; i++) if (isVisible(rects[i])) return n;
    }
    return null;
  }

  function positionOfNode(n) {
    var r = document.createRange();
    r.selectNodeContents(n);
    var rects = r.getClientRects();
    var rc = rects.length ? rects[0] : r.getBoundingClientRect();
    return paged() ? Math.floor((rc.left + scroller().scrollLeft + 1) / W) : rc.top + scroller().scrollTop;
  }

  /** Yeniden sayfalar; okunan metin düğümünü ekranda tutar. Çapa, yerleşim değişmeden ÖNCE alınmalıdır. */
  function relayout(preAnchor) {
    var anchor = preAnchor !== undefined ? preAnchor : (ready ? firstVisibleNode() : null);
    var f = currentFraction();
    applyLayout();
    measure();
    if (anchor) {
      if (paged()) goTo(positionOfNode(anchor), false);
      else { scroller().scrollTop = Math.max(0, positionOfNode(anchor) - 8); report(); }
    } else {
      goToFraction(f);
    }
    if (ready && paged()) B.onReady(page, count);
  }

  function goToAnchor(id) {
    var el = document.getElementById(id) || document.getElementsByName(id)[0];
    if (!el) return false;
    var rc = el.getBoundingClientRect();
    if (paged()) goTo(Math.floor((rc.left + scroller().scrollLeft + 1) / W), false);
    else { scroller().scrollTop += rc.top - 16; report(); }
    return true;
  }

  // ---- Gezinme ----
  function turnWithin(dir) {
    var style = turnStyle();
    if (style === 'curl') B.curlAuto(dir);
    else goTo(page + dir, style === 'slide');
  }

  function next() {
    if (paged()) {
      if (page < count - 1) turnWithin(1);
      else { goTo(page, false); B.next(); }
    } else {
      var s = scroller();
      if (s.scrollTop + H >= s.scrollHeight - 4) B.next();
      else animateTop(s.scrollTop + H * 0.88);
    }
  }

  function prev() {
    if (paged()) {
      if (page > 0) turnWithin(-1);
      else { goTo(0, false); B.prev(); }
    } else {
      var s = scroller();
      if (s.scrollTop <= 4) B.prev();
      else animateTop(Math.max(0, s.scrollTop - H * 0.88));
    }
  }

  // ---- Dokunma ----
  var sx = 0, sy = 0, st = 0, moved = false, dragging = false, baseLeft = 0, multi = false;
  var maxMove = 0, curlDir = 0, curlTick = false, lastDx = 0;

  function hasSelection() {
    var sel = window.getSelection();
    return sel && !sel.isCollapsed;
  }

  function isInteractive(t) {
    return t && t.closest && t.closest('a[href], button, input, select, textarea, audio, video, .okur-chapter-nav');
  }

  document.addEventListener('touchstart', function (e) {
    multi = e.touches.length > 1;
    if (multi) return;
    var t = e.touches[0];
    sx = t.clientX; sy = t.clientY; st = Date.now();
    moved = false; dragging = false; maxMove = 0; curlDir = 0; lastDx = 0;
    cancelAnim();
    baseLeft = scroller().scrollLeft;
  }, { passive: true });

  document.addEventListener('touchmove', function (e) {
    if (multi || e.touches.length > 1) return;
    var t = e.touches[0];
    var dx = t.clientX - sx, dy = t.clientY - sy;
    maxMove = Math.max(maxMove, Math.abs(dx), Math.abs(dy));
    lastDx = dx;
    if (!moved && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
      moved = true;
      dragging = paged() && Math.abs(dx) > Math.abs(dy) && !hasSelection();
      if (dragging && turnStyle() === 'curl') {
        var dir = dx < 0 ? 1 : -1;
        curlDir = (dir > 0 ? page < count - 1 : page > 0) ? dir : 0;
        if (curlDir) B.curlStart(curlDir);
      }
    }
    if (dragging && turnStyle() !== 'slide') {
      // Kıvrılma: çizim yerel tarafta; animasyonsuz modda içerik sabit kalır
      e.preventDefault();
      if (curlDir && !curlTick) {
        curlTick = true;
        requestAnimationFrame(function () { curlTick = false; B.curlMove(lastDx / W); });
      }
    } else if (dragging) {
      e.preventDefault();
      var x = baseLeft - dx;
      var maxX = (count - 1) * W;
      if (x < 0) x = x / 3; else if (x > maxX) x = maxX + (x - maxX) / 3;
      scroller().scrollLeft = x;
    } else if (paged() && moved && !hasSelection()) {
      e.preventDefault();
    }
  }, { passive: false });

  document.addEventListener('touchend', function (e) {
    if (multi) { if (e.touches.length === 0) multi = false; return; }
    var t = e.changedTouches[0];
    var dx = t.clientX - sx, dt = Math.max(1, Date.now() - st);
    if (dragging) {
      dragging = false;
      var fast = Math.abs(dx) / dt > 0.35;
      var forward = dx < -W * 0.18 || (fast && dx < -24);
      var backward = dx > W * 0.18 || (fast && dx > 24);
      if (curlDir) {
        B.curlEnd(curlDir > 0 ? forward : backward);
        curlDir = 0;
        return;
      }
      if (turnStyle() === 'slide') {
        if (forward) next(); else if (backward) prev(); else goTo(page, true);
      } else {
        // Bölüm sınırı veya animasyonsuz mod
        if (forward) { if (page < count - 1) goTo(page + 1, false); else B.next(); }
        else if (backward) { if (page > 0) goTo(page - 1, false); else B.prev(); }
      }
      return;
    }
    // Dokunma: küçük titremelere tolerans
    if (maxMove > 18 || dt > 500) return;
    if (hasSelection()) { window.getSelection().removeAllRanges(); return; }
    if (isInteractive(e.target)) return;
    if (chromeVisible) { B.onTap('center'); return; }
    var zone = t.clientX / W;
    if (cfg.tapZones && zone < 0.25) prev();
    else if (cfg.tapZones && zone > 0.75) next();
    else B.onTap('center');
  }, { passive: true });

  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a) return;
    e.preventDefault();
    B.onLink(a.href);
  }, true);

  var scrollTick = false;
  window.addEventListener('scroll', function () {
    if (paged() || !ready || scrollTick) return;
    scrollTick = true;
    requestAnimationFrame(function () { scrollTick = false; report(); });
  }, { passive: true });

  var resizeTimer = null;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (window.innerWidth !== W || window.innerHeight !== H) relayout();
    }, 120);
  });

  // ---- Arama ----
  function clearSearch() {
    var marks = document.querySelectorAll('mark.okur-hl');
    for (var i = 0; i < marks.length; i++) {
      var m = marks[i], p = m.parentNode;
      while (m.firstChild) p.insertBefore(m.firstChild, m);
      p.removeChild(m);
      p.normalize();
    }
  }

  function highlight(n, i, len) {
    var r = document.createRange();
    r.setStart(n, i);
    r.setEnd(n, Math.min(n.nodeValue.length, i + len));
    var m = document.createElement('mark');
    m.className = 'okur-hl';
    try { r.surroundContents(m); } catch (err) { return; }
    var rc = m.getBoundingClientRect();
    if (paged()) goTo(Math.floor((rc.left + scroller().scrollLeft + 1) / W), false);
    else { scroller().scrollTop += rc.top - H / 3; report(); }
  }

  function search(q, nth) {
    clearSearch();
    if (!q || !document.body) return false;
    var lq = q.toLocaleLowerCase('tr');
    var w = textWalker(), n, k = 0, last = null;
    while ((n = w.nextNode())) {
      var text = n.nodeValue.toLocaleLowerCase('tr');
      var idx = text.indexOf(lq);
      while (idx >= 0) {
        if (k === nth) { highlight(n, idx, q.length); return true; }
        last = { n: n, i: idx };
        k++;
        idx = text.indexOf(lq, idx + lq.length);
      }
    }
    if (last) { highlight(last.n, last.i, q.length); return true; }
    return false;
  }

  // ---- Sesli okuma ve yer imi için metin ----
  function textFromHere(maxLen) {
    var start = firstVisibleNode();
    if (!start) return '';
    var w = textWalker();
    w.currentNode = start;
    var parts = [start.nodeValue], len = start.nodeValue.length, n;
    var lastBlock = blockOf(start);
    while ((n = w.nextNode()) && len < (maxLen || 200000)) {
      var b = blockOf(n);
      parts.push(b !== lastBlock ? '\n' + n.nodeValue : n.nodeValue);
      lastBlock = b;
      len += n.nodeValue.length;
    }
    return parts.join('').replace(/[ \t]+/g, ' ');
  }

  function blockOf(n) {
    var el = n.parentNode;
    while (el && el !== document.body) {
      var d = window.getComputedStyle(el).display;
      if (d !== 'inline' && d !== 'inline-block') return el;
      el = el.parentNode;
    }
    return document.body;
  }

  function visibleSnippet() {
    var n = firstVisibleNode();
    if (!n) return '';
    var w = textWalker();
    w.currentNode = n;
    var s = n.nodeValue, m;
    while (s.length < 140 && (m = w.nextNode())) s += ' ' + m.nodeValue;
    return s.replace(/\s+/g, ' ').trim().substring(0, 140);
  }

  /** Sesli okumada okunan paragrafı ekranda tutar. */
  function ensureVisible(snippet) {
    if (!snippet || !document.body) return;
    var key = snippet.replace(/\s+/g, ' ').trim().substring(0, 40);
    if (!key) return;
    var start = firstVisibleNode();
    var w = textWalker(), n;
    if (start) w.currentNode = start;
    var node = start;
    var tries = 0;
    while (node && tries < 4000) {
      if (node.nodeValue.replace(/\s+/g, ' ').indexOf(key.substring(0, Math.min(key.length, node.nodeValue.length))) >= 0) {
        var r = document.createRange();
        r.selectNodeContents(node);
        var rects = r.getClientRects();
        var visible = false;
        for (var i = 0; i < rects.length; i++) if (isVisible(rects[i])) { visible = true; break; }
        if (!visible) {
          if (paged()) goTo(positionOfNode(node), true);
          else animateTop(Math.max(0, positionOfNode(node) - H / 4));
        }
        return;
      }
      node = w.nextNode();
      tries++;
    }
  }

  // ---- Bölüm gezinme düğmeleri (kaydırma modu) ----
  function addChapterNav() {
    if (paged() || !document.body) return;
    var old = document.querySelectorAll('.okur-chapter-nav');
    for (var i = 0; i < old.length; i++) old[i].parentNode.removeChild(old[i]);
    function make(label, fn, atTop) {
      var d = document.createElement('div');
      d.className = 'okur-chapter-nav';
      var b = document.createElement('button');
      b.textContent = label;
      b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); fn(); });
      d.appendChild(b);
      if (atTop) document.body.insertBefore(d, document.body.firstChild); else document.body.appendChild(d);
    }
    if (cfg.hasPrev) make('‹ Önceki bölüm', function () { B.prev(); }, true);
    if (cfg.hasNext) make('Sonraki bölüm ›', function () { B.next(); }, false);
  }

  function reveal() {
    root.classList.remove('okur-loading');
  }

  function init() {
    try {
      if (cfg.lang && !root.getAttribute('lang')) root.setAttribute('lang', cfg.lang);
      addChapterNav();
      applyLayout();
      measure();
      ready = true;
      var placed = false;
      if (cfg.search && cfg.search.query) placed = search(cfg.search.query, cfg.search.nth || 0);
      if (!placed && cfg.anchor) placed = goToAnchor(cfg.anchor);
      if (!placed) goToFraction(cfg.atEnd ? 1 : (cfg.fraction || 0));
      B.onReady(page, count);
      report();
    } finally {
      reveal();
    }
  }

  var initialized = false;
  function start() {
    if (initialized) return;
    initialized = true;
    var go = function () { requestAnimationFrame(init); };
    if (document.fonts && document.fonts.ready) {
      var done = false;
      document.fonts.ready.then(function () { if (!done) { done = true; go(); } });
      setTimeout(function () { if (!done) { done = true; go(); } }, 400);
    } else {
      go();
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();

  window.addEventListener('load', function () { if (ready) relayout(); });

  window.Okur = {
    next: next,
    prev: prev,
    goToFraction: function (f) { goToFraction(f); },
    goToAnchor: goToAnchor,
    jumpBy: function (d) { goTo(page + d, false); },
    setChrome: function (v) { chromeVisible = !!v; },
    setTheme: function (css) {
      var anchor = ready ? firstVisibleNode() : null;
      themeStyle.textContent = css;
      relayout(anchor);
    },
    setConfig: function (json) {
      var c = JSON.parse(json);
      var modeChanged = c.mode && c.mode !== cfg.mode;
      var f = currentFraction();
      var anchor = ready ? firstVisibleNode() : null;
      for (var k in c) cfg[k] = c[k];
      if (modeChanged) {
        scroller().scrollLeft = 0; scroller().scrollTop = 0;
        addChapterNav();
        applyLayout(); measure(); goToFraction(f);
        B.onReady(page, count);
      } else {
        relayout(anchor);
      }
    },
    search: search,
    clearSearch: clearSearch,
    textFromHere: textFromHere,
    visibleSnippet: visibleSnippet,
    ensureVisible: ensureVisible,
    fraction: currentFraction
  };
})();
