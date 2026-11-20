// Botón de cristal en 3D (three.js): transmisión, dispersión e iridiscencia reales sobre un fondo que se refracta.
// Cada <button class="glassbtn"> con un <canvas> dentro se monta solo. data-glass="dark|light", data-bead="1" añade la esfera,
// data-emblem="spark|shield" elige el emblema de la esfera. Si no hay WebGL se queda la versión CSS (clase .fallback).
import * as THREE from "three";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";

const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
const lerp = (a, b, t) => a + (b - a) * t;
const controllers = (window.glassCtl = {});

function backdropTexture(light) {
  const c = document.createElement("canvas");
  c.width = 1024; c.height = 512;
  const g = c.getContext("2d");
  if (light) {
    const bg = g.createLinearGradient(0, 0, 1024, 512);
    bg.addColorStop(0, "#eef0f5"); bg.addColorStop(1, "#c9cfdc");
    g.fillStyle = bg; g.fillRect(0, 0, 1024, 512);
  } else {
    g.fillStyle = "#07060d"; g.fillRect(0, 0, 1024, 512);
  }
  const blobs = light
    ? [[240, 150, 280, "rgba(255,255,255,0.95)"], [760, 380, 300, "rgba(135,150,180,0.55)"], [870, 110, 210, "rgba(200,170,255,0.4)"], [420, 430, 230, "rgba(140,200,255,0.4)"]]
    : [[200, 160, 280, "rgba(150,110,255,0.85)"], [520, 390, 300, "rgba(60,220,240,0.7)"], [830, 150, 250, "rgba(255,90,190,0.7)"], [910, 430, 210, "rgba(255,190,80,0.55)"]];
  for (const [x, y, r, col] of blobs) {
    const rg = g.createRadialGradient(x, y, 0, x, y, r);
    rg.addColorStop(0, col); rg.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = rg; g.fillRect(0, 0, 1024, 512);
  }
  // líneas y barras: sin detalles nítidos detrás, el cristal no tendría nada que torcer ni separar en colores
  for (let i = 0; i < 12; i++) {
    g.strokeStyle = light ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.3)";
    g.lineWidth = 2 + (i % 3) * 2;
    g.beginPath(); g.moveTo(i * 95 + 30, 0); g.lineTo(i * 95 - 150, 512); g.stroke();
  }
  if (light) {
    g.fillStyle = "rgba(80,92,120,0.35)";
    for (let i = 0; i < 5; i++) g.fillRect(40 + i * 210, 60 + (i % 2) * 260, 90, 180);
  }
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  return t;
}

