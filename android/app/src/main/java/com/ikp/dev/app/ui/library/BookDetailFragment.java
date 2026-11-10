package com.ikp.dev.app.ui.library;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.MessageKeys;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Published book detail (real backend data). Download: shows only when the
 * licence allows and the user is signed in; because the public API does not
 * expose file IDs the action explains the gap honestly (M3 API report) — no
 * broken or fake download.
 */
public final class BookDetailFragment extends Fragment {

    private BookDetailViewModel viewModel;
    private StateView stateView;
    private View content;
    private Button btnDownload;
    private TextView tvDownloadRestricted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BookDetailViewModel.class);
        stateView = view.findViewById(R.id.detail_state);
        content = view.findViewById(R.id.detail_content);
        btnDownload = view.findViewById(R.id.btn_download);
        tvDownloadRestricted = view.findViewById(R.id.tv_download_restricted);

        ImageButton back = view.findViewById(R.id.btn_back);
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        btnDownload.setOnClickListener(v -> startDownload());

        Bundle args = getArguments();
        String bookId = args == null ? null : args.getString("bookId");
        if (bookId == null || bookId.isEmpty()) {
            stateView.renderError(null);
            return;
        }
        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            switch (state.kind) {
                case LOADING:
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderLoading();
                    content.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    stateView.setVisibility(View.GONE);
                    content.setVisibility(View.VISIBLE);
                    if (state.data != null) {
                        render(view, state.data);
                    }
                    break;
                case EMPTY:
                    stateView.renderEmpty();
                    content.setVisibility(View.GONE);
                    break;
                case ERROR:
                    stateView.renderError(state.error);
                    stateView.setOnRetry(() -> viewModel.load(bookId));
                    content.setVisibility(View.GONE);
                    break;
            }
        });
        viewModel.load(bookId);
    }

    private void render(View view, Models.BookDetail b) {
        ((TextView) view.findViewById(R.id.tv_title)).setText(orDash(b.title));
        ((TextView) view.findViewById(R.id.tv_subtitle)).setText(orDash(b.subtitle));
        ((TextView) view.findViewById(R.id.tv_authors)).setText(
                b.authors.isEmpty() ? "—" : TextUtils.join(", ", b.authors));
        ((TextView) view.findViewById(R.id.tv_description)).setText(
                b.description == null || b.description.isEmpty() ? "—" : b.description);
        ((TextView) view.findViewById(R.id.tv_language)).setText(orDash(b.language));
        ((TextView) view.findViewById(R.id.tv_publisher)).setText(orDash(b.publisher));
        ((TextView) view.findViewById(R.id.tv_year)).setText(
                b.publicationYear == null ? "—" : String.valueOf(b.publicationYear));
        ((TextView) view.findViewById(R.id.tv_pages)).setText(
                b.pageCount == null ? "—" : String.valueOf(b.pageCount));
        ((TextView) view.findViewById(R.id.tv_formats)).setText(
                b.formats.isEmpty() ? "—" : TextUtils.join(", ", b.formats));
        ((TextView) view.findViewById(R.id.tv_copyright)).setText(orDash(b.copyrightInfo));

        boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
        if (b.downloadAllowed && signedIn) {
            btnDownload.setVisibility(View.VISIBLE);
            tvDownloadRestricted.setVisibility(View.GONE);
        } else {
            btnDownload.setVisibility(View.GONE);
            tvDownloadRestricted.setVisibility(View.VISIBLE);
            if (!b.downloadAllowed) {
                tvDownloadRestricted.setText(R.string.book_download_not_allowed);
            } else {
                tvDownloadRestricted.setText(R.string.error_forbidden);
            }
        }
    }

    private void startDownload() {
        Bundle args = getArguments();
        String bookId = args == null ? null : args.getString("bookId");
        if (bookId == null || bookId.isEmpty()) return;
        btnDownload.setEnabled(false);
        btnDownload.setText(R.string.download_in_progress);
        String bookTitle = ((TextView) requireView().findViewById(R.id.tv_title)).getText().toString();
        ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<com.ikp.dev.app.data.local.entity.UserDataEntities.DownloadMetadataEntity>
                    result = ServiceLocator.offlineDownloadsRepository().downloadBook(bookId, bookTitle);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                btnDownload.setEnabled(true);
                btnDownload.setText(R.string.book_download_action);
                if (result.isSuccess()) {
                    Toast.makeText(requireContext(), R.string.download_saved,
                            Toast.LENGTH_LONG).show();
                } else {
                    String key = result.error == null ? "error_unknown" : result.error.messageKey;
                    Toast.makeText(requireContext(), MessageKeys.resIdForKey(key),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}
