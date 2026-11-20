// Vista previa de Pausa: comportamiento de las pantallas y panel para pedir cambios.
"use strict";

const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

/* ---------- chips de duración ---------- */
$$("[data-chips='focus']").forEach(group => {
  group.addEventListener("click", e => {
    const chip = e.target.closest(".chip");
    if (!chip) return;
    $$(".chip", group).forEach(c => c.setAttribute("aria-pressed", String(c === chip)));
    $("#start-focus span").textContent = `Empezar ${chip.dataset.v}`;
  });
});

/* ---------- interruptores ---------- */
$$(".switch").forEach(sw => {
  const flip = () => sw.setAttribute("aria-checked", String(sw.getAttribute("aria-checked") !== "true"));
  sw.parentElement.addEventListener("click", flip);
  sw.addEventListener("keydown", e => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); flip(); } });
});

/* ---------- periodo de estadísticas ---------- */
$$(".segmented").forEach(group => group.addEventListener("click", e => {
  const b = e.target.closest("button");
  if (b) $$("button", group).forEach(x => x.setAttribute("aria-pressed", String(x === b)));
}));

/* ---------- terminar antes: mantener pulsado ---------- */
(() => {
  const btn = $("#hold-end");
  const HOLD_MS = 3000;
  let start = 0, raf = 0;
  const label = btn.firstElementChild;
  const original = label.innerHTML;
  const tick = () => {
    const p = Math.min((performance.now() - start) / HOLD_MS, 1);
    btn.style.setProperty("--p", p);
    if (p >= 1) { done(); return; }
    raf = requestAnimationFrame(tick);
  };
  const stop = () => { cancelAnimationFrame(raf); start = 0; btn.style.setProperty("--p", 0); };
  const done = () => {
    label.innerHTML = "Concentración terminada<small class='hint'>Vista previa: nada se guardó.</small>";
    setTimeout(() => { label.innerHTML = original; stop(); }, 1800);
  };
  btn.addEventListener("pointerdown", e => { btn.setPointerCapture(e.pointerId); start = performance.now(); tick(); });
  ["pointerup", "pointercancel", "pointerleave"].forEach(ev => btn.addEventListener(ev, () => { if (start) stop(); }));
  // con teclado: Espacio mantenido
  btn.addEventListener("keydown", e => { if ((e.key === " " || e.key === "Enter") && !start) { e.preventDefault(); start = performance.now(); tick(); } });
  btn.addEventListener("keyup", () => { if (start) stop(); });
})();

