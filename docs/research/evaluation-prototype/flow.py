"""Reference prototype of the room evaluation in DOMAIN sections 4 to 7. Research code, not production code."""
import math

import numpy as np

PI = math.pi
TAU = 2 * PI
EXACT = 1.0
RES = 0.01
MINF = 1.0
RANK_TOL = 1e-9
PROBE = 0.1
RANK_K = 10.0
USE_AMBIGUITY_RULE = True
DIR_EPS = math.radians(2.0)
SL, SD = 5.0, 5.0
SR, SM, SF = math.radians(0.5), math.radians(1.0), 10.0
Q95 = [3.841459, 5.991465, 7.814728, 9.487729, 11.070498, 12.591587, 14.067140, 15.507313, 16.918978, 18.307038,
       19.675138, 21.026070, 22.362032, 23.684791, 24.995790, 26.296228, 27.587112, 28.869299, 30.143527, 31.410433,
       32.670573, 33.924438, 35.172462, 36.415029, 37.652484, 38.885139, 40.113272, 41.337138, 42.556968, 43.772972]
Q99 = [6.634897, 9.210340, 11.344867, 13.276704, 15.086272, 16.811894, 18.475307, 20.090235, 21.665994, 23.209251,
       24.724970, 26.216967, 27.688250, 29.141238, 30.577914, 31.999927, 33.408664, 34.805306, 36.190869, 37.566235,
       38.932173, 40.289360, 41.638398, 42.979820, 44.314105, 45.641683, 46.962942, 48.278236, 49.587884, 50.892181]


def wh(z, r):
    v = 2 / (9 * r)
    return r * (1 - v + z * math.sqrt(v)) ** 3


def q95(r):
    return Q95[r - 1] if r <= 30 else wh(1.6448536269514722, r)


def q99(r):
    return Q99[r - 1] if r <= 30 else wh(2.3263478740408408, r)


def wrap(a):
    return PI - ((PI - a) % TAU)


class Input:
    """corners: ('F',), ('R', d) or ('M', d, milliDegrees). diagonals: (a, b, mm)."""

    def __init__(self, lengths, corners, diagonals=()):
        self.L = [float(v) for v in lengths]
        self.c = list(corners)
        self.D = [tuple(v) for v in diagonals]
        self.n = len(self.L)
        self.U = sum(self.L)
        self.that, self.s = targets(self.c)
        self.free = [i for i in range(1, self.n) if self.c[i][0] != 'M']


def mdeg_turn(c):
    if c[0] == 'R':
        return c[1] * 90000
    return c[1] * (180000 - c[2])


def targets(c):
    n = len(c)
    t = [0] * n
    for i in range(1, n):
        t[i] = mdeg_turn(c[i])
    total = sum(t[1:])
    s = 1 if total >= 0 else -1
    if c[0][0] == 'F':
        r = (360000 * s - total) % 360000
        if r > 180000:
            r -= 360000
        best = None
        for cand in (-90000, 0, 90000):
            dist = abs(r - cand) % 360000
            dist = min(dist, 360000 - dist)
            key = (dist, -s * cand)
            if best is None or key < best[0]:
                best = (key, cand)
        t[0] = best[1]
    else:
        t[0] = mdeg_turn(c[0])
    return [math.radians(v / 1000.0) for v in t], s


def walk(L, t):
    n = len(L)
    phi = np.zeros(n)
    phi[1:] = np.cumsum(np.asarray(t[1:], float))
    dx = np.asarray(L) * np.cos(phi)
    dy = np.asarray(L) * np.sin(phi)
    P = np.zeros((n + 1, 2))
    P[1:, 0] = np.cumsum(dx)
    P[1:, 1] = np.cumsum(dy)
    return P, phi


def adj_wall(a, b, n):
    a, b = min(a, b), max(a, b)
    if b == a + 1:
        return a
    if a == 0 and b == n - 1:
        return n - 1
    return None


def seg_dist(a, b, c, d):
    def orient(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])

    if orient(a, b, c) * orient(a, b, d) < 0 and orient(c, d, a) * orient(c, d, b) < 0:
        return 0.0

    def pd(p, q, r):
        vx, vy = r[0] - q[0], r[1] - q[1]
        ll = vx * vx + vy * vy
        if ll == 0:
            return math.hypot(p[0] - q[0], p[1] - q[1])
        u = max(0.0, min(1.0, ((p[0] - q[0]) * vx + (p[1] - q[1]) * vy) / ll))
        return math.hypot(p[0] - q[0] - u * vx, p[1] - q[1] - u * vy)

    return min(pd(a, c, d), pd(b, c, d), pd(c, a, b), pd(d, a, b))


