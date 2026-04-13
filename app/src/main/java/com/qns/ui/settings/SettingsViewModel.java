package com.qns.ui.settings;
import androidx.lifecycle.*;
import com.qns.data.repository.AuthRepository;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.*;

@HiltViewModel
public class SettingsViewModel extends ViewModel {
    private final AuthRepository repo;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<Boolean> loggedOut = new MutableLiveData<>();

    @Inject
    public SettingsViewModel(AuthRepository repo) { this.repo = repo; }

    public void logout() {
        bag.add(repo.logout().observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> loggedOut.setValue(true)));
    }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }
}