/* ---------- estadísticas por app: anillo de puntos y carrusel ---------- */
(() => {
  const apps = [
    { name: "Brave", glyph: "B", bg: "oklch(0.68 0.19 45)", hue: 50, avoided: 41, blocked: 38, pauses: 3, saved: "2 h 10 min" },
    { name: "Clash Royale", glyph: "CR", bg: "linear-gradient(135deg,oklch(0.55 0.2 260),oklch(0.45 0.18 290))", hue: 265, avoided: 32, blocked: 32, pauses: 0, saved: "2 h 40 min" },
    { name: "Shorts en YouTube", glyph: "▶", bg: "oklch(0.55 0.23 27)", hue: 25, avoided: 14, blocked: 14, pauses: 0, saved: "1 h 05 min" },
    { name: "Instagram", glyph: "In", bg: "linear-gradient(135deg,oklch(0.6 0.22 340),oklch(0.62 0.2 25))", hue: 340, avoided: 8, blocked: 3, pauses: 5, saved: "40 min" },
  ];
  const total = apps.reduce((n, a) => n + a.avoided, 0);
  const rail = $("#rail"), pager = $("#pager"), detail = $("#detail");
  const svg = $("#dotring svg");
  const N = 150, CX = 125, CY = 125;

  // puntos de un anillo: determinista para que no cambie en cada carga
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const dots = [];
  for (let i = 0; i < N; i++) {
    const a = (i / N) * Math.PI * 2 - Math.PI / 2;
    const r = 96 + (rnd() - 0.5) * 22;
    const c = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    c.setAttribute("cx", (CX + Math.cos(a) * r).toFixed(1));
    c.setAttribute("cy", (CY + Math.sin(a) * r).toFixed(1));
    c.dataset.base = (1.1 + rnd() * 1.1).toFixed(2);
    svg.appendChild(c);
    dots.push(c);
  }

  function light(index) {
    const before = apps.slice(0, index).reduce((n, a) => n + a.avoided, 0);
    const from = Math.round((before / total) * N);
    const to = Math.round(((before + apps[index].avoided) / total) * N);
    const hue = apps[index].hue;
    dots.forEach((c, i) => {
      const on = i >= from && i < to;
      const base = parseFloat(c.dataset.base);
      c.setAttribute("r", on ? (base + 1.2).toFixed(2) : base.toFixed(2));
      c.setAttribute("fill", on ? `oklch(0.9 0.12 ${hue})` : "oklch(1 0 0 / 0.28)");
      c.style.filter = on ? `drop-shadow(0 0 3px oklch(0.8 0.17 ${hue}))` : "none";
    });
  }

  apps.forEach((a, i) => {
    const card = document.createElement("article");
    card.className = "appcard";
    card.style.setProperty("--h", a.hue);
    card.innerHTML = `<div class="who"><div class="appicon" style="background:${a.bg}">${a.glyph}</div>${a.name}</div>
      <div><strong>${a.avoided}</strong><span>aperturas evitadas</span></div>`;
    rail.appendChild(card);
    const p = document.createElement("i");
    pager.appendChild(p);
  });

  let current = -1;
  function select(i) {
    if (i === current) return;
    current = i;
    $$(".appcard", rail).forEach((c, k) => c.classList.toggle("on", k === i));
    $$("i", pager).forEach((p, k) => p.classList.toggle("on", k === i));
    const a = apps[i];
    const share = Math.round((a.avoided / total) * 100);
    detail.innerHTML = `<div><b>${a.name}</b><small>${a.blocked} bloqueos${a.pauses ? ` y ${a.pauses} pausas` : ""}</small></div>
      <div class="right"><b>${share}% de la semana</b><small>Unas ${a.saved} sin gastar</small></div>`;
    light(i);
  }
  const nearest = () => {
    const mid = rail.scrollLeft + rail.clientWidth / 2;
    let best = 0, dist = Infinity;
    $$(".appcard", rail).forEach((c, k) => {
      const d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid);
      if (d < dist) { dist = d; best = k; }
    });
    return best;
  };
  rail.addEventListener("scroll", () => select(nearest()), { passive: true });
  rail.addEventListener("click", e => {
    const card = e.target.closest(".appcard");
    if (card) card.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", inline: "center", block: "nearest" });
  });
  requestAnimationFrame(() => { rail.scrollLeft = 0; select(0); });
})();

/* ---------- bloqueo: espera para pedir un pase y variante clara ---------- */
(() => {
  const btn = $("#pass-btn"), label = $("#pass-wait");
  let left = 5;
  const timer = setInterval(() => {
    left--;
    if (left <= 0) { clearInterval(timer); btn.disabled = false; label.textContent = ""; }
    else label.textContent = `(en ${left} s)`;
  }, 1000);
  $("#theme-block").addEventListener("click", e => {
    const phone = $("#block-phone");
    const light = phone.dataset.theme !== "light";
    if (light) phone.dataset.theme = "light"; else delete phone.dataset.theme;
    e.target.textContent = light ? "Ver variante oscura" : "Ver variante clara";
  });
})();

/* ---------- pausa: respiración y cuenta atrás ---------- */
(() => {
  const orb = $("#breath-orb"), word = $("#breath-word"), left = $("#pause-left");
  const arc = $("#pause-arc"), go = $("#pause-go"), wait = $("#pause-wait");
  const TOTAL = 5, BREATH = 4000;
  let remaining = TOTAL, inhale = true;
  const apply = () => {
    word.textContent = inhale ? "Inhala" : "Exhala";
    orb.style.setProperty("--s", inhale ? 1 : 0.86);
  };
  apply();
  setTimeout(() => { orb.style.setProperty("--s", 1); }, 50);
  setInterval(() => { inhale = !inhale; apply(); }, BREATH);
  const t = setInterval(() => {
    remaining--;
    arc.setAttribute("stroke-dashoffset", String(653 * (1 - remaining / TOTAL)));
    if (remaining <= 0) { clearInterval(t); left.textContent = "Ya puedes continuar"; go.disabled = false; wait.textContent = ""; }
    else { left.textContent = `${remaining} s`; wait.textContent = `(en ${remaining} s)`; }
  }, 1000);
})();