def simple(P, minf=MINF):
    n = len(P)
    pts = [(float(p[0]), float(p[1])) for p in P]
    for i in range(n):
        for j in range(i + 2, n):
            if i == 0 and j == n - 1:
                continue
            if seg_dist(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n]) < minf:
                return False
    return True


def rank(M, tol):
    if M.size == 0:
        return 0
    A = np.array(M, float)
    mx = np.abs(A).max()
    if mx == 0:
        return 0
    r = 0
    lim = min(A.shape)
    while r < lim:
        sub = np.abs(A[r:, r:])
        i, j = np.unravel_index(sub.argmax(), sub.shape)
        if sub[i, j] <= tol * mx:
            break
        A[[r, r + i]] = A[[r + i, r]]
        A[:, [r, r + j]] = A[:, [r + j, r]]
        A[r + 1:] -= np.outer(A[r + 1:, r] / A[r, r], A[r])
        r += 1
    return r


def cond_jac(inp, L, t, names):
    """Rows of the named hard conditions by u_free, distances divided by U."""
    n, U, free = inp.n, sum(L), inp.free
    P, _ = walk(L, t)
    J = np.zeros((len(names), len(free)))
    for row, z in enumerate(names):
        for col, j in enumerate(free):
            if z == 'x':
                J[row, col] = -(P[n, 1] - P[j, 1]) / U
            elif z == 'y':
                J[row, col] = (P[n, 0] - P[j, 0]) / U
            elif z == 'c':
                J[row, col] = -1.0
            else:
                a, b, dd = inp.D[z]
                a, b = min(a, b), max(a, b)
                if j >= b:
                    continue
                pivot = P[a] if j <= a else P[j]
                dv = P[b] - pivot
                diff = P[b] - P[a]
                J[row, col] = (diff[0] * -dv[1] + diff[1] * dv[0]) / (dd * U)
    return J


def cond_names(inp):
    names = ['x', 'y']
    if inp.c[0][0] == 'M':
        names.append('c')
    order = sorted(range(len(inp.D)), key=lambda k: (min(inp.D[k][:2]), max(inp.D[k][:2]), inp.D[k][2], k))
    names += [k for k in order if adj_wall(inp.D[k][0], inp.D[k][1], inp.n) is None]
    return names


def analyze_constraints(inp):
    names = cond_names(inp)
    m = 2 + (inp.c[0][0] == 'M') + len(inp.D)
    v = len(inp.free)
    if v == 0:
        return dict(v=0, m=m, rho=0, sel=[])
    J0 = cond_jac(inp, inp.L, inp.that, names)
    r0 = rank(J0, RANK_TOL)
    point, rho, J = inp.that, r0, J0
    if r0 < m:
        probe = list(inp.that)
        for i in inp.free:
            probe[i] = inp.that[i] + PROBE * i / inp.n
        J1 = cond_jac(inp, inp.L, probe, names)
        r1 = rank(J1, RANK_TOL)
        if r1 > r0:
            point, rho, J = probe, r1, J1
    sel, idx = [], []
    for row, z in enumerate(names):
        if rank(J[idx + [row]], RANK_TOL) > len(idx):
            idx.append(row)
            sel.append(z)
    return dict(v=v, m=m, rho=rho, sel=sel)


def rank_at(inp, L, t):
    if not inp.free:
        return 0
    names = cond_names(inp)
    J = cond_jac(inp, L, t, names)
    return rank(J, max(RANK_TOL, RANK_K * RES / sum(L)))


def violated(inp, t):
    """Decision H3: alpha = 180 has no direction, otherwise a violation needs d*tau < -2 degrees."""
    out = []
    for i, c in enumerate(inp.c):
        if c[0] == 'F' or (c[0] == 'M' and c[2] == 180000):
            continue
        turn = (TAU * inp.s - sum(t[1:])) if (i == 0 and c[0] == 'M') else t[i]
        if c[1] * wrap(turn) < -DIR_EPS:
            out.append(i)
    return out


