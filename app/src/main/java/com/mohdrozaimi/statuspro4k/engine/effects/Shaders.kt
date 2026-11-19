package com.mohdrozaimi.statuspro4k.engine.effects

/** GLSL ES 1.00 shaders so they run on every Android 10+ GPU (ES2 or ES3 contexts). */
internal object Shaders {

    const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

    const val COPY = """
precision highp float;
uniform sampler2D uTexSampler;
varying vec2 vTexSamplingCoord;
void main() { gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord); }
"""

    /**
     * Edge-preserving noise reduction (bilateral, 13 taps) + natural colour grade:
     * shadow lift, highlight recovery, gentle S-curve contrast and skin-protecting vibrance.
     */
    const val COLOR_DENOISE = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uDenoise;
uniform float uContrast;
uniform float uVibrance;
uniform float uShadows;
uniform float uHighlights;
varying vec2 vTexSamplingCoord;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 tap(vec2 o) { return texture2D(uTexSampler, vTexSamplingCoord + o * uTexel).rgb; }

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = src.rgb;

  if (uDenoise > 0.001) {
    float sigma = mix(0.02, 0.11, uDenoise);
    float inv = 1.0 / (2.0 * sigma * sigma);
    vec3 acc = c;
    float wsum = 1.0;
    vec3 s; float d; float w;
    s = tap(vec2( 1.0, 0.0)); d = dot(s - c, s - c); w = 0.80 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2(-1.0, 0.0)); d = dot(s - c, s - c); w = 0.80 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 0.0, 1.0)); d = dot(s - c, s - c); w = 0.80 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 0.0,-1.0)); d = dot(s - c, s - c); w = 0.80 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 1.0, 1.0)); d = dot(s - c, s - c); w = 0.64 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2(-1.0, 1.0)); d = dot(s - c, s - c); w = 0.64 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 1.0,-1.0)); d = dot(s - c, s - c); w = 0.64 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2(-1.0,-1.0)); d = dot(s - c, s - c); w = 0.64 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 2.0, 0.0)); d = dot(s - c, s - c); w = 0.40 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2(-2.0, 0.0)); d = dot(s - c, s - c); w = 0.40 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 0.0, 2.0)); d = dot(s - c, s - c); w = 0.40 * exp(-d * inv); acc += s * w; wsum += w;
    s = tap(vec2( 0.0,-2.0)); d = dot(s - c, s - c); w = 0.40 * exp(-d * inv); acc += s * w; wsum += w;
    vec3 den = acc / wsum;
    // keep a little original texture so skin and fabric don't turn plastic
    c = mix(c, den, 0.55 + 0.4 * uDenoise);
  }

  float y = dot(c, LUMA);
  float sh = 1.0 - smoothstep(0.0, 0.55, y);
  float hl = smoothstep(0.55, 1.0, y);
  float y2 = y + uShadows * 0.16 * sh * (1.0 - y) - uHighlights * 0.14 * hl * y;
  float sc = y2 * y2 * (3.0 - 2.0 * y2);
  y2 = mix(y2, sc, uContrast * 0.30);
  c += (y2 - y);

  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float sat = mx - mn;
  float skin = step(c.b, c.g) * step(c.g, c.r) * smoothstep(0.05, 0.25, c.r - c.b) * (1.0 - smoothstep(0.45, 0.7, sat));
  float vib = uVibrance * 0.55 * (1.0 - sat) * (1.0 - 0.7 * skin);
  float yy = dot(c, LUMA);
  c = mix(vec3(yy), c, 1.0 + vib);

  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
"""

    /**
     * High-quality resampler. Upscaling: 16-tap Catmull-Rom bicubic with anti-ringing clamp
     * (sharp edges, no halos). Downscaling: 4x4 supersampled area filter (no aliasing).
     */
    const val SCALE = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uInSize;
uniform float uDownscale;
uniform vec2 uOutTexel;
varying vec2 vTexSamplingCoord;

vec4 texel(vec2 ip) { return texture2D(uTexSampler, (ip + 0.5) / uInSize); }

vec4 catmull(float x, vec4 a, vec4 b, vec4 c, vec4 d) {
  float x2 = x * x; float x3 = x2 * x;
  return 0.5 * ((2.0 * b) + (-a + c) * x + (2.0 * a - 5.0 * b + 4.0 * c - d) * x2 + (-a + 3.0 * b - 3.0 * c + d) * x3);
}

void main() {
  if (uDownscale > 0.5) {
    vec2 outStep = uOutTexel;
    vec4 acc = vec4(0.0);
    for (int j = 0; j < 4; j++) {
      for (int i = 0; i < 4; i++) {
        vec2 o = (vec2(float(i), float(j)) - 1.5) * 0.25 * outStep;
        acc += texture2D(uTexSampler, vTexSamplingCoord + o);
      }
    }
    gl_FragColor = acc / 16.0;
    return;
  }
  vec2 p = vTexSamplingCoord * uInSize - 0.5;
  vec2 f = fract(p);
  vec2 i = floor(p);
  vec4 r0 = catmull(f.x, texel(i + vec2(-1.0,-1.0)), texel(i + vec2(0.0,-1.0)), texel(i + vec2(1.0,-1.0)), texel(i + vec2(2.0,-1.0)));
  vec4 b00 = texel(i);
  vec4 b10 = texel(i + vec2(1.0, 0.0));
  vec4 b01 = texel(i + vec2(0.0, 1.0));
  vec4 b11 = texel(i + vec2(1.0, 1.0));
  vec4 r1 = catmull(f.x, texel(i + vec2(-1.0, 0.0)), b00, b10, texel(i + vec2(2.0, 0.0)));
  vec4 r2 = catmull(f.x, texel(i + vec2(-1.0, 1.0)), b01, b11, texel(i + vec2(2.0, 1.0)));
  vec4 r3 = catmull(f.x, texel(i + vec2(-1.0, 2.0)), texel(i + vec2(0.0, 2.0)), texel(i + vec2(1.0, 2.0)), texel(i + vec2(2.0, 2.0)));
  vec4 res = catmull(f.y, r0, r1, r2, r3);
  vec4 lo = min(min(b00, b10), min(b01, b11));
  vec4 hi = max(max(b00, b10), max(b01, b11));
  gl_FragColor = clamp(res, lo, hi);
}
"""

    /** Contrast Adaptive Sharpening (after AMD FidelityFX CAS, MIT) — limits halos & noise boost. */
    const val CAS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uSharpness;
