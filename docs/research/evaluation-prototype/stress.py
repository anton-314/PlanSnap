"""Seeded stress scenario of the room evaluation. Research code, not part of the Gradle build.

Usage from the repository root: python3 -I -B docs/research/evaluation-prototype/stress.py [cases-per-class]
[--no-ambiguity]. See README.md for the classes and the target values.
"""
import collections
import math
import multiprocessing as mp
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import flow as FLOW  # noqa: E402

SEED = 20261124


def normalize(pts, rng, mirror=None):
    n = len(pts)
    k = rng.randrange(n)
    pts = pts[k:] + pts[:k]
    if (rng.random() < 0.5) if mirror is None else mirror:
        pts = [(x, -y) for x, y in pts]
    x0, y0 = pts[0]
    ang = math.atan2(pts[1][1] - y0, pts[1][0] - x0)
    co, si = math.cos(-ang), math.sin(-ang)
    return [((x - x0) * co - (y - y0) * si, (x - x0) * si + (y - y0) * co) for x, y in pts]


def turns(pts):
    n = len(pts)
    out = []
    for i in range(n):
        ax, ay = pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]
        bx, by = pts[(i + 1) % n][0] - pts[i][0], pts[(i + 1) % n][1] - pts[i][1]
        out.append(math.atan2(ax * by - ay * bx, ax * bx + ay * by))
    return out


def lengths(pts):
    n = len(pts)
    return [math.dist(pts[i], pts[(i + 1) % n]) for i in range(n)]


