package com.ikp.dev.app.ui.personal;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.MessageKeys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Personal Knowledge Library (M4 §10): private uploads via the document
 * picker, edit/delete with confirmation, open in the shared reader.
 * STRICT PRIVACY: everything is scoped server-side to the signed-in user.
 */
public class PersonalLibraryFragment extends Fragment {

    private final List<ContentDtos.PersonalBookDto> books = new ArrayList<>();
    private PersonalBookAdapter adapter;
    private TextView stateText;
    private TextView stateTitle;
    private TextView collectionsText;
    private RecyclerView recycler;
    private View progress;
    private View stateContainer;
    private View signInButton;
    private View retryButton;
    private View stateUploadButton;
    private View uploadButton;
    private View collectionsSection;

    private final ActivityResultLauncher<String[]> openDocument =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) showUploadDialog(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_personal_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        stateTitle = v.findViewById(R.id.state_title);
        collectionsText = v.findViewById(R.id.text_collections);
        recycler = v.findViewById(R.id.recycler);
        progress = v.findViewById(R.id.progress);
        stateContainer = v.findViewById(R.id.state_container);
        signInButton = v.findViewById(R.id.btn_sign_in);
        retryButton = v.findViewById(R.id.btn_retry);
        stateUploadButton = v.findViewById(R.id.btn_state_upload);
        uploadButton = v.findViewById(R.id.button_upload);
        collectionsSection = v.findViewById(R.id.collections_section);
        adapter = new PersonalBookAdapter(this::open, this::actions);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        View.OnClickListener addBook = x -> openDocument.launch(
                new String[]{"application/pdf", "application/epub+zip"});
        uploadButton.setOnClickListener(addBook);
        stateUploadButton.setOnClickListener(addBook);
        v.findViewById(R.id.button_new_collection).setOnClickListener(x -> newCollection());
        v.findViewById(R.id.btn_retry).setOnClickListener(x -> load());
        v.findViewById(R.id.btn_sign_in).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (!isAdded() || getView() == null) return;
        progress.setVisibility(View.VISIBLE);
        stateContainer.setVisibility(View.VISIBLE);
        stateTitle.setVisibility(View.GONE);
        stateText.setVisibility(View.GONE);
        signInButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        stateUploadButton.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.PersonalBookDto>> r =
                    ServiceLocator.personalLibraryRepository().books(null, 1, 50);
            Outcome<List<ContentDtos.PersonalCollectionDto>> cols =
                    ServiceLocator.personalLibraryRepository().collections();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                boolean requiresLogin = requiresLogin(r) || requiresLogin(cols);
                if (requiresLogin) {
                    books.clear();
                    adapter.submit(books);
                    showState(R.string.personal_sign_in_title, R.string.personal_sign_in_body,
                            R.drawable.ic_shield_security, true, false, false);
                    setPrivateActionsVisible(false);
                } else if (r.isSuccess() && r.value != null) {
                    books.clear();
                    books.addAll(r.value);
                    adapter.submit(books);
                    setPrivateActionsVisible(true);
                    if (books.isEmpty()) {
                        showState(R.string.personal_empty_title, R.string.personal_empty_body,
                                R.drawable.ic_shortcut_books, false, false, true);
                    } else {
                        stateContainer.setVisibility(View.GONE);
                        recycler.setVisibility(View.VISIBLE);
                    }
                } else {
                    books.clear();
                    adapter.submit(books);
                    setPrivateActionsVisible(true);
                    showState(R.string.personal_load_error_title,
                            R.string.personal_load_error_body, R.drawable.ic_error,
                            false, true, false);
                }
                if (cols.isSuccess() && cols.value != null) {
                    StringBuilder sb = new StringBuilder();
                    for (ContentDtos.PersonalCollectionDto c : cols.value) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(c.name).append(" (").append(c.bookCount).append(')');
                    }
                    collectionsText.setText(sb.length() == 0
                            ? getString(R.string.personal_no_collections) : sb.toString());
                }
            });
        });
    }

    private boolean requiresLogin(Outcome<?> outcome) {
        return outcome != null && outcome.error != null
                && "AUTH-002".equals(outcome.error.code);
    }

    private void setPrivateActionsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        collectionsSection.setVisibility(visibility);
        uploadButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showState(int title, int message, int icon, boolean signIn,
                           boolean retry, boolean addBook) {
        stateContainer.setVisibility(View.VISIBLE);
        stateTitle.setVisibility(View.VISIBLE);
        stateTitle.setText(title);
        stateText.setVisibility(View.VISIBLE);
        stateText.setText(message);
        ((android.widget.ImageView) stateContainer.findViewById(R.id.state_icon)).setImageResource(icon);
        signInButton.setVisibility(signIn ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(retry ? View.VISIBLE : View.GONE);
        stateUploadButton.setVisibility(addBook ? View.VISIBLE : View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void open(ContentDtos.PersonalBookDto book) {
        Bundle args = new Bundle();
        args.putString("mode", "personal");
        args.putString("bookId", book.bookId);
        args.putString("format", book.format);
        args.putString("title", book.title);
        args.putBoolean("textSearchable",
                book.textSearchable != null && book.textSearchable);
        NavHostFragment.findNavController(this).navigate(R.id.readerFragment, args);
    }

    private void actions(ContentDtos.PersonalBookDto book) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(book.title)
                .setItems(new CharSequence[]{
                        getString(R.string.personal_action_edit),
                        getString(R.string.personal_action_delete),
                        getString(R.string.personal_action_add_to_collection)
                }, (dialog, which) -> {
                    if (which == 0) edit(book);
                    else if (which == 1) confirmDelete(book);
                    else addToCollection(book);
                }).show();
    }

    private void showUploadDialog(Uri uri) {
        View form = getLayoutInflater().inflate(R.layout.view_personal_book_form, null, false);
        EditText title = form.findViewById(R.id.input_title);
        EditText author = form.findViewById(R.id.input_author);
        EditText description = form.findViewById(R.id.input_description);
        title.setText(suggestedName(uri));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personal_upload_title)
                .setView(form)
                .setPositiveButton(R.string.action_upload, (d, w) -> {
                    File target = copyToCache(uri);
                    if (target == null) {
                        Toast.makeText(requireContext(), R.string.error_generic,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    progress.setVisibility(View.VISIBLE);
                    ServiceLocator.executors().io.execute(() -> {
                        Outcome<ContentDtos.PersonalBookDto> r =
                                ServiceLocator.personalLibraryRepository().upload(target,
                                        title.getText().toString().trim(),
                                        author.getText().toString().trim(),
                                        description.getText().toString().trim());
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            progress.setVisibility(View.GONE);
                            if (r.isSuccess()) {
                                load();
                            } else {
                                Toast.makeText(requireContext(),
                                        MessageKeys.resIdForKey(r.error == null
                                                ? "NET-UNKNOWN" : r.error.messageKey),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void edit(ContentDtos.PersonalBookDto book) {
        View form = getLayoutInflater().inflate(R.layout.view_personal_book_form, null, false);
        EditText title = form.findViewById(R.id.input_title);
        EditText author = form.findViewById(R.id.input_author);
        EditText description = form.findViewById(R.id.input_description);
        title.setText(book.title);
        author.setText(book.author);
        description.setText(book.description);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personal_edit_title)
                .setView(form)
                .setPositiveButton(R.string.action_save, (d, w) ->
                        ServiceLocator.executors().io.execute(() -> {
                            Outcome<ContentDtos.PersonalBookDto> result =
                                    ServiceLocator.personalLibraryRepository().patch(book.bookId,
                                    title.getText().toString().trim(),
                                    author.getText().toString().trim(),
                                    description.getText().toString().trim());
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> finishMutation(result));
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(ContentDtos.PersonalBookDto book) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personal_delete_title)
                .setMessage(getString(R.string.personal_delete_message, book.title))
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        ServiceLocator.executors().io.execute(() -> {
                            Outcome<java.util.Map<String, Object>> result =
                                    ServiceLocator.personalLibraryRepository().delete(book.bookId);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> finishMutation(result));
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void newCollection() {
        View form = getLayoutInflater().inflate(R.layout.view_personal_book_form, null, false);
        EditText title = form.findViewById(R.id.input_title);
        title.setHint(R.string.personal_collection_name_hint);
        form.findViewById(R.id.input_author).setVisibility(View.GONE);
        form.findViewById(R.id.input_description).setVisibility(View.GONE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personal_new_collection)
                .setView(form)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.personal_collection_name_hint,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ServiceLocator.executors().io.execute(() -> {
                        Outcome<java.util.Map<String, Object>> result =
                                ServiceLocator.personalLibraryRepository().createCollection(name);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> finishMutation(result));
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addToCollection(ContentDtos.PersonalBookDto book) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.PersonalCollectionDto>> cols =
                    ServiceLocator.personalLibraryRepository().collections();
            if (!isAdded() || !cols.isSuccess() || cols.value == null) return;
            requireActivity().runOnUiThread(() -> {
                if (cols.value.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.personal_no_collections,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                CharSequence[] names = new CharSequence[cols.value.size()];
                for (int i = 0; i < names.length; i++) names[i] = cols.value.get(i).name;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.personal_action_add_to_collection)
                        .setItems(names, (d, w) -> ServiceLocator.executors().io.execute(() -> {
                            Outcome<java.util.Map<String, Object>> result =
                                    ServiceLocator.personalLibraryRepository().addBookToCollection(
                                            cols.value.get(w).collectionId, book.bookId);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> finishMutation(result));
                        }))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void finishMutation(Outcome<?> result) {
        if (result != null && result.isSuccess()) {
            load();
            return;
        }
        String key = result == null || result.error == null
                ? "NET-UNKNOWN" : result.error.messageKey;
        Toast.makeText(requireContext(), MessageKeys.resIdForKey(key), Toast.LENGTH_LONG).show();
    }

    private String suggestedName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "";
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".epub")) name = name.substring(0, name.lastIndexOf('.'));
        return name.replace("%20", " ").replace('_', ' ');
    }

    private File copyToCache(Uri uri) {
        try {
            InputStream in = requireContext().getContentResolver().openInputStream(uri);
            if (in == null) return null;
            String name = "upload_" + System.currentTimeMillis()
                    + (uri.toString().toLowerCase().contains(".epub") ? ".epub" : ".pdf");
            File target = new File(requireContext().getCacheDir(), name);
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            in.close();
            return target;
        } catch (Exception e) {
            return null;
        }
    }
}
