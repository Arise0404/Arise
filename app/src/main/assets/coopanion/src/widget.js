/**
 * 把大肥鱼从页面里剥出来的悬浮组件：给它一个容器元素就能跑，不依赖 pet.html、不依赖 Electron。
 *
 *   import { createPetWidget } from './src/widget.js';
 *   const pet = await createPetWidget(document.getElementById('overlay'), { scheme: 'qwen-cape' });
 *   pet.say('今天也要好好休息哦'); pet.do('nod'); pet.wear('claude-crew'); pet.destroy();
 *
 * 容器只要有尺寸、在文档流里就行，组件不动宿主页面的样式。
 * opts：scheme 配色-服装、pack 贴图目录（默认取本模块旁边的 ../whale/，与宿主页面路径无关）、
 * scale 大小、roam 自由活动（'free'/'off'）、sfx sound.js 的 createSfx() 结果、onEvent 事件回调、
 * enter 入场（'drop' 从天上掉下来 / 缺省直接站着）、floor 地面高度、skin 形象包自定义打扮、
 * onBox(box) 身体框变化时回调（页面坐标，最快 150ms 一次）——悬浮窗用它把窗口对齐到身体。
 */
import { createBody } from '../kit/body.js';
import { createWhaleFigure } from '../whale/figure.js';

const CSS = `
.wp-root{position:absolute;inset:0;overflow:visible;touch-action:none;-webkit-user-select:none;user-select:none}
.wp-bubble{position:absolute;z-index:2;max-width:min(260px,70%);padding:8px 12px;border-radius:14px;
  background:#fff;color:#1d2340;border:1px solid #d9deec;box-shadow:0 6px 20px rgba(20,30,70,.12);
  transform:translate(-50%,-100%);pointer-events:none;white-space:pre-wrap;font:14px/1.4 system-ui,"PingFang SC",sans-serif}
.wp-bubble[hidden]{display:none}
`;
const TYPED_MS = 70;      // 打字每字间隔
const TAIL_MS = 1800;     // 打完停留多久再收起

