// OculiX Reporter — Phase 1 JS.
// Pure vanilla, no dep. Collapses tests by default, opens failed ones,
// enables lightbox zoom on screenshots, and sidebar navigation.
(function () {
  'use strict';

  // --- Collapse / expand tests ---
  document.querySelectorAll('.test-header').forEach(function (h) {
    h.addEventListener('click', function () {
      var card = h.closest('.test');
      card.classList.toggle('open');
    });
  });

  // Auto-open failed/error tests on load.
  document.querySelectorAll('.test').forEach(function (c) {
    var o = c.getAttribute('data-outcome');
    if (o === 'failed' || o === 'error') c.classList.add('open');
  });

  // --- Sidebar filter ---
  var navLinks = document.querySelectorAll('.sidebar-nav a[data-filter]');
  navLinks.forEach(function (a) {
    a.addEventListener('click', function (e) {
      e.preventDefault();
      var filter = a.getAttribute('data-filter');
      navLinks.forEach(function (l) { l.classList.remove('active'); });
      a.classList.add('active');
      document.querySelectorAll('.test').forEach(function (c) {
        if (filter === 'all' || c.getAttribute('data-outcome') === filter) {
          c.style.display = '';
        } else {
          c.style.display = 'none';
        }
      });
    });
  });

  // --- Lightbox for screenshots ---
  var lightbox = document.createElement('div');
  lightbox.className = 'lightbox';
  var lbImg = document.createElement('img');
  lightbox.appendChild(lbImg);
  document.body.appendChild(lightbox);
  lightbox.addEventListener('click', function () { lightbox.classList.remove('open'); });
  document.querySelectorAll('.step-shot img').forEach(function (i) {
    i.addEventListener('click', function () {
      lbImg.src = i.src;
      lightbox.classList.add('open');
    });
  });
})();
