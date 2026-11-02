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
        if (curl.root && curl.root.contains(n)) return NodeFilter.FILTER_REJECT;
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
    curlInvalidate();
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

  // ---- Sayfa kıvrılma (gerçekçi çevirme) ----
  // Sayfanın bir kopyası sabit bir katmanda tutulur; çevrilen sayfa bu kopyadan kırpılarak çizilir,
  // katlanan kanat ve gölgeler CSS ile verilir. Yerel ekran görüntüsüne ihtiyaç duymaz.
  var curl = { root: null, page: null, inner: null, flap: null, shadow: null, active: false, dir: 0, from: 0, p: 0, raf: null, auto: false };

  function curlInvalidate() {
    if (curl.active) curlFinishNow();
    if (curl.root && curl.root.parentNode) curl.root.parentNode.removeChild(curl.root);
    curl.root = null;
  }

  function curlBuild() {
    var bg = window.getComputedStyle(root).backgroundColor || '#fff';
    var bs = window.getComputedStyle(document.body);
    var r = document.createElement('div');
    r.id = 'okur-curl';
    var pg = document.createElement('div');
    pg.className = 'okur-cp';
    var inner = document.createElement('div');
    inner.className = 'okur-ci';
    for (var c = document.body.firstChild; c; c = c.nextSibling) {
      if (c === r) continue;
      inner.appendChild(c.cloneNode(true));
    }
    inner.style.cssText =
      'position:absolute;left:0;top:0;margin:0;box-sizing:border-box;width:' + W + 'px;height:' + H + 'px;' +
      'padding:' + bs.paddingTop + ' ' + bs.paddingRight + ' ' + bs.paddingBottom + ' ' + bs.paddingLeft + ';' +
      'column-count:' + bs.columnCount + ';column-gap:' + bs.columnGap + ';column-fill:auto;';
    pg.appendChild(inner);
    var fl = document.createElement('div'); fl.className = 'okur-cf';
    var sh = document.createElement('div'); sh.className = 'okur-cs';
    r.appendChild(sh); r.appendChild(pg); r.appendChild(fl);
    var st = document.getElementById('okur-curl-style') || document.createElement('style');
    st.id = 'okur-curl-style';
    st.textContent =
      '#okur-curl{position:fixed !important;left:0;top:0;width:' + W + 'px;height:' + H + 'px;z-index:2147483646;' +
      'pointer-events:none;visibility:hidden;overflow:hidden;background:transparent !important;}' +
      '#okur-curl .okur-cp{position:absolute !important;left:0;top:0;width:' + W + 'px;height:' + H + 'px;overflow:hidden;' +
      'background-color:' + bg + ' !important;will-change:clip-path;}' +
      '#okur-curl .okur-ci{will-change:transform;}' +
      '#okur-curl .okur-cf{position:absolute !important;top:0;height:' + H + 'px;' +
      'background-color:' + bg + ' !important;' +
      'background-image:linear-gradient(to right,rgba(255,255,255,.22),rgba(255,255,255,0) 22%,rgba(0,0,0,0) 62%,rgba(0,0,0,.20)) !important;' +
      'box-shadow:-4px 0 14px rgba(0,0,0,.22);will-change:transform,width;}' +
      '#okur-curl .okur-cs{position:absolute !important;top:0;height:' + H + 'px;width:40px;' +
      'background-image:linear-gradient(to right,rgba(0,0,0,.30),rgba(0,0,0,0)) !important;will-change:transform,opacity;}';
    headOrRoot().appendChild(st);
    document.body.appendChild(r);
    curl.root = r; curl.page = pg; curl.inner = inner; curl.flap = fl; curl.shadow = sh;
  }

  /** p: 0 = çevrilen sayfa tamamen ekranda, 1 = tamamen çevrilmiş */
  function curlDraw(p) {
    p = Math.max(0, Math.min(1, p));
    curl.p = p;
    var fold = W * (1 - p);
    var flapLeft = 2 * fold - W;
    curl.page.style.clipPath = 'inset(0 ' + (W - fold) + 'px 0 0)';
    curl.flap.style.transform = 'translateX(' + Math.max(flapLeft, -W) + 'px)';
    curl.flap.style.width = Math.max(0, fold - Math.max(flapLeft, -W)) + 'px';
    curl.flap.style.display = p > 0.002 && p < 0.998 ? 'block' : 'none';
    var sw = 34 * (1 - p * 0.5) + 6;
    curl.shadow.style.width = sw + 'px';
    curl.shadow.style.transform = 'translateX(' + fold + 'px)';
    curl.shadow.style.opacity = String(1 - p * 0.6);
  }

  function curlStart(dir) {
    if (curl.active) curlFinishNow();
    var target = page + dir;
    if (target < 0 || target > count - 1) return false;
    if (!curl.root) curlBuild();
    curl.active = true;
    curl.dir = dir;
    curl.from = page;
    // İleri: kopya mevcut sayfayı gösterir, gerçek belge hemen sonraki sayfaya geçer.
    // Geri: kopya önceki sayfayı gösterir ve üstten kapanır; gerçek belge sonunda geçer.
    var shown = dir > 0 ? page : target;
    curl.inner.style.transform = 'translateX(' + (-shown * W) + 'px)';
    curlDraw(dir > 0 ? 0 : 1);
    curl.root.style.visibility = 'visible';
    if (dir > 0) goTo(target, false);
    return true;
  }

  function curlDrag(dx) {
    if (!curl.active || curl.auto) return;
    var f = dx / W;
    curlDraw(curl.dir > 0 ? -f * 1.1 : 1 - f * 1.1);
  }

  function curlEnd(commit) {
    if (!curl.active) return;
    var dir = curl.dir;
    var target = dir > 0 ? (commit ? 1 : 0) : (commit ? 0 : 1);
    var start = curl.p, delta = target - start;
    var duration = 140 + 320 * Math.abs(delta);
    var t0 = null;
    if (curl.raf) cancelAnimationFrame(curl.raf);
    function step(ts) {
      if (t0 === null) t0 = ts;
      var k = Math.min(1, (ts - t0) / duration);
      curlDraw(start + delta * (1 - Math.pow(1 - k, 3)));
      if (k < 1) curl.raf = requestAnimationFrame(step);
      else { curl.raf = null; curlComplete(commit); }
    }
    curl.raf = requestAnimationFrame(step);
  }

  function curlComplete(commit) {
    var dir = curl.dir;
    if (dir > 0 && !commit) goTo(curl.from, false);
    if (dir < 0 && commit) goTo(curl.from - 1, false);
    curl.active = false;
    curl.auto = false;
    if (curl.root) curl.root.style.visibility = 'hidden';
  }

  function curlFinishNow() {
    if (curl.raf) { cancelAnimationFrame(curl.raf); curl.raf = null; }
    curlComplete(true);
  }

  // ---- Gezinme ----
  function turnWithin(dir) {
    var style = turnStyle();
    if (style === 'curl' && paged()) {
      if (curlStart(dir)) { curl.auto = true; curlEnd(true); }
    } else {
      goTo(page + dir, style === 'slide');
    }
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
        if (curlDir && !curlStart(curlDir)) curlDir = 0;
      }
    }
    if (dragging && turnStyle() !== 'slide') {
      // Kıvrılma: çizim yerel tarafta; animasyonsuz modda içerik sabit kalır
      e.preventDefault();
      if (curlDir && !curlTick) {
        curlTick = true;
        requestAnimationFrame(function () { curlTick = false; curlDrag(lastDx); });
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
        curlEnd(curlDir > 0 ? forward : backward);
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
    curlInvalidate();
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
    curlInvalidate();
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
  // Okunan metnin her parçasının hangi metin düğümüne denk geldiği tutulur; böylece TTS'in
  // bildirdiği karakter aralığı sayfada tam olarak işaretlenebilir. Bloklar U+2029 ile ayrılır.
  var ttsSegs = [];

  function textFromHere(maxLen) {
    ttsSegs = [];
    var start = firstVisibleNode();
    if (!start) return '';
    var w = textWalker();
    w.currentNode = start;
    var out = '', n = start, lastBlock = null, limit = maxLen || 200000;
    while (n && out.length < limit) {
      var b = blockOf(n);
      if (lastBlock && b !== lastBlock) out += '\u2029';
      lastBlock = b;
      ttsSegs.push({ n: n, s: out.length, e: out.length + n.nodeValue.length });
      out += n.nodeValue;
      n = w.nextNode();
    }
    return out;
  }

  function locate(off, isEnd) {
    var lo = 0, hi = ttsSegs.length - 1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1, g = ttsSegs[mid];
      if (off < g.s) hi = mid - 1;
      else if (off > g.e || (off === g.e && !isEnd)) lo = mid + 1;
      else return { n: g.n, o: off - g.s };
    }
    // Ayırıcıya denk geldiyse en yakın parçaya kaydır
    var g2 = isEnd ? ttsSegs[Math.max(0, lo - 1)] : ttsSegs[Math.min(ttsSegs.length - 1, lo)];
    if (!g2) return null;
    return { n: g2.n, o: isEnd ? g2.n.nodeValue.length : 0 };
  }

  var hlLayer = null;
  var hasHighlightApi = !!(window.CSS && CSS.highlights && window.Highlight);

  function ttsMark(start, end, kind) {
    if (!ttsSegs.length || end <= start) return;
    var a = locate(start, false), b = locate(end, true);
    if (!a || !b) return;
    var r = document.createRange();
    try { r.setStart(a.n, Math.min(a.o, a.n.nodeValue.length)); r.setEnd(b.n, Math.min(b.o, b.n.nodeValue.length)); } catch (e) { return; }
    if (hasHighlightApi) {
      CSS.highlights.set(kind === 'word' ? 'okur-word' : 'okur-sent', new Highlight(r));
    } else {
      drawRects(r, kind);
    }
    if (kind === 'word' || !hasWordEvents) followRange(r);
  }

  var hasWordEvents = false;

  function drawRects(r, kind) {
    if (!hlLayer) {
      hlLayer = document.createElement('div');
      hlLayer.id = 'okur-hl-layer';
      hlLayer.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;pointer-events:none;z-index:2147483645;';
      document.body.appendChild(hlLayer);
    }
    var old = hlLayer.querySelectorAll('.k-' + kind);
    for (var i = 0; i < old.length; i++) hlLayer.removeChild(old[i]);
    var rects = r.getClientRects();
    for (var j = 0; j < rects.length; j++) {
      var rc = rects[j], d = document.createElement('div');
      d.className = 'k-' + kind;
      d.style.cssText = 'position:fixed;border-radius:4px;left:' + rc.left + 'px;top:' + rc.top + 'px;width:' + rc.width + 'px;height:' + rc.height + 'px;' +
        'background:' + (kind === 'word' ? 'rgba(255,190,60,.55)' : 'rgba(255,190,60,.16)') + ';';
      hlLayer.appendChild(d);
    }
  }

  function followRange(r) {
    var rects = r.getClientRects();
    if (!rects.length) return;
    var rc = rects[0];
    if (paged()) {
      if (rc.left < 0 || rc.left >= W - 1) {
        var target = Math.floor((rc.left + scroller().scrollLeft + 1) / W);
        if (target === page + 1) turnWithin(1); else goTo(target, true);
        if (!hasHighlightApi && hlLayer) hlLayer.innerHTML = '';
      }
    } else if (rc.top < 0 || rc.bottom > H * 0.85) {
      animateTop(Math.max(0, scroller().scrollTop + rc.top - H * 0.3));
    }
  }

  function ttsClear() {
    if (hasHighlightApi) { CSS.highlights.delete('okur-word'); CSS.highlights.delete('okur-sent'); }
    if (hlLayer) hlLayer.innerHTML = '';
  }

  if (hasHighlightApi) {
    var hs = document.createElement('style');
    hs.textContent = '::highlight(okur-sent){background-color:rgba(255,190,60,.18);}' +
      '::highlight(okur-word){background-color:rgba(255,190,60,.62);}';
    headOrRoot().appendChild(hs);
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
    ttsMark: function (s, e, kind) { if (kind === 'word') hasWordEvents = true; ttsMark(s, e, kind); },
    ttsClear: ttsClear,
    fraction: currentFraction
  };
})();
