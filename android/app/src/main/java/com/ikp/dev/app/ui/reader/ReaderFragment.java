package com.ikp.dev.app.ui.reader;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.EpubParser;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Shared PDF/EPUB reader (M4 §9/§10, LIB-02/03). PDFs render via the built-in
 * PdfRenderer (zoom + page navigation); text-layer search is honestly marked
 * unsupported in this release. EPUBs render reflowable in a WebView with
 * light/dark/sepia themes, font-size control, TOC and REAL in-book search.
 * Progress + history are recorded locally and best-effort remotely.
 */
public class ReaderFragment extends Fragment {

    private TextView title;
    private TextView stateText;
    private TextView pdfSearchNote;
    private ImageView pdfPage;
    private WebView webView;
    private View pdfControls;
    private View epubControls;
    private PdfRenderer renderer;
    private PdfRenderer.Page currentPage;
    private int pageIndex = 0;
    private EpubParser.Epub epub;
    private int sectionIndex = 0;
    private String mode = "platform";
    private String bookId = "";
    private float fontScale = 1.0f;
    private String theme = "light";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reader, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mode = args.getString("mode", "platform");
            bookId = args.getString("bookId", "");
            String format = args.getString("format", "pdf");
            title = v.findViewById(R.id.tv_title);
            title.setText(args.getString("title", ""));
            fontScale = ServiceLocator.appPrefs().getFloat("reader_font_scale", 1.0f);
            theme = ServiceLocator.appPrefs().getString("reader_theme", "light");
            stateText = v.findViewById(R.id.state_text);
            pdfPage = v.findViewById(R.id.pdf_page);
            webView = v.findViewById(R.id.epub_web);
            pdfControls = v.findViewById(R.id.pdf_controls);
            epubControls = v.findViewById(R.id.epub_controls);
            pdfSearchNote = v.findViewById(R.id.tv_pdf_search_note);
            v.findViewById(R.id.btn_back).setOnClickListener(x -> {
                saveProgress();
                NavHostFragment.findNavController(this).popBackStack();
            });
            v.findViewById(R.id.btn_prev).setOnClickListener(x -> renderPage(pageIndex - 1));
            v.findViewById(R.id.btn_next).setOnClickListener(x -> renderPage(pageIndex + 1));
            v.findViewById(R.id.btn_theme).setOnClickListener(x -> {
                theme = "dark".equals(theme) ? "sepia" : "light".equals(theme) ? "dark" : "light";
                ServiceLocator.appPrefs().edit().putString("reader_theme", theme).apply();
                renderEpubSection();
            });
            v.findViewById(R.id.btn_font).setOnClickListener(x -> {
                fontScale = fontScale >= 1.6f ? 1.0f : (float) (fontScale + 0.15);
                ServiceLocator.appPrefs().edit().putFloat("reader_font_scale", fontScale).apply();
                renderEpubSection();
            });
            v.findViewById(R.id.btn_toc).setOnClickListener(x -> showToc());
            v.findViewById(R.id.btn_epub_search).setOnClickListener(x -> showEpubSearch());
            attachZoom();
            stateText.setText(R.string.state_loading);
            stateText.setVisibility(View.VISIBLE);
            final String fmt = format;
            String localPath = args.getString("localPath");
            if (localPath != null && !localPath.trim().isEmpty()) {
                File localFile = new File(localPath);
                if (!localFile.isFile()) {
                    stateText.setText(R.string.reader_open_failed);
                    return;
                }
                stateText.setVisibility(View.GONE);
                if ("epub".equalsIgnoreCase(fmt)) openEpub(localFile);
                else openPdf(localFile);
                recordHistory();
                return;
            }
            ServiceLocator.executors().io.execute(() -> {
                Outcome<ContentDtos.SignedUrlDto> token = "personal".equals(mode)
                        ? ServiceLocator.personalLibraryRepository().downloadToken(bookId)
                        : platformToken(bookId);
                if (!isAdded()) return;
                if (!token.isSuccess()) {
                    requireActivity().runOnUiThread(() -> {
                        stateText.setText(R.string.reader_open_failed);
                        Toast.makeText(requireContext(), R.string.reader_open_failed,
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                try {
                    File f = download(new URL(token.value.url), fmt);
                    recordHistory();
                    requireActivity().runOnUiThread(() -> {
                        stateText.setVisibility(View.GONE);
                        if ("epub".equalsIgnoreCase(fmt)) {
                            openEpub(f);
                        } else {
                            openPdf(f);
                        }
                    });
                } catch (Exception e) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            stateText.setText(R.string.reader_open_failed));
                }
            });
        }
    }

