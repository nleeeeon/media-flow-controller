package domain.unionfind;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class DSU {
    private int[] parent, size, canonical;
    private final ReentrantLock lock = new ReentrantLock();

    public DSU(int n) {
        int cap = Math.max(1, n);
        parent = new int[cap];
        size = new int[cap];
        canonical = new int[cap];
        for (int i = 0; i < cap; i++) { parent[i] = i; size[i] = 1; canonical[i] = i; }
    }

    public void ensure(int need) {
        lock.lock();
        try {
            if (need <= parent.length) return;
            int m = Math.max(need, parent.length * 2);
            parent = Arrays.copyOf(parent, m);
            size   = Arrays.copyOf(size,   m);
            canonical = Arrays.copyOf(canonical, m);
            for (int i = need; i < m; i++) { parent[i] = i; size[i] = 1; canonical[i] = i; }
        } finally {
            lock.unlock();
        }
    }

    private int findNoLock(int x) {
        if (parent[x] == x) return x;
        int r = x;
        while (r != parent[r]) r = parent[r];
        int cur = x;
        while (cur != r) {
            int p = parent[cur];
            parent[cur] = r;
            cur = p;
        }
        return r;
    }

    public int find(int x) {
        lock.lock();
        try { return findNoLock(x); }
        finally { lock.unlock(); }
    }

    public boolean same(int a, int b) {
        lock.lock();
        try { return findNoLock(a) == findNoLock(b); }
        finally { lock.unlock(); }
    }

    /** size優先で木を安定させつつ、見た目代表 preferId をできる限り採用する */
    public void unionPrefer(int a, int b, int preferId) {
        lock.lock();
        try {
            int ra = findNoLock(a), rb = findNoLock(b);
            if (ra == rb) {
                // 同集合なら見た目代表の更新だけ（preferId がこの集合内なら反映）
                if (findNoLock(preferId) == ra) canonical[ra] = preferId;
                return;
            }
            // 木の安定化（小さい方を大きい方に付ける）
            if (size[ra] < size[rb]) { int t = ra; ra = rb; rb = t; }
            parent[rb] = ra;
            size[ra] += size[rb];

            // 見た目代表の決定
            int chosen;
            int pr = findNoLock(preferId);
            if (pr == ra || pr == rb) chosen = preferId;           // 希望が統合集合内
            else                       chosen = canonical[ra];      // 既存代表を継続
            canonical[ra] = chosen;
        } finally {
            lock.unlock();
        }
    }

    int canonicalOf(int x) {
        lock.lock();
        try { return canonical[ findNoLock(x) ]; }
        finally { lock.unlock(); }
    }
}