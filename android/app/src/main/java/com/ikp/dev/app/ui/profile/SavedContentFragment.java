package com.ikp.dev.app.ui.profile;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.error.ApiError;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.data.local.entity.UserDataEntities;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.MessageKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Profile destinations for server-backed bookmarks/notes and on-device downloads. */
public final class SavedContentFragment extends Fragment {

    public static final String SECTION_BOOKMARKS = "bookmarks";
    public static final String SECTION_DOWNLOADS = "downloads";
    public static final String SECTION_NOTES = "notes";

    private static final int TYPE_BOOKMARK = 1;
    private static final int TYPE_NOTE = 2;
    private static final int TYPE_DOWNLOAD = 3;
    private static final Pattern VALID_REFERENCE = Pattern.compile(
            "^(ayah:\\d{1,3}:\\d{1,3}|hadith:\\d+|book:[0-9a-fA-F-]{36}|dua:\\d+|dict:\\d+"
                    + "|research:[0-9a-fA-F-]{36}|course:[0-9a-fA-F-]{36}|lesson:[0-9a-fA-F-]{36}"
                    + "|fatwa:[0-9a-fA-F-]{36}|scholar:[0-9a-fA-F-]{36})$");

    private final List<Row> rows = new ArrayList<>();
    private RowsAdapter adapter;
    private String section;
    private TextView title;
    private TextView subtitle;
    private TextView stateTitle;
    private TextView stateMessage;
    private View stateContainer;
    private ProgressBar progress;
    private RecyclerView recycler;
    private com.google.android.material.button.MaterialButton stateAction;
    private com.google.android.material.button.MaterialButton retry;
    private View fab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        section = args == null ? SECTION_BOOKMARKS : args.getString("section", SECTION_BOOKMARKS);
        title = view.findViewById(R.id.tv_title);
        subtitle = view.findViewById(R.id.tv_subtitle);
        stateTitle = view.findViewById(R.id.tv_state_title);
        stateMessage = view.findViewById(R.id.tv_state_message);
        stateContainer = view.findViewById(R.id.state_container);
        progress = view.findViewById(R.id.progress);
        recycler = view.findViewById(R.id.rv_items);
        stateAction = view.findViewById(R.id.btn_state_action);
        retry = view.findViewById(R.id.btn_retry);
        fab = view.findViewById(R.id.fab_action);

