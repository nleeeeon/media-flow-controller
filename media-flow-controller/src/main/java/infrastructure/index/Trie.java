package infrastructure.index;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 前方一致検索対応・複数値保持Trie
 */
public final class Trie<V> {

    private final Node<V> root = new Node<>();

    private static final class Node<V> {
        private final Map<Character, Node<V>> children = new HashMap<>();
        private final Set<V> values = new HashSet<>();
        private boolean isEnd;
    }
    
    

    /* ----------------------------
       登録
       ---------------------------- */
    public boolean insert(String key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        Node<V> current = root;
        for (char c : key.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new Node<>());
        }

        current.isEnd = true;
        return current.values.add(value);
    }

    /* ----------------------------
       前方一致検索
       ---------------------------- */
    public Set<V> get(String prefix) {
        Objects.requireNonNull(prefix);

        Node<V> node = findNode(prefix);
        if (node == null) {
            return Collections.emptySet();
        }

        Set<V> results = new HashSet<>();
        collect(node, results);
        return results;
    }

    /* ----------------------------
       完全一致取得
       ---------------------------- */
    public Set<V> getExact(String key) {
        Objects.requireNonNull(key);

        Node<V> node = findNode(key);
        if (node == null || !node.isEnd) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(node.values);
    }

    /* ----------------------------
       特定valueのみ削除
       ---------------------------- */
    public boolean removeValue(String key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return removeValue(root, key, value, 0);
    }

    private boolean removeValue(Node<V> current, String key, V value, int index) {

        if (index == key.length()) {
            if (!current.isEnd) return false;

            boolean removed = current.values.remove(value);

            // 終端フラグは「値があるか」で決める
            if (current.values.isEmpty()) {
                current.isEnd = false;
            }

            // このノードを削除してよいかを返す
            return removed && current.children.isEmpty() && !current.isEnd;
        }

        char c = key.charAt(index);
        Node<V> child = current.children.get(c);
        if (child == null) return false;

        boolean shouldDeleteChild =
                removeValue(child, key, value, index + 1);

        if (shouldDeleteChild) {
            current.children.remove(c);
        }

        // 親も不要なら伝播
        return current.children.isEmpty() && !current.isEnd;
    }

    /* ----------------------------
       キー全削除
       ---------------------------- */
    public boolean delete(String key) {
        Objects.requireNonNull(key);
        return delete(root, key, 0);
    }

    private boolean delete(Node<V> current, String key, int index) {
        if (index == key.length()) {
            if (!current.isEnd) {
                return false;
            }
            current.isEnd = false;
            current.values.clear();
            return current.children.isEmpty();
        }

        char c = key.charAt(index);
        Node<V> child = current.children.get(c);
        if (child == null) {
            return false;
        }

        boolean shouldDeleteChild = delete(child, key, index + 1);

        if (shouldDeleteChild) {
            current.children.remove(c);
            return current.children.isEmpty() && !current.isEnd;
        }

        return false;
    }

    /* ----------------------------
       内部処理
       ---------------------------- */
    private void collect(Node<V> node, Set<V> results) {
        if (node.isEnd) {
            results.addAll(node.values);
        }
        for (Node<V> child : node.children.values()) {
            collect(child, results);
        }
    }

    private Node<V> findNode(String key) {
        Node<V> current = root;
        for (char c : key.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
  //以下はテスト用
	  public Map<String, Set<V>> dumpAll() {
		    Map<String, Set<V>> result = new LinkedHashMap<>();
		    collectAll(root, new StringBuilder(), result);
		    return result;
		}

		private void collectAll(Node<V> node,
		                        StringBuilder prefix,
		                        Map<String, Set<V>> result) {

		    if (node.isEnd) {
		        result.put(prefix.toString(),
		                   Collections.unmodifiableSet(node.values));
		    }

		    for (Map.Entry<Character, Node<V>> entry : node.children.entrySet()) {
		        prefix.append(entry.getKey());
		        collectAll(entry.getValue(), prefix, result);
		        prefix.deleteCharAt(prefix.length() - 1);
		    }
		}
}