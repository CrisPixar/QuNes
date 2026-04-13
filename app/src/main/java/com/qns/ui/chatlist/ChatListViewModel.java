package com.qns.ui.chatlist;
import androidx.lifecycle.*;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.repository.ChatRepository;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.*;

@HiltViewModel
public class ChatListViewModel extends ViewModel {
    private final ChatRepository repo;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<List<ChatEntity>> chats   = new MutableLiveData<>();
    public final MutableLiveData<Boolean>          loading = new MutableLiveData<>(false);
    public final MutableLiveData<String>           error   = new MutableLiveData<>();

    @Inject
    public ChatListViewModel(ChatRepository repo) {
        this.repo = repo;
        bag.add(repo.observeChats().observeOn(AndroidSchedulers.mainThread()).subscribe(chats::setValue));
        syncChats();
    }
    public void syncChats() {
        loading.setValue(true);
        bag.add(repo.syncChats().observeOn(AndroidSchedulers.mainThread())
            .subscribe(()->loading.setValue(false), e->{ loading.setValue(false); error.setValue(e.getMessage()); }));
    }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }
}
