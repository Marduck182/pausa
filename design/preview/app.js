// Vista previa de Pausa: comportamiento de las pantallas y panel para pedir cambios.
"use strict";

const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

/* ---------- panel para pedir cambios ---------- */
(() => {
  const panel = $("#panel"), select = $("#note-screen"), text = $("#note-text");
  const state = $("#note-state"), list = $("#notes"), count = $("#count");
  const fillScreens = () => {
    const keep = select.value;
    const screens = $$("[data-screen]").map(s => s.dataset.screen);
    select.innerHTML = ["General", ...screens].map(s => `<option>${s}</option>`).join("");
    if (screens.includes(keep)) select.value = keep;
  };
  fillScreens();
  window.refreshNoteScreens = fillScreens;

  const LOCAL_KEY = "pausa-preview-notes";
  let useServer = true;

  const render = notes => {
    count.textContent = `(${notes.length})`;
    list.innerHTML = "";
    notes.slice().reverse().forEach(n => {
      const li = document.createElement("li");
      const small = document.createElement("small");
      small.textContent = `${n.screen} · ${new Date(n.at).toLocaleString("es-MX")}`;
      li.append(small, document.createTextNode(n.note));
      list.appendChild(li);
    });
  };
  const loadLocal = () => { try { return JSON.parse(localStorage.getItem(LOCAL_KEY) || "[]"); } catch { return []; } };

  async function load() {
    try {
      const r = await fetch("/api/feedback", { cache: "no-store" });
      if (!r.ok) throw new Error();
      render(await r.json());
      state.textContent = "";
    } catch {
      useServer = false;
      render(loadLocal());
      state.textContent = "Sin servidor local: las notas se guardan solo en este navegador. Abre la página con servidor.py para que Claude las lea.";
    }
  }

  async function save() {
    const note = text.value.trim();
    if (!note) { state.textContent = "Escribe qué quieres cambiar."; text.focus(); return; }
    const entry = { screen: select.value, note, at: new Date().toISOString() };
    if (useServer) {
      try {
        const r = await fetch("/api/feedback", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(entry) });
        if (!r.ok) throw new Error(await r.text());
      } catch (err) { state.textContent = "No se pudo guardar. Revisa que el servidor siga abierto."; return; }
    } else {
      const all = loadLocal(); all.push(entry); localStorage.setItem(LOCAL_KEY, JSON.stringify(all));
    }
    text.value = "";
    state.textContent = "Guardado. Dime en el chat que lo revise.";
    await load();
    if (!useServer) state.textContent = "Guardado solo en este navegador.";
  }

  const open = screen => {
    if (screen) select.value = screen;
    panel.classList.add("open"); panel.setAttribute("aria-hidden", "false");
    text.focus();
  };
  const close = () => { panel.classList.remove("open"); panel.setAttribute("aria-hidden", "true"); };

  $("#open-panel").addEventListener("click", () => open());
  $("#close-panel").addEventListener("click", close);
  $("#note-save").addEventListener("click", save);
  document.addEventListener("keydown", e => { if (e.key === "Escape") close(); });
  $$(".ask").forEach(b => b.addEventListener("click", () => open(b.closest("[data-screen]").dataset.screen)));
  load();
})();


/* ---------- temas Claro y Oscuro ---------- */
const galleries = { light: $("#gallery-light"), dark: $("#gallery-dark") };
const loaded = {};
const DOCK = [["home", "Inicio"], ["apps", "Apps"], ["focus", "Enfoque"], ["chart", "Estadísticas"]];
const PILL = '<div class="pillstage" aria-hidden="true"><div class="pill"><svg viewBox="0 0 24 24"><path d="M12 19V5M6 11l6-6 6 6"/></svg></div></div>';
const NOTES = {
  light: "Claro: estudio plateado con esferas multicolor sobre un fondo de colores que se mueve despacio.",
  dark: "Oscuro: cápsulas de cristal sobre un fondo vivo; la cápsula 3D es la acción de cada pantalla.",
};