export async function createPetWidget(target, opts = {}) {
  if (!target?.appendChild) throw new TypeError('createPetWidget 需要一个容器元素');
  const doc = target.ownerDocument;
  const pack = opts.pack ?? new URL('../whale/', import.meta.url).href;

  const style = doc.createElement('style');
  style.textContent = CSS;
  doc.head.appendChild(style);

  const root = doc.createElement('div');
  root.className = 'wp-root';
  const bubble = doc.createElement('div');
  bubble.className = 'wp-bubble';
  bubble.hidden = true;
  root.append(bubble);
  target.appendChild(root);

  const figure = await createWhaleFigure(pack, { scheme: opts.scheme });
  const bounds = () => {
    const r = root.getBoundingClientRect();
    return { W: r.width, H: r.height, floorY: r.height - (opts.floor ?? 22), S: .42 * (opts.scale ?? 1) * Math.min(1.4, r.height / 420) };
  };
  const sound = (name, kind, ...args) => {
    const s = opts.sfx;
    if (!s) return;
    if (s.play) s.play(name, kind, ...args); else s[name]?.(...args);
  };
  const body = createBody(
    { root, bounds, emit: opts.onEvent ?? (() => {}), sound, start: { enter: opts.enter, skin: opts.skin } },
    { figure, roam: opts.roam ?? 'free' },
  );

  // 指针：按下即捕获，移动和抬起都转给身体，拎起、甩出、戳它的判定都在 kit 里
  const point = e => { const r = root.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
  /** 转发给身体的唯一入口：type 取 down/move/up/cancel/leave，坐标是容器内 CSS 像素。 */
  const inject = (type, at) => (type === 'leave' ? body.pointer('leave') : body.pointer(type, at));
  const down = e => { inject('down', point(e)); root.setPointerCapture?.(e.pointerId); };
  const move = e => inject('move', point(e));
  const up = e => { inject('up', point(e)); root.releasePointerCapture?.(e.pointerId); };
  const leave = () => inject('leave');
  root.addEventListener('pointerdown', down);
  root.addEventListener('pointermove', move);
  root.addEventListener('pointerup', up);
  root.addEventListener('pointercancel', up);
  root.addEventListener('pointerleave', leave);

  const ro = new ResizeObserver(() => body.resize());
  ro.observe(root);

  let line = '', shown = 0, nextAt = 0, frames = 0, last = performance.now(), raf = 0, alive = true;
  let boxAt = 0, boxPrev = null;
  const onBox = opts.onBox;
  function boxOf(l = body.layout()) {
    const r = root.getBoundingClientRect();
    return { x: r.left + l.box.x, y: r.top + l.box.y, w: l.box.w, h: l.box.h };
  }
  /** 悬浮窗要对齐身体，但每帧叫一次安卓太贵：最快 150ms 一次，且身体没动就不叫。 */
  function reportBox(now) {
    if (!onBox || now - boxAt < 150) return;
    const b = boxOf();
    const moved = !boxPrev || Math.abs(b.x - boxPrev.x) + Math.abs(b.y - boxPrev.y) + Math.abs(b.w - boxPrev.w) + Math.abs(b.h - boxPrev.h) > .5;
    boxAt = now;
    if (!moved) return;
    boxPrev = b;
    onBox(b);
  }
  function type(now) {
    if (!line || now < nextAt) return;
    if (shown < line.length) {
      shown++;
      bubble.textContent = line.slice(0, shown);
      bubble.hidden = false;
      body.talk();
      opts.sfx?.babble?.(line[shown - 1]);
      nextAt = now + TYPED_MS;
    } else if (now > nextAt + TAIL_MS) {
      line = '';
      bubble.hidden = true;
    }
  }
  function frame(now) {
    if (!alive) return;
    raf = requestAnimationFrame(frame);
    const dt = Math.min(.05, (now - last) / 1000);
    last = now;
    type(now);
    body.step(dt);
    const l = body.layout();
    if (!bubble.hidden) { bubble.style.left = `${l.bubble.x}px`; bubble.style.top = `${l.bubble.y}px`; }
    root.style.cursor = l.cursor || '';
    reportBox(now);
    frames++;
  }
  raf = requestAnimationFrame(frame);

  return {
    /** 逐字打进气泡，口型跟着；说完自动收起 */
    say(text) { line = String(text ?? ''); shown = 0; nextAt = 0; bubble.hidden = !line; body.set({ hold: 6 }); },
    do(word) { return body.do(word); },
    /**
     * 外部注入指针，悬浮窗用：绘制层设成不吃触摸，另开一个贴身体的小窗收触摸，
     * 把触摸点的坐标换算到绘制层坐标系后从这里进来。坐标是容器内 CSS 像素。
     */
    pointer(type, at) { inject(type, at); },
    cue(kind) { body.cue(kind); },
    async wear(schemeOutfit) { await body.setScheme(schemeOutfit, { fade: .45 }); },
    set(patch) { body.set(patch); },
    place(x, facing) { body.place(x, facing); },
    /** 词表：这个身体认得的表情和动作 */
    get words() { return body.words; },
    /** 气泡锚点在页面坐标里的位置 */
    anchor() { const r = root.getBoundingClientRect(), b = body.layout().bubble; return { x: r.left + b.x, y: r.top + b.y }; },
    stats() {
      const l = body.layout();
      return { frames, mode: l.mode, moving: l.moving, scheme: figure.scheme, x: l.x, facing: l.facing, box: boxOf(l) };
    },
    inner: body,
    destroy() {
      alive = false;
      cancelAnimationFrame(raf);
      ro.disconnect();
      root.removeEventListener('pointerdown', down);
      root.removeEventListener('pointermove', move);
      root.removeEventListener('pointerup', up);
      root.removeEventListener('pointercancel', up);
      root.removeEventListener('pointerleave', leave);
      body.dispose();       // 关掉 WebGL 上下文、摘掉 svg 和 kit 注入的两张 style
      root.remove();
      style.remove();
    },
  };
}
