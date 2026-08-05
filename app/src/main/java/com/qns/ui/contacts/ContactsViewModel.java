package com.qns.ui.contacts;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public final class ContactsViewModel extends ViewModel {
    private final ApiService api;
    private final ServerRepository servers;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<List<Contact>> contacts = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public ContactsViewModel(ApiService api, ServerRepository servers) {
        this.api = api;
        this.servers = servers;
    }

    public void search(String query) {
        String value = query == null ? "" : query.trim();
        if (value.length() < 2) {
            contacts.setValue(new ArrayList<>());
            error.setValue("Введите минимум 2 символа");
            return;
        }
        error.setValue(null);
        loading.setValue(true);
        bag.add(api.searchUsers(servers.current().api("api/users/search"), value)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(raw -> {
                List<Contact> result = new ArrayList<>();
                for (Map<String, Object> item : raw) result.add(new Contact(
                    string(item, "id"),
                    string(item, "username"),
                    Boolean.TRUE.equals(item.get("isScam"))
                ));
                contacts.setValue(result);
                loading.setValue(false);
            }, valueError -> {
                loading.setValue(false);
                error.setValue(valueError.getMessage());
            }));
    }

    public void openChat(String userId, OpenChatListener listener) {
        bag.add(api.createChat(
                servers.current().api("api/chats"),
                Map.of("type", "direct", "memberIds", java.util.List.of(userId))
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(result -> listener.onOpen(string(result, "chatId")), value -> error.setValue(value.getMessage())));
    }

    private static String string(Map<String, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    protected void onCleared() {
        bag.clear();
        super.onCleared();
    }

    public interface OpenChatListener { void onOpen(String chatId); }

    public static final class Contact {
        public final String id;
        public final String username;
        public final boolean scam;

        public Contact(String id, String username, boolean scam) {
            this.id = id;
            this.username = username;
            this.scam = scam;
        }
    }
}
