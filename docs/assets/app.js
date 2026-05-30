/* Juml documentation site — shared behavior */
(function () {
  // --- scrollspy (sidebar TOC) ---
  var links = Array.prototype.slice.call(document.querySelectorAll('#toc a'));
  if (links.length) {
    var map = {};
    links.forEach(function (a) {
      var id = a.getAttribute('href');
      if (id && id.charAt(0) === '#') {
        var el = document.getElementById(id.slice(1));
        if (el) map[id.slice(1)] = a;
      }
    });
    var ids = Object.keys(map);
    var onScroll = function () {
      var pos = window.scrollY + 140, current = ids[0];
      for (var i = 0; i < ids.length; i++) {
        var el = document.getElementById(ids[i]);
        if (el && el.offsetTop <= pos) current = ids[i];
      }
      links.forEach(function (a) { a.classList.remove('active'); });
      if (map[current]) map[current].classList.add('active');
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // --- mobile sidebar toggle ---
  var sb = document.getElementById('sidebar');
  var bd = document.getElementById('backdrop');
  var btn = document.getElementById('menuBtn');
  function toggle(open) {
    if (!sb) return;
    sb.classList.toggle('open', open);
    if (bd) bd.classList.toggle('show', open);
  }
  if (btn) btn.addEventListener('click', function () { toggle(!sb.classList.contains('open')); });
  if (bd) bd.addEventListener('click', function () { toggle(false); });
  document.querySelectorAll('#toc a').forEach(function (a) {
    a.addEventListener('click', function () { if (window.innerWidth <= 880) toggle(false); });
  });

  // --- copy buttons on <pre> ---
  document.querySelectorAll('pre').forEach(function (pre) {
    var b = document.createElement('button');
    b.className = 'copy-btn'; b.type = 'button'; b.textContent = 'copy';
    b.addEventListener('click', function () {
      var code = pre.querySelector('code');
      var text = code ? code.innerText : pre.innerText;
      navigator.clipboard.writeText(text).then(function () {
        b.textContent = 'copied!'; setTimeout(function () { b.textContent = 'copy'; }, 1200);
      });
    });
    pre.appendChild(b);
  });

  // --- generic table filter (any input[data-filter="#tableId"]) ---
  document.querySelectorAll('input[data-filter]').forEach(function (input) {
    var table = document.querySelector(input.getAttribute('data-filter'));
    if (!table) return;
    var countTag = input.getAttribute('data-count') ? document.querySelector(input.getAttribute('data-count')) : null;
    var rows = Array.prototype.slice.call(table.querySelectorAll('tbody tr'));
    function refresh() {
      var q = input.value.trim().toLowerCase(), shown = 0;
      rows.forEach(function (r) {
        var hit = !q || r.textContent.toLowerCase().indexOf(q) !== -1;
        r.classList.toggle('hidden', !hit);
        if (hit) shown++;
      });
      if (countTag) countTag.textContent = shown + ' / ' + rows.length + ' 件';
    }
    input.addEventListener('input', refresh);
    refresh();
  });
})();
