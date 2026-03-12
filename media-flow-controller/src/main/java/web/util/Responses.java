package web.util;

import java.io.IOException;

import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletResponse;

/**
 * HttpServletResponse に書き込む専用クラス
 * ※ JSONの中身構築は Jsons に任せる
 */
public final class Responses {

	private Responses() {
	}

	/** JSONレスポンスの基本設定 */
	public static void prepareJson(HttpServletResponse resp) {
		resp.setContentType("application/json; charset=UTF-8");
	}

	/** JsonObject をそのまま書き込む */
	public static void writeJson(HttpServletResponse resp, JsonObject json) throws IOException {
		resp.getWriter().write(json.toString());
	}

	/** 成功レスポンス（汎用） */
	public static void writeOk(HttpServletResponse resp) throws IOException {
		writeJson(resp, Jsons.ok());
	}

	public static void writeUploadOk(HttpServletResponse resp, boolean success, String message) throws IOException {
		writeJson(resp, Jsons.okUpload(success, message));
	}

	/** エラーレスポンス（HTTPステータスを指定しない版） */
	public static void writeErr(HttpServletResponse resp, String code, String message) throws IOException {
		writeJson(resp, Jsons.err(code, message));
	}

	/** エラーレスポンス（HTTPステータス付き版） */
	public static void writeErr(HttpServletResponse resp, int status, String code, String message) throws IOException {
		resp.setStatus(status);
		writeJson(resp, Jsons.err(code, message));
	}
}