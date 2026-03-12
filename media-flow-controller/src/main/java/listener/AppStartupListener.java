package listener;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;

import infrastructure.concurrent.AnimeApiQueueExecutor;
import infrastructure.concurrent.BackgroundExecutorPiano;
import infrastructure.concurrent.SongApiQueueExecutor;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.index.IdGenerator;
import infrastructure.index.MusicIndexManager;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Tomcat起動時（サーバー起動 or デプロイ時）に一度だけ実行される。
 */
@WebListener
public class AppStartupListener implements ServletContextListener {
	public static final MusicIndexManager musicIndexManager;
	public static final IdGenerator idGenerator;
	static {
		musicIndexManager = new MusicIndexManager();
		idGenerator = new IdGenerator();
		
    }
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("=== AppStartupListener: サーバー起動時 ===");
        try {
            // ここに起動時に実行したいクラスを呼び出す
            myStartTask(sce.getServletContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("=== AppStartupListener: サーバー停止時 ===");
        
        
        //以下はチャットgptに投げてとりあえず書いたコード。読み込んだdriverを閉じる
     // 1) 例外を握りつぶさずログ → finally で必ずドライバ解除まで行く
        ClassLoader appCl = this.getClass().getClassLoader();
        ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(appCl);

        try {
            // 先にコネクションプールや自前のリソースを全部閉じる
        	SongApiQueueExecutor.shutdown();
            BackgroundExecutorPiano.shutdown();
            AnimeApiQueueExecutor.shutdown();
            try {
            	musicIndexManager.uploadDatabase();
    		} catch (SQLException e) {
    			// TODO 自動生成された catch ブロック
    			e.printStackTrace();
    		}

        } catch (Throwable t) {
            t.printStackTrace(); // 「重大: …」の真犯人をまず見つける
        } finally {
            // 2) 取りこぼしの無いドライバ解除（子クラスローダも含めて判定）
            try {
                var it = java.sql.DriverManager.getDrivers().asIterator();
                while (it.hasNext()) {
                    var d = it.next();
                    ClassLoader drvCl = d.getClass().getClassLoader();
                    if (isSameOrChild(drvCl, appCl)) {
                        try {
                            java.sql.DriverManager.deregisterDriver(d);
                            // System.out.println("Deregistered: " + d);
                        } catch (java.sql.SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                Thread.currentThread().setContextClassLoader(oldTCCL);
            }
        }
    }
    
    private static boolean isSameOrChild(ClassLoader drvCl, ClassLoader appCl) {
        if (drvCl == null) return false; // ブート/システムCLなら違う
        if (drvCl == appCl) return true;
        for (ClassLoader p = drvCl.getParent(); p != null; p = p.getParent()) {
            if (p == appCl) return true;
        }
        return false;
    }
    private static java.sql.Driver pgDriver;
    private void myStartTask(jakarta.servlet.ServletContext ctx) throws SQLException, ClassNotFoundException {
    	try {

            // 明示登録（Tomcat の再デプロイ時リーク防止のため）
    		//Class.forName("org.postgresql.Driver"); // ★ PostgreSQL ドライバ
            //pgDriver = new org.postgresql.Driver();
            //DriverManager.registerDriver(pgDriver);

            // ---（参考）MariaDB を使う場合は下を使う ---
             Class.forName("org.mariadb.jdbc.Driver");
             pgDriver = new org.mariadb.jdbc.Driver();
             DriverManager.registerDriver(pgDriver);
             util.string.EnglishNameToRomaji.loadFromWebInfAssets(
                     ctx,
                     "/WEB-INF/assets/all_names.txt"
             );
             
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("DB driver not found (PostgreSQL)", e);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to register PostgreSQL driver", e);
        } catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
    	DbVendorChecker.init(Db.get());
    	musicIndexManager.init();
    	idGenerator.init();
    	
    	

        
    }
    
    
}