def chord_start(inp):
    L, n, s = inp.L, inp.n, inp.s
    longest = max(L)
    lo, hi = longest / 2, sum(L)
    full = sum(2 * math.asin(min(1.0, v / (2 * lo))) for v in L)
    outside = full < TAU
    for _ in range(100):
        mid = (lo + hi) / 2
        arcs = [2 * math.asin(min(1.0, v / (2 * mid))) for v in L]
        if outside:
            k = L.index(longest)
            arcs[k] = TAU - arcs[k]
        if (sum(arcs) > TAU) != outside:
            lo = mid
        else:
            hi = mid
    arcs = [2 * math.asin(min(1.0, v / (2 * hi))) for v in L]
    if outside:
        k = L.index(longest)
        arcs[k] = TAU - arcs[k]
    return [s * (arcs[i - 1] + arcs[i]) / 2 for i in range(n)]


def outside_residual(inp, sel, t):
    P, _ = walk(inp.L, t)
    worst = 0.0
    if inp.c[0][0] == 'M' and 'c' not in sel:
        worst = abs(wrap(TAU * inp.s - sum(t[1:]) - inp.that[0])) * inp.U / TAU
    for k, (a, b, dd) in enumerate(inp.D):
        if k in sel or adj_wall(a, b, inp.n) is not None:
            continue
        worst = max(worst, abs(float(np.hypot(*(P[a] - P[b]))) - dd))
    return worst


def direction_start(inp):
    free = inp.free
    if not free:
        return None
    fixed = sum(inp.that[i] for i in range(1, inp.n) if i not in free)
    closing = inp.that[0]
    weight = sum(inp.c[i][1] for i in free)
    if weight == 0:
        return None
    beta = (TAU * inp.s - fixed - closing) / weight
    if not 0 < beta < PI:
        return None
    t = list(inp.that)
    for i in free:
        t[i] = inp.c[i][1] * beta
    return t


def all_residuals(inp, t, names):
    P, _ = walk(inp.L, t)
    n, U = inp.n, inp.U
    out = []
    for z in names:
        if z == 'x':
            out.append(P[n, 0] / U)
        elif z == 'y':
            out.append(P[n, 1] / U)
        elif z == 'c':
            out.append(wrap(TAU * inp.s - sum(t[1:]) - inp.that[0]))
        else:
            a, b, dd = inp.D[z]
            e = float(np.hypot(*(P[a] - P[b])))
            out.append((e * e - dd * dd) / (2 * dd) / U)
    return np.array(out)


def refine(inp, t):
    """Levenberg-Marquardt on all hard conditions, used only for the linearization start of branch B."""
    names = cond_names(inp)
    free = inp.free
    u = np.array([t[i] for i in free])

    def full(uu):
        tt = list(inp.that)
        for k, i in enumerate(free):
            tt[i] = float(uu[k])
        return tt

    damp = 1e-3
    r = all_residuals(inp, full(u), names)
    now = float(r @ r)
    for _ in range(60):
        J = cond_jac(inp, inp.L, full(u), names)
        JJ = J.T @ J
        g = J.T @ r
        if np.max(np.abs(g)) < 1e-13:
            break
        improved = False
        for _ in range(12):
            try:
                d = np.linalg.solve(JJ + damp * np.eye(len(u)), -g)
            except np.linalg.LinAlgError:
                damp *= 4
                continue
            cand = u + d
            rc = all_residuals(inp, full(cand), names)
            val = float(rc @ rc)
            if val < now:
                u, r, now = cand, rc, val
                damp = max(damp / 3, 1e-12)
                improved = True
                break
            damp *= 4
        if not improved:
            break
    return full(u)