function emblemTexture(kind) {
  const c = document.createElement("canvas");
  c.width = c.height = 256;
  const g = c.getContext("2d");
  const grad = g.createLinearGradient(30, 230, 226, 26);
  if (kind === "shield") { grad.addColorStop(0, "#b9a7ff"); grad.addColorStop(1, "#7fe7f2"); }
  else { grad.addColorStop(0, "#4f8dff"); grad.addColorStop(0.5, "#c46bff"); grad.addColorStop(1, "#ff9a56"); }
  if (kind === "shield") {
    g.strokeStyle = grad; g.lineWidth = 13; g.lineCap = g.lineJoin = "round";
    g.save(); g.translate(30, 26); g.scale(8.5, 8.5);
    g.lineWidth = 1.5;
    g.stroke(new Path2D("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z"));
    g.stroke(new Path2D("M12 7l3.5 1.5v3c0 2.6-1.5 4.3-3.5 5.4-2-1.1-3.5-2.8-3.5-5.4v-3z"));
    g.restore();
  } else {
    g.fillStyle = grad;
    g.beginPath();
    g.moveTo(128, 14); g.quadraticCurveTo(128, 128, 242, 128);
    g.quadraticCurveTo(128, 128, 128, 242); g.quadraticCurveTo(128, 128, 14, 128);
    g.quadraticCurveTo(128, 128, 128, 14); g.closePath(); g.fill();
  }
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

function mount(btn) {
  const canvas = btn.querySelector("canvas");
  const light = btn.dataset.glass === "light";
  const bead = btn.dataset.bead === "1";
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: "low-power" });
  } catch {
    btn.classList.add("fallback");
    return;
  }
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = light ? 1.0 : 1.1;

  const scene = new THREE.Scene();
  scene.environment = new THREE.PMREMGenerator(renderer).fromScene(new RoomEnvironment(), 0.04).texture;
  const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 60);

  const backTex = backdropTexture(light);
  const BACK_Z = light ? -1.4 : -1.8;
  const back = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: backTex, toneMapped: false }));
  back.position.z = BACK_Z;
  scene.add(back);

  const glassOpts = {
    transmission: 1, roughness: 0.03, ior: 1.5, dispersion: 7, iridescence: 1, iridescenceIOR: 1.3,
    iridescenceThicknessRange: [250, 700], clearcoat: 1, clearcoatRoughness: 0.03, envMapIntensity: 1.3,
    attenuationColor: new THREE.Color(light ? 0xdfe6ff : 0xb9a7ff), attenuationDistance: 2.5,
  };
  const bodyMat = new THREE.MeshPhysicalMaterial({ ...glassOpts, thickness: 1.2 });
  const rig = new THREE.Group();
  scene.add(rig);

  const LENGTH = 2.3, RADIUS = 0.55;
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(RADIUS, LENGTH, 24, 64), bodyMat);
  body.rotation.z = Math.PI / 2;
  body.scale.set(1, 1, light ? 0.38 : 0.62);
  rig.add(body);

  let beadMat, emblem;
  if (bead) {
    beadMat = new THREE.MeshPhysicalMaterial({ ...glassOpts, thickness: 2.2, ior: 1.6, dispersion: 10 });
    const sphere = new THREE.Mesh(new THREE.SphereGeometry(0.47, 64, 48), beadMat);
    sphere.position.x = -LENGTH / 2;
    rig.add(sphere);
    emblem = new THREE.Mesh(
      new THREE.PlaneGeometry(0.5, 0.5),
      new THREE.MeshBasicMaterial({ map: emblemTexture(btn.dataset.emblem || "spark"), transparent: true, toneMapped: false }),
    );
    emblem.position.set(-LENGTH / 2, 0, 0.05);
    rig.add(emblem);
  }

  const lights = [0x6fe8ff, 0xff7ad9, 0xffc46b].map(col => {
    const l = new THREE.PointLight(col, 26, 9, 2);
    scene.add(l);
    return l;
  });

  const base = light ? { x: 0.3, y: -0.16 } : { x: 0, y: 0 };
  const target = { x: 0, y: 0 }, now = { x: 0, y: 0 };
  let hover = 0, hoverTarget = 0, visible = true, raf = 0;
  const params = { dispersion: 7, thickness: 1.2, ior: 1.5, tilt: 1 };

  btn.addEventListener("pointermove", e => {
    const r = btn.getBoundingClientRect();
    target.y = ((e.clientX - r.left) / r.width - 0.5) * 0.7 * params.tilt;
    target.x = -((e.clientY - r.top) / r.height - 0.5) * 0.5 * params.tilt;
    hoverTarget = 1;
  });
  btn.addEventListener("pointerleave", () => { target.x = target.y = 0; hoverTarget = 0; });

  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    const fitWidth = (LENGTH + RADIUS * 2) * 1.34;
    const dist = fitWidth / (2 * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * camera.aspect);
    camera.position.set(0, 0, dist);
    camera.updateProjectionMatrix();
    const span = dist - BACK_Z;
    const planeH = 2 * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * span * 1.25;
    back.scale.set(planeH * camera.aspect, planeH, 1);
    draw(performance.now() / 1000);
  }

  function draw(t) {
    now.x = lerp(now.x, target.x, 0.08);
    now.y = lerp(now.y, target.y, 0.08);
    hover = lerp(hover, hoverTarget, 0.08);
    const idle = reduce ? 0 : 1;
    rig.rotation.x = base.x + now.x + Math.sin(t * 0.7) * 0.05 * idle;
    rig.rotation.y = base.y + now.y + Math.sin(t * 0.5 + 1) * 0.08 * idle;
    bodyMat.dispersion = params.dispersion + hover * 4;
    bodyMat.thickness = params.thickness;
    bodyMat.ior = params.ior;
    if (beadMat) { beadMat.dispersion = params.dispersion * 1.4 + hover * 4; beadMat.ior = params.ior + 0.1; }
    if (emblem) { emblem.position.x = -LENGTH / 2 + now.y * 0.25; emblem.position.y = now.x * -0.2; }
    backTex.offset.x = reduce ? 0 : (t * 0.018) % 1;
    lights.forEach((l, i) => {
      const a = (reduce ? 0 : t * 0.6) + (i * Math.PI * 2) / 3;
      l.position.set(Math.cos(a) * 2.8, Math.sin(a * 1.3) * 1.2, 2.2);
    });
    renderer.render(scene, camera);
  }

  function loop() {
    if (!visible || document.hidden) { raf = 0; return; }
    draw(performance.now() / 1000);
    raf = requestAnimationFrame(loop);
  }

  new ResizeObserver(resize).observe(canvas);
  new IntersectionObserver(([e]) => {
    visible = e.isIntersecting;
    if (visible && !raf && !reduce) raf = requestAnimationFrame(loop);
  }, { threshold: 0.05 }).observe(btn);
  document.addEventListener("visibilitychange", () => { if (!document.hidden && !raf && visible && !reduce) raf = requestAnimationFrame(loop); });
  resize();
  if (!reduce) raf = requestAnimationFrame(loop);

  if (btn.id) controllers[btn.id] = { set: p => Object.assign(params, p), redraw: () => draw(performance.now() / 1000) };
}

function mountAll(root = document) {
  root.querySelectorAll(".glassbtn:not([data-mounted])").forEach(b => { b.dataset.mounted = "1"; mount(b); });
}
window.mountGlass = mountAll;
addEventListener("pausa:gallery-ready", () => mountAll());
mountAll();
