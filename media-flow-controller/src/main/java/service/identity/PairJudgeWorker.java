package service.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import domain.unionfind.DSU;
import domain.unionfind.NameIds;

public final class PairJudgeWorker {

    private final ConcurrentLinkedDeque<int[]> queue = new ConcurrentLinkedDeque<>();
    private final Set<String> enqueued  = ConcurrentHashMap.newKeySet();
    private final Set<String> negatives = ConcurrentHashMap.newKeySet();

    private final NameIds ids = new NameIds();
    private final DSU dsu = new DSU(64);

    private final ArtistPairJudge judge;
    private final ArtistIdentityUpdater updater;

    public PairJudgeWorker(ArtistPairJudge judge, ArtistIdentityUpdater updater) {
        this.judge = judge;
        this.updater = updater;
    }

    private static String key(int a, int b) {
        return (a < b) ? (a + "#" + b) : (b + "#" + a);
    }

    public void submit(String A, String B) {
        int a = ids.id(A), b = ids.id(B);
        if (a == b) return;

        String k = key(a, b);
        if (negatives.contains(k)) return;
        if (!enqueued.add(k)) return;

        dsu.ensure(Math.max(a, b) + 1);
        queue.addLast(new int[]{a, b});
    }

    /** キューが空になるまで処理 */
    public void processAll() throws InterruptedException {
        while (!queue.isEmpty()) {
            int[] e = queue.pollFirst();
            if (e == null) continue;

            int a = e[0], b = e[1];
            if (dsu.same(a, b)) continue;

            String na = ids.name(a), nb = ids.name(b);

            String canonical;
            try {
                canonical = judge.resolveCanonical(na, nb);
            } catch (Exception ex) {
                
                
                ex.printStackTrace();
                continue;
            }

            if (canonical == null) {
                negatives.add(key(a, b));
                continue;
            }

            // canonical が na か nb のどちらかで返る前提
            boolean keepA = na.equals(canonical);
            int newId = keepA ? a : b;
            int oldId = keepA ? b : a;
            String newArtist = keepA ? na : nb;
            String oldArtist = keepA ? nb : na;

            updater.applySameArtist(newArtist, oldArtist, newId, oldId);
            
            dsu.unionPrefer(newId, oldId, newId);
        }
    }

    public Map<String, List<String>> groups() {
        Map<Integer, List<Integer>> tmp = new HashMap<>();
        int n = ids.size();
        for (int i = 0; i < n; i++) {
            int r = dsu.find(i);
            tmp.computeIfAbsent(r, k -> new ArrayList<>()).add(i);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (var e : tmp.entrySet()) {
            String rep = ids.name(e.getKey());
            List<String> members = new ArrayList<>();
            for (int id : e.getValue()) members.add(ids.name(id));
            out.put(rep, members);
        }
        return out;
    }
}