def rectilinear(rng, n):
    k = max(1, (n - 2) // 2)
    widths = [rng.randint(600, 3000) for _ in range(k)]
    heights = []
    for _ in range(k):
        h = rng.randint(800, 5000)
        while heights and abs(h - heights[-1]) < 300:
            h = rng.randint(800, 5000)
        heights.append(h)
    width = sum(widths)
    pts = [(0, 0), (width, 0)]
    x = width
    for i in range(k - 1, -1, -1):
        pts.append((x, heights[i]))
        x -= widths[i]
        pts.append((x, heights[i]))
    out = []
    for p in pts:
        if not out or p != out[-1]:
            out.append(p)
    if out[-1] == out[0]:
        out.pop()
    return [(float(a), float(b)) for a, b in out]


def radial(rng, n, convex):
    while True:
        cuts = sorted(rng.uniform(0, 2 * math.pi) for _ in range(n))
        gaps = [(cuts[(i + 1) % n] - cuts[i]) % (2 * math.pi) for i in range(n)]
        if min(gaps) > 0.25 and max(gaps) < 2.6:
            break
    if convex:
        a, b = rng.uniform(2000, 6000), rng.uniform(2000, 6000)
        return [(a * math.cos(v), b * math.sin(v)) for v in cuts]
    return [(rng.uniform(1500, 6000) * math.cos(v), rng.uniform(1500, 6000) * math.sin(v)) for v in cuts]


def valid_truth(pts, min_edge=150, feature=30):
    n = len(pts)
    if min(lengths(pts)) < min_edge:
        return False
    if not FLOW.simple(pts, feature):
        return False
    return all(abs(v) < math.radians(170) for v in turns(pts))


def truth(rng, nmin=3, nmax=12):
    while True:
        n = rng.randint(nmin, nmax)
        kind = rng.choice(['convex', 'concave', 'rect'])
        if kind == 'rect':
            n = max(4, n - n % 2)
            pts = rectilinear(rng, n)
        else:
            pts = radial(rng, n, kind == 'convex')
        if len(pts) >= 3 and valid_truth(pts):
            return normalize(pts, rng), kind


def corner_of(tau, rng, declared):
    deg = math.degrees(tau)
    alpha = round((180 - abs(deg)) * 1000)
    d = 1 if deg > 0 else -1
    if abs(deg) < 1e-9:
        d = rng.choice([1, -1])
    if declared:
        return ('R', d)
    return ('M', d, max(1, min(180000, alpha)))


def build(pts, rng, honest, diag_max=4, wall_rep=2, p_declared=0.6):
    n = len(pts)
    tt = turns(pts)
    L = [max(10, round(v)) for v in lengths(pts)]
    corners = []
    for i in range(n):
        right = abs(abs(math.degrees(tt[i])) - 90) < 1e-6
        declared = (right and rng.random() < 0.8) if honest else rng.random() < p_declared
        if i == 0:
            u = rng.random()
            if u < 0.65:
                corners.append(('F',))
            elif u < 0.95 or (honest and not right):
                corners.append(corner_of(tt[0], rng, False))
            else:
                corners.append(corner_of(tt[0], rng, True))
        else:
            corners.append(corner_of(tt[i], rng, declared))
    pairs = [(a, b) for a in range(n) for b in range(a + 2, n) if not (a == 0 and b == n - 1)]
    rng.shuffle(pairs)
    diagonals = [(a, b, round(math.dist(pts[a], pts[b]))) for a, b in pairs[:rng.randint(0, min(diag_max, len(pts)))]]
    for _ in range(rng.randint(0, wall_rep) if rng.random() < 0.3 else 0):
        w = rng.randrange(n)
        a, b = w, (w + 1) % n
        diagonals.append((min(a, b), max(a, b), L[w]))
    return FLOW.Input(L, corners, diagonals)


def describe(inp):
    return dict(L=[int(v) for v in inp.L], c=inp.c, D=inp.D)


def shape_points(res, n):
    P, _ = FLOW.walk(res['L'], res['t'])
    return [(float(P[i, 0]), float(P[i, 1])) for i in range(n)]


def distance_to_truth(res, pts):
    return FLOW.shape_distance(shape_points(res, len(pts)), pts)


def meets_inputs(res, inp):
    n = inp.n
    q = shape_points(res, n)
    if max(abs(a - b) for a, b in zip(res['L'], inp.L)) > 1.5:
        return False
    for a, b, dd in inp.D:
        if abs(math.dist(q[a], q[b]) - dd) > 1.5:
            return False
    for i, c in enumerate(inp.c):
        if c[0] == 'M' and abs(FLOW.wrap(res['t'][i] - FLOW.math.radians(c[1] * (180000 - c[2]) / 1000))) > math.radians(0.05):
            return False
    return not FLOW.violated(inp, res['t'])


def rounding_impossible(inp, pts, res):
    """The raw values provably admit no closed shape only if a necessary condition fails within the rounding budget."""
    if res.get('reason') not in ('WALL_TOO_LONG', 'DIAGONAL_IMPOSSIBLE'):
        return False
    return FLOW.necessary(inp, relax=0.5 * (inp.n + 1)) is None


def check_true(res, inp, pts, tag, out, keep):
    status = res['status']
    limit = max(50.0, 0.01 * inp.U)
    if status == 'CONFLICT':
        if rounding_impossible(inp, pts, res):
            out[tag + ':rounding-impossible'] += 1
            return
        key = f"false-conflict:{res.get('reason')}:{res['path']}"
        out[tag + ':' + key] += 1
        keep(tag, key, inp)
        return
    q = shape_points(res, inp.n)
    area = sum(q[i][0] * q[(i + 1) % inp.n][1] - q[(i + 1) % inp.n][0] * q[i][1] for i in range(inp.n))
    if (area > 0) != (inp.s > 0):
        out[tag + ':info-winding-differs-from-s'] += 1
    if not FLOW.simple(q):
        key = f'non-simple:{status}'
        out[tag + ':' + key] += 1
        keep(tag, key, inp)
        return
    if res.get('proposal') == 'AMBIGUOUS_SHAPE':
        shown = 'shown-differs' if distance_to_truth(res, pts) > limit else 'shown-matches'
        out[f"{tag}:info-ambiguous:{res['path']}:instead-{res['instead']}:{shown}"] += 1
    if status == 'CONFIRMED':
        dist = distance_to_truth(res, pts)
        if dist > limit:
            if meets_inputs(res, inp):
                out[tag + ':ambiguous-other-solution'] += 1
                keep(tag, 'ambiguous-other-solution', inp)
            else:
                key = f"wrong-confirmed:{res['path']}"
                out[tag + ':' + key] += 1
                keep(tag, key, inp)


def run_class1(job):
    idx, honest = job
    rng = random.Random(SEED * 2 + idx * 2 + honest)
    pts, kind = truth(rng)
    inp = build(pts, rng, bool(honest))
    tag = 'K1a' if honest else 'K1b'
    return one(inp, lambda res, out, keep: check_true(res, inp, pts, tag, out, keep), tag)


def run_class2(job):
    rng = random.Random(SEED * 3 + job)
    if rng.random() < 0.6:
        pts, _ = truth(rng, 4, 12)
        inp = build(pts, rng, False)
        flips = rng.sample(range(1, inp.n), min(rng.randint(1, 2), inp.n - 1))
        c = list(inp.c)
        for i in flips:
            c[i] = (c[i][0], -c[i][1]) + tuple(c[i][2:])
        inp = FLOW.Input([int(v) for v in inp.L], c, inp.D)
    else:
        n = rng.randint(4, 10)
        L = [rng.choice([1000, 2000, 3000, 4000]) for _ in range(n)]
        c = [('F',)] + [('R', rng.choice([1, -1])) for _ in range(n - 1)]
        inp = FLOW.Input(L, c, [])

    def check(res, out, keep):
        if res['status'] in ('CONFIRMED', 'NEEDS_ADJUSTMENT', 'PROPOSED'):
            if not FLOW.simple(shape_points(res, inp.n)):
                key = f"non-simple:{res['status']}:{res['path']}"
                out['K2:' + key] += 1
                keep('K2', key, inp)
        if res['status'] == 'CONFIRMED' and FLOW.violated(inp, res['t']):
            key = f"confirmed-direction:{res['path']}"
            out['K2:' + key] += 1
            keep('K2', key, inp)

    return one(inp, check, 'K2')


def run_class3(job):
    rng = random.Random(SEED * 5 + job)
    while True:
        pts, _ = truth(rng, 4, 12)
        if sum(abs(abs(math.degrees(v)) - 90) > 3 for v in turns(pts)) >= 2:
            break
    tt = turns(pts)
    n = len(pts)
    measured = set(rng.sample(range(1, n), rng.randint(0, max(0, n - 4))))
    corners = [('F',)] + [corner_of(tt[i], rng, i not in measured) for i in range(1, n)]
    inp = FLOW.Input([round(v) for v in lengths(pts)], corners, [])

    def check(res, out, keep):
        if res['status'] == 'CONFIRMED':
            key = f"confirmed-underdetermined:{res['path']}"
            out['K3:' + key] += 1
            keep('K3', key, inp)
        if res['status'] in ('NEEDS_ADJUSTMENT', 'PROPOSED') and not FLOW.simple(shape_points(res, n)):
            key = f"non-simple:{res['status']}"
            out['K3:' + key] += 1
            keep('K3', key, inp)

    return one(inp, check, 'K3')


def rect_gap(gap_mm):
    return FLOW.Input([4000 + gap_mm, 3000, 4000, 3000], [('F',), ('R', 1), ('R', 1), ('R', 1)], [])


def boundary_shape(rng):
    kind = rng.choice(['triangle', 'tiny-triangle', 'collinear', 'near-collinear', 'sharp', 'thin'])
    if kind == 'triangle':
        pts = [(0.0, 0.0), (rng.uniform(500, 6000), 0.0), (rng.uniform(-2000, 6000), rng.uniform(300, 5000))]
    elif kind == 'tiny-triangle':
        a = rng.randint(10, 40)
        pts = [(0.0, 0.0), (float(a), 0.0), (rng.uniform(0, a), rng.uniform(10, 40))]
    elif kind == 'collinear':
        w, h = rng.randint(2000, 6000), rng.randint(2000, 5000)
        cut = rng.randint(500, w - 500)
        pts = [(0.0, 0.0), (float(cut), 0.0), (float(w), 0.0), (float(w), float(h)), (0.0, float(h))]
    elif kind == 'near-collinear':
        w, h = rng.randint(2000, 6000), rng.randint(2000, 5000)
        cut = rng.randint(500, w - 500)
        bump = rng.choice([-1, 1]) * rng.uniform(0.2, 8)
        pts = [(0.0, 0.0), (float(cut), bump), (float(w), 0.0), (float(w), float(h)), (0.0, float(h))]
    elif kind == 'sharp':
        w = rng.randint(3000, 8000)
        pts = [(0.0, 0.0), (float(w), 0.0), (float(w) * rng.uniform(0.2, 0.9), rng.uniform(120, 500))]
    else:
        w = rng.randint(3000, 9000)
        h = rng.randint(30, 200)
        pts = [(0.0, 0.0), (float(w), 0.0), (float(w), float(h)), (0.0, float(h))]
    return normalize(pts, rng), kind


def run_class4(job):
    rng = random.Random(SEED * 7 + job)
    if job % 5 == 0:
        return run_gap(job, rng)
    pts, kind = boundary_shape(rng)
    n = len(pts)
    honest = rng.random() < 0.5
    tt = turns(pts)
    L = [max(10, round(v)) for v in lengths(pts)]
    corners = []
    for i in range(n):
        right = abs(abs(math.degrees(tt[i])) - 90) < 1e-6
        declared = right and honest and rng.random() < 0.7
        if i == 0 and rng.random() < 0.6:
            corners.append(('F',))
        else:
            corners.append(corner_of(tt[i], rng, declared))
    pairs = [(a, b) for a in range(n) for b in range(a + 2, n) if not (a == 0 and b == n - 1)]
    rng.shuffle(pairs)
    D = [(a, b, round(math.dist(pts[a], pts[b]))) for a, b in pairs[:rng.randint(0, 2)]]
    inp = FLOW.Input(L, corners, D)
    tag = 'K4-' + kind
    return one(inp, lambda res, out, keep: check_true(res, inp, pts, tag, out, keep), tag)


def run_gap(job, rng):
    g = rng.choice([0, 1, 2, 139, 140, 141, 49, 50, 51])
    inp = rect_gap(g)
    per = inp.U
    T = max(50.0, 0.01 * per)

    def check(res, out, keep):
        want = 'CONFIRMED' if g <= 1 else ('NEEDS_ADJUSTMENT' if g <= T else None)
        if want and res['status'] != want:
            key = f'gap-status:{g}:{res["status"]}'
            out['K4-gap:' + key] += 1
            keep('K4-gap', key, inp)
        if want is None and res['status'] == 'CONFIRMED' and FLOW.violated(inp, res['t']):
            out['K4-gap:direction'] += 1

    return one(inp, check, 'K4-gap')


def one(inp, check, tag):
    out = collections.Counter()
    samples = {}

    def keep(t, key, value):
        samples.setdefault(t + ':' + key, describe(value))

    out[tag + ':total'] += 1
    try:
        res = FLOW.evaluate(inp)
        out[tag + ':status:' + res['status']] += 1
        if res.get('proposal') == 'AMBIGUOUS_SHAPE':
            out[tag + ':info-proposed-ambiguous'] += 1
            if res['ambiguity']['recommendation'] is None:
                out[tag + ':info-ambiguous-without-recommendation'] += 1
        check(res, out, keep)
    except Exception as ex:  # A crash is a finding, never a skipped case.
        key = 'crash:' + type(ex).__name__
        out[tag + ':' + key] += 1
        keep(tag, key, inp)
    return out, samples


def merge(results, total, samples):
    for out, sm in results:
        total.update(out)
        for k, v in sm.items():
            samples.setdefault(k, v)


def main(argv):
    args = [v for v in argv if v != '--no-ambiguity']
    FLOW.USE_AMBIGUITY_RULE = len(args) == len(argv)
    count = int(args[0]) if args else 2400
    total, samples = collections.Counter(), {}
    with mp.get_context('fork').Pool(6) as pool:
        merge(pool.imap_unordered(run_class1, [(i, 1) for i in range(count)], 32), total, samples)
        merge(pool.imap_unordered(run_class1, [(i, 0) for i in range(count)], 32), total, samples)
        merge(pool.imap_unordered(run_class2, range(count), 32), total, samples)
        merge(pool.imap_unordered(run_class3, range(count), 32), total, samples)
        merge(pool.imap_unordered(run_class4, range(count), 32), total, samples)
    tags = sorted({k.split(':')[0] for k in total})
    grand = 0
    for tag in tags:
        n = total[tag + ':total']
        grand += n
        st = {k.split(':')[2]: v for k, v in sorted(total.items()) if k.startswith(tag + ':status:')}
        dev = {k[len(tag) + 1:]: v for k, v in total.items() if k.startswith(tag + ':') and
               not k.startswith(tag + ':status:') and k != tag + ':total'}
        print(f'{tag:18} n={n:6} {st}')
        for k, v in sorted(dev.items(), key=lambda z: (-z[1], z[0])):
            print(f'    {v:6}  {k}')
    print('TOTAL', grand)


if __name__ == '__main__':
    main(sys.argv[1:])