def solve_keep_simple(inp, sel, start):
    """SQP of DOMAIN 5.2. Once an iterate is closed and simple, a step must keep the outline simple."""
    n, U, free = inp.n, inp.U, inp.free
    that, s = inp.that, inp.s
    if not free:
        return None
    u = np.array([start[i] for i in free], float)
    closing_soft = inp.c[0][0] != 'M'
    closed = 'x' in sel and 'y' in sel

    def full(uu):
        tt = list(that)
        for k, i in enumerate(free):
            tt[i] = float(uu[k])
        return tt

    def cons(tt):
        return all_residuals(inp, tt, sel)

    def cost(tt):
        f = sum(wrap(tt[i] - that[i]) ** 2 for i in free)
        if closing_soft:
            f += wrap(TAU * s - sum(tt[1:]) - that[0]) ** 2
        return f

    def is_simple(tt):
        P, _ = walk(inp.L, tt)
        return simple(P[:n])

    rho = 1.0
    v = len(free)
    best = None
    for _ in range(200):
        tt = full(u)
        c = cons(tt)
        feasible = np.max(np.abs(c)) * U <= RES
        keep = feasible and closed and is_simple(tt)
        if keep:
            best = tt
        J = cond_jac(inp, inp.L, tt, sel)
        g = np.array([2 * wrap(tt[i] - that[i]) for i in free])
        H = 2.0 * np.eye(v)
        if closing_soft:
            g -= 2 * wrap(TAU * s - sum(tt[1:]) - that[0])
            H += 2.0
        H += 1e-8 * np.eye(v)
        k = len(sel)
        K = np.block([[H, J.T], [J, np.zeros((k, k))]])
        rhs = np.concatenate([-g, -c])
        try:
            z = np.linalg.solve(K, rhs)
        except np.linalg.LinAlgError:
            z = np.linalg.lstsq(K, rhs, rcond=1e-12)[0]
        d, lam = z[:v], z[v:]
        if feasible and np.max(np.abs(g + J.T @ lam)) <= 1e-7:
            return tt if (not closed or is_simple(tt)) else best
        rho = max(rho, 2 * float(np.max(np.abs(lam))) if k else 1.0)
        m0 = cost(tt) + rho * float(np.abs(c).sum())
        step = 1.0
        accepted = False
        for _ in range(21):
            cand = u + step * d
            ct = full(cand)
            if cost(ct) + rho * float(np.abs(cons(ct)).sum()) < m0 and (not keep or is_simple(ct)):
                u = cand
                accepted = True
                break
            step /= 2
        if not accepted:
            break
    tt = full(u)
    if np.max(np.abs(cons(tt))) * U <= RES and (not closed or is_simple(tt)):
        return tt
    return best


def solver(inp, sel):
    """Runs every start of DOMAIN 5.3. Returns the selected turns and the other results, or None without a result."""
    n, s, that = inp.n, inp.s, inp.that
    starts = [list(that), [(that[i] + s * TAU / n) / 2 for i in range(n)], chord_start(inp)]
    extra = direction_start(inp)
    if extra is not None:
        starts.append(extra)
    names = cond_names(inp)
    overdetermined = len(sel) < len(names)
    closed = 'x' in sel and 'y' in sel
    results = []

    def consider(tt):
        tt = list(tt)
        tt[0] = that[0] if inp.c[0][0] == 'M' else TAU * s - sum(tt[1:])
        P, _ = walk(inp.L, tt)
        if closed and not simple(P[:n]):
            return
        f = sum(wrap(tt[i] - that[i]) ** 2 for i in inp.free)
        if inp.c[0][0] != 'M':
            f += wrap(tt[0] - that[0]) ** 2
        results.append((bool(violated(inp, tt)), round(outside_residual(inp, sel, tt)), round(f / 1e-9),
                        [round(tt[i] * 1e6) for i in inp.free], tt))

    for st in starts:
        tt = solve_keep_simple(inp, sel, st)
        if tt is not None:
            consider(refine(inp, tt) if overdetermined else tt)
        phase1 = refine(inp, st)
        if overdetermined:
            consider(phase1)
        else:
            tt = solve_keep_simple(inp, sel, phase1)
            if tt is not None:
                consider(tt)
    if not results:
        return None
    results.sort(key=lambda z: z[:4])
    return results[0][4], [z[4] for z in results[1:]]


def hard_residual_mm(inp, t):
    """Largest residual of all hard conditions in mm. A diagonal between adjacent corners only repeats a wall."""
    P, _ = walk(inp.L, t)
    worst = float(np.hypot(*P[inp.n]))
    if inp.c[0][0] == 'M':
        worst = max(worst, abs(wrap(TAU * inp.s - sum(t[1:]) - inp.that[0])) * inp.U / TAU)
    for a, b, dd in inp.D:
        if adj_wall(a, b, inp.n) is None:
            worst = max(worst, abs(float(np.hypot(*(P[a] - P[b]))) - dd))
    return worst