    private Outcome<ContentDtos.SignedUrlDto> platformToken(String bookPublicId) {
        try {
            com.ikp.dev.app.data.remote.LibraryApi api = ServiceLocator.apiClient()
                    .create(com.ikp.dev.app.data.remote.LibraryApi.class);
            retrofit2.Response<com.ikp.dev.app.core.network.ApiResponse<
                    com.ikp.dev.app.data.remote.dto.LibraryDtos.BookDetailDto>> detail =
                    api.bookDetail(bookPublicId).execute();
            if (!detail.isSuccessful() || detail.body() == null
                    || detail.body().data == null || detail.body().data.files == null
                    || detail.body().data.files.isEmpty()) {
                return Outcome.fail(new com.ikp.dev.app.core.error.ApiError(
                        com.ikp.dev.app.core.error.ErrorMapper.NOT_FOUND, "error_generic"));
            }
            com.ikp.dev.app.data.remote.dto.LibraryDtos.BookDetailDto d = detail.body().data;
            com.ikp.dev.app.data.remote.dto.LibraryDtos.BookFileRefDto chosen = d.files.get(0);
            for (com.ikp.dev.app.data.remote.dto.LibraryDtos.BookFileRefDto f : d.files) {
                if (Boolean.TRUE.equals(f.downloadAllowed)) {
                    chosen = f;
                    break;
                }
            }
            retrofit2.Response<com.ikp.dev.app.core.network.ApiResponse<
                    com.ikp.dev.app.data.remote.dto.LibraryDtos.DownloadTokenDto>> tokenRes =
                    api.downloadToken(bookPublicId,
                            java.util.Map.of("fileId", chosen.fileId)).execute();
            if (!tokenRes.isSuccessful() || tokenRes.body() == null || tokenRes.body().data == null) {
                return Outcome.fail(new com.ikp.dev.app.core.error.ApiError(
                        com.ikp.dev.app.core.error.ErrorMapper.LICENCE, "error_forbidden"));
            }
            ContentDtos.SignedUrlDto out = new ContentDtos.SignedUrlDto();
            out.url = tokenRes.body().data.url;
            out.textSearchable = chosen.ocrAvailable;
            return Outcome.ok(out);
        } catch (Exception e) {
            return Outcome.fail(new com.ikp.dev.app.core.error.ApiError(
                    com.ikp.dev.app.core.error.ApiError.UNKNOWN, "error_generic"));
        }
    }

