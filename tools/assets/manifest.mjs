// Student Hub UI 素材清单：每条由 tools/generate-assets.mjs 通过 gpt-image-2 生成。
// file 相对于小程序根 student-hub-weapp/；post 描述生成后的本地后处理。

const STYLE =
  'flat vector illustration, modern minimal style, soft rounded shapes, smooth subtle gradients, ' +
  'vibrant friendly energetic mood, indigo blue (#2563EB) and teal (#14B8A6) as dominant colors with ' +
  'soft sky-blue and warm cream accents, clean balanced composition, high quality, ' +
  'no text, no letters, no numbers, no watermark, no signature';

const ICON_TAIL =
  'centered composition with comfortable padding around the subject, isolated on a fully transparent background';

const MASCOT =
  'a cute round mascot character: chubby rounded blob with a smooth indigo-to-teal gradient body, ' +
  'a small antenna with a glowing star tip on top of its head, big friendly sparkling eyes, tiny happy smile, ' +
  'small stubby arms, wearing a tiny dark navy graduation cap';

const A = (file, prompt, opts = {}) => ({
  file,
  prompt: `${prompt}, ${STYLE}${opts.noTail ? '' : ', ' + ICON_TAIL}`,
  size: opts.size || '1024x1024',
  transparent: opts.transparent !== false,
  post: opts.post || {},
});