def shape_distance(p, q):
    """DOMAIN 5.6: largest vertex distance after the optimal proper rigid alignment of the centroids."""
    n = len(p)
    ax, ay = sum(v[0] for v in p) / n, sum(v[1] for v in p) / n
    bx, by = sum(v[0] for v in q) / n, sum(v[1] for v in q) / n
    a = [(v[0] - ax, v[1] - ay) for v in p]
    b = [(v[0] - bx, v[1] - by) for v in q]
    dot = sum(u[0] * w[0] + u[1] * w[1] for u, w in zip(a, b))
    cross = sum(u[0] * w[1] - u[1] * w[0] for u, w in zip(a, b))
    theta = math.atan2(cross, dot)
    co, si = math.cos(theta), math.sin(theta)
    return max(math.hypot(co * u[0] - si * u[1] - w[0], si * u[0] + co * u[1] - w[1]) for u, w in zip(a, b))


def points(L, t):
    P, _ = walk(L, t)
    return [(float(P[i, 0]), float(P[i, 1])) for i in range(len(L))]


def recommend(inp, shown_t, shown, other_t, other, limit):
    """DOMAIN 5.6: free closing corner first, then the unmeasured diagonal with the largest length difference."""
    if inp.c[0][0] == 'F' and abs(wrap(shown_t[0] - other_t[0])) * inp.U / TAU > limit:
        return ('C', 0)
    n = inp.n
    known = {(min(a, b), max(a, b)) for a, b, _ in inp.D}
    gaps = []
    for a in range(n):
        for b in range(a + 2, n):
            if (a == 0 and b == n - 1) or (a, b) in known:
                continue
            gaps.append((abs(math.dist(shown[a], shown[b]) - math.dist(other[a], other[b])), a, b))
    if not gaps:
        return None
    top = max(g[0] for g in gaps)
    if top <= EXACT:
        return None
    _, a, b = next(g for g in gaps if g[0] >= top - RES)
    return ('D', a, b)


def ambiguity(inp, L, t, alternatives, limit):
    """DOMAIN 5.6: the valid alternative farthest from the displayed shape, if it is farther than T."""
    if not USE_AMBIGUITY_RULE:
        return None
    shown = points(L, t)
    valid = []
    for other_t in alternatives:
        if violated(inp, other_t) or hard_residual_mm(inp, other_t) > EXACT:
            continue
        other = points(inp.L, other_t)
        dist = shape_distance(shown, other)
        if dist > limit:
            valid.append((dist, other_t, other))
    if not valid:
        return None
    top = max(v[0] for v in valid)
    dist, other_t, other = next(v for v in valid if v[0] >= top - RES)
    return dict(distance=dist, turns=other_t, recommendation=recommend(inp, t, shown, other_t, other, limit))


def C_of(inp, x, wrapped):
    n = inp.n
    L, t = x[:n], x[n:2 * n]
    P, _ = walk(L, t)
    sum_t = float(np.sum(t)) - TAU * inp.s
    out = [P[n, 0], P[n, 1], wrap(sum_t) if wrapped else sum_t]
    for k, (a, b, _) in enumerate(inp.D):
        w = adj_wall(a, b, n)
        dist = x[w] if w is not None else float(np.hypot(*(P[a] - P[b])))
        out.append(dist - x[2 * n + k])
    return np.array(out)


def A_of(inp, x):
    n, nd = inp.n, len(inp.D)
    L, t = x[:n], x[n:2 * n]
    P, phi = walk(L, t)
    A = np.zeros((3 + nd, 2 * n + nd))
    A[0, :n] = np.cos(phi)
    A[1, :n] = np.sin(phi)
    for j in range(1, n):
        A[0, n + j] = -(P[n, 1] - P[j, 1])
        A[1, n + j] = P[n, 0] - P[j, 0]
    A[2, n:2 * n] = 1.0
    for k, (a0, b0, _) in enumerate(inp.D):
        row = 3 + k
        A[row, 2 * n + k] = -1.0
        w = adj_wall(a0, b0, n)
        if w is not None:
            A[row, w] = 1.0
            continue
        a, b = min(a0, b0), max(a0, b0)
        diff = P[b] - P[a]
        dist = float(np.hypot(*diff))
        if dist == 0:
            continue
        ux, uy = diff / dist
        for i in range(a, b):
            A[row, i] = ux * math.cos(phi[i]) + uy * math.sin(phi[i])
        for j in range(1, b):
            pivot = P[a] if j <= a else P[j]
            dv = P[b] - pivot
            A[row, n + j] = ux * -dv[1] + uy * dv[0]
    return A


