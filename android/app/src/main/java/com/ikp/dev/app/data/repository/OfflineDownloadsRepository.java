package com.ikp.dev.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.core.error.ApiError;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.local.dao.UserDataDao;
import com.ikp.dev.app.data.local.entity.UserDataEntities.DownloadMetadataEntity;
import com.ikp.dev.app.data.remote.LibraryApi;
import com.ikp.dev.app.data.remote.dto.LibraryDtos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Response;

/** Downloads licence-authorized public books to app-private storage and indexes them in Room. */
public final class OfflineDownloadsRepository {

    private final Context context;
    private final IkpDatabase database;
    private final LibraryApi libraryApi;

    public OfflineDownloadsRepository(@NonNull Context context, @NonNull ApiClient client,
                                      @NonNull IkpDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.libraryApi = client.create(LibraryApi.class);
    }

    public Outcome<DownloadMetadataEntity> downloadBook(String bookId, String title) {
        try {
            Response<ApiResponse<LibraryDtos.BookDetailDto>> detailResponse =
                    libraryApi.bookDetail(bookId).execute();
            if (!detailResponse.isSuccessful() || detailResponse.body() == null
                    || detailResponse.body().data == null) {
                return Outcome.fail(ErrorMapper.fromResponse(detailResponse));
            }
            LibraryDtos.BookDetailDto detail = detailResponse.body().data;
            LibraryDtos.BookFileRefDto file = chooseFile(detail.files);
            if (file == null || !Boolean.TRUE.equals(file.downloadAllowed)) {
                return Outcome.fail(new ApiError(ErrorMapper.LICENCE, "book_download_not_allowed"));
            }

            String format = safeFormat(file.format);
            if (format == null) {
                return Outcome.fail(new ApiError(ErrorMapper.LICENCE, "book_download_not_allowed"));
            }
            Map<String, String> request = new HashMap<>();
            request.put("fileId", file.fileId);
            Response<ApiResponse<LibraryDtos.DownloadTokenDto>> tokenResponse =
                    libraryApi.downloadToken(bookId, request).execute();
            if (!tokenResponse.isSuccessful() || tokenResponse.body() == null
                    || tokenResponse.body().data == null
                    || tokenResponse.body().data.url == null) {
                return Outcome.fail(ErrorMapper.fromResponse(tokenResponse));
            }

            LibraryDtos.DownloadTokenDto token = tokenResponse.body().data;
            File directory = new File(context.getFilesDir(), "offline_downloads");
            if (!directory.exists() && !directory.mkdirs()) {
                return Outcome.fail(new ApiError(ApiError.UNKNOWN, "error_generic"));
            }
            String safeId = bookId.replaceAll("[^A-Za-z0-9_-]", "_");
            File target = new File(directory, safeId + "." + format);
            File temporary = new File(directory, safeId + "." + format + ".part");
            long size = fetch(token.url, temporary);
            if (size <= 0L) {
                temporary.delete();
                return Outcome.fail(new ApiError(ApiError.BAD_RESPONSE, "error_generic"));
            }
            if (token.sha256 != null && token.sha256.matches("(?i)[0-9a-f]{64}")) {
                String actual = sha256(temporary);
                if (!token.sha256.equalsIgnoreCase(actual)) {
                    temporary.delete();
                    return Outcome.fail(new ApiError(ApiError.BAD_RESPONSE, "error_generic"));
                }
            }
            if (target.exists() && !target.delete()) {
                temporary.delete();
                return Outcome.fail(new ApiError(ApiError.UNKNOWN, "error_generic"));
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
                return Outcome.fail(new ApiError(ApiError.UNKNOWN, "error_generic"));
            }

            DownloadMetadataEntity row = new DownloadMetadataEntity();
            row.mediaId = safeId + ":" + format;
            row.bookId = bookId;
            row.title = title == null || title.trim().isEmpty() ? detail.title : title.trim();
            row.format = format;
            row.sizeBytes = size;
            row.sha256 = token.sha256;
            row.status = "COMPLETE";
            row.urlExpiresAtEpochMs = null;
            row.downloadedAtEpochMs = System.currentTimeMillis();
            row.filePath = target.getAbsolutePath();
            database.userDataDao().upsertDownload(row);
            return Outcome.ok(row);
        } catch (Exception e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    private static LibraryDtos.BookFileRefDto chooseFile(List<LibraryDtos.BookFileRefDto> files) {
        if (files == null || files.isEmpty()) return null;
        for (LibraryDtos.BookFileRefDto file : files) {
            if (file != null && Boolean.TRUE.equals(file.downloadAllowed)
                    && "pdf".equalsIgnoreCase(file.format)) return file;
        }
        for (LibraryDtos.BookFileRefDto file : files) {
            if (file != null && Boolean.TRUE.equals(file.downloadAllowed)) return file;
        }
        return null;
    }

    private static String safeFormat(String format) {
        if (format == null) return null;
        String normalized = format.toLowerCase(Locale.ROOT).replace(".", "").trim();
        if (normalized.contains("pdf")) return "pdf";
        if (normalized.contains("epub")) return "epub";
        return null;
    }

    private static long fetch(String url, File out) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestMethod("GET");
        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            connection.disconnect();
            throw new java.io.IOException("Download server returned " + response);
        }
        long size = 0L;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(out)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                size += count;
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }
        return size;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }
}
