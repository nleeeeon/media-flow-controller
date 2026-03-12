package dao.youtube;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class Channels_dao {
	private Connection c() throws SQLException {
		return Db.get();
	}

	private static final DbVendor VENDOR = DbVendorChecker.get();

	public void upsertChannels(
			Map<String, Long> subscriberMap,
			Map<String, String> titleMap) throws SQLException {

		if (subscriberMap == null || subscriberMap.isEmpty())
			return;

		final String sql;

		if (VENDOR == DbVendor.POSTGRES) {
			sql = """
					INSERT INTO channels (
					  channel_id,
					  channel_title,
					  subscriber_count
					) VALUES (?,?,?)
					ON CONFLICT (channel_id) DO UPDATE SET
					  channel_title     = EXCLUDED.channel_title,
					  subscriber_count  = EXCLUDED.subscriber_count
					""";
		} else {
			sql = """
					INSERT INTO channels (
					  channel_id,
					  channel_title,
					  subscriber_count
					) VALUES (?,?,?)
					ON DUPLICATE KEY UPDATE
					  channel_title=VALUES(channel_title),
					  subscriber_count=VALUES(subscriber_count)
					""";
		}

		try (var con = c(); var ps = con.prepareStatement(sql)) {

			int count = 0;

			for (var e : subscriberMap.entrySet()) {

				String channelId = e.getKey();
				Long subscriberCount = e.getValue();
				String title = titleMap.get(channelId);

				ps.setString(1, channelId);

				if (title != null) {
					ps.setString(2, title);
				} else {
					ps.setNull(2, java.sql.Types.VARCHAR);
				}

				if (subscriberCount != null) {
					ps.setLong(3, subscriberCount);
				} else {
					ps.setNull(3, java.sql.Types.BIGINT);
				}

				ps.addBatch();

				if (++count % 500 == 0) {
					ps.executeBatch();
				}
			}

			ps.executeBatch();
		}
	}

	public void upsertChannelIds(Set<String> channelIds) throws SQLException {

		if (channelIds == null || channelIds.isEmpty())
			return;

		final String sql;

		if (VENDOR == DbVendor.POSTGRES) {
			sql = """
					INSERT INTO channels (channel_id)
					VALUES (?)
					ON CONFLICT (channel_id) DO NOTHING
					""";
		} else {
			sql = """
					INSERT INTO channels (channel_id)
					VALUES (?)
					ON DUPLICATE KEY UPDATE
					  channel_id = VALUES(channel_id)
					""";
		}

		try (var con = c(); var ps = con.prepareStatement(sql)) {

			int count = 0;

			for (String channelId : channelIds) {

				ps.setString(1, channelId);
				ps.addBatch();

				if (++count % 500 == 0) {
					ps.executeBatch();
				}
			}

			ps.executeBatch();
		}
	}
}