def adjust(inp, mode, start, wrapped=True):
    n, nd = inp.n, len(inp.D)
    obs_t, sg = [], []
    for i in range(n):
        k = inp.c[i][0]
        if k == 'M':
            obs_t.append(True)
            sg.append(SM)
        elif k == 'R' and mode == 'ME':
            obs_t.append(True)
            sg.append(SR)
        else:
            obs_t.append(False)
            sg.append(SF)
    t0 = list(inp.that)
    if inp.c[0][0] == 'F':
        t0[0] = TAU * inp.s - sum(inp.that[1:])
    x0 = np.array(inp.L + t0 + [float(d[2]) for d in inp.D])
    q = np.array([SL ** 2] * n + [v ** 2 for v in sg] + [SD ** 2] * nd)
    x = x0.copy()
    x[n:2 * n] = start
    for it in range(1, 52):
        cc = C_of(inp, x, wrapped)
        if np.max(np.abs(cc)) <= RES:
            break
        if it == 51:
            return None
        A = A_of(inp, x)
        AQ = A * q
        reference = x0.copy()
        for i in range(n):
            if not obs_t[i]:
                reference[n + i] = x[n + i]
        try:
            w = np.linalg.solve(AQ @ A.T, cc + A @ (reference - x))
        except np.linalg.LinAlgError:
            return None
        x = reference - AQ.T @ w
        if not np.all(np.isfinite(x)) or np.any(x[:n] <= 0):
            return None
    v = x - x0
    U = inp.U
    mm, names = [], []
    q2 = 0.0
    for i in range(n):
        mm.append(abs(v[i]))
        names.append(('W', i))
        q2 += (v[i] / SL) ** 2
    for i in range(n):
        if obs_t[i]:
            dv = wrap(v[n + i])
            mm.append(abs(dv) * U / TAU)
            names.append(('C', i))
            q2 += (dv / sg[i]) ** 2
    for k in range(nd):
        mm.append(abs(v[2 * n + k]))
        names.append(('D', k))
        q2 += (v[2 * n + k] / SD) ** 2
    top = max(mm)
    big = next(i for i, value in enumerate(mm) if value >= top - RES)
    return dict(L=[float(z) for z in x[:n]], t=[float(z) for z in x[n:2 * n]], S=float(sum(mm)),
                maxc=float(top), largest=names[big] if top > RES else None, q2=float(q2), it=it)


def necessary(inp, relax=0.0):
    L, n = inp.L, inp.n
    longest = max(L)
    if longest >= sum(L) - longest - MINF:
        return 'WALL_TOO_LONG'
    for a0, b0, dd in inp.D:
        a, b = min(a0, b0), max(a0, b0)
        if adj_wall(a, b, n) is not None:
            continue
        for chain in (L[a:b], L[b:] + L[:a]):
            total = sum(chain)
            biggest = max(chain)
            if dd > total + relax or dd < 2 * biggest - total - relax:
                return 'DIAGONAL_IMPOSSIBLE'
    return None


def closure_measure(inp):
    n = inp.n
    P, _ = walk(inp.L, inp.that)
    g = float(np.hypot(*P[n]))
    rs = [abs(float(np.hypot(*(P[a] - P[b]))) - d) for a, b, d in inp.D]
    a0 = 0.0
    if inp.c[0][0] != 'F':
        a0 = abs(wrap(inp.that[0] - (TAU * inp.s - sum(inp.that[1:])))) * inp.U / TAU
    return max([g, a0] + rs), P


def result(status, path, **kw):
    return dict(status=status, path=path, **kw)


