package com.example.jetpackdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import android.app.Activity;
import android.os.Bundle;

import com.example.jetpackdemo.LifeCycle.DefaultObserver;
import com.example.jetpackdemo.LifeCycle.MyLifeCycleObserver;

public class MyActivity extends Activity implements LifecycleOwner {
    LifecycleRegistry mLifeCycleRegistry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        mLifeCycleRegistry = new LifecycleRegistry(this);
        mLifeCycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        getLifecycle().addObserver(new DefaultObserver());
        getLifecycle().addObserver(new MyLifeCycleObserver());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mLifeCycleRegistry.setCurrentState(Lifecycle.State.STARTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLifeCycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLifeCycleRegistry.setCurrentState(Lifecycle.State.STARTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifeCycleRegistry;
    }
}