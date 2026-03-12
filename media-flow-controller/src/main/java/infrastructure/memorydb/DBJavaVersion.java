package infrastructure.memorydb;

	import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

	public class DBJavaVersion<ID, T> {
	  private final Function<T, ID> idFn;
	  private final Map<ID, T> byId = new ConcurrentHashMap<>();
	  private final Map<String, Index<?, T>> indexes = new ConcurrentHashMap<>();
	  private final ReadWriteLock rw = new ReentrantReadWriteLock();

	  public DBJavaVersion(Function<T, ID> idFn) { this.idFn = idFn; }

	  // ---- インデックス登録 ----
	  public <K> void addUniqueIndex(String name, Function<T, K> keyFn) {
	    indexes.put(name, new UniqueIndex<>(keyFn));
	  }
	  public <K> void addNonUniqueIndex(String name, Function<T, K> keyFn) {
	    indexes.put(name, new NonUniqueIndex<>(keyFn));
	  }

	  // ---- CRUD ----
	  public T upsert(T row) {
	    rw.writeLock().lock();
	    try {
	      ID id = idFn.apply(row);
	      T prev = byId.put(id, row);
	      // インデックス反映
	      for (Index<?, T> ix : indexes.values()) ix.onUpsert(prev, row);
	      
	      return prev;
	    } finally { rw.writeLock().unlock(); }
	  }

	  public List<T> upsertAll(Collection<T> rows) {
		    rw.writeLock().lock();
		    try {
		        List<T> prevs = new ArrayList<>();
		        for (T r : rows) {
		            T prev = upsertNoLock(r);
		            if (prev != null) prevs.add(prev);
		        }
		        return prevs;
		    } finally { rw.writeLock().unlock(); }
		}
	  private T upsertNoLock(T row) {
	    ID id = idFn.apply(row);
	    T prev = byId.put(id, row);
	    for (Index<?, T> ix : indexes.values()) ix.onUpsert(prev, row);
	    return prev;
	  }

	  public T findById(ID id) {
	    rw.readLock().lock();
	    try { return byId.get(id); }
	    finally { rw.readLock().unlock(); }
	  }

	  public T deleteById(ID id) {
	    rw.writeLock().lock();
	    try {
	      T prev = byId.remove(id);
	      if (prev != null) for (Index<?, T> ix : indexes.values()) ix.onDelete(prev);
	      
	      return prev;
	    } finally { rw.writeLock().unlock(); }
	  }

	  // ---- インデックス検索 ----
	  /*@SuppressWarnings("unchecked")
	  public <K> T findUnique(String indexName, K key) {
	    rw.readLock().lock();
	    try { return ((UniqueIndex<K, T>) indexes.get(indexName)).get(key); }
	    finally { rw.readLock().unlock(); }
	  }*/
	  
	  @SuppressWarnings("unchecked")
	  public <K> T findUnique(String indexName, K key) {
	    rw.readLock().lock();
	    try {
	      Index<?, T> ix = indexes.get(indexName);
	      if (ix == null) return null;

	      if (ix instanceof UniqueIndex<?, ?>) {
	        return ((UniqueIndex<K, T>) ix).get(key);
	      }
	      if (ix instanceof UniqueMultiIndex<?, ?>) {
	        return ((UniqueMultiIndex<K, T>) ix).get(key);
	      }
	      // ユニーク以外は null（呼び出し側で scan を使う）
	      return null;
	    } finally { rw.readLock().unlock(); }
	  }

	  /*@SuppressWarnings("unchecked")
	  public <K> List<T> scan(String indexName, K key) {
	    rw.readLock().lock();
	    try {
	      var s = ((NonUniqueIndex<K, T>) indexes.get(indexName)).get(key);
	      if (s == null || s.isEmpty()) return List.of();
	      return List.copyOf(s);
	    } finally { rw.readLock().unlock(); }
	  }*/
	  @SuppressWarnings("unchecked")
	  public <K> List<T> scan(String indexName, K key) {
	    rw.readLock().lock();
	    try {
	      Index<?, T> ix = indexes.get(indexName);
	      if (ix == null) return List.of();

	      // Non-unique（単一キー）
	      if (ix instanceof NonUniqueIndex<?, ?>) {
	        var s = ((NonUniqueIndex<K, T>) ix).get(key);
	        if (s == null || s.isEmpty()) return List.of();
	        return List.copyOf(s);
	      }

	      // Multi（多値キー）
	      if (ix instanceof MultiIndex<?, ?>) {
	        var s = ((MultiIndex<K, T>) ix).get(key);
	        if (s == null || s.isEmpty()) return List.of();
	        return List.copyOf(s);
	      }

	      // Unique（1件 or なし）
	      if (ix instanceof UniqueIndex<?, ?>) {
	        T v = ((UniqueIndex<K, T>) ix).get(key);
	        return (v == null) ? List.of() : List.of(v);
	      }
	      
	      if (ix instanceof UniqueMultiIndex<?, ?>) {
	          T v = ((UniqueMultiIndex<K, T>) ix).get(key);
	          return (v == null) ? List.of() : List.of(v);
	        }

	      // 未対応タイプ
	      return List.of();
	    } finally {
	      rw.readLock().unlock();
	    }
	  }


	  // ===== インデックス実装 =====
	  private interface Index<K, T> {
	    void onUpsert(T prev, T next);
	    void onDelete(T prev);
	  }
	  private static final class UniqueIndex<K, T> implements Index<K, T> {
	    private final Function<T, K> fn;
	    private final Map<K, T> map = new ConcurrentHashMap<>();
	    UniqueIndex(Function<T, K> fn) { this.fn = fn; }
	    T get(K k) { return map.get(k); }
	    public void onUpsert(T prev, T next) {
	      K kNew = fn.apply(next);
	      if (prev != null) {
	        K kOld = fn.apply(prev);
	        if (!Objects.equals(kOld, kNew)) map.remove(kOld);
	      }
	      map.put(kNew, next);
	    }
	    public void onDelete(T prev) { map.remove(fn.apply(prev)); }
	  }
	  private static final class NonUniqueIndex<K, T> implements Index<K, T> {
	    private final Function<T, K> fn;
	    private final Map<K, Set<T>> map = new ConcurrentHashMap<>();
	    NonUniqueIndex(Function<T, K> fn) { this.fn = fn; }
	    Set<T> get(K k) { return map.get(k); }
	    public void onUpsert(T prev, T next) {
	      if (prev != null) {
	        K kOld = fn.apply(prev);
	        K kNew = fn.apply(next);
	        if (!Objects.equals(kOld, kNew)) {
	          var s = map.get(kOld);
	          if (s != null) { s.remove(prev); if (s.isEmpty()) map.remove(kOld); }
	        } else {
	          var s = map.computeIfAbsent(kNew, kk -> ConcurrentHashMap.newKeySet());
	          s.remove(prev); // 上書き時は旧参照を外す
	        }
	      }
	      map.computeIfAbsent(fn.apply(next), kk -> ConcurrentHashMap.newKeySet()).add(next);
	    }
	    public void onDelete(T prev) {
	      var s = map.get(fn.apply(prev));
	      if (s != null) { s.remove(prev); if (s.isEmpty()) map.remove(fn.apply(prev)); }
	    }
	  }
	  
	  public List<T> findAll() {
		  rw.readLock().lock();
		  try {
		    // Mapの現在値をコピーして返す（呼び出し側が変更できない不変リスト）
		    return List.copyOf(byId.values());
		  } finally {
		    rw.readLock().unlock();
		  }
		}
	  
	// ---- 追加: 多値インデックス登録 ----
	  public <K> void addNonUniqueMultiIndex(String name, Function<T, Collection<K>> keysFn) {
	    indexes.put(name, new MultiIndex<>(keysFn));
	  }

	  // ===== 追加: 多値インデックス実装 =====
	  private static final class MultiIndex<K, T> implements Index<K, T> {
	    private final Function<T, Collection<K>> fn;               // レコード -> キーの集合
	    private final Map<K, Set<T>> map = new ConcurrentHashMap<>();

	    MultiIndex(Function<T, Collection<K>> fn) { this.fn = fn; }

	    Set<T> get(K k) { return map.get(k); }

	    // シンプル実装：prevの全キーから外し、nextの全キーに入れ直す
	    public void onUpsert(T prev, T next) {
	      if (prev != null) {
	        for (K k : safeKeys(fn.apply(prev))) {
	          var s = map.get(k);
	          if (s != null) { s.remove(prev); if (s.isEmpty()) map.remove(k); }
	        }
	      }
	      for (K k : safeKeys(fn.apply(next))) {
	        map.computeIfAbsent(k, kk -> ConcurrentHashMap.newKeySet()).add(next);
	      }
	    }

	    public void onDelete(T prev) {
	      for (K k : safeKeys(fn.apply(prev))) {
	        var s = map.get(k);
	        if (s != null) { s.remove(prev); if (s.isEmpty()) map.remove(k); }
	      }
	    }

	    private Collection<K> safeKeys(Collection<K> c) {
	      return (c == null) ? List.of() : c;
	    }
	  }
	  
	// ---- 追加: 多値ユニークインデックス登録 ----
	  public <K> void addUniqueMultiIndex(String name, Function<T, Collection<K>> keysFn) {
	    indexes.put(name, new UniqueMultiIndex<>(keysFn, false));
	  }

	  // 衝突を例外で弾きたい場合の厳格モード
	  public <K> void addUniqueMultiIndex(String name, Function<T, Collection<K>> keysFn, boolean strict) {
	    indexes.put(name, new UniqueMultiIndex<>(keysFn, strict));
	  }
	  
	// ===== 追加: 多値ユニークインデックス実装 =====
	  private static final class UniqueMultiIndex<K, T> implements Index<K, T> {
	    private final Function<T, Collection<K>> fn;   // レコード -> キー集合
	    private final Map<K, T> map = new ConcurrentHashMap<>();
	    private final boolean strict;                  // 衝突時に例外を投げるか

	    UniqueMultiIndex(Function<T, Collection<K>> fn, boolean strict) {
	      this.fn = fn;
	      this.strict = strict;
	    }

	    T get(K k) { return map.get(k); }

	    @Override
	    public void onUpsert(T prev, T next) {
	      // 1) 旧キーをすべて外す（prev!=null のとき）
	      if (prev != null) {
	        for (K k : safeKeys(fn.apply(prev))) {
	          // 参照同一で外す（別人が偶然同じキーに後から載っていたら外さない）
	          map.computeIfPresent(k, (kk, v) -> (v == prev) ? null : v);
	        }
	      }

	      // 2) 新キーを張る（ユニーク性をチェック）
	      for (K k : safeKeys(fn.apply(next))) {
	        T exists = map.get(k);
	        if (exists != null && exists != next) {
	          if (strict) {
	            throw new IllegalStateException("UniqueMultiIndex duplicate key: " + k);
	          }
	          // 後勝ちで上書き
	        }
	        map.put(k, next);
	      }
	    }

	    @Override
	    public void onDelete(T prev) {
	      for (K k : safeKeys(fn.apply(prev))) {
	        map.computeIfPresent(k, (kk, v) -> (v == prev) ? null : v);
	      }
	    }

	    private Collection<K> safeKeys(Collection<K> c) {
	      return (c == null) ? List.of() : c;
	    }
	  }

	  public int size() {
		    rw.readLock().lock();
		    try {
		        return byId.size();
		    } finally {
		        rw.readLock().unlock();
		    }
		}



	  
	}