def branch_b(inp, an, start, T, path, alternatives=()):
    n = inp.n
    ad = adjust(inp, 'SD', start)
    if ad is None:
        return result('CONFLICT', path + '/B2', reason='ADJUSTMENT_NOT_CONVERGED')
    P, _ = walk(ad['L'], ad['t'])
    if not simple(P[:n]):
        return result('CONFLICT', path + '/B3', reason='NO_SIMPLE_SHAPE')
    rho_l = rank_at(inp, ad['L'], ad['t'])
    missing = an['v'] - rho_l
    r = an['m'] - rho_l
    info = dict(L=ad['L'], t=ad['t'], ad=ad, missing=missing, r=r, rho_l=rho_l)
    if r >= 1 and ad['S'] > T and ad['q2'] > q99(r):
        return result('CONFLICT', path + '/B5', reason='MEASUREMENTS_INCONSISTENT', ad=ad, r=r)
    if missing == 0 and violated(inp, ad['t']):
        return result('CONFLICT', path + '/B6', reason='TURN_DIRECTION_VIOLATED', ad=ad, r=r)
    if missing > 0:
        return result('PROPOSED', path + '/B7', proposal='UNDERDETERMINED', **info)
    amb = ambiguity(inp, ad['L'], ad['t'], alternatives, T)
    if amb:
        # Decision 3 of T-ARC-008: also a room that would need an adjustment becomes a proposal with its corrections.
        instead = 'CONFIRMED' if ad['maxc'] <= EXACT else 'NEEDS_ADJUSTMENT'
        return result('PROPOSED', path + '/B7', proposal='AMBIGUOUS_SHAPE', ambiguity=amb, instead=instead, **info)
    if ad['maxc'] <= EXACT:
        return result('CONFIRMED', path + '/B8', **info)
    explicit = r >= 1 and ad['S'] > T and q95(r) < ad['q2'] <= q99(r)
    return result('NEEDS_ADJUSTMENT', path + '/B9', explicit=explicit, **info)


def evaluate(inp, abs_t=50.0, rel=0.01, relax=0.0):
    n = inp.n
    bad = necessary(inp, relax)
    if bad:
        return result('CONFLICT', 'N', reason=bad)
    T = max(abs_t, rel * inp.U)
    e, P = closure_measure(inp)
    if e <= EXACT:
        if not simple(P[:n]):
            return result('CONFLICT', 'E', reason='NO_SIMPLE_SHAPE', e=e)
        return result('CONFIRMED', 'E', L=list(inp.L), t=target_shape_turns(inp), e=e)
    if e <= T:
        start = target_shape_turns(inp)
        ad = adjust(inp, 'ME', start)
        if ad is None:
            return result('CONFLICT', 'M', reason='ADJUSTMENT_NOT_CONVERGED', e=e)
        Q, _ = walk(ad['L'], ad['t'])
        if not simple(Q[:n]):
            return result('CONFLICT', 'M', reason='NO_SIMPLE_SHAPE', e=e)
        return result('NEEDS_ADJUSTMENT', 'M', L=ad['L'], t=ad['t'], ad=ad, e=e)
    an = analyze_constraints(inp)
    target = target_shape_turns(inp)
    if an['m'] == an['rho']:
        solved = solver(inp, an['sel'])
        if solved is None:
            return branch_b(inp, an, target, T, 'A>B')
        sol, alternatives = solved
        rho_l = rank_at(inp, inp.L, sol)
        missing = an['v'] - rho_l
        if missing > 0:
            return result('PROPOSED', 'A2', proposal='UNDERDETERMINED', L=list(inp.L), t=sol, missing=missing,
                          r=an['m'] - rho_l, rho_l=rho_l)
        if violated(inp, sol):
            return result('CONFLICT', 'A3', reason='TURN_DIRECTION_VIOLATED', t=sol)
        amb = ambiguity(inp, inp.L, sol, alternatives, T)
        if amb:
            return result('PROPOSED', 'A4', proposal='AMBIGUOUS_SHAPE', ambiguity=amb, instead='CONFIRMED',
                          L=list(inp.L), t=sol, missing=0, r=0)
        return result('CONFIRMED', 'A5', L=list(inp.L), t=sol, missing=0, r=0)
    if an['v'] == 0:
        return branch_b(inp, an, target, T, 'B')
    solved = solver(inp, an['sel'])
    if solved is None:
        return branch_b(inp, an, target, T, 'B1>B')
    start, alternatives = solved
    start = list(start)
    start[0] = TAU * inp.s - sum(start[1:])
    return branch_b(inp, an, start, T, 'B', alternatives)


def target_shape_turns(inp):
    t = list(inp.that)
    if inp.c[0][0] == 'F':
        t[0] = TAU * inp.s - sum(t[1:])
    return t