async function loadGallery(d) {
  if (loaded[d]) return;
  loaded[d] = true;
  const host = galleries[d];
  host.innerHTML = '<p class="load-note">Cargando…</p>';
  try {
    const r = await fetch(`screens-${d}.html`, { cache: "no-store" });
    if (!r.ok) throw new Error(r.status);
    host.innerHTML = await r.text();
  } catch {
    host.innerHTML = '<p class="load-note">No se pudo cargar este tema. Abre la página con servidor.py.</p>';
    loaded[d] = false;
    return;
  }
  $$("[data-dock]", host).forEach(nav => {
    const current = Number(nav.dataset.dock);
    nav.setAttribute("aria-label", "Principal");
    nav.innerHTML = DOCK.map(([icon, label], k) =>
      `<button class="tab"${k === current ? ' aria-current="page"' : ""}><svg class="i"><use href="#i-${icon}"/></svg>${label}</button>`).join("");
  });
  $$("[data-pillstage]", host).forEach(el => { el.outerHTML = PILL; });
  initGallery(host);
  window.refreshNoteScreens?.();
  dispatchEvent(new Event("pausa:gallery-ready"));
}

function applyDesign(d) {
  document.body.dataset.design = d;
  Object.entries(galleries).forEach(([k, el]) => { el.hidden = k !== d; });
  $$(".design-switch button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.d === d)));
  $("#design-note").textContent = NOTES[d];
  try { localStorage.setItem("pausa-preview-design", d); } catch {}
  history.replaceState(null, "", `#${d}`);
  loadGallery(d);
}

/* La cápsula de cristal que sube desde el dock, se estira con una flecha que brilla y se va. */
const pillRuns = new WeakMap();
function playPill(phone, done) {
  const pill = $(".pill", phone), arrow = $(".pill svg", phone);
  if (!pill) return;
  pillRuns.get(phone)?.forEach(a => a.cancel());
  const lift = 150;
  const out = "cubic-bezier(0.16, 1, 0.3, 1)";
  let runs;
  if (reduceMotion) {
    // Sin movimiento: ya formada, aparece y se desvanece.
    pill.style.height = "190px"; pill.style.width = "64px";
    arrow.style.opacity = 1;
    runs = [pill.animate([{ opacity: 0 }, { opacity: 1, offset: 0.3 }, { opacity: 1, offset: 0.7 }, { opacity: 0 }], { duration: 1600 })];
  } else {
    pill.style.removeProperty("height"); pill.style.removeProperty("width"); arrow.style.removeProperty("opacity");
    const lifted = `translate3d(0,${-lift}px,0)`;
    runs = [
      pill.animate([
        { offset: 0,    opacity: 0, width: "56px", height: "56px", transform: "translate3d(0,26px,0) scale(0.6)", filter: "blur(10px)", easing: out },
        { offset: 0.14, opacity: 1, width: "56px", height: "56px", transform: "translate3d(0,0,0) scale(1)", filter: "blur(0)", easing: out },
        { offset: 0.36, opacity: 1, width: "56px", height: "56px", transform: lifted, filter: "blur(0)", easing: out },
        { offset: 0.56, opacity: 1, width: "64px", height: "190px", transform: lifted, filter: "blur(0)", easing: "ease-in-out" },
        { offset: 0.74, opacity: 1, width: "64px", height: "190px", transform: `translate3d(5px,${-lift - 50}px,0)`, filter: "blur(0)", easing: "cubic-bezier(0.5, 0, 0.75, 0)" },
        { offset: 1,    opacity: 0, width: "60px", height: "180px", transform: `translate3d(-4px,${-lift - 330}px,0) scale(0.92)`, filter: "blur(12px)" },
      ], { duration: 4200 }),
      arrow.animate([
        { opacity: 0, transform: "translateY(10px)" },
        { opacity: 1, transform: "translateY(0)", offset: 0.35 },
        { opacity: 0.75, offset: 0.7 },
        { opacity: 1, offset: 1 },
      ], { duration: 1500, delay: 1750, fill: "both", easing: out }),
      arrow.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 500, delay: 3700, fill: "forwards" }),
    ];
  }
  pillRuns.set(phone, runs);
  runs[0].finished.then(() => { runs.forEach(a => a.cancel()); done?.(); }).catch(() => {});
}