varying vec2 vTexSamplingCoord;

vec3 t(float x, float y) { return texture2D(uTexSampler, vTexSamplingCoord + vec2(x, y) * uTexel).rgb; }

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 a = t(-1.0,-1.0); vec3 b = t(0.0,-1.0); vec3 c = t(1.0,-1.0);
  vec3 d = t(-1.0, 0.0); vec3 e = src.rgb;     vec3 f = t(1.0, 0.0);
  vec3 g = t(-1.0, 1.0); vec3 h = t(0.0, 1.0); vec3 i = t(1.0, 1.0);
  vec3 mnR = min(min(min(d, e), min(f, b)), h);
  vec3 mnR2 = min(mnR, min(min(a, c), min(g, i)));
  mnR += mnR2;
  vec3 mxR = max(max(max(d, e), max(f, b)), h);
  vec3 mxR2 = max(mxR, max(max(a, c), max(g, i)));
  mxR += mxR2;
  vec3 amp = clamp(min(mnR, 2.0 - mxR) / max(mxR, vec3(1e-4)), 0.0, 1.0);
  amp = sqrt(amp);
  float peak = -1.0 / mix(8.0, 5.0, clamp(uSharpness, 0.0, 1.0));
  vec3 w = amp * peak;
  vec3 res = (b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w);
  gl_FragColor = vec4(clamp(res, 0.0, 1.0), src.a);
}
"""

    /**
     * Motion-compensated frame interpolation. For every output pixel a symmetric block-matching
     * search (25 candidate vectors over 3 radii, 5-tap luma patches) finds the motion between the
     * previous and next frame, then both frames are sampled along that vector and blended at the
     * temporal position uAlpha. If no vector matches well (occlusion / very fast motion), the
     * nearest real frame is used instead of a ghosted blend.
     */
    const val INTERPOLATE = """
precision highp float;
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform vec2 uTexel;
uniform float uAlpha;
uniform float uMotionScale;
varying vec2 vTexSamplingCoord;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);

float cost(vec2 uv, vec2 v) {
  vec2 pp = uv - v * uAlpha * uTexel;
  vec2 cp = uv + v * (1.0 - uAlpha) * uTexel;
  vec2 o1 = vec2(2.0, 0.0) * uTexel;
  vec2 o2 = vec2(0.0, 2.0) * uTexel;
  float s = abs(dot(texture2D(uPrev, pp).rgb - texture2D(uCur, cp).rgb, LUMA));
  s += abs(dot(texture2D(uPrev, pp + o1).rgb - texture2D(uCur, cp + o1).rgb, LUMA));
  s += abs(dot(texture2D(uPrev, pp - o1).rgb - texture2D(uCur, cp - o1).rgb, LUMA));
  s += abs(dot(texture2D(uPrev, pp + o2).rgb - texture2D(uCur, cp + o2).rgb, LUMA));
  s += abs(dot(texture2D(uPrev, pp - o2).rgb - texture2D(uCur, cp - o2).rgb, LUMA));
  return s;
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec2 best = vec2(0.0);
  float bestCost = cost(uv, best) - 0.02;  // small bias towards "no motion"
  for (int r = 0; r < 3; r++) {
    float radius = (r == 0 ? 3.0 : (r == 1 ? 8.0 : 18.0)) * uMotionScale;
    for (int k = 0; k < 8; k++) {
      float ang = float(k) * 0.7853982;
      vec2 v = vec2(cos(ang), sin(ang)) * radius;
      float cc = cost(uv, v) + 0.004 * float(r);
      if (cc < bestCost) { bestCost = cc; best = v; }
    }
  }
  vec4 p = texture2D(uPrev, uv - best * uAlpha * uTexel);
  vec4 c = texture2D(uCur, uv + best * (1.0 - uAlpha) * uTexel);
  vec4 blended = mix(p, c, uAlpha);
  vec4 nearest = uAlpha < 0.5 ? texture2D(uPrev, uv) : texture2D(uCur, uv);
  float conf = 1.0 - smoothstep(0.18, 0.45, bestCost);
  gl_FragColor = mix(nearest, blended, conf);
}
"""
}
