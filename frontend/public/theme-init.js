// Apply the saved or system theme before first paint to avoid a flash of the wrong theme.
// A classic (blocking) script loaded from the same origin, so the Content-Security-Policy needs no inline scripts.
(function () {
  try {
    var pref = localStorage.getItem('campuspark.theme');
    var dark =
      pref === 'dark' ||
      ((pref === null || pref === 'system') && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) document.documentElement.classList.add('dark');
  } catch (e) {
    /* storage unavailable: keep the light theme */
  }
})();