/* ---------- patrón con el dedo ---------- */
(() => {
  const pad = $("#pad"), msg = $("#pad-msg");
  const NS = "http://www.w3.org/2000/svg";
  const pts = [];
  for (let i = 0; i < 9; i++) pts.push({ x: 40 + (i % 3) * 80, y: 40 + Math.floor(i / 3) * 80 });
  const lines = document.createElementNS(NS, "g");
  pad.appendChild(lines);
  const circles = pts.map(p => {
    const c = document.createElementNS(NS, "circle");
    c.setAttribute("cx", p.x); c.setAttribute("cy", p.y); c.setAttribute("r", 8); c.setAttribute("class", "d");
    pad.appendChild(c);
    return c;
  });
  let path = [], live = null, drawing = false;
  const local = e => {
    const r = pad.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * 240, y: ((e.clientY - r.top) / r.height) * 240 };
  };
  const hit = q => pts.findIndex(p => Math.hypot(p.x - q.x, p.y - q.y) < 26);
  const redraw = () => {
    lines.innerHTML = "";
    for (let i = 1; i < path.length; i++) {
      const l = document.createElementNS(NS, "line");
      l.setAttribute("x1", pts[path[i - 1]].x); l.setAttribute("y1", pts[path[i - 1]].y);
      l.setAttribute("x2", pts[path[i]].x); l.setAttribute("y2", pts[path[i]].y);
      lines.appendChild(l);
    }
    circles.forEach((c, i) => { const on = path.includes(i); c.classList.toggle("on", on); c.setAttribute("r", on ? 11 : 8); });
  };
  pad.addEventListener("pointerdown", e => { pad.setPointerCapture(e.pointerId); drawing = true; path = []; const i = hit(local(e)); if (i >= 0) path.push(i); redraw(); });
  pad.addEventListener("pointermove", e => { if (!drawing) return; const i = hit(local(e)); if (i >= 0 && !path.includes(i)) { path.push(i); redraw(); } });
  const end = () => {
    if (!drawing) return; drawing = false;
    msg.textContent = path.length >= 4 ? "Vista previa: aquí se comprobaría el patrón." : "Muy corto: une al menos 4 puntos.";
    setTimeout(() => { path = []; redraw(); }, 600);
  };
  pad.addEventListener("pointerup", end);
  pad.addEventListener("pointercancel", end);
})();

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


/* ---------- diseños A, B y C ---------- */
const galleries = { a: $("#gallery-a"), b: $("#gallery-b"), c: $("#gallery-c"), c2: $("#gallery-c2"), d: $("#gallery-d") };
const loaded = {};
const DOCK = [["home", "Inicio"], ["apps", "Apps"], ["focus", "Enfoque"], ["chart", "Estadísticas"]];
const PILL = '<div class="pillstage" aria-hidden="true"><div class="pill"><svg viewBox="0 0 24 24"><path d="M12 19V5M6 11l6-6 6 6"/></svg></div></div>';
const NOTES = {
  a: "Diseño A, Noche: superficies planas y anillos. Usa «Pedir cambio» en cualquier pantalla.",
  b: "Diseño B, Cristal oscuro: composiciones propias y un botón de cristal 3D con refracción prismática.",
  c: "Diseño C, Estudio claro: cápsula de cristal con esfera, inspirada en tu referencia.",
  c2: "Diseño C v2: la cápsula de cristal sobre los divs del D, con el fondo del D dentro de cada pantalla.",
  d: "Diseño D, Índice: negro puro, rejilla con cruces, nodos cuadrados y video de fondo.",
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
    host.innerHTML = '<p class="load-note">No se pudo cargar este diseño. Abre la página con servidor.py.</p>';
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
  initGallery(host, d);
  window.refreshNoteScreens?.();
  dispatchEvent(new Event("pausa:gallery-ready"));
}

