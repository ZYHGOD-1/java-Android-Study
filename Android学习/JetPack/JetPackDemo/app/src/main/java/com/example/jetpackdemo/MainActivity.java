package com.example.jetpackdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.jetpackdemo.LifeCycle.DefaultObserver;
import com.example.jetpackdemo.LifeCycle.MyLifeCycleObserver;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getLifecycle().addObserver(new MyLifeCycleObserver());
        getLifecycle().addObserver(new DefaultObserver());
        TextView textView = findViewById(R.id.text_hello);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,MyActivity.class));
            }
        });
    }
}