package com.qns.ui.server;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.remote.ServerProfile;
import com.qns.data.remote.ServerRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class ServerViewModel extends ViewModel {
    private final ServerRepository repository;
    public final MutableLiveData<List<ServerProfile>> servers = new MutableLiveData<>();
    public final MutableLiveData<ServerProfile> current = new MutableLiveData<>();
    public final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public ServerViewModel(ServerRepository repository) {
        this.repository = repository;
        refresh();
    }

    public void refresh() {
        servers.setValue(repository.getServers());
        current.setValue(repository.current());
    }

    public void select(String id) {
        try {
            repository.select(id);
            refresh();
        } catch (RuntimeException errorValue) {
            error.setValue(errorValue.getMessage());
        }
    }

    public void add(String name, String url) {
        try {
            repository.addCustom(name, url);
            error.setValue(null);
            refresh();
        } catch (RuntimeException errorValue) {
            error.setValue(errorValue.getMessage());
        }
    }

    public void remove(String id) {
        repository.removeCustom(id);
        refresh();
    }
}