        configureHeader();
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        retry.setOnClickListener(v -> load());
        stateAction.setOnClickListener(v -> stateAction());
        fab.setOnClickListener(v -> showNoteEditor(null));

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RowsAdapter();
        recycler.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void configureHeader() {
        int titleId;
        int subtitleId;
        if (SECTION_DOWNLOADS.equals(section)) {
            titleId = R.string.saved_downloads_title;
            subtitleId = R.string.saved_downloads_subtitle;
            fab.setVisibility(View.GONE);
        } else if (SECTION_NOTES.equals(section)) {
            titleId = R.string.saved_notes_title;
            subtitleId = R.string.saved_notes_subtitle;
            fab.setVisibility(View.GONE);
            ((com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton) fab)
                    .setText(R.string.saved_notes_add);
        } else {
            section = SECTION_BOOKMARKS;
            titleId = R.string.saved_bookmarks_title;
            subtitleId = R.string.saved_bookmarks_subtitle;
            fab.setVisibility(View.GONE);
        }
        title.setText(titleId);
        subtitle.setText(subtitleId);
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        stateTitle.setVisibility(View.GONE);
        stateMessage.setVisibility(View.GONE);
        stateAction.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        stateContainer.setVisibility(View.VISIBLE);

        if (SECTION_DOWNLOADS.equals(section)) {
            ServiceLocator.executors().io.execute(() -> {
                List<UserDataEntities.DownloadMetadataEntity> downloaded =
                        ServiceLocator.database().userDataDao().downloads();
                List<Row> loaded = new ArrayList<>();
                for (UserDataEntities.DownloadMetadataEntity item : downloaded) {
                    if (item.filePath == null || !new File(item.filePath).isFile()) {
                        ServiceLocator.database().userDataDao().deleteDownload(item.mediaId);
                        continue;
                    }
                    Row row = new Row(TYPE_DOWNLOAD);
                    row.title = item.title;
                    row.ref = item.bookId;
                    row.path = item.filePath;
                    row.format = item.format;
                    row.detail = format(item.format) + " · " + size(item.sizeBytes);
                    loaded.add(row);
                }
                if (isAdded()) requireActivity().runOnUiThread(() -> renderRows(
                        R.string.saved_empty_downloads_title, R.string.saved_empty_downloads,
                        R.string.saved_open_bookstore, true, loaded));
            });
            return;
        }

        if (!ServiceLocator.sessionManager().isSignedIn()) {
            progress.setVisibility(View.GONE);
            showState(R.string.saved_sign_in_title, R.string.personal_sign_in_body,
                    R.string.personal_sign_in, true);
            return;
        }

        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> result;
            List<Row> loaded = new ArrayList<>();
            if (SECTION_NOTES.equals(section)) {
                Outcome<List<ContentDtos.NoteDto>> notes = ServiceLocator.userContentRepository().notes();
                result = notes;
                if (notes.isSuccess() && notes.value != null) {
                    for (ContentDtos.NoteDto note : notes.value) {
                        Row row = new Row(TYPE_NOTE);
                        row.ref = note.refKey;
                        row.title = referenceTitle(note.refKey);
                        row.detail = note.body;
                        row.date = note.updatedAt;
                        loaded.add(row);
                    }
                }
            } else {
                Outcome<List<ContentDtos.BookmarkDto>> bookmarks =
                        ServiceLocator.userContentRepository().bookmarks();
                result = bookmarks;
                if (bookmarks.isSuccess() && bookmarks.value != null) {
                    for (ContentDtos.BookmarkDto bookmark : bookmarks.value) {
                        Row row = new Row(TYPE_BOOKMARK);
                        row.ref = bookmark.refKey;
                        row.title = referenceTitle(bookmark.refKey);
                        row.detail = bookmark.note == null || bookmark.note.trim().isEmpty()
                                ? "Saved reference · " + bookmark.refKey : bookmark.note;
                        row.date = bookmark.createdAt;
                        loaded.add(row);
                    }
                }
            }
            if (!isAdded()) return;
            Outcome<?> finalResult = result;
            requireActivity().runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (finalResult.isSuccess()) {
                    renderRows(SECTION_NOTES.equals(section) ? R.string.saved_empty_notes_title
                                    : R.string.saved_empty_bookmarks_title,
                            SECTION_NOTES.equals(section) ? R.string.saved_empty_notes
                                    : R.string.saved_empty_bookmarks,
                            0, false, loaded);
                } else if (finalResult.error != null
                        && ErrorMapper.AUTH_UNAUTHORIZED.equals(finalResult.error.code)) {
                    showState(R.string.saved_sign_in_title, R.string.personal_sign_in_body,
                            R.string.personal_sign_in, true);
                } else {
                    showState(R.string.saved_load_error_title, R.string.saved_load_error_body,
                            0, false);
                    retry.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void renderRows(int emptyTitle, int emptyBody, int emptyAction,
                            boolean browseAction, List<Row> loaded) {
        progress.setVisibility(View.GONE);
        if (SECTION_NOTES.equals(section)) fab.setVisibility(View.VISIBLE);
        rows.clear();
        rows.addAll(loaded);
        adapter.notifyDataSetChanged();
        if (!rows.isEmpty()) {
            stateContainer.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            return;
        }
        showState(emptyTitle, emptyBody, emptyAction, browseAction);
        if (SECTION_NOTES.equals(section)) stateAction.setVisibility(View.GONE);
    }

    private void showState(int titleRes, int messageRes, int actionRes, boolean browseAction) {
        progress.setVisibility(View.GONE);
        if (SECTION_NOTES.equals(section)) {
            boolean canCreate = titleRes != R.string.saved_sign_in_title
                    && titleRes != R.string.saved_load_error_title;
            fab.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        }
        stateContainer.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        stateTitle.setVisibility(View.VISIBLE);
        stateMessage.setVisibility(View.VISIBLE);
        stateTitle.setText(titleRes);
        stateMessage.setText(messageRes);
        stateAction.setVisibility(actionRes == 0 ? View.GONE : View.VISIBLE);
        if (actionRes != 0) stateAction.setText(actionRes);
        stateAction.setTag(browseAction ? "browse" : "login");
        retry.setVisibility(View.GONE);
    }

    private void stateAction() {
        Object action = stateAction.getTag();
        if ("browse".equals(action)) {
            NavHostFragment.findNavController(this).navigate(R.id.libraryFragment);
        } else {
            NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
        }
    }

    private void showNoteEditor(@Nullable Row existing) {
        android.widget.LinearLayout form = new android.widget.LinearLayout(requireContext());
        form.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);
        EditText reference = new EditText(requireContext());
        reference.setSingleLine(true);
        reference.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        reference.setHint(R.string.saved_notes_reference_hint);
        EditText body = new EditText(requireContext());
        body.setMinLines(4);
        body.setGravity(android.view.Gravity.TOP);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setHint(R.string.saved_notes_body_hint);
        form.addView(reference, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.LinearLayout.LayoutParams bodyParams = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = pad / 2;
        form.addView(body, bodyParams);
        if (existing != null) {
            reference.setText(existing.ref);
            reference.setEnabled(false);
            body.setText(existing.detail);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.saved_notes_add : R.string.saved_notes_title)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.saved_notes_save, (dialog, which) -> {
                    String ref = reference.getText().toString().trim();
                    String text = body.getText().toString().trim();
                    if (!VALID_REFERENCE.matcher(ref).matches() || text.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.saved_note_reference_error,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveNote(ref, text);
                }).show();
    }

    private void saveNote(String ref, String text) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<Map<String, Object>> result = ServiceLocator.userContentRepository().putNote(ref, text);
            showMutationResult(result, R.string.saved_note_saved);
        });
    }

    private void deleteRow(Row row) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.saved_remove_confirmation, row.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    if (row.type == TYPE_DOWNLOAD) {
                        ServiceLocator.executors().io.execute(() -> {
                            File file = new File(row.path);
                            boolean deleted = !file.exists() || file.delete();
                            if (deleted) ServiceLocator.database().userDataDao().deleteDownload(
                                    row.ref.replaceAll("[^A-Za-z0-9_-]", "_") + ":" + row.format);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                if (deleted) {
                                    Toast.makeText(requireContext(), R.string.saved_download_removed,
                                            Toast.LENGTH_SHORT).show();
                                    load();
                                } else Toast.makeText(requireContext(), R.string.error_generic,
                                        Toast.LENGTH_LONG).show();
                            });
                        });
                    } else {
                        ServiceLocator.executors().io.execute(() -> {
                            Outcome<Map<String, Object>> result = row.type == TYPE_NOTE
                                    ? ServiceLocator.userContentRepository().deleteNote(row.ref)
                                    : ServiceLocator.userContentRepository().deleteBookmark(row.ref);
                            showMutationResult(result, R.string.saved_item_removed);
                        });
                    }
                }).show();
    }

    private void showMutationResult(Outcome<?> result, int successMessage) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (result != null && result.isSuccess()) {
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
                load();
            } else {
                String key = result == null || result.error == null
                        ? "error_unknown" : result.error.messageKey;
                Toast.makeText(requireContext(), MessageKeys.resIdForKey(key), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openRow(Row row) {
        if (row.type == TYPE_DOWNLOAD) {
            Bundle args = new Bundle();
            args.putString("mode", "downloaded");
            args.putString("bookId", row.ref);
            args.putString("format", row.format);
            args.putString("title", row.title);
            args.putString("localPath", row.path);
            args.putBoolean("textSearchable", true);
            NavHostFragment.findNavController(this).navigate(R.id.readerFragment, args);
            return;
        }
        if (row.type == TYPE_NOTE) {
            showNoteEditor(row);
            return;
        }
        openReference(row.ref);
    }

    private void openReference(String ref) {
        if (ref == null) return;
        String[] parts = ref.split(":");
        try {
            Bundle args = new Bundle();
            switch (parts[0]) {
                case "ayah":
                    args.putInt("surahId", Integer.parseInt(parts[1]));
                    NavHostFragment.findNavController(this).navigate(R.id.quranReaderFragment, args);
                    break;
                case "book":
                    args.putString("bookId", parts[1]);
                    NavHostFragment.findNavController(this).navigate(R.id.bookDetailFragment, args);
                    break;
                case "hadith":
                    NavHostFragment.findNavController(this).navigate(R.id.hadithCollectionsFragment);
                    break;
                case "dua":
                    NavHostFragment.findNavController(this).navigate(R.id.duasFragment);
                    break;
                case "dict":
                    NavHostFragment.findNavController(this).navigate(R.id.dictionaryFragment);
                    break;
                case "research":
                case "fatwa":
                case "scholar":
                    NavHostFragment.findNavController(this).navigate(R.id.researchFragment);
                    break;
                case "course":
                case "lesson":
                    NavHostFragment.findNavController(this).navigate(R.id.academyFragment);
                    break;
                default:
                    Toast.makeText(requireContext(), R.string.saved_reference_unavailable,
                            Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.saved_reference_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static String referenceTitle(String ref) {
        if (ref == null) return "Saved reference";
        String[] part = ref.split(":");
        if (part.length == 3 && "ayah".equals(part[0])) return "Quran · " + part[1] + ":" + part[2];
        if (part.length > 1) return part[0].substring(0, 1).toUpperCase(Locale.ROOT)
                + part[0].substring(1) + " · " + part[1];
        return ref;
    }

    private static String format(String format) {
        return format == null ? "Book" : format.toUpperCase(Locale.ROOT);
    }

    private static String size(Long bytes) {
        if (bytes == null || bytes <= 0) return "Offline";
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private final class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_content, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            holder.title.setText(row.title == null ? "Saved item" : row.title);
            holder.detail.setText(row.detail == null ? "" : row.detail);
            holder.icon.setImageResource(row.type == TYPE_DOWNLOAD ? R.drawable.ic_download
                    : row.type == TYPE_NOTE ? R.drawable.ic_action_notes : R.drawable.ic_bookmark_filled);
            holder.action.setContentDescription(getString(R.string.cd_more_actions));
            holder.itemView.setOnClickListener(v -> openRow(row));
            holder.action.setOnClickListener(v -> deleteRow(row));
        }

        @Override public int getItemCount() { return rows.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView detail;
            final ImageView icon;
            final ImageButton action;

            Holder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_item_title);
                detail = itemView.findViewById(R.id.tv_item_detail);
                icon = itemView.findViewById(R.id.iv_kind);
                action = itemView.findViewById(R.id.btn_item_action);
            }
        }
    }

    private static final class Row {
        final int type;
        String title;
        String detail;
        String date;
        String ref;
        String path;
        String format;
        Row(int type) { this.type = type; }
    }
}