/* Fondo del diseño D: video, rejilla de 1 px, cruces en las intersecciones, tres nodos cuadrados y sus conectores. */
const D_VIDEO = "https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260813_115057_94c3699b-0fd1-4124-bcf3-3626bb8c1f77.mp4";
function buildDBackground(on) {
  const bg = $("#d-bg");
  bg.hidden = !on;
  if (!on) { bg.innerHTML = ""; return; }
  const V = ["12.6%", "37.5%", "61.9%", "86.2%"], H = ["32.7%", "71.4%"];
  const line = (x1, y1, x2, y2, delay) => `<svg class="d-conn d-fade-in" style="animation-delay:${delay}ms"><line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="rgba(255,255,255,0.25)" stroke-width="1" vector-effect="non-scaling-stroke"/></svg>`;
  const label = (cls, pos, delay, title, text, w) => `<div class="d-lab ${cls}" style="${pos};animation-delay:${delay}ms"><span>${title}</span><p style="max-width:${w}px">${text}</p></div>`;
  const sq = (pos, delay) => `<div class="d-sq d-scale" style="${pos};animation-delay:${delay}ms"></div>`;
  let html = `<video class="d-fade-in" src="${D_VIDEO}" muted loop playsinline ${reduceMotion ? "" : "autoplay"} preload="metadata"></video><div class="scrim"></div>`;
  V.forEach((left, i) => { html += `<div class="d-vline d-grid-v" style="left:${left};animation-delay:${600 + i * 100}ms"></div>`; });
  H.forEach((top, i) => { html += `<div class="d-hline d-grid-h" style="top:${top};animation-delay:${800 + i * 150}ms"></div>`; });
  H.forEach((top, hi) => V.forEach((left, vi) => {
    html += `<div class="d-plus d-scale" style="top:${top};left:${left};animation-delay:${1000 + (hi * 4 + vi) * 80}ms"><i></i><i></i></div>`;
  }));
  html += `<div class="d-nodes">`
    + label("d-slide-l", "top:11%;left:26%", 1100, "[ APPS_BLOQUEADAS ]", "Tres apps con una regla activa ahora.", 160)
    + label("d-slide-l", "top:76%;left:3%", 1400, "[ PAUSA_CONSCIENTE ]", "Unos segundos de espera antes de abrir.", 160)
    + label("d-slide-r", "top:50%;left:78%", 1700, "[ SIN_INTERNET ]", "Todo ocurre en este teléfono, sin cuenta.", 180)
    + sq("top:27%;left:60%", 1500) + sq("top:58%;left:32%", 1800) + sq("top:63%;left:50%", 2100)
    + line("38%", "14%", "52%", "14%", 1200) + line("52%", "14%", "60%", "27%", 1400)
    + line("32%", "58%", "20%", "74%", 1500) + line("20%", "74%", "6%", "74%", 1700)
    + line("78%", "53%", "63%", "53%", 1800) + line("63%", "53%", "50%", "63%", 2000)
    + `</div>`;
  bg.innerHTML = html;
  $("video", bg)?.addEventListener("error", () => $("video", bg)?.remove());
}

function applyDesign(d) {
  document.body.dataset.design = d;
  Object.entries(galleries).forEach(([k, el]) => { el.hidden = k !== d; });
  $$(".design-switch button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.d === d)));
  $("#design-note").textContent = NOTES[d];
  try { localStorage.setItem("pausa-preview-design", d); } catch {}
  history.replaceState(null, "", `#${d}`);
  buildDBackground(d === "d");
  if (d !== "a") loadGallery(d);
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

function initHold(btn) {
  const HOLD_MS = 3000;
  const label = btn.firstElementChild;
  const original = label.innerHTML;
  let start = 0, raf = 0;
  const stop = () => { cancelAnimationFrame(raf); start = 0; btn.style.setProperty("--p", 0); };
  const tick = () => {
    const p = Math.min((performance.now() - start) / HOLD_MS, 1);
    btn.style.setProperty("--p", p);
    if (p >= 1) { label.innerHTML = "Concentración terminada<small class='hint'>Vista previa: nada se guardó.</small>"; setTimeout(() => { label.innerHTML = original; stop(); }, 1800); return; }
    raf = requestAnimationFrame(tick);
  };
  const begin = () => { if (!start) { start = performance.now(); tick(); } };
  btn.addEventListener("pointerdown", e => { btn.setPointerCapture(e.pointerId); begin(); });
  ["pointerup", "pointercancel", "pointerleave"].forEach(ev => btn.addEventListener(ev, () => { if (start) stop(); }));
  btn.addEventListener("keydown", e => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); begin(); } });
  btn.addEventListener("keyup", () => { if (start) stop(); });
}