/* Mantener pulsado: la cápsula (o el botón) se llena hasta completar el tiempo. */
function initHold(btn) {
  const HOLD_MS = 3000;
  const label = $(".gb-label", btn) || btn.firstElementChild;
  const original = label.innerHTML;
  let start = 0, raf = 0;
  const stop = () => { cancelAnimationFrame(raf); start = 0; btn.style.setProperty("--p", 0); };
  const tick = () => {
    const p = Math.min((performance.now() - start) / HOLD_MS, 1);
    btn.style.setProperty("--p", p);
    if (p >= 1) { label.innerHTML = "<b>Concentración terminada</b><small class='hint'>Vista previa: nada se guardó.</small>"; setTimeout(() => { label.innerHTML = original; stop(); }, 1800); return; }
    raf = requestAnimationFrame(tick);
  };
  const begin = () => { if (!start) { start = performance.now(); tick(); } };
  btn.addEventListener("pointerdown", e => { btn.setPointerCapture(e.pointerId); begin(); });
  ["pointerup", "pointercancel", "pointerleave"].forEach(ev => btn.addEventListener(ev, () => { if (start) stop(); }));
  btn.addEventListener("keydown", e => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); begin(); } });
  btn.addEventListener("keyup", () => { if (start) stop(); });
}

/* El fondo vivo dentro de la app: video, rejilla y cruces. El video solo corre mientras la pantalla se ve. */
const D_VIDEO = "https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260813_115057_94c3699b-0fd1-4124-bcf3-3626bb8c1f77.mp4";
function buildAppBackgrounds(host) {
  const plus = [["25%", "33.33%"], ["50%", "33.33%"], ["75%", "33.33%"], ["25%", "66.66%"], ["50%", "66.66%"], ["75%", "66.66%"]];
  $$("[data-appbg]", host).forEach(el => {
    el.outerHTML = `<div class="app-bg" aria-hidden="true"><video muted loop playsinline preload="metadata" src="${D_VIDEO}"></video><i class="scrim"></i><i class="grid"></i>${plus.map(([x, y]) => `<i class="plus" style="left:${x};top:${y}"></i>`).join("")}</div>`;
  });
  const videos = $$(".app-bg video", host);
  videos.forEach(v => v.addEventListener("error", () => v.remove()));
  if (reduceMotion) return;
  const io = new IntersectionObserver(entries => entries.forEach(e => { if (e.isIntersecting) e.target.play().catch(() => {}); else e.target.pause(); }), { threshold: 0.25 });
  videos.forEach(v => io.observe(v));
}