export const ASSETS = [
  // ── 品牌与头图 ────────────────────────────────────────────────
  A('assets/brand/logo-mark.png',
    'app logo mark: a friendly graduation cap merged with a rounded chat bubble, bold simple geometric shapes, two-tone indigo blue and teal', { post: { resize: 240 } }),
  A('assets/banner/login-hero.png',
    `${MASCOT} joyfully welcoming new students in front of a modern campus: rounded minimalist university buildings, floating books and sparkles, students walking, cheerful sky`,
    { size: '1536x1024', transparent: false, noTail: true, post: { resize: 900, format: 'jpeg', quality: 72 } }),
  A('assets/banner/home-banner.png',
    `${MASCOT} standing at a rounded campus service desk helping tiny students, around them floating icons: wrench, document, certificate scroll and speech bubbles, confetti and sparkles in the air`,
    { size: '1536x1024', transparent: false, noTail: true, post: { resize: 900, format: 'jpeg', quality: 72 } }),
  A('assets/banner/profile-header.png',
    'abstract flowing gradient waves and soft rounded blobs, scattered tiny doodles of books, stars, planels and speech bubbles, very subtle and airy, leaving generous empty space in the upper area, suitable as a decorative header backdrop',
    { size: '1536x1024', transparent: false, noTail: true, post: { resize: 750, format: 'jpeg', quality: 55 } }),

  // ── AI 吉祥物 ────────────────────────────────────────────────
  A('assets/mascot/mascot-main.png',
    `${MASCOT}, full body, waving hello with one raised arm, energetic jumping pose, tiny sparkles around`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/mascot/mascot-avatar.png',
    `circular avatar portrait, close-up face of ${MASCOT}, cheerful expression, solid soft teal circular background behind the face`,
    { post: { resize: 320, format: 'jpeg', quality: 68 } }),
  A('assets/mascot/mascot-thinking.png',
    `${MASCOT}, thoughtful pose with one stubby arm on its chin, looking up, small floating question-mark-shaped bubbles and a slowly rotating gear above its head`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/mascot/mascot-celebrate.png',
    `${MASCOT}, arms raised high in celebration, confetti and star-shaped fireworks bursting around, delighted expression with closed happy eyes`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),

  // ── 头像框 ───────────────────────────────────────────────────
  A('assets/frame/avatar-frame-glow.png',
    'decorative circular avatar frame: a glowing gradient ring from indigo blue to teal, decorated with tiny stars and sparkles on the ring, empty transparent circle in the middle, ring only, no face inside', { post: { resize: 240 } }),
  A('assets/frame/avatar-frame-campus.png',
    'decorative circular avatar frame: a bold rounded ring with tiny campus elements attached around it — a miniature book, a leaf, a star and a lightning bolt, empty transparent circle in the middle, ring and charms only, no face inside', { post: { resize: 240 } }),

  // ── TabBar 图标（81x81，灰/蓝两态）───────────────────────────
  A('assets/tab/tab-home.png',
    'simple rounded house icon with a small graduation-cap-shaped roof tip, solid muted gray-blue (#94A3B8), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-home-active.png',
    'simple rounded house icon with a small graduation-cap-shaped roof tip, solid vibrant indigo blue (#2563EB), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-service.png',
    'simple rounded four-squared grid icon with one rounded square replaced by a sparkle, solid muted gray-blue (#94A3B8), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-service-active.png',
    'simple rounded four-squared grid icon with one rounded square replaced by a sparkle, solid vibrant indigo blue (#2563EB), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-assistant.png',
    'simple rounded chat bubble icon with a small four-pointed star inside, solid muted gray-blue (#94A3B8), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-assistant-active.png',
    'simple rounded chat bubble icon with a small four-pointed star inside, solid vibrant indigo blue (#2563EB), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-profile.png',
    'simple rounded person bust icon, solid muted gray-blue (#94A3B8), bold strokes, minimal geometric',
    { post: { resize: 81 } }),
  A('assets/tab/tab-profile-active.png',
    'simple rounded person bust icon, solid vibrant indigo blue (#2563EB), bold strokes, minimal geometric',
    { post: { resize: 81 } }),

  // ── 服务分类图标（彩色贴纸风）────────────────────────────────
  A('assets/category/cat-repair.png',
    'sticker-style icon of a crossed wrench and screwdriver in front of a tiny rounded dormitory building, coral orange and indigo accents', { post: { resize: 112 } }),
  A('assets/category/cat-affair.png',
    'sticker-style icon of a rounded clipboard with a big checkmark and a tiny pen, teal and indigo accents', { post: { resize: 112 } }),
  A('assets/category/cat-certificate.png',
    'sticker-style icon of a certificate scroll with a wax seal and ribbon, warm gold and indigo accents', { post: { resize: 112 } }),
  A('assets/category/cat-leave.png',
    'sticker-style icon of a rounded calendar page with a small clock, sky blue and teal accents', { post: { resize: 112 } }),
  A('assets/category/cat-payment.png',
    'sticker-style icon of a rounded wallet with a coin and a tiny receipt, warm gold and teal accents', { post: { resize: 112 } }),
  A('assets/category/cat-consult.png',
    'sticker-style icon of two overlapping rounded chat bubbles, one with a question mark shape, indigo and coral accents', { post: { resize: 112 } }),
  A('assets/category/cat-guide.png',
    'sticker-style icon of a rounded location pin on a folded mini map with a small flag, teal and indigo accents', { post: { resize: 112 } }),
  A('assets/category/cat-activity.png',
    'sticker-style icon of a rounded megaphone with sound waves and a tiny star, coral and indigo accents',
    { post: { resize: 112 } }),

  // ── 身份标识图标 ─────────────────────────────────────────────
  A('assets/identity/icon-student.png',
    'cute round badge sticker of a happy student character wearing a graduation cap and holding a book, bold flat vector, indigo blue and teal palette',
    { post: { resize: 144 } }),
  A('assets/identity/icon-teacher.png',
    'cute round badge sticker of a friendly teacher character with round glasses holding a pointer at a chalkboard, bold flat vector, teal and warm gold palette',
    { post: { resize: 144 } }),
  A('assets/identity/icon-logistics.png',
    'cute round badge sticker of a friendly campus maintenance worker character with a safety helmet and wrench, bold flat vector, coral orange and indigo palette',
    { post: { resize: 144 } }),

  // ── 空状态与庆祝 ─────────────────────────────────────────────
  A('assets/empty/empty-requests.png',
    `${MASCOT} sitting on a tiny rounded empty box, relaxed pose, one or two small floating document sheets nearby, calm atmosphere`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/empty/empty-notifications.png',
    `${MASCOT} sleeping curled up next to a tiny rounded mailbox, floating star-shaped sleep bubbles above its head, cozy night-soft colors`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/empty/empty-search.png',
    `${MASCOT} holding a big magnifying glass and looking through it with a puzzled but hopeful face, tiny floating sparkles`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/empty/error-network.png',
    `${MASCOT} holding a disconnected rounded power plug, a small storm cloud floating above, embarrassed but cute expression`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
  A('assets/success/success-submit.png',
    `${MASCOT} happily launching a paper airplane that leaves a rounded dotted trail with a big soft checkmark badge in the background, celebratory sparkles`,
    { post: { resize: 384, format: 'jpeg', quality: 68 } }),
];