/* El fondo del D dentro de la app: video, rejilla y cruces. El video solo corre mientras la pantalla se ve. */
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

function initGallery(host, d) {
  buildAppBackgrounds(host);
  // duración (B: dial; C: menú de la flecha)
  $$(".g-dial", host).forEach(group => group.addEventListener("click", e => {
    const chip = e.target.closest(".g-chip");
    if (!chip) return;
    $$(".g-chip", group).forEach(c => c.setAttribute("aria-pressed", String(c === chip)));
    $("[data-dur]", group.closest(".screen")).textContent = chip.dataset.v;
  }));
  const chev = $(".gb-chev[aria-controls]", host), menu = chev && $("#" + chev.getAttribute("aria-controls"), host);
  if (chev && menu) {
    chev.addEventListener("click", () => {
      const open = menu.hidden;
      menu.hidden = !open;
      chev.setAttribute("aria-expanded", String(open));
    });
    menu.addEventListener("click", e => {
      const item = e.target.closest("button");
      if (!item) return;
      $$("button", menu).forEach(b => b.setAttribute("aria-pressed", String(b === item)));
      $("[data-dur]", host).textContent = item.dataset.v;
      menu.hidden = true;
      chev.setAttribute("aria-expanded", "false");
    });
  }

  // interruptores
  host.addEventListener("click", e => {
    const hit = e.target.closest(".hit");
    const sw = hit && ($(".switch", hit) || $(".d-sw", hit));
    if (sw) sw.setAttribute("aria-checked", String(sw.getAttribute("aria-checked") !== "true"));
  });
  host.addEventListener("keydown", e => {
    if (e.target.matches(".switch") && (e.key === " " || e.key === "Enter")) { e.preventDefault(); e.target.click(); e.target.parentElement.click(); }
  });

  $$("[data-d-chips]", host).forEach(group => group.addEventListener("click", e => {
    const b = e.target.closest("button");
    if (!b) return;
    $$("button", group).forEach(x => x.setAttribute("aria-pressed", String(x === b)));
    $("[data-d-start]", group.closest(".screen")).textContent = `Empezar ${b.dataset.v}`;
  }));
  $$(".d-sw", host).forEach(sw => sw.addEventListener("keydown", e => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); sw.parentElement.click(); } }));
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

  // bloqueo (B): salir con la cápsula
  const leave = $("#gb-leave", host), block = $("#gblock", host);
  if (leave && block) leave.addEventListener("click", () => {
    const screen = $(".screen", block);
    screen.classList.add("dim");
    playPill(block, () => screen.classList.remove("dim"));
  });

  // animación de salida (B): se repite mientras se ve
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

  // estadísticas (C): esferas por app
  const bubbles = $("#c-bubbles", host);
  if (bubbles) {
    const data = [
      { n: "Brave", a: 41, b: 38, p: 3, s: "2 h 10 min" }, { n: "Clash Royale", a: 32, b: 32, p: 0, s: "2 h 40 min" },
      { n: "Shorts en YouTube", a: 14, b: 14, p: 0, s: "1 h 05 min" }, { n: "Instagram", a: 8, b: 3, p: 5, s: "40 min" },
    ];
    const total = data.reduce((n, x) => n + x.a, 0), detail = $("#c-detail", host);
    const pick = i => {
      $$(".bead", bubbles).forEach((b, k) => b.setAttribute("aria-pressed", String(k === i)));
      const x = data[i];
      detail.innerHTML = `<div><b>${x.n}</b><small>${x.b} bloqueos${x.p ? ` y ${x.p} pausas` : ""}</small></div><div class="right"><b>${Math.round((x.a / total) * 100)}% de la semana</b><small>Unas ${x.s} sin gastar</small></div>`;
    };
    bubbles.addEventListener("click", e => { const b = e.target.closest(".bead"); if (b) pick(Number(b.dataset.i)); });
    pick(0);
  }
}

/* arranque: el diseño guardado o el de la dirección */
(() => {
  $$(".design-switch button").forEach(b => b.addEventListener("click", () => applyDesign(b.dataset.d)));
  let start = location.hash.slice(1);
  if (!NOTES[start]) { try { start = localStorage.getItem("pausa-preview-design"); } catch {} }
  applyDesign(NOTES[start] ? start : "a");
})();