function initGallery(host) {
  buildAppBackgrounds(host);

  // duración: dial de cápsulas (oscuro)
  $$(".g-dial", host).forEach(group => group.addEventListener("click", e => {
    const chip = e.target.closest(".g-chip");
    if (!chip) return;
    $$(".g-chip", group).forEach(c => c.setAttribute("aria-pressed", String(c === chip)));
    $("[data-dur]", group.closest(".screen")).textContent = chip.dataset.v;
  }));
  // duración: menú de la flecha de la cápsula (claro)
  $$(".gb-chev[aria-controls]", host).forEach(chev => {
    const menu = $("#" + chev.getAttribute("aria-controls"), host);
    chev.addEventListener("click", () => {
      const open = menu.hidden;
      menu.hidden = !open;
      chev.setAttribute("aria-expanded", String(open));
    });
    menu.addEventListener("click", e => {
      const item = e.target.closest("button");
      if (!item) return;
      $$("button", menu).forEach(b => b.setAttribute("aria-pressed", String(b === item)));
      $("[data-dur]", chev.closest(".screen")).textContent = item.dataset.v;
      menu.hidden = true;
      chev.setAttribute("aria-expanded", "false");
    });
  });

  // interruptores
  host.addEventListener("click", e => {
    const hit = e.target.closest(".hit");
    const sw = hit && $(".switch", hit);
    if (sw) sw.setAttribute("aria-checked", String(sw.getAttribute("aria-checked") !== "true"));
  });
  host.addEventListener("keydown", e => {
    if (e.target.matches(".switch") && (e.key === " " || e.key === "Enter")) { e.preventDefault(); e.target.parentElement.click(); }
  });

  $$(".g-hold", host).forEach(initHold);

  // espera para pedir un pase
  $$("[data-pass]", host).forEach(btn => {
    const label = $("[data-pass-wait]", btn);
    let left = 5;
    const timer = setInterval(() => {
      left--;
      if (left <= 0) { clearInterval(timer); btn.disabled = false; label.textContent = ""; } else label.textContent = `(en ${left} s)`;
    }, 1000);
  });

  // pausa: respiración y cuenta atrás
  $$("[data-breath]", host).forEach(sphere => {
    const screen = sphere.closest(".screen");
    const word = $("[data-word]", sphere), left = $("[data-left]", sphere), go = $("[data-go]", screen), wait = $("[data-go-wait]", screen);
    let remaining = 5, inhale = true;
    const apply = () => { word.textContent = inhale ? "Inhala" : "Exhala"; sphere.style.setProperty("--s", inhale ? 1 : 0.86); };
    apply();
    setInterval(() => { inhale = !inhale; apply(); }, 4000);
    const t = setInterval(() => {
      remaining--;
      if (remaining <= 0) { clearInterval(t); left.textContent = "Ya puedes"; go.disabled = false; wait.textContent = ""; }
      else { left.textContent = `${remaining} s`; wait.textContent = `(en ${remaining} s)`; }
    }, 1000);
  });

  // bloqueo (oscuro): salir con la cápsula
  const leave = $("#gb-leave", host), block = $("#gblock", host);
  if (leave && block) leave.addEventListener("click", () => {
    const screen = $(".screen", block);
    screen.classList.add("dim");
    playPill(block, () => screen.classList.remove("dim"));
  });

  // animación de salida (oscuro): se repite mientras se ve
  const demo = $("#gpill-phone", host);
  if (demo) {
    $("[data-replay]", host).addEventListener("click", () => playPill(demo));
    let timer = 0;
    const loop = () => { playPill(demo); timer = setTimeout(loop, 7000); };
    new IntersectionObserver(([e]) => { clearTimeout(timer); if (e.isIntersecting) timer = setTimeout(loop, 600); }, { threshold: 0.6 }).observe(demo);
  }

  // estudio: controles del material en vivo
  $$("[data-ctl]", host).forEach(input => input.addEventListener("input", () => {
    const out = input.previousElementSibling?.querySelector("output");
    if (out) out.textContent = input.value;
    window.glassCtl?.[input.dataset.ctl]?.set({ [input.dataset.key]: Number(input.value) });
    window.glassCtl?.[input.dataset.ctl]?.redraw();
  }));

  // estadísticas (claro): esferas por app
  const bubbles = $(".c-bubbles", host), detail = $(".c-detail", host);
  if (bubbles && detail) {
    const data = [
      { n: "Brave", a: 41, b: 38, p: 3, s: "2 h 10 min" }, { n: "Clash Royale", a: 32, b: 32, p: 0, s: "2 h 40 min" },
      { n: "Shorts en YouTube", a: 14, b: 14, p: 0, s: "1 h 05 min" }, { n: "Instagram", a: 8, b: 3, p: 5, s: "40 min" },
    ];
    const total = data.reduce((n, x) => n + x.a, 0);
    const pick = i => {
      $$(".bead", bubbles).forEach((b, k) => b.setAttribute("aria-pressed", String(k === i)));
      const x = data[i];
      detail.innerHTML = `<div><b>${x.n}</b><small>${x.b} bloqueos${x.p ? ` y ${x.p} pausas` : ""}</small></div><div class="right"><b>${Math.round((x.a / total) * 100)}% de la semana</b><small>Unas ${x.s} sin gastar</small></div>`;
    };
    bubbles.addEventListener("click", e => { const b = e.target.closest(".bead"); if (b) pick(Number(b.dataset.i)); });
    pick(0);
  }
}

/* arranque: el tema de la dirección, el último elegido o el oscuro */
(() => {
  $$(".design-switch button").forEach(b => b.addEventListener("click", () => applyDesign(b.dataset.d)));
  let start = location.hash.slice(1);
  if (!NOTES[start]) { try { start = localStorage.getItem("pausa-preview-design"); } catch {} }
  applyDesign(NOTES[start] ? start : "dark");
})();