    private static File download(URL url, String fmt) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        File out = File.createTempFile("reader_", "." + fmt);
        try (InputStream in = conn.getInputStream();
             FileOutputStream fout = new FileOutputStream(out)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fout.write(buf, 0, n);
        }
        return out;
    }

    private void recordHistory() {
        ServiceLocator.executors().io.execute(() ->
                ServiceLocator.userContentRepository()
                        .recordHistory("book:" + bookId, title == null ? null : title.getText().toString()));
    }

    // ------------------------------------------------------------------ PDF

    private void openPdf(File file) {
        ServiceLocator.executors().io.execute(() -> {
            try {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer r = new PdfRenderer(fd);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    renderer = r;
                    pdfPage.setVisibility(View.VISIBLE);
                    pdfControls.setVisibility(View.VISIBLE);
                    pdfSearchNote.setVisibility(View.VISIBLE);
                    pdfSearchNote.setText(R.string.reader_pdf_search_unavailable);
                    renderPage(0);
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        stateText.setText(R.string.reader_open_failed));
            }
        });
    }

    private void renderPage(int index) {
        if (renderer == null) return;
        if (index < 0 || index >= renderer.getPageCount()) return;
        ServiceLocator.executors().io.execute(() -> {
            try {
                PdfRenderer.Page page = renderer.openPage(index);
                Bitmap bmp = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2,
                        Bitmap.Config.ARGB_8888);
                bmp.eraseColor(0xFFFFFFFF);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                final int count = renderer.getPageCount();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (currentPage != null) currentPage.close();
                    currentPage = null;
                    pdfPage.setImageBitmap(bmp);
                    pageIndex = index;
                    ((TextView) requireView().findViewById(R.id.tv_page_number))
                            .setText(getString(R.string.reader_page_of, index + 1, count));
                    saveProgress();
                });
            } catch (Exception ignored) {
                // render failures leave the previous page on screen
            }
        });
    }

    private void attachZoom() {
        if (pdfPage == null) return;
        final ScaleGestureDetector detector = new ScaleGestureDetector(requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector d) {
                        pdfPage.setScaleX(pdfPage.getScaleX() * d.getScaleFactor());
                        pdfPage.setScaleY(pdfPage.getScaleY() * d.getScaleFactor());
                        return true;
                    }
                });
        pdfPage.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    // ----------------------------------------------------------------- EPUB

    private void openEpub(File file) {
        ServiceLocator.executors().io.execute(() -> {
            try {
                EpubParser.Epub parsed = EpubParser.parse(file);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    epub = parsed;
                    webView.setVisibility(View.VISIBLE);
                    epubControls.setVisibility(View.VISIBLE);
                    renderEpubSection();
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        stateText.setText(R.string.reader_open_failed));
            }
        });
    }

    private void renderEpubSection() {
        if (epub == null || webView == null || epub.sections.isEmpty()) return;
        sectionIndex = Math.max(0, Math.min(sectionIndex, epub.sections.size() - 1));
        String bg = "dark".equals(theme) ? "#1C1B18" : "sepia".equals(theme) ? "#F5ECD9" : "#FFFFFF";
        String fg = "dark".equals(theme) ? "#EDE9DE" : "#1B1B1B";
        String html = "<html dir=\"auto\"><head><meta name=\"viewport\" content=\"width=device-width, "
                + "initial-scale=1\"/><style>body{background:" + bg + ";color:" + fg
                + ";font-size:" + (16 * fontScale) + "px;padding:16px;line-height:1.6}"
                + "[dir=rtl]{text-align:right}</style></head><body>"
                + epub.sections.get(sectionIndex).html + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        ((TextView) requireView().findViewById(R.id.tv_section)).setText(
                getString(R.string.reader_section_of, sectionIndex + 1, epub.sections.size()));
        saveProgress();
    }

    private void showToc() {
        if (epub == null) return;
        String[] titles = new String[epub.sections.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = epub.sections.get(i).title;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reader_toc)
                .setItems(titles, (d, w) -> {
                    sectionIndex = w;
                    renderEpubSection();
                }).show();
    }

    private void showEpubSearch() {
        if (epub == null) return;
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.reader_search_hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reader_search_title)
                .setView(input)
                .setPositiveButton(R.string.action_search, (d, w) -> {
                    String q = input.getText().toString().toLowerCase(java.util.Locale.ROOT);
                    StringBuilder results = new StringBuilder();
                    for (int i = 0; i < epub.sections.size(); i++) {
                        String plain = epub.sections.get(i).html
                                .replaceAll("<[^>]+>", " ").toLowerCase(java.util.Locale.ROOT);
                        if (plain.contains(q)) results.append(i).append(": ")
                                .append(epub.sections.get(i).title).append('\n');
                    }
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.reader_search_results)
                            .setMessage(results.length() == 0
                                    ? getString(R.string.search_no_results_group)
                                    : results.toString().trim())
                            .setPositiveButton(R.string.ok, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveProgress() {
        final int percent;
        if (renderer != null) {
            percent = renderer.getPageCount() == 0 ? 0
                    : (int) (100L * pageIndex / renderer.getPageCount());
        } else if (epub != null) {
            percent = (int) (100L * sectionIndex / Math.max(1, epub.sections.size()));
        } else {
            return;
        }
        ServiceLocator.executors().io.execute(() ->
                ServiceLocator.userContentRepository()
                        .putProgress("book:" + bookId, percent, "{}"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (currentPage != null) currentPage.close();
            if (renderer != null) renderer.close();
        } catch (Exception ignored) { }
    }
}
