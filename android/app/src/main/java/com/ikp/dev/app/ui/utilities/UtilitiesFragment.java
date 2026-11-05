package com.ikp.dev.app.ui.utilities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;

/** Utilities hub (M4 §13): Prayer Times, Qibla, Tasbih + module shortcuts. */
public class UtilitiesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_utilities, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        v.findViewById(R.id.card_prayer).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.prayerTimesFragment));
        v.findViewById(R.id.card_qibla).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.qiblaFragment));
        v.findViewById(R.id.card_tasbih).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.tasbihFragment));
        v.findViewById(R.id.card_duas).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_duas));
        v.findViewById(R.id.card_calendar).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_calendar));
        v.findViewById(R.id.card_dictionary).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_dictionary));
    }
}
