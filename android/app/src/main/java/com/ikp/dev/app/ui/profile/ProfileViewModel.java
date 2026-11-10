package com.ikp.dev.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.data.repository.PersonalLibraryRepository;
import com.ikp.dev.app.data.repository.UserContentRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.List;

/**
 * Profile ViewModel: real /auth/me data, learning & library metrics, sessions, logout.
 */
public final class ProfileViewModel extends ViewModel {

    public static class ProfileStats {
        public final int courses;
        public final int bookmarks;
        public final int downloads;
        public final int streakDays;

        public ProfileStats(int courses, int bookmarks, int downloads, int streakDays) {
            this.courses = courses;
            this.bookmarks = bookmarks;
            this.downloads = downloads;
            this.streakDays = streakDays;
        }
    }

    private final MutableLiveData<UiState<Models.User>> profileState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> logoutDone = new MutableLiveData<>();
    private final MutableLiveData<ProfileStats> stats = new MutableLiveData<>(new ProfileStats(0, 0, 0, 0));

    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    public ProfileViewModel() {
        this(ServiceLocator.authRepository(), ServiceLocator.sessionManager());
    }

    public ProfileViewModel(AuthRepository authRepository, SessionManager sessionManager) {
        this.authRepository = authRepository;
        this.sessionManager = sessionManager;
    }

    public LiveData<UiState<Models.User>> profileState() {
        return profileState;
    }

    public LiveData<Boolean> logoutDone() {
        return logoutDone;
    }

    public LiveData<ProfileStats> stats() {
        return stats;
    }

    public boolean isSignedIn() {
        return sessionManager.isSignedIn();
    }

    public void loadProfile() {
        if (!isSignedIn()) {
            profileState.postValue(UiState.success(null));
            ServiceLocator.executors().io.execute(() -> {
                stats.postValue(new ProfileStats(0, 0, localDownloadCount(), 0));
            });
            return;
        }
        profileState.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<Models.User> r = authRepository.me();
            if (r.isSuccess() && r.value != null) {
                profileState.postValue(UiState.success(r.value));
            } else if (r.isSuccess()) {
                profileState.postValue(UiState.empty());
            } else {
                profileState.postValue(UiState.error(r.error));
            }

            // Also load user metrics asynchronously
            try {
                int bookmarkCount = 0;
                UserContentRepository ucRepo = ServiceLocator.userContentRepository();
                Outcome<List<ContentDtos.BookmarkDto>> bmRes = ucRepo.bookmarks();
                if (bmRes.isSuccess() && bmRes.value != null) {
                    bookmarkCount = bmRes.value.size();
                }

                int downloadCount = localDownloadCount();

                int coursesCount = 0;
                AcademyRepository acRepo = ServiceLocator.academyRepository();
                if (acRepo != null) {
                    var cRes = acRepo.courses(1, 100);
                    if (cRes.isSuccess() && cRes.value != null) {
                        coursesCount = cRes.value.size();
                    }
                }

                stats.postValue(new ProfileStats(coursesCount, bookmarkCount, downloadCount, 0));
            } catch (Exception ignored) {
                stats.postValue(new ProfileStats(0, 0, localDownloadCount(), 0));
            }
        });
    }

    private int localDownloadCount() {
        int count = 0;
        try {
            for (com.ikp.dev.app.data.local.entity.UserDataEntities.DownloadMetadataEntity row
                    : ServiceLocator.database().userDataDao().downloads()) {
                if (row.filePath != null && new java.io.File(row.filePath).isFile()) {
                    count++;
                } else if (row.mediaId != null) {
                    ServiceLocator.database().userDataDao().deleteDownload(row.mediaId);
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    public void logout() {
        ServiceLocator.executors().io.execute(() -> {
            authRepository.logout();
            logoutDone.postValue(Boolean.TRUE);
        });
    }
}
