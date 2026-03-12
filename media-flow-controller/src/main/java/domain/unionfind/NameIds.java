package domain.unionfind;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class NameIds {
    private final ConcurrentHashMap<String, Integer> toId = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> toName = new CopyOnWriteArrayList<>();
    private final AtomicInteger next = new AtomicInteger(0);

    public int id(String name) {
        return toId.computeIfAbsent(name, k -> {
            int id = next.getAndIncrement();
            toName.add(k); // index=id を保証
            return id;
        });
    }
    public String name(int id) { return toName.get(id); }
    public int size() { return toName.size(); }
